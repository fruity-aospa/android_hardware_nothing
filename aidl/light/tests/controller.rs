use super::*;
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};

static NEXT: AtomicUsize = AtomicUsize::new(0);

struct Fixture {
    controller: &'static Controller,
    dir: PathBuf,
}

impl Fixture {
    fn new() -> Self {
        let dir = std::env::temp_dir().join(format!(
            "glyph-controller-test-{}-{}", std::process::id(), NEXT.fetch_add(1, Ordering::Relaxed),
        ));
        fs::create_dir(&dir).unwrap();
        for node in ["frame_brightness", "single_brightness", "operating_mode", "always_on"] {
            fs::write(dir.join(node), "").unwrap();
        }
        Self { controller: Controller::for_test(), dir }
    }

    fn write(&self, node: Node, value: &str, lit: bool) -> Result<()> {
        self.controller.write(self.dir.to_str().unwrap(), node, value, lit)
    }

    fn read(&self, node: &str) -> String { fs::read_to_string(self.dir.join(node)).unwrap() }

    fn break_node(&self, node: &str) {
        fs::remove_file(self.dir.join(node)).unwrap();
        fs::create_dir(self.dir.join(node)).unwrap();
    }

    fn repair_node(&self, node: &str, value: &str) {
        fs::remove_dir(self.dir.join(node)).unwrap();
        fs::write(self.dir.join(node), value).unwrap();
    }

    fn assert_lit_retry(&self) {
        self.write(Node::Frame, "128", true).unwrap();
        assert_eq!(self.read("operating_mode"), "1");
        assert_eq!(self.read("always_on"), "1");
        assert_eq!(self.read("frame_brightness"), "128");
    }
}

impl Drop for Fixture {
    fn drop(&mut self) { fs::remove_dir_all(&self.dir).unwrap(); }
}

#[test]
fn failed_standby_still_clears_retention_and_lit_retry_rearms() {
    let fixture = Fixture::new();
    fixture.write(Node::Frame, "255", true).unwrap();
    fixture.break_node("operating_mode");

    let expected = fs::write(fixture.dir.join("operating_mode"), "2").unwrap_err();
    let actual = fixture.write(Node::Frame, "0", false).unwrap_err();
    assert_eq!(actual.raw_os_error(), expected.raw_os_error());
    assert_eq!(fixture.read("always_on"), "0");

    fixture.repair_node("operating_mode", "2");
    fixture.assert_lit_retry();
}

#[test]
fn failed_retention_clear_after_standby_forces_lit_retry_to_rearm() {
    let fixture = Fixture::new();
    fixture.write(Node::Frame, "255", true).unwrap();
    fixture.break_node("always_on");

    let expected = fs::write(fixture.dir.join("always_on"), "0").unwrap_err();
    let actual = fixture.write(Node::Frame, "0", false).unwrap_err();
    assert_eq!(actual.raw_os_error(), expected.raw_os_error());
    assert_eq!(fixture.read("operating_mode"), "2");

    fixture.repair_node("always_on", "0");
    fixture.assert_lit_retry();
}

#[test]
fn failed_frame_still_clears_retention_when_standby_also_fails() {
    let fixture = Fixture::new();
    fixture.write(Node::Frame, "255", true).unwrap();
    fixture.break_node("operating_mode");
    fs::remove_file(fixture.dir.join("frame_brightness")).unwrap();

    assert_eq!(fixture.write(Node::Frame, "0", false).unwrap_err().kind(), std::io::ErrorKind::NotFound);
    assert_eq!(fixture.read("always_on"), "0");

    fixture.repair_node("operating_mode", "2");
    fs::write(fixture.dir.join("frame_brightness"), "").unwrap();
    fixture.assert_lit_retry();
}

#[test]
fn power_down_returns_first_error_when_both_cleanup_nodes_fail() {
    let fixture = Fixture::new();
    fixture.write(Node::Frame, "255", true).unwrap();
    fixture.break_node("operating_mode");
    fs::remove_file(fixture.dir.join("always_on")).unwrap();
    std::os::unix::fs::symlink(fixture.dir.join("missing-parent/always_on"), fixture.dir.join("always_on")).unwrap();

    let first = fs::write(fixture.dir.join("operating_mode"), "2").unwrap_err();
    let second = fs::write(fixture.dir.join("always_on"), "0").unwrap_err();
    assert_ne!(first.raw_os_error(), second.raw_os_error());
    assert_eq!(fixture.write(Node::Frame, "0", false).unwrap_err().raw_os_error(), first.raw_os_error());
}

#[test]
fn power_up_does_not_retain_after_failed_arm() {
    let fixture = Fixture::new();
    fixture.break_node("operating_mode");
    fs::write(fixture.dir.join("always_on"), "0").unwrap();

    fixture.controller.power(&fixture.dir, true).unwrap_err();
    assert_eq!(fixture.read("always_on"), "0");
    assert!(fixture.controller.history().is_empty());
}
