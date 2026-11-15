/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The AW20036 driver.

use std::sync::Mutex;
use std::time::Duration;

use super::{Controller, Power, State};

pub static AW20036: Controller = Controller {
    frame: "frame_brightness",
    single: "single_brightness",
    power: Some(Power { mode: "operating_mode", retain: "always_on" }),
    state: Mutex::new(State::IDLE),
    #[cfg(test)]
    history: Mutex::new(Vec::new()),
    coalesce_window: Duration::from_millis(2),
    min_update_period_millis: 16,
};
