// Shape follows the selected frozen light-V3 AIDL. Binder/parcel dispatch is outside host scope.
#![allow(non_snake_case)]
pub mod aidl {
    pub mod android {
        pub mod hardware {
            pub mod light {
                pub mod LightType {
                    #[derive(Clone, Copy, Debug, PartialEq)]
                    pub enum LightType {
                        CAMERA,
                        APPLICATION,
                    }
                }
                pub mod InterpolationType {
                    #[derive(Clone, Copy, Debug, PartialEq)]
                    pub enum InterpolationType {
                        NONE,
                        LINEAR,
                        INVALID,
                    }
                }
                pub mod HwLight {
                    pub struct HwLight {
                        pub id: i32,
                        pub ordinal: i32,
                        pub r#type: super::LightType::LightType,
                        pub minUpdatePeriodMillis: i32,
                    }
                }
                pub mod HwLightState {
                    #[derive(Default)]
                    pub struct HwLightState {
                        pub color: i32,
                    }
                }
                pub mod HwLightEffect {
                    #[derive(Clone)]
                    pub struct HwLightEffect {
                        pub lightId: i32,
                        pub frames: Vec<i32>,
                        pub colors: Vec<i32>,
                        pub iterations: i32,
                        pub preemptive: bool,
                        pub framePeriodMillis: i32,
                        pub interpolationType: super::InterpolationType::InterpolationType,
                    }
                }
                pub mod ILights {
                    pub trait ILights: binder::Interface {
                        fn setLightState(
                            &self,
                            id: i32,
                            state: &super::HwLightState::HwLightState,
                        ) -> binder::Result<()>;
                        fn setLightEffects(
                            &self,
                            effects: &[super::HwLightEffect::HwLightEffect],
                        ) -> binder::Result<()>;
                        fn getLights(&self) -> binder::Result<Vec<super::HwLight::HwLight>>;
                    }
                }
            }
        }
    }
}
