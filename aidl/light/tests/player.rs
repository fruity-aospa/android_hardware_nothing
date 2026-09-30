use super::*;
use crate::controller::Node;
use crate::device::{Device, Shell};
use std::fs;
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

fn fixture() -> (&'static Device, std::path::PathBuf, Worker) {
    let dir = std::env::temp_dir().join(format!(
        "glyph-native-test-{}-{}",
        std::process::id(),
        NEXT.fetch_add(1, Ordering::Relaxed)
    ));
    fs::create_dir(&dir).unwrap();
    for node in [
        "frame_brightness",
        "all_white_brightness",
        "single_brightness",
        "operating_mode",
        "always_on",
    ] {
        fs::write(dir.join(node), "").unwrap();
    }
    let device = Device::for_test(
        Box::leak(dir.to_str().unwrap().to_owned().into_boxed_str()),
        Shell::Black,
    );
    let (_, requests) = mpsc::channel();
    let worker = Worker {
        device,
        requests,
        resting: vec![0; 36],
        playback: None,
        redraw: None,
        recording: None,
        drawn: vec![0; 36],
        effects: BTreeMap::new(),
    };
    (device, dir, worker)
}

fn last_frame(device: &Device) -> Vec<u32> {
    device
        .controller
        .history()
        .iter()
        .rev()
        .find(|(node, _)| *node == Node::Frame)
        .unwrap()
        .1
        .split_whitespace()
        .map(|v| v.parse().unwrap())
        .collect()
}

#[test]
fn camera_commands_keep_stock_countdowns_and_all_off_encodings() {
    let camera = crate::device::DEVICE.camera;
    for (command, frames) in [(15, 182), (43, 601)] {
        let effect = camera.countdown(command, 255).unwrap();
        assert_eq!(effect.frames(), frames);
        assert_eq!(
            effect.frame_period,
            std::time::Duration::from_micros(16_666)
        );
        assert_eq!(effect.tracks.len(), 36);
        assert_eq!(effect.frame(0, &[255; 36]), vec![0; 36]);
        assert_eq!(effect.frame(frames - 1, &[255; 36]), vec![0; 36]);
        assert_eq!(effect.frame(1, &[0; 36])[19], 255);
        assert_eq!(effect.frame(1, &[0; 36])[20], 0);
        assert_eq!(effect.frame(1, &[0; 36])[35], 255);
    }
    for command in [0, 3, 6, 10, 14, 42] {
        let fill = camera.fill(command).unwrap();
        assert!(!fill.lit);
        assert!(!fill.camera_only);
    }
    assert!(camera.fill(7).unwrap().lit);
    assert!(!camera.fill(7).unwrap().camera_only);
    assert!(!camera.fill(11).unwrap().camera_only);
    assert!(camera.fill(11).unwrap().lit);
    assert!(camera.countdown(120, 255).is_none());
    assert!(camera.fill(120).is_none());
}

#[test]
fn shell_recording_calibration_preserves_gray_without_lowering_other_strip_levels() {
    for (text, shell, level) in [
        ("BLACK", Shell::Black, 255),
        ("GRAY\n", Shell::Gray, 140),
        ("WHITE", Shell::White, 255),
    ] {
        assert!(Shell::parse(text) == shell);
        let device = crate::device::DEVICE.for_shell(shell);
        assert_eq!(device.camera.recording_level, level);
        assert_eq!(device.max_brightness, 255);
        assert_eq!(device.camera.recording_segment, Some(15));
    }
}

#[test]
fn off_cancels_a_queued_countdown_and_stale_deadline_cannot_relight() {
    let (device, dir, mut worker) = fixture();
    worker.handle(Command::Play(device.camera.countdown(43, 255).unwrap()));
    let first = worker.playback.as_ref().unwrap().due;
    worker.tick(first);
    worker.tick(first + std::time::Duration::from_micros(16_666));
    assert_eq!(last_frame(device)[19], 255);
    let (reply, result) = mpsc::channel();
    worker.handle(Command::SetAll { level: 0, reply });
    result.recv().unwrap().unwrap();
    let count = device.controller.history().len();
    worker.tick(first + std::time::Duration::from_secs(30));
    assert!(worker.playback.is_none());
    assert_eq!(device.controller.history().len(), count);
    assert_eq!(last_frame(device), vec![0; 36]);
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "0");
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn recording_phase_wakeup_keeps_countdown_frame_clock_and_cancellation() {
    let (device, dir, mut worker) = fixture();
    worker.handle(Command::Play(device.camera.countdown(15, 255).unwrap()));
    worker.handle(Command::SetRecording(true));
    let frame = worker.playback.as_ref().unwrap().due;
    worker.tick(frame);
    assert_eq!(last_frame(device)[15], 255);
    let count = worker.playback.as_ref().unwrap().frame;
    worker.playback.as_mut().unwrap().due = frame + std::time::Duration::from_secs(2);
    let recording_due = worker.recording.as_ref().unwrap().due;
    worker.tick(recording_due);
    assert_eq!(worker.playback.as_ref().unwrap().frame, count);
    assert_eq!(last_frame(device)[15], 0);
    worker.handle(Command::SetRecording(false));
    assert!(worker.recording.is_none());
    worker.handle(Command::Stop);
    worker.tick(Instant::now());
    assert!(worker.next_wake().is_none());
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn delayed_recording_skips_missed_phases_and_keeps_other_active_channels() {
    let (device, dir, mut worker) = fixture();
    worker.handle(Command::SetSegments {
        segments: &[0],
        level: 99,
    });
    worker.tick(Instant::now() + std::time::Duration::from_millis(3));
    worker.handle(Command::SetRecording(true));
    let due = worker.recording.as_ref().unwrap().due;
    worker.tick(due + std::time::Duration::from_millis(1_850));
    assert!(worker.recording.as_ref().unwrap().lit);
    assert_eq!(last_frame(device)[0], 99);
    worker.tick(worker.recording.as_ref().unwrap().due);
    assert_eq!(last_frame(device)[15], 0);
    assert_eq!(last_frame(device)[0], 99);
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "1");
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn owner_teardown_cannot_leave_a_recording_vote() {
    let (device, dir, mut worker) = fixture();
    worker.handle(Command::SetRecording(true));
    let (send, receive) = mpsc::channel();
    worker.requests = receive;
    drop(send);
    worker.run();
    assert_eq!(last_frame(device), vec![0; 36]);
    assert_eq!(fs::read_to_string(dir.join("operating_mode")).unwrap(), "2");
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "0");
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn producer_burst_is_drained_before_camera_off_acknowledges() {
    let (device, dir, _) = fixture();
    let player = std::sync::Arc::new(Player::new(device));
    player.set_recording(true);
    player.play(device.camera.countdown(43, 255).unwrap());
    let producers: Vec<_> = (0..4)
        .map(|_| {
            let player = std::sync::Arc::clone(&player);
            thread::spawn(move || {
                for value in 0..256 {
                    player.set_segments(&[0], value);
                }
            })
        })
        .collect();
    for producer in producers {
        producer.join().unwrap();
    }
    player.set_recording(false);
    player.set_all(0).unwrap();
    assert_eq!(last_frame(device), vec![0; 36]);
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "0");
    let writes = device.controller.history().len();
    drop(player);
    let deadline = Instant::now() + std::time::Duration::from_secs(2);
    while device.controller.history().len() == writes && Instant::now() < deadline {
        thread::sleep(std::time::Duration::from_millis(1));
    }
    assert!(device.controller.history().len() > writes);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn failed_frame_drops_playback_and_retention_then_accepts_new_work() {
    let (device, dir, mut worker) = fixture();
    worker.handle(Command::SetRecording(true));
    worker.handle(Command::Play(device.camera.countdown(15, 255).unwrap()));
    let due = worker.playback.as_ref().unwrap().due;
    fs::remove_file(dir.join("frame_brightness")).unwrap();
    worker.tick(due);
    assert!(worker.playback.is_none());
    assert!(worker.recording.is_none());
    assert!(worker.next_wake().is_none());
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "0");
    fs::write(dir.join("frame_brightness"), "").unwrap();
    let (reply, result) = mpsc::channel();
    worker.handle(Command::SetAll { level: 120, reply });
    result.recv().unwrap().unwrap();
    assert_eq!(last_frame(device), vec![120; 36]);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn malformed_frame_never_reaches_driver_or_powers_controller() {
    let (device, dir, _) = fixture();
    for size in [0, 20, 35, 37, 625] {
        assert_eq!(
            device.write_frame(&vec![255; size]).unwrap_err().kind(),
            std::io::ErrorKind::InvalidInput
        );
    }
    assert!(device.controller.history().is_empty());
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "");
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn catalog_exposes_demonstrated_camera_lights_and_rejects_matrix() {
    let (device, dir, _) = fixture();
    assert!(crate::catalog::resolve(device, 105).is_some());
    assert!(crate::catalog::resolve(device, 106).is_some());
    assert!(crate::catalog::resolve(device, 120).is_none());
    fs::remove_dir_all(dir).unwrap();
}

fn effect(
    id: i32,
    channels: &[usize],
    points: &[(i32, u32)],
    period: u64,
    iterations: i32,
    linear: bool,
    preemptive: bool,
) -> LightEffect {
    LightEffect {
        id,
        segments: channels.to_vec(),
        points: points.to_vec(),
        linear,
        frame_period: Duration::from_millis(period),
        iterations,
        preemptive,
    }
}

fn set_all(worker: &mut Worker, value: u32, now: Instant) {
    let (reply, result) = mpsc::channel();
    worker.handle_at(
        Command::SetAll {
            level: value,
            reply,
        },
        now,
    );
    result.recv().unwrap().unwrap();
}

#[test]
fn none_keeps_previous_colour_until_control_deadline_and_holds_terminal_colour() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    set_all(&mut worker, 20, start);
    worker.handle_at(
        Command::Effects(vec![effect(
            210,
            &[0],
            &[(0, 0), (5, 255)],
            16,
            1,
            false,
            true,
        )]),
        start,
    );
    worker.tick(start);
    worker.tick(start + Duration::from_millis(64));
    assert_eq!(last_frame(device)[0], 0);
    worker.tick(start + Duration::from_millis(80));
    assert_eq!(last_frame(device)[0], 255);
    assert!(worker.effects.is_empty());
    worker.tick(start + Duration::from_secs(100));
    assert_eq!(last_frame(device)[0], 255);
    assert_eq!(last_frame(device)[1], 20);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn infinite_none_displays_terminal_point_before_each_new_iteration() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![effect(
            210,
            &[0],
            &[(0, 0), (5, 255)],
            16,
            0,
            false,
            true,
        )]),
        start,
    );
    for (frame, level) in [(0, 0), (4, 0), (5, 255), (6, 0), (11, 255), (12, 0)] {
        worker.tick(start + Duration::from_millis(frame * 16));
        assert_eq!(last_frame(device)[0], level, "frame {frame}");
    }
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn positive_first_linear_seeds_displayed_colour_and_later_infinite_iteration() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    set_all(&mut worker, 100, start);
    worker.handle_at(
        Command::Effects(vec![effect(210, &[0], &[(5, 200)], 16, 0, true, true)]),
        start,
    );
    for (frame, level) in [(0, 100), (1, 120), (5, 200), (6, 200), (7, 200), (11, 200)] {
        worker.tick(start + Duration::from_millis(frame * 16));
        assert_eq!(last_frame(device)[0], level, "frame {frame}");
    }
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn periods_and_iteration_counts_remain_independent_in_one_driver_frame() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![
            effect(210, &[0], &[(0, 0), (2, 200)], 16, 1, true, true),
            effect(211, &[1], &[(0, 0), (2, 100)], 32, 2, true, true),
        ]),
        start,
    );
    worker.tick(start + Duration::from_millis(16));
    assert_eq!(&last_frame(device)[..2], &[100, 0]);
    worker.tick(start + Duration::from_millis(32));
    assert_eq!(&last_frame(device)[..2], &[200, 50]);
    assert!(!worker.effects.contains_key(&210));
    assert!(worker.effects.contains_key(&211));
    worker.tick(start + Duration::from_millis(64));
    assert_eq!(&last_frame(device)[..2], &[200, 100]);
    worker.tick(start + Duration::from_millis(96));
    assert_eq!(&last_frame(device)[..2], &[200, 0]);
    worker.tick(start + Duration::from_millis(160));
    assert_eq!(&last_frame(device)[..2], &[200, 100]);
    assert!(worker.effects.is_empty());
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn queued_finite_effect_starts_after_terminal_write_and_seeds_that_displayed_colour() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![effect(
            210,
            &[0],
            &[(0, 0), (2, 100)],
            16,
            1,
            true,
            true,
        )]),
        start,
    );
    worker.tick(start + Duration::from_millis(16));
    assert_eq!(last_frame(device)[0], 50);
    worker.handle_at(
        Command::Effects(vec![effect(210, &[0], &[(2, 200)], 16, 1, true, false)]),
        start + Duration::from_millis(16),
    );
    for (frame, level) in [(2, 100), (3, 100), (4, 150), (5, 200)] {
        worker.tick(start + Duration::from_millis(frame * 16));
        assert_eq!(last_frame(device)[0], level, "frame {frame}");
    }
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn queued_infinite_effect_switches_at_next_iteration_boundary_and_stale_queue_is_cancellable() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![effect(
            210,
            &[0],
            &[(0, 0), (2, 100)],
            16,
            0,
            false,
            true,
        )]),
        start,
    );
    worker.tick(start + Duration::from_millis(16));
    worker.handle_at(
        Command::Effects(vec![effect(210, &[0], &[(2, 200)], 16, 1, true, false)]),
        start + Duration::from_millis(16),
    );
    worker.tick(start + Duration::from_millis(32));
    assert_eq!(last_frame(device)[0], 100);
    worker.tick(start + Duration::from_millis(48));
    assert_eq!(last_frame(device)[0], 100);
    worker.tick(start + Duration::from_millis(64));
    assert_eq!(last_frame(device)[0], 150);
    set_all(&mut worker, 0, start + Duration::from_millis(64));
    worker.tick(start + Duration::from_secs(100));
    assert_eq!(last_frame(device), vec![0; 36]);
    assert!(worker.next_wake().is_none());
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn static_partial_group_cancels_only_affected_tracks_and_pending_channels() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![effect(
            250,
            &[0, 1],
            &[(0, 0), (2, 100)],
            16,
            0,
            true,
            true,
        )]),
        start,
    );
    worker.handle_at(
        Command::Effects(vec![effect(
            250,
            &[0, 1],
            &[(0, 255), (2, 50)],
            16,
            1,
            true,
            false,
        )]),
        start,
    );
    worker.tick(start + Duration::from_millis(16));
    worker.handle_at(
        Command::SetSegments {
            segments: &[0],
            level: 7,
        },
        start + Duration::from_millis(16),
    );
    worker.tick(start + Duration::from_millis(32));
    assert_eq!(&last_frame(device)[..2], &[7, 100]);
    worker.tick(start + Duration::from_millis(48));
    assert_eq!(&last_frame(device)[..2], &[7, 255]);
    worker.tick(start + Duration::from_millis(80));
    assert_eq!(&last_frame(device)[..2], &[7, 50]);
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn actual_hal_rejects_invalid_batch_atomically_and_draws_both_stock_fill_forms() {
    use android_hardware_light::aidl::android::hardware::light::{
        HwLightEffect::HwLightEffect, HwLightState::HwLightState, ILights::ILights,
        InterpolationType::InterpolationType,
    };
    use binder::ExceptionCode;
    let (device, _, _) = fixture();
    let hal = crate::lights::GlyphLights::new(device);
    for command in [7, 11] {
        hal.setLightState(106, &HwLightState { color: command })
            .unwrap();
        assert_eq!(last_frame(device), vec![255; 36]);
    }
    for command in [3, 6, 10, 14, 42] {
        hal.setLightState(106, &HwLightState { color: command })
            .unwrap();
        assert_eq!(last_frame(device), vec![0; 36]);
    }
    let valid = HwLightEffect {
        lightId: 210,
        frames: vec![0, 2],
        colors: vec![0, -1],
        iterations: 1,
        preemptive: true,
        framePeriodMillis: 16,
        interpolationType: InterpolationType::LINEAR,
    };
    let mut malformed = Vec::new();
    for period in [-16, 0, 15] {
        let mut effect = valid.clone();
        effect.framePeriodMillis = period;
        malformed.push(effect);
    }
    let mut effect = valid.clone();
    effect.iterations = -1;
    malformed.push(effect);
    let mut effect = valid.clone();
    effect.frames = vec![0, 0];
    malformed.push(effect);
    let mut effect = valid.clone();
    effect.frames = vec![-1, 2];
    malformed.push(effect);
    let mut effect = valid.clone();
    effect.colors.pop();
    malformed.push(effect);
    let mut effect = valid.clone();
    effect.frames.clear();
    effect.colors.clear();
    malformed.push(effect);
    let mut effect = valid.clone();
    effect.interpolationType = InterpolationType::INVALID;
    malformed.push(effect);
    let mut overflow = valid.clone();
    overflow.frames = vec![0, i32::MAX];
    overflow.framePeriodMillis = i32::MAX;
    overflow.iterations = i32::MAX;
    malformed.push(overflow);
    for invalid in malformed {
        let before = device.controller.history().len();
        let error = hal.setLightEffects(&[valid.clone(), invalid]).unwrap_err();
        assert_eq!(error.code, ExceptionCode::ILLEGAL_ARGUMENT);
        assert_eq!(device.controller.history().len(), before);
    }
    for id in [120, 200, 9999] {
        let mut invalid = valid.clone();
        invalid.lightId = id;
        let before = device.controller.history().len();
        assert_eq!(
            hal.setLightEffects(&[valid.clone(), invalid])
                .unwrap_err()
                .code,
            ExceptionCode::UNSUPPORTED_OPERATION
        );
        assert_eq!(device.controller.history().len(), before);
    }
    let ids: Vec<_> = hal
        .getLights()
        .unwrap()
        .into_iter()
        .map(|light| light.id)
        .collect();
    assert!(ids.contains(&105) && ids.contains(&106) && !ids.contains(&120));
    // The frozen AIDL permits independent finite and infinite iterations in the same batch.
    let mut infinite = valid.clone();
    infinite.iterations = 0;
    let mut finite = valid;
    finite.lightId = 211;
    finite.framePeriodMillis = 32;
    hal.setLightEffects(&[infinite, finite]).unwrap();
    hal.setLightState(200, &HwLightState { color: 0 }).unwrap();
}

#[test]
fn preemptive_replacement_starts_now_from_displayed_value_and_discards_old_queue() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now();
    worker.handle_at(
        Command::Effects(vec![effect(
            210,
            &[0],
            &[(0, 0), (5, 100)],
            16,
            1,
            true,
            true,
        )]),
        start,
    );
    worker.tick(start + Duration::from_millis(16));
    assert_eq!(last_frame(device)[0], 20);
    worker.handle_at(
        Command::Effects(vec![effect(210, &[0], &[(2, 30)], 16, 1, true, false)]),
        start + Duration::from_millis(16),
    );
    worker.handle_at(
        Command::Effects(vec![effect(210, &[0], &[(2, 200)], 16, 1, true, true)]),
        start + Duration::from_millis(16),
    );
    for (frame, level) in [(1, 20), (2, 110), (3, 200), (100, 200)] {
        worker.tick(start + Duration::from_millis(frame * 16));
        assert_eq!(last_frame(device)[0], level);
    }
    assert!(worker.effects.is_empty());
    fs::remove_dir_all(dir).unwrap();
}

#[test]
fn expired_camera_and_recording_deadlines_progress_while_commands_remain_backlogged() {
    let (device, dir, mut worker) = fixture();
    let start = Instant::now() - Duration::from_secs(1);
    worker.handle_at(
        Command::Play(device.camera.countdown(43, 255).unwrap()),
        start,
    );
    worker.handle_at(Command::SetRecording(true), start);
    let before = device.controller.history().len();
    let (sender, receiver) = mpsc::channel();
    worker.requests = receiver;
    for _ in 0..MAX_COMMAND_BURST {
        sender
            .send(Command::SetSegments {
                segments: &[35],
                level: 7,
            })
            .unwrap();
    }
    let (checkpoint, reached) = mpsc::channel();
    sender.send(Command::Checkpoint(checkpoint)).unwrap();
    for _ in 0..1024 {
        sender
            .send(Command::SetSegments {
                segments: &[35],
                level: 7,
            })
            .unwrap();
    }
    let owner = thread::spawn(move || worker.run());
    // A recv-timeout-only worker reaches this checkpoint without ever ticking.
    assert!(reached.recv_timeout(Duration::from_secs(2)).unwrap() > before);
    let (reply, off) = mpsc::channel();
    sender.send(Command::SetRecording(false)).unwrap();
    sender.send(Command::SetAll { level: 0, reply }).unwrap();
    off.recv_timeout(Duration::from_secs(2)).unwrap().unwrap();
    assert_eq!(last_frame(device), vec![0; 36]);
    drop(sender);
    owner.join().unwrap();
    assert_eq!(fs::read_to_string(dir.join("always_on")).unwrap(), "0");
    fs::remove_dir_all(dir).unwrap();
}
