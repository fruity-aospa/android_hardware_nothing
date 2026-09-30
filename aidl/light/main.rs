/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Serves the glyph strips as ordinary Android lights.

mod controller;
mod device;
mod indicator;
mod lights;
mod player;

use android_hardware_light::aidl::android::hardware::light::ILights::{BnLights, ILights};
use binder::BinderFeatures;
use log::LevelFilter;

use device::{Device, Shell, DEVICE};
use lights::GlyphLights;

const LOG_TAG: &str = "glyph-lights";

fn main() {
    logger::init(
        logger::Config::default().with_tag_on_device(LOG_TAG).with_max_level(LevelFilter::Info),
    );

    binder::ProcessState::set_thread_pool_max_thread_count(0);

    let shell = Shell::read();
    shell.publish();
    DEVICE.tell_shell();
    let device: &'static Device = Box::leak(Box::new(DEVICE.for_shell(shell)));

    let service = BnLights::new_binder(GlyphLights::new(device), BinderFeatures::default());
    let name = format!("{}/default", GlyphLights::get_descriptor());
    binder::add_service(&name, service.as_binder()).expect("Failed to register service");

    binder::ProcessState::join_thread_pool();
}
