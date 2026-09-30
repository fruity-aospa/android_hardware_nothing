/*
 * SPDX-FileCopyrightText: Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

//! Validate the frozen V3 control-point contract before changing any light.
use super::color;
use android_hardware_light::aidl::android::hardware::light::{
    HwLightEffect::HwLightEffect, InterpolationType::InterpolationType,
};

pub fn controls(effect: &HwLightEffect, max_brightness: u32) -> Option<Vec<(i32, u32)>> {
    if effect.frames.len() != effect.colors.len()
        || effect.frames.is_empty()
        || !matches!(
            effect.interpolationType,
            InterpolationType::NONE | InterpolationType::LINEAR
        )
    {
        return None;
    }
    if effect
        .frames
        .iter()
        .enumerate()
        .any(|(index, &frames)| frames < 0 || (index > 0 && frames == 0))
    {
        return None;
    }
    Some(
        effect
            .frames
            .iter()
            .zip(&effect.colors)
            .map(|(&frames, &color)| (frames, color::level(color, max_brightness)))
            .collect(),
    )
}
