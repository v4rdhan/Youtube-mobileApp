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
  const wrappers = ['playerResponse', 'response', 'args', 'PLAYER_VARS',
    'endpoint', 'watchEndpoint', 'unserializedPrefetchData'];

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
        (/^\/youtubei\/v\d+\/(player|next|reel\/reel_watch_sequence|navigation\/resolve_url)\/?$/.test(url.pathname) ||
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

  // Data filtering cannot end an ad that the player already started (for
  // example, before late injection). Only act on an explicit player ad state;
  // a spinner or a stalled content video alone is never a reason to seek/reload.
  if (typeof document === 'undefined' || window !== window.top) return;
  let timer = null;
  let route = '';
  let adPlayer = null;
  let adSince = 0;
  let lastAction = 0;
  let reloaded = false;
  let contentTime = 0;
  let skipped = new WeakSet();
  let sought = new WeakMap();

  function getPlayer() {
    return document.getElementById('movie_player') || document.querySelector('.html5-video-player');
  }

  function hasAd(player) {
    return player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
  }

  function resetAd() {
    adPlayer = null;
    skipped = new WeakSet();
    sought = new WeakMap();
  }

  function recoverAd() {
    timer = null;
    if (route !== location.href) {
      route = location.href;
      reloaded = false;
      contentTime = 0;
      resetAd();
    }
    const player = getPlayer();
    if (!hasAd(player)) { resetAd(); return; }
    const now = Date.now();
    if (adPlayer !== player) {
      resetAd();
      adPlayer = player;
      adSince = now;
      lastAction = now;
    }
    // Poll only for the duration of an ad. Metadata and player APIs can become
    // available without a DOM mutation, including when an ad request is blocked.
    scheduleRecovery(250);
    const skip = Array.from(player.querySelectorAll('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button, .ytp-ad-skip-button-container button'))
      .find(button => !button.disabled && button.getAttribute('aria-disabled') !== 'true' &&
        button.getClientRects().length && !skipped.has(button));
    if (skip) {
      skipped.add(skip);
      try { skip.click(); lastAction = now; return; } catch (_) {}
    }
    const video = player.querySelector('video');
    if (video && Number.isFinite(video.duration) && video.duration > 0) {
      const source = video.currentSrc || video.src;
      const previous = sought.get(video);
      if (!previous || previous.source !== source || previous.duration !== video.duration) {
        try {
          // End this finite ad through the media element so YouTube advances to
          // its content. Do not change playback rate, mute state, or content URLs.
          video.currentTime = video.duration;
          sought.set(video, { source, duration: video.duration });
          lastAction = now;
          return;
        } catch (_) {}
      }
    }
    // An ad with no usable media can otherwise buffer forever. Make one retry
    // through YouTube's player, after allowing skip/ended handlers to run.
    if (reloaded || now - adSince < 750 || now - lastAction < 750 ||
        (video && video.readyState >= 2 && !video.error && !video.ended)) return;
    try {
      const url = new URL(location.href);
      if (url.pathname !== '/watch') return;
      const id = url.searchParams.get('v');
      const response = typeof player.getPlayerResponse === 'function' ? player.getPlayerResponse() : window.ytInitialPlayerResponse;
      if (!id || response?.videoDetails?.videoId !== id || response.videoDetails.isLive ||
          response.playabilityStatus?.status !== 'OK' || !response.streamingData ||
          typeof player.loadVideoById !== 'function') return;
      clean(response);
      const start = response.playerConfig?.playbackStartConfig?.startSeconds;
      const timestamp = url.searchParams.get('t') || url.searchParams.get('start') || '';
      const match = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s?)?$/.exec(timestamp);
      const seconds = match ? Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0) : 0;
      const position = contentTime || (Number.isFinite(start) && start >= 0 ? start : seconds);
      reloaded = true;
      player.loadVideoById(id, position);
    } catch (_) { /* Site/player changes must not interrupt ordinary playback. */ }
  }

  function scheduleRecovery(delay = 0) {
    if (timer === null) timer = setTimeout(recoverAd, delay);
  }

  document.addEventListener('timeupdate', function (event) {
    const player = getPlayer();
    if (route === location.href && player && !hasAd(player) &&
        event.target === player.querySelector('video') && Number.isFinite(event.target.currentTime)) {
      contentTime = event.target.currentTime;
    }
  }, true);
  for (const event of ['loadedmetadata', 'durationchange', 'error', 'yt-navigate-finish']) {
    document.addEventListener(event, () => scheduleRecovery(), true);
  }
  new MutationObserver(() => scheduleRecovery()).observe(document, {
    childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'disabled', 'aria-disabled']
  });
  scheduleRecovery();
})();
