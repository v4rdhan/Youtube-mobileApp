// StreamShield's YouTube-specific player filter. This is independent of ABP scriptlets.
(function () {
  'use strict';
  if (location.protocol !== 'https:' ||
      !/(^|\.)youtube\.com$/.test(location.hostname) ||
      window.__streamshieldPlayerProtection) return;
  Object.defineProperty(window, '__streamshieldPlayerProtection', { value: true });

  const parse = JSON.parse;
  const stringify = JSON.stringify;
  const adKeys = ['adPlacements', 'playerAds', 'adSlots'];
  const wrappers = ['playerResponse', 'response', 'args', 'PLAYER_VARS'];

  // Follow known player envelopes only; do not walk or rewrite stream metadata,
  // playability/access checks, captions, playlists, or arbitrary page objects.
  function prune(value, seen = new WeakSet()) {
    if (!value || typeof value !== 'object' || seen.has(value)) return false;
    seen.add(value);
    let changed = false;
    for (const key of adKeys) {
      if (Object.prototype.hasOwnProperty.call(value, key)) {
        try { changed = delete value[key] || changed; } catch (_) {}
      }
    }
    if (Array.isArray(value)) {
      for (const entry of value) changed = prune(entry, seen) || changed;
    } else {
      for (const key of wrappers) changed = prune(value[key], seen) || changed;
      // Some player configs still carry a serialized response.
      if (typeof value.player_response === 'string') {
        const cleaned = pruneText(value.player_response);
        if (cleaned !== value.player_response) {
          try { value.player_response = cleaned; changed = true; } catch (_) {}
        }
      }
    }
    return changed;
  }

  function clean(value) {
    // Unexpected site data must not break playback or JSON.parse's reviver.
    try { prune(value); } catch (_) {}
    return value;
  }

  function pruneText(text) {
    if (typeof text !== 'string' || !/"(?:adPlacements|playerAds|adSlots|player_response)"/.test(text)) return text;
    try {
      const value = parse(text);
      return prune(value) ? stringify(value) : text;
    } catch (_) { return text; }
  }

  // Initial HTML assigns these before starting the player. Cover both an
  // existing value (late fallback injection) and every subsequent assignment.
  for (const name of ['ytInitialPlayerResponse', 'ytInitialData']) {
    try {
      const descriptor = Object.getOwnPropertyDescriptor(window, name);
      if (descriptor && (!descriptor.configurable || descriptor.get || descriptor.set)) {
        clean(window[name]);
        continue;
      }
      let value = clean(window[name]);
      Object.defineProperty(window, name, {
        configurable: true,
        enumerable: descriptor ? descriptor.enumerable : true,
        get() { return value; },
        set(next) { value = clean(next); }
      });
    } catch (_) {}
  }

  JSON.parse = new Proxy(parse, {
    apply(target, receiver, args) { return clean(Reflect.apply(target, receiver, args)); }
  });

  function isPlayerUrl(address) {
    try {
      const url = new URL(address, location.href);
      return url.protocol === 'https:' && /(^|\.)youtube\.com$/.test(url.hostname) &&
        (/^\/youtubei\/v\d+\/(player|next|reel\/reel_watch_sequence)\/?$/.test(url.pathname) ||
         /^\/(watch|playlist)\/?$/.test(url.pathname));
    } catch (_) { return false; }
  }

  // Response.json bypasses the page's JSON.parse in WebView. Filtering here
  // also retains the original Response's URL, headers, status, and body state.
  if (typeof Response !== 'undefined') {
    for (const method of ['json', 'text']) {
      const original = Response.prototype[method];
      if (typeof original !== 'function') continue;
      Response.prototype[method] = new Proxy(original, {
        apply(target, receiver, args) {
          const result = Reflect.apply(target, receiver, args);
          if (!isPlayerUrl(receiver.url)) return result;
          return result.then(method === 'json' ? clean : pruneText);
        }
      });
    }
  }

  // The mobile site also uses XHR for navigation without a full page load.
  if (typeof XMLHttpRequest !== 'undefined') {
    for (const name of ['response', 'responseText']) {
      const descriptor = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, name);
      if (!descriptor || !descriptor.get || !descriptor.configurable) continue;
      Object.defineProperty(XMLHttpRequest.prototype, name, {
        ...descriptor,
        get() {
          const value = Reflect.apply(descriptor.get, this, []);
          if (!isPlayerUrl(this.responseURL)) return value;
          return typeof value === 'string' ? pruneText(value) : clean(value);
        }
      });
    }
  }
})();
