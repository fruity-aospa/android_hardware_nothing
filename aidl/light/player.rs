/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! One driver owner, with independent light clocks and cancellable camera playback.

mod animation;
mod curve;

pub use animation::{Animation, Track};
pub use curve::Curve;

use std::collections::{BTreeMap, VecDeque};
use std::io::{self, Result};
use std::sync::mpsc::{self, Receiver, RecvTimeoutError, Sender};
use std::sync::{Mutex, PoisonError};
use std::thread;
use std::time::{Duration, Instant};

use crate::device::{Device, Segments};
use log::error;

/// A V3 light's controls; clocks and iteration counts belong to this light.
pub struct LightEffect {
    pub id: i32,
    pub segments: Vec<usize>,
    pub points: Vec<(i32, u32)>,
    pub linear: bool,
    pub frame_period: Duration,
    pub iterations: i32,
    pub preemptive: bool,
}

pub struct Player {
    commands: Mutex<Sender<Command>>,
}

enum Command {
    SetSegments {
        segments: Segments,
        level: u32,
    },
    SetAll {
        level: u32,
        reply: Sender<Result<()>>,
    },
    Stop,
    SetRecording(bool),
    Play(Animation),
    Effects(Vec<LightEffect>),
    #[cfg(test)]
    Checkpoint(Sender<usize>),
}

impl Player {
    pub fn new(device: &'static Device) -> Self {
        let (commands, requests) = mpsc::channel();
        let worker = Worker {
            device,
            requests,
            resting: vec![0; device.segments],
            playback: None,
            redraw: None,
            recording: None,
            drawn: vec![0; device.segments],
            effects: BTreeMap::new(),
        };
        thread::spawn(move || worker.run());
        Player {
            commands: Mutex::new(commands),
        }
    }
    pub fn set_segments(&self, segments: Segments, level: u32) {
        self.send(Command::SetSegments { segments, level });
    }
    pub fn set_all(&self, level: u32) -> Result<()> {
        let (reply, result) = mpsc::channel();
        self.send(Command::SetAll { level, reply });
        result
            .recv()
            .unwrap_or_else(|_| Err(io::ErrorKind::BrokenPipe.into()))
    }
    pub fn stop(&self) {
        self.send(Command::Stop);
    }
    pub fn play(&self, animation: Animation) {
        if animation.frames() > 0 {
            self.send(Command::Play(animation));
        }
    }
    pub fn effects(&self, effects: Vec<LightEffect>) {
        self.send(Command::Effects(effects));
    }
    pub fn set_recording(&self, lit: bool) {
        self.send(Command::SetRecording(lit));
    }
    fn send(&self, command: Command) {
        let _ = self
            .commands
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .send(command);
    }
}

struct Worker {
    device: &'static Device,
    requests: Receiver<Command>,
    resting: Vec<u32>,
    playback: Option<Playback>,
    redraw: Option<Instant>,
    recording: Option<Recording>,
    drawn: Vec<u32>,
    effects: BTreeMap<i32, EffectPlayback>,
}
struct Recording {
    segment: usize,
    level: u32,
    lit: bool,
    due: Instant,
}
const RECORDING_PHASE: Duration = Duration::from_millis(500);
// A continuous producer may not keep an expired driver deadline behind recv forever.
const MAX_COMMAND_BURST: usize = 32;

struct Playback {
    animation: Animation,
    length: u64,
    frame: u64,
    last_frame: Option<u64>,
    iteration: i32,
    due: Instant,
}
impl Playback {
    fn new(animation: Animation, due: Instant) -> Playback {
        Playback {
            length: animation.frames(),
            animation,
            frame: 0,
            last_frame: None,
            iteration: 0,
            due,
        }
    }
}
struct EffectPlayback {
    effect: LightEffect,
    curves: Vec<Track>,
    start: Instant,
    due: Option<Instant>,
    pending: VecDeque<LightEffect>,
    interrupt_at: Option<Instant>,
    ready_next: Option<Instant>,
    cycle: u128,
    terminal_visible: bool,
}
impl EffectPlayback {
    fn new(effect: LightEffect, start: Instant, previous: &[u32]) -> Self {
        let curves = effect
            .segments
            .iter()
            .map(|&segment| Track {
                segment,
                curve: std::sync::Arc::new(
                    Curve::with_start(
                        &effect.points,
                        effect.linear,
                        previous.get(segment).copied().unwrap_or(0),
                    )
                    .expect("Validated light effect"),
                ),
            })
            .collect();
        Self {
            effect,
            curves,
            start,
            due: Some(start),
            pending: VecDeque::new(),
            interrupt_at: None,
            ready_next: None,
            cycle: 0,
            terminal_visible: false,
        }
    }
    fn last_frame(&self) -> u64 {
        self.curves
            .iter()
            .map(|track| track.curve.frames().saturating_sub(1))
            .max()
            .unwrap_or(0)
    }
}

pub fn frame_duration(period: Duration, frames: u128) -> Option<Duration> {
    let nanos = period.as_nanos().checked_mul(frames)?;
    Some(Duration::new(
        u64::try_from(nanos / 1_000_000_000).ok()?,
        (nanos % 1_000_000_000) as u32,
    ))
}
fn at(start: Instant, period: Duration, frame: u128) -> Option<Instant> {
    start.checked_add(frame_duration(period, frame)?)
}

impl Worker {
    fn run(mut self) {
        let mut burst = 0;
        loop {
            let received = match self.next_wake() {
                Some(wake) => self
                    .requests
                    .recv_timeout(wake.saturating_duration_since(Instant::now())),
                None => self
                    .requests
                    .recv()
                    .map_err(|_| RecvTimeoutError::Disconnected),
            };
            match received {
                Ok(command) => {
                    self.handle(command);
                    burst += 1;
                    if burst >= MAX_COMMAND_BURST {
                        let now = Instant::now();
                        if self.next_wake().is_some_and(|due| due <= now) {
                            self.tick(now);
                        }
                        burst = 0;
                    }
                }
                Err(RecvTimeoutError::Timeout) => {
                    self.tick(Instant::now());
                    burst = 0;
                }
                Err(RecvTimeoutError::Disconnected) => {
                    self.recording = None;
                    self.playback = None;
                    self.effects.clear();
                    self.resting.fill(0);
                    self.draw_resting();
                    return;
                }
            }
        }
    }
    fn next_wake(&self) -> Option<Instant> {
        self.playback
            .as_ref()
            .map(|playback| playback.due)
            .into_iter()
            .chain(self.redraw)
            .chain(self.recording.as_ref().map(|recording| recording.due))
            .chain(self.effects.values().filter_map(|effect| effect.due))
            .min()
    }
    fn handle(&mut self, command: Command) {
        self.handle_at(command, Instant::now());
    }
    fn handle_at(&mut self, command: Command, now: Instant) {
        match command {
            Command::SetSegments { segments, level } => {
                self.release(segments);
                self.release_effects(segments);
                for &segment in segments {
                    if let Some(resting) = self.resting.get_mut(segment) {
                        *resting = level;
                    }
                }
                self.redraw
                    .get_or_insert(now + self.device.controller.coalesce_window);
            }
            Command::SetAll { level, reply } => {
                self.playback = None;
                self.effects.clear();
                self.redraw = None;
                self.resting.fill(level);
                self.drawn.clone_from(&self.resting);
                let result = self.write_current();
                if let Err(e) = &result {
                    self.fail(io::Error::new(e.kind(), e.to_string()));
                }
                let _ = reply.send(result);
            }
            Command::SetRecording(lit) => {
                self.recording = if lit {
                    self.device
                        .camera
                        .recording_segment
                        .map(|segment| Recording {
                            segment,
                            level: self.device.camera.recording_level,
                            lit: true,
                            due: now + RECORDING_PHASE,
                        })
                } else {
                    None
                };
                if let Err(e) = self.write_current() {
                    self.fail(e);
                }
            }
            Command::Stop => {
                self.playback = None;
                self.effects.clear();
                self.redraw = Some(now);
            }
            Command::Play(animation) => {
                self.effects.clear();
                self.redraw = None;
                self.playback = Some(Playback::new(animation, now));
            }
            Command::Effects(effects) => {
                for effect in effects {
                    self.apply_effect(effect, now);
                }
            }
            #[cfg(test)]
            Command::Checkpoint(reply) => {
                let _ = reply.send(self.device.controller.history().len());
            }
        }
    }
    fn apply_effect(&mut self, effect: LightEffect, now: Instant) {
        if !effect.preemptive {
            if let Some(active) = self.effects.get_mut(&effect.id) {
                if active.effect.iterations == 0 && active.interrupt_at.is_none() {
                    let length = active.last_frame() as u128 + 1;
                    let elapsed = now.saturating_duration_since(active.start).as_nanos();
                    let cycle = active.effect.frame_period.as_nanos() * length;
                    active.interrupt_at = at(
                        active.start,
                        active.effect.frame_period,
                        (elapsed / cycle + 1) * length,
                    );
                }
                active.pending.push_back(effect);
                return;
            }
        }
        let id = effect.id;
        self.effects.remove(&id);
        self.release(&effect.segments);
        self.release_effects(&effect.segments);
        self.effects
            .insert(id, EffectPlayback::new(effect, now, &self.drawn));
    }
    fn release(&mut self, segments: &[usize]) {
        let Some(playback) = self.playback.as_mut() else { return };
        playback
            .animation
            .tracks
            .retain(|track| !segments.contains(&track.segment));
        if playback.animation.tracks.is_empty() {
            self.playback = None;
        }
    }
    fn release_effects(&mut self, segments: &[usize]) {
        self.effects.retain(|_, active| {
            active
                .curves
                .retain(|track| !segments.contains(&track.segment));
            active
                .effect
                .segments
                .retain(|segment| !segments.contains(segment));
            for pending in &mut active.pending {
                pending
                    .segments
                    .retain(|segment| !segments.contains(segment));
            }
            active.pending.retain(|effect| !effect.segments.is_empty());
            !active.curves.is_empty()
        });
    }
    fn tick(&mut self, now: Instant) {
        let mut changed = false;
        if let Some(recording) = self.recording.as_mut() {
            if recording.due <= now {
                let phases =
                    now.duration_since(recording.due).as_nanos() / RECORDING_PHASE.as_nanos() + 1;
                if phases % 2 == 1 {
                    recording.lit = !recording.lit;
                }
                recording.due =
                    at(recording.due, RECORDING_PHASE, phases).unwrap_or(now + RECORDING_PHASE);
                changed = true;
            }
        }
        if self
            .playback
            .as_ref()
            .is_some_and(|playback| playback.due <= now)
        {
            self.advance_camera(now);
            changed = true;
        }
        if self
            .effects
            .values()
            .any(|effect| effect.due.is_some_and(|due| due <= now))
        {
            self.advance_effects(now);
            changed = true;
        }
        if self.redraw.is_some_and(|redraw| redraw <= now) {
            self.redraw = None;
            changed = true;
        }
        if changed {
            self.render();
        }
    }
    fn advance_camera(&mut self, now: Instant) {
        let Some(playback) = self.playback.as_mut() else { return };
        let period = playback.animation.frame_period;
        let missed = now.saturating_duration_since(playback.due).as_nanos() / period.as_nanos();
        let next = playback.frame as u128 + missed;
        let iterations = next / playback.length as u128;
        let iteration = playback.iteration as u128 + iterations;
        if playback.animation.iterations != 0 && iteration >= playback.animation.iterations as u128
        {
            return self.finish();
        }
        playback.iteration = iteration as i32;
        playback.last_frame = Some((next % playback.length as u128) as u64);
        playback.frame = playback.last_frame.unwrap() + 1;
        if playback.frame == playback.length {
            playback.frame = 0;
            playback.iteration += 1;
            if playback.animation.iterations != 0
                && playback.iteration >= playback.animation.iterations
            {
                return self.finish();
            }
        }
        playback.due = at(playback.due, period, missed + 1).unwrap_or(now + period);
    }
    fn advance_effects(&mut self, now: Instant) {
        let ids: Vec<_> = self.effects.keys().copied().collect();
        for id in ids {
            let Some(mut active) = self.effects.remove(&id) else { continue };
            loop {
                if let Some(start) = active.ready_next {
                    if now < start {
                        self.effects.insert(id, active);
                        break;
                    }
                    let next = active.pending.pop_front().expect("Queued effect boundary");
                    let pending = active.pending;
                    self.release(&next.segments);
                    self.release_effects(&next.segments);
                    // Its predecessor's terminal value was written on the previous tick.
                    active = EffectPlayback::new(next, start, &self.drawn);
                    active.pending = pending;
                    if active.effect.iterations == 0 && !active.pending.is_empty() {
                        active.interrupt_at = at(
                            start,
                            active.effect.frame_period,
                            active.last_frame() as u128 + 1,
                        );
                    }
                    continue;
                }
                let last = active.last_frame();
                let period = active.effect.frame_period;
                let elapsed =
                    now.saturating_duration_since(active.start).as_nanos() / period.as_nanos();
                let span = last as u128 + 1;
                let cycle = elapsed / span;
                let interrupted = active.interrupt_at.is_some_and(|deadline| deadline <= now);
                if interrupted && active.terminal_visible && !active.pending.is_empty() {
                    active.ready_next = active.interrupt_at;
                    continue;
                }
                if cycle != active.cycle && !interrupted {
                    active.terminal_visible = false;
                }
                if cycle != active.cycle && active.effect.points[0].0 > 0 {
                    active.curves = active
                        .effect
                        .segments
                        .iter()
                        .map(|&segment| Track {
                            segment,
                            curve: std::sync::Arc::new(
                                Curve::with_start(
                                    &active.effect.points,
                                    active.effect.linear,
                                    self.drawn[segment],
                                )
                                .expect("Validated effect"),
                            ),
                        })
                        .collect();
                }
                active.cycle = cycle;
                let finite_end =
                    last as u128 + span * active.effect.iterations.saturating_sub(1).max(0) as u128;
                let ended = last == 0 || (active.effect.iterations > 0 && elapsed >= finite_end);
                let frame = if ended || interrupted {
                    last
                } else {
                    (elapsed % span) as u64
                };
                for track in &active.curves {
                    self.resting[track.segment] = track.curve.level(frame);
                }
                if frame == last {
                    active.terminal_visible = true;
                }
                if ended || interrupted {
                    if !active.pending.is_empty() {
                        // Do not erase a terminal NONE point before it reaches the driver.
                        active.ready_next = Some(now + period);
                        active.due = active.ready_next;
                        self.effects.insert(id, active);
                    }
                    break;
                }
                active.due = at(active.start, period, elapsed + 1);
                self.effects.insert(id, active);
                break;
            }
        }
    }

    fn finish(&mut self) {
        if let Some(playback) = self.playback.take() {
            for track in &playback.animation.tracks {
                self.resting[track.segment] = 0;
            }
        }
    }
    fn render(&mut self) {
        self.drawn = if let Some(playback) = &self.playback {
            playback.last_frame.map_or_else(
                || self.resting.clone(),
                |frame| playback.animation.frame(frame, &self.resting),
            )
        } else {
            self.resting.clone()
        };
        if let Err(e) = self.write_current() {
            self.fail(e);
        }
    }
    fn fail(&mut self, error: io::Error) {
        error!("Failed to draw the glyph frame: {error}");
        self.recording = None;
        self.playback = None;
        self.effects.clear();
        self.redraw = None;
        self.resting.fill(0);
        self.drawn.fill(0);
        let _ = self.device.write_frame(&self.drawn);
    }
    fn write_current(&self) -> Result<()> {
        let mut levels = self.drawn.clone();
        if let Some(recording) = &self.recording {
            if let Some(level) = levels.get_mut(recording.segment) {
                *level = if recording.lit { recording.level } else { 0 };
            }
        }
        self.device.write_frame(&levels)
    }
    fn draw_resting(&mut self) {
        self.redraw = None;
        self.render();
    }
}

#[cfg(test)]
#[path = "tests/player.rs"]
mod tests;
