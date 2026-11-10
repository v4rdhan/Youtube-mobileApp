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
  // Retain only IDs, not response/stream objects. This supplies evidence for a
  // startup retry when removing ad data leaves no ad-showing DOM marker.
  const filteredVideos = new Set();
  let onAdData = () => {};
  const wrappers = ['playerResponse', 'response', 'config', 'args', 'PLAYER_VARS',
    'endpoint', 'watchEndpoint', 'unserializedPrefetchData'];

  // Follow known player envelopes only; do not walk or rewrite stream metadata,
  // playability/access checks, captions, playlists, or arbitrary page objects.
  function prune(value, seen = new WeakSet()) {
    if (!value || typeof value !== 'object' || seen.has(value)) return false;
    seen.add(value);
    let changed = false;
    let removedAds = false;
    for (const key of adKeys) {
      if (Object.prototype.hasOwnProperty.call(value, key)) {
        try {
          const ads = value[key];
          if (delete value[key]) {
            changed = true;
            removedAds = removedAds || (Array.isArray(ads) && ads.length > 0);
          }
        } catch (_) {}
      }
    }
    if (removedAds && typeof value.videoDetails?.videoId === 'string') {
      filteredVideos.add(value.videoDetails.videoId);
      if (filteredVideos.size > 32) filteredVideos.delete(filteredVideos.values().next().value);
      onAdData();
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
  // existing value (late fallback injection), subsequent assignments, and
  // objects filled in place before the player reads them. ytplayer.config
  // can also contain a serialized args.player_response with startup ads.
  for (const name of ['ytInitialPlayerResponse', 'ytInitialData', 'playerResponse', 'ytplayer']) {
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
        get() { return clean(value); },
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
        (/^\/youtubei\/v\d+\/(player|get_watch|next|reel\/reel_watch_sequence|navigation\/resolve_url)\/?$/.test(url.pathname) ||
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
  // example, before late injection). Seek only in an explicit player ad state.
  // An unmarked startup may retry once if ad data was removed for this video,
  // playback was requested, and no content frames arrive for eight seconds.
  if (typeof document === 'undefined' || window !== window.top) return;
  let timer = null;
  let route = '';
  let adPlayer = null;
  let adSince = 0;
  let lastAction = 0;
  let reloaded = false;
  let contentTime = 0;
  let startupSince = null;
  let contentStarted = false;
  let startupVideo = null;
  let skipped = new WeakSet();
  let sought = new WeakMap();

  function getPlayer() {
    return document.getElementById('movie_player') || document.querySelector('.html5-video-player');
  }

  function hasAdClass(player) {
    return player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'));
  }

  function hasAd(player) {
    if (!player) return false;
    if (hasAdClass(player)) return true;
    try { return player.getStatsForNerds?.()?.debug_info?.startsWith('SSAP, AD') === true; }
    catch (_) { return false; }
  }

  function resetAd() {
    adPlayer = null;
    skipped = new WeakSet();
    sought = new WeakMap();
  }

  function recoverAd() {
    timer = null;
    const url = new URL(location.href);
    const videoRoute = url.pathname === '/watch' ? url.searchParams.get('v') || '' : url.pathname;
    if (route !== videoRoute) {
      route = videoRoute;
      reloaded = false;
      contentTime = 0;
      startupSince = null;
      contentStarted = false;
      startupVideo = null;
      resetAd();
    }
    const player = getPlayer();
    const now = Date.now();
    if (!hasAd(player)) {
      resetAd();
      const video = player?.querySelector('video');
      if (startupVideo !== video) { startupVideo = video; startupSince = null; }
      if (video && video.readyState >= 2) contentStarted = true;
      // The player's buffering state also covers WebViews that leave a video
      // paused until its first media segment arrives. A user pause cancels this.
      let buffering = false;
      try { buffering = player?.getPlayerState?.() === 3; } catch (_) {}
      if (reloaded || contentStarted || !video || video.error ||
          !filteredVideos.has(videoRoute) || (video.paused !== false && !buffering) ||
          document.visibilityState === 'hidden' || !playableResponse(player, url)) {
        startupSince = null;
        return;
      }
      if (startupSince === null) startupSince = now;
      scheduleRecovery(250);
      if (now - startupSince >= 8000) retryContent(player, url);
      return;
    }
    startupSince = null;
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
    // SSAP diagnostics alone do not establish that the media duration is an
    // ad duration: it may describe the entire content stream. Never seek it.
    if (hasAdClass(player) && video && Number.isFinite(video.duration) && video.duration > 0) {
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
    retryContent(player, url);
  }

  function playableResponse(player, url) {
    try {
      if (url.pathname !== '/watch') return null;
      const id = url.searchParams.get('v');
      const response = typeof player.getPlayerResponse === 'function' ? player.getPlayerResponse() : window.ytInitialPlayerResponse;
      if (!id || response?.videoDetails?.videoId !== id || response.videoDetails.isLive ||
          response.playabilityStatus?.status !== 'OK' || !response.streamingData ||
          typeof player.loadVideoById !== 'function') return null;
      return response;
    } catch (_) { return null; }
  }

  function retryContent(player, url) {
    try {
      const response = playableResponse(player, url);
      if (!response) return;
      clean(response);
      const start = response.playerConfig?.playbackStartConfig?.startSeconds;
      const timestamp = url.searchParams.get('t') || url.searchParams.get('start') || '';
      const match = /^(?:(\d+)h)?(?:(\d+)m)?(?:(\d+(?:\.\d+)?)s?)?$/.exec(timestamp);
      const seconds = match ? Number(match[1] || 0) * 3600 + Number(match[2] || 0) * 60 + Number(match[3] || 0) : 0;
      const position = contentTime || (Number.isFinite(start) && start >= 0 ? start : seconds);
      reloaded = true;
      player.loadVideoById(response.videoDetails.videoId, position);
    } catch (_) { /* Site/player changes must not interrupt ordinary playback. */ }
  }

  function scheduleRecovery(delay = 0) {
    if (timer === null) timer = setTimeout(recoverAd, delay);
  }

  // A delayed player response may be the only event after blocked ad traffic.
  onAdData = () => scheduleRecovery();
  document.addEventListener('timeupdate', function (event) {
    const player = getPlayer();
    if (route === new URL(location.href).searchParams.get('v') && player && !hasAd(player) &&
        event.target === player.querySelector('video') && Number.isFinite(event.target.currentTime)) {
      contentTime = event.target.currentTime;
      if (contentTime > 0 && event.target.readyState >= 2) contentStarted = true;
    }
  }, true);
  for (const event of ['play', 'playing', 'pause', 'waiting', 'stalled', 'canplay', 'loadedmetadata',
    'durationchange', 'error', 'yt-navigate-finish', 'visibilitychange']) {
    document.addEventListener(event, () => scheduleRecovery(), true);
  }
  new MutationObserver(() => scheduleRecovery()).observe(document, {
    childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'disabled', 'aria-disabled']
  });
  scheduleRecovery();
})();
