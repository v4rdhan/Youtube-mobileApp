//! JNI boundary. Engine is shared under a mutex; adblock's single-thread feature is disabled.
use adblock::{Engine, lists::{FilterSet, ParseOptions}, request::Request};
use jni::{JNIEnv, objects::{JClass, JString}, sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE}};
use std::sync::{Mutex, OnceLock};

static ENGINE: OnceLock<Mutex<Engine>> = OnceLock::new();

fn build_engine(rules: &str) -> Engine {
    let mut filters = FilterSet::new(false);
    filters.add_filter_list(rules.to_owned(), ParseOptions::default());
    Engine::new_with_filter_set(filters)
}

fn read(env: &mut JNIEnv, value: &JString) -> Option<String> {
    env.get_string(value).ok().map(Into::into)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_streamshield_blocking_NativeBridge_replaceRules(
    mut env: JNIEnv, _class: JClass, rules: JString,
) -> jboolean {
    let Some(rules) = read(&mut env, &rules) else { return JNI_FALSE };
    // Build without holding the request mutex so the old engine keeps serving requests.
    let engine = build_engine(&rules);
    let Ok(mut guard) = ENGINE.get_or_init(|| Mutex::new(Engine::default())).lock() else {
        return JNI_FALSE;
    };
    *guard = engine;
    JNI_TRUE
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_streamshield_blocking_NativeBridge_shouldBlock(
    mut env: JNIEnv, _class: JClass, url: JString, source: JString, kind: JString, method: JString,
) -> jboolean {
    let values = (read(&mut env, &url), read(&mut env, &source), read(&mut env, &kind), read(&mut env, &method));
    let (Some(url), Some(source), Some(kind), Some(method)) = values else { return JNI_FALSE };
    let Ok(request) = Request::new(&url, &source, &kind, &method) else { return JNI_FALSE };
    let Some(engine) = ENGINE.get() else { return JNI_FALSE };
    let Ok(guard) = engine.lock() else { return JNI_FALSE };
    if guard.check_network_request(&request).should_block() { JNI_TRUE } else { JNI_FALSE }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_app_streamshield_blocking_NativeBridge_cosmeticSelectors(
    mut env: JNIEnv, _class: JClass, url: JString,
) -> jstring {
    let json = read(&mut env, &url)
        .and_then(|url| ENGINE.get().and_then(|e| e.lock().ok())
            .map(|engine| engine.url_cosmetic_resources(&url).hide_selectors))
        .and_then(|selectors| serde_json::to_string(&selectors).ok())
        .unwrap_or_else(|| "[]".into());
    env.new_string(json).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[cfg(test)]
mod tests {
    use super::*;
    const RULES: &str = include_str!("../../app/src/main/assets/filters/starter.txt");

    #[test]
    fn blocks_ad_endpoints_without_blocking_video_cdn() {
        let engine = build_engine(RULES);
        for url in ["https://googleads.g.doubleclick.net/pagead/id", "https://www.youtube.com/api/stats/ads?x=1",
            "https://m.youtube.com/youtubei/v1/player/ad_break?prettyPrint=false"] {
            for method in ["GET", "POST"] {
                let request = Request::new(url, "https://m.youtube.com/watch?v=test", "xmlhttprequest", method).unwrap();
                assert!(engine.check_network_request(&request).should_block(), "{method} {url}");
            }
        }
        for url in ["https://rr1.googlevideo.com/videoplayback?id=test", "https://www.youtube.com/youtubei/v1/player", "https://i.ytimg.com/vi/test/hqdefault.jpg",
            "https://m.youtube.com/youtubei/v1/player/ad_breakfast", "https://youtube.com.example.org/youtubei/v1/player/ad_break"] {
            let request = Request::new(url, "https://m.youtube.com", "media", "GET").unwrap();
            assert!(!engine.check_network_request(&request).should_block(), "{url}");
        }
    }

    #[test]
    fn respects_exceptions_and_request_types() {
        let engine = build_engine("||example.org^$script\n@@||example.org/allowed.js$script");
        for (url, kind, expected) in [("https://example.org/ad.js", "script", true), ("https://example.org/allowed.js", "script", false), ("https://example.org/photo.jpg", "image", false)] {
            assert_eq!(engine.check_network_request(&Request::new(url, "https://m.youtube.com", kind, "GET").unwrap()).should_block(), expected);
        }
    }

    #[test]
    fn cosmetics_are_domain_scoped() {
        let engine = build_engine(RULES);
        assert!(engine.url_cosmetic_resources("https://m.youtube.com/watch?v=test").hide_selectors.contains("ytm-ad-slot-renderer"));
        assert!(engine.url_cosmetic_resources("https://example.org").hide_selectors.is_empty());
    }

    #[test]
    fn invalid_requests_are_rejected() {
        assert!(Request::new("not a url", "https://m.youtube.com", "other", "GET").is_err());
    }
}
