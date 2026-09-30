/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The glyph controller chip: the nodes its driver offers, and how to write them.

#[cfg(any(phone2, phone3a))]
mod aw20036;
#[cfg(phone1)]
mod aw210xx;

#[cfg(any(phone2, phone3a))]
pub use aw20036::AW20036;
#[cfg(phone1)]
pub use aw210xx::AW210XX;

use std::fs;
use std::io::{Result, Write};
use std::path::Path;
use std::sync::Mutex;
use std::time::Duration;

/// The nodes a driver offers.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Node {
    /// Takes one level per segment.
    Frame,

    /// Takes a single level for every segment at once.
    All,

    /// Takes one LED's index and level.
    Single,
}

/// The nodes that power a controller up and down, where its driver would otherwise drop writes or
/// power it off in suspend.
pub struct Power {
    /// Arms level writes, and puts the controller to rest once nothing is lit.
    mode: &'static str,

    /// Keeps the controller on through suspend, for as long as anything is lit.
    retain: &'static str,
}

/// What is lit, and which way the controller was last powered.
struct State {
    strips: bool,
    indicator: bool,
    powered: Option<bool>,
}

impl State {
    const IDLE: State = State { strips: false, indicator: false, powered: None };
}

/// A glyph controller: the names of its nodes, and what they need.
pub struct Controller {
    frame: &'static str,
    all: &'static str,
    single: &'static str,

    /// How the controller is powered, where it needs more than a write.
    power: Option<Power>,

    state: Mutex<State>,

    #[cfg(test)]
    history: Mutex<Vec<(Node, String)>>,

    /// A frame arrives as one call per light. Waiting this long for the rest of it keeps a frame
    /// to a single write, which the driver flushes over i2c.
    pub coalesce_window: Duration,

    /// How often the strips can be redrawn.
    pub min_update_period_millis: i32,
}

impl Controller {
    /// Writes a node in the directory the phone's driver registered, `lit` if it lights anything.
    pub fn write(&self, dir: &str, node: Node, value: &str, lit: bool) -> Result<()> {
        let dir = Path::new(dir);

        // Held throughout, so powering up or down never lands between a level and its write.
        let mut state = self.state.lock().unwrap();

        // Must be armed before the level lands, or the write is dropped.
        if lit && state.powered != Some(true) {
            if let Err(e) = self.power(dir, true) {
                let _ = self.power(dir, false);
                state.powered = None;
                return Err(e);
            }
            state.powered = Some(true);
        }

        let name = match node {
            Node::Frame => self.frame,
            Node::All => self.all,
            Node::Single => self.single,
        };
        let written = fs::OpenOptions::new().write(true).open(dir.join(name))
            .and_then(|mut file| file.write_all(value.as_bytes()));
        if let Err(e) = written {
            // A failed frame cannot leave this controller retained indefinitely.
            match node {
                Node::Single => state.indicator = false,
                Node::Frame | Node::All => state.strips = false,
            }
            if !state.strips && !state.indicator {
                if self.power(dir, false).is_ok() { state.powered = Some(false); }
                else { state.powered = None; }
            }
            return Err(e);
        }
        #[cfg(test)]
        self.history.lock().unwrap().push((node, value.to_owned()));

        match node {
            Node::Single => state.indicator = lit,
            Node::Frame | Node::All => state.strips = lit,
        }
        if !state.strips && !state.indicator && state.powered != Some(false) {
            // Either power node can change before the other write fails.
            state.powered = None;
            self.power(dir, false)?;
            state.powered = Some(false);
        }
        Ok(())
    }

    /// Powers the controller up to light, or down to rest once nothing is lit.
    fn power(&self, dir: &Path, up: bool) -> Result<()> {
        let Some(power) = &self.power else { return Ok(()) };

        let (mode, retain) = if up { ("1", "1") } else { ("2", "0") };
        let mode_written = fs::write(dir.join(power.mode), mode);
        if up {
            mode_written?;
            fs::write(dir.join(power.retain), retain)
        } else {
            // A failed standby write must still release the suspend-retention vote.
            let retain_written = fs::write(dir.join(power.retain), retain);
            mode_written.and(retain_written)
        }
    }
}

#[cfg(test)]
impl Controller {
    pub(crate) fn for_test() -> &'static Controller {
        Box::leak(Box::new(Controller {
            frame: "frame_brightness", all: "all_white_brightness", single: "single_brightness",
            power: Some(Power { mode: "operating_mode", retain: "always_on" }),
            state: Mutex::new(State::IDLE), history: Mutex::new(Vec::new()),
            coalesce_window: Duration::from_millis(2), min_update_period_millis: 16,
        }))
    }
    pub(crate) fn history(&self) -> Vec<(Node, String)> { self.history.lock().unwrap().clone() }
}

#[cfg(test)]
#[path = "tests/controller.rs"]
mod tests;
