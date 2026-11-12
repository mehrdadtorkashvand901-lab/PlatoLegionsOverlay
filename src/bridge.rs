use crate::{card::Unit, search::analyze, state::{Move, State}, utility::calc_scores};
use jni::{JNIEnv, objects::{JObject, JString}, sys::jstring};
use serde::Deserialize;
use serde_json::{Value, json};

#[derive(Deserialize)]
#[serde(tag="op", rename_all="snake_case", deny_unknown_fields)]
enum Request {
    New { units: [[Unit;2];2] },
    Validate { state: State },
    Apply { state: State, #[serde(rename="move")] mov: Move },
    Analyze { state: State, budget_ms: u64, max_depth: u8 },
}
fn handle(input: &str) -> Result<Value, String> {
    if input.len() > 65536 { return Err("اندازهٔ ورودی بیش از حد مجاز است".into()); }
    let request: Request = serde_json::from_str(input).map_err(|e| format!("JSON نامعتبر: {}", e))?;
    Ok(match request {
        Request::New { units } => json!({"ok":true,"state":State::new(units)}),
        Request::Validate { state } => { state.validate()?; json!({"ok":true,"scores":calc_scores(&state.board),"finished":state.finished()}) },
        Request::Apply { state, mov } => json!({"ok":true,"state":state.play(mov)?}),
        Request::Analyze { state, budget_ms, max_depth } => json!({"ok":true,"analysis":analyze(&state,budget_ms,max_depth)?}),
    })
}
pub fn request(input: &str) -> String {
    match std::panic::catch_unwind(|| handle(input)) {
        Ok(Ok(v)) => v.to_string(),
        Ok(Err(e)) => json!({"ok":false,"error":e}).to_string(),
        Err(_) => json!({"ok":false,"error":"خطای داخلی موتور؛ وضعیت را دوباره بررسی کنید"}).to_string(),
    }
}

#[no_mangle]
pub extern "system" fn Java_ir_legions_overlay_EngineBridge_request(mut env: JNIEnv, _this: JObject, input: JString) -> jstring {
    let result = match env.get_string(&input) {
        Ok(s) => request(&String::from(s)),
        Err(_) => json!({"ok":false,"error":"ورودی JNI قابل خواندن نیست"}).to_string(),
    };
    match env.new_string(result) { Ok(s) => s.into_raw(), Err(_) => std::ptr::null_mut() }
}
