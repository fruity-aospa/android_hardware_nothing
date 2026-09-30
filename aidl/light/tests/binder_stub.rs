// Transport-only host fixture; production HAL method bodies are compiled unchanged.
#![allow(non_camel_case_types)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum ExceptionCode {
    UNSUPPORTED_OPERATION,
    ILLEGAL_ARGUMENT,
    ILLEGAL_STATE,
}
#[derive(Debug)]
pub struct Status {
    pub code: ExceptionCode,
}
impl Status {
    pub fn new_exception(code: ExceptionCode, _: Option<&str>) -> Self {
        Self { code }
    }
}
pub type Result<T> = std::result::Result<T, Status>;
pub trait Interface {}
