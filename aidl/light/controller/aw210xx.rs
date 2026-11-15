/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! The AW210XX driver.

use std::sync::Mutex;
use std::time::Duration;

use super::{Controller, State};

pub static AW210XX: Controller = Controller {
    frame: "frame_leds_effect",
    single: "single_led_br",
    power: None,
    state: Mutex::new(State::IDLE),
    #[cfg(test)]
    history: Mutex::new(Vec::new()),
    coalesce_window: Duration::from_millis(2),
    min_update_period_millis: 16,
};
