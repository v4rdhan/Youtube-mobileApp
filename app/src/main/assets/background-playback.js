(function () {
  'use strict';
  if (window !== window.top || window.__streamshieldMedia ||
      typeof StreamShieldMedia === 'undefined') return;

  const elements = () => Array.from(document.querySelectorAll('video,audio'));
  const isPlaying = media => !media.paused && !media.ended && !media.error && !media.muted && media.volume > 0;
  let previous = '';
  function report() {
    const state = JSON.stringify({
      playing: elements().some(isPlaying),
      title: document.title.replace(/ - YouTube$/, '')
    });
    if (state === previous) return;
    previous = state;
    StreamShieldMedia.postMessage(state);
  }

  // Keep an already playing video from being paused by the site's visibility
  // handler. User pause, errors, audio focus, and access checks remain effective.
  for (const [property, visible] of [['hidden', false], ['webkitHidden', false], ['visibilityState', 'visible']]) {
    try {
      let owner = document;
      let descriptor;
      while (owner && !descriptor) {
        descriptor = Object.getOwnPropertyDescriptor(owner, property);
        owner = Object.getPrototypeOf(owner);
      }
      if (!descriptor || !descriptor.get) continue;
      const original = descriptor.get;
      Object.defineProperty(document, property, {
        configurable: true,
        get() { return elements().some(isPlaying) ? visible : Reflect.apply(original, document, []); }
      });
    } catch (_) {}
  }
  for (const event of ['visibilitychange', 'webkitvisibilitychange']) {
    window.addEventListener(event, function (event) {
      if (elements().some(isPlaying)) event.stopImmediatePropagation();
    }, true);
  }
  for (const event of ['play', 'playing', 'pause', 'ended', 'volumechange', 'emptied', 'error', 'loadedmetadata']) {
    document.addEventListener(event, report, true);
  }
  new MutationObserver(report).observe(document, { childList: true, subtree: true });
  window.__streamshieldMedia = {
    pause() { elements().forEach(media => media.pause()); report(); },
    report
  };
  report();
})();
