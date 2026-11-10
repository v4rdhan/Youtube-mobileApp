import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const script = readFileSync(new URL('../../app/src/main/assets/youtube-player.js', import.meta.url), 'utf8');
const playerUrl = 'https://m.youtube.com/youtubei/v1/player?prettyPrint=false';
const fixture = () => ({
  adPlacements: [{ preRoll: true }], playerAds: [{}], adSlots: [{}],
  videoDetails: { videoId: 'video-id', title: 'Video' },
  streamingData: { adaptiveFormats: [{ url: 'https://video.googlevideo.com/videoplayback?id=content' }] },
  playabilityStatus: { status: 'OK' }, captions: { tracks: [] },
  playerConfig: { streamSelectionConfig: {} }
});

function createPage(host = 'm.youtube.com', initial = {}) {
  // Subclasses keep prototype hooks isolated between test pages while exercising
  // native Response parsing, body consumption, rejection, and metadata behavior.
  class PageResponse extends Response {
    json() { return super.json(); }
    text() { return super.text(); }
  }
  class PageXHR {
    constructor(url, value, type = '') { this.responseURL = url; this.value = value; this.responseType = type; }
    get response() { return this.value; }
    get responseText() {
      if (this.responseType && this.responseType !== 'text') throw new Error('InvalidStateError');
      return this.value;
    }
  }
  const page = vm.createContext({
    location: { protocol: 'https:', hostname: host, href: `https://${host}/watch?v=video-id` },
    URL, Response: PageResponse, XMLHttpRequest: PageXHR, ...initial
  });
  vm.runInContext('window = globalThis; top = window', page);
  vm.runInContext(script, page);
  return page;
}

function response(page, body, url = playerUrl) {
  const result = new page.Response(body, { status: 200, headers: { 'Content-Type': 'application/json' } });
  Object.defineProperty(result, 'url', { value: url });
  return result;
}

function assertClean(value) {
  for (const key of ['adPlacements', 'playerAds', 'adSlots']) assert.equal(key in value, false, key);
  const { adPlacements, playerAds, adSlots, ...content } = fixture();
  assert.deepEqual(JSON.parse(JSON.stringify(value)), content);
}

test('initial inline player data is filtered before its first read and on later assignments', () => {
  const page = createPage();
  page.next = fixture();
  vm.runInContext('var ytInitialPlayerResponse = next', page);
  assertClean(page.ytInitialPlayerResponse);
  page.next = fixture();
  vm.runInContext('ytInitialPlayerResponse = next', page);
  assertClean(page.ytInitialPlayerResponse);
});

test('late injection cleans existing globals and nested initial data', () => {
  const page = createPage('www.youtube.com', {
    ytInitialPlayerResponse: fixture(), ytInitialData: { playerResponse: fixture() }
  });
  assertClean(page.ytInitialPlayerResponse);
  assertClean(page.ytInitialData.playerResponse);
});

test('standalone inline playerResponse is filtered before its first read and after replacement', () => {
  const page = createPage();
  for (let index = 0; index < 2; index++) {
    page.next = fixture();
    vm.runInContext('var playerResponse = next; startupResponse = playerResponse', page);
    assertClean(page.startupResponse);
    assert.equal(page.startupResponse, page.next, 'preserve the response identity');
  }
  assertClean(createPage('www.youtube.com', { playerResponse: fixture() }).playerResponse);
});

test('startup objects populated after assignment are cleaned again before consumption', () => {
  const page = createPage();
  for (const name of ['ytInitialPlayerResponse', 'ytInitialData', 'playerResponse']) {
    const value = {};
    page[name] = value;
    // An inline script may fill a retained reference rather than reassigning
    // the global. No JSON parser or global setter sees this population.
    Object.assign(value, fixture());
    assertClean(page[name]);
    assert.equal(page[name], value);
    Object.assign(value, fixture());
    assertClean(page[name]);
  }
});

test('inline ytplayer configs filter serialized startup ads before parsing', () => {
  const page = createPage();
  page.config = { args: { player_response: JSON.stringify(fixture()), autoplay: '1' }, assets: { js: '/player.js' } };
  vm.runInContext('var ytplayer = {}; ytplayer.config = config; startupConfig = ytplayer.config', page);
  assertClean(JSON.parse(page.startupConfig.args.player_response));
  assert.equal(page.startupConfig, page.config);
  assert.equal(page.startupConfig.args.autoplay, '1');
  assert.equal(page.startupConfig.assets.js, '/player.js');

  const late = createPage('www.youtube.com', {
    ytplayer: { config: { args: { player_response: JSON.stringify(fixture()) } } }
  });
  assertClean(JSON.parse(late.ytplayer.config.args.player_response));
  page.ytplayer = { config: { playerResponse: fixture() } };
  assertClean(page.ytplayer.config.playerResponse);
});

test('JSON parsing covers player responses, SPA envelopes, arrays, and serialized configs', () => {
  const page = createPage();
  const values = [fixture(), { playerResponse: fixture() }, [{ response: { playerResponse: fixture() } }],
    { args: { player_response: JSON.stringify(fixture()) } }];
  for (const value of values) {
    page.source = JSON.stringify(value);
    const cleaned = vm.runInContext('JSON.parse(source)', page);
    if (Array.isArray(cleaned)) assertClean(cleaned[0].response.playerResponse);
    else if (cleaned.args) assertClean(JSON.parse(cleaned.args.player_response));
    else assertClean(cleaned.playerResponse || cleaned);
  }
});

test('JSON reviver behavior, primitives, unrelated data, and parse errors are preserved', () => {
  const page = createPage();
  assert.equal(vm.runInContext('JSON.parse(\'{"value":2}\', (k,v) => k === "value" ? v * 3 : v).value', page), 6);
  for (const value of [null, true, 12, 'hello', { contents: [{ title: 'Keep me' }] }]) {
    page.source = JSON.stringify(value);
    assert.equal(vm.runInContext('JSON.stringify(JSON.parse(source))', page), JSON.stringify(value));
  }
  assert.throws(() => vm.runInContext('JSON.parse("invalid")', page), { name: 'SyntaxError' });
});

test('fetch JSON and text responses lose ads while retaining content and Response metadata', async () => {
  const page = createPage();
  for (const url of [playerUrl, 'https://m.youtube.com/youtubei/v1/next',
    'https://m.youtube.com/youtubei/v1/get_watch?prettyPrint=false',
    'https://m.youtube.com/youtubei/v1/reel/reel_watch_sequence',
    'https://m.youtube.com/watch?v=next&pbj=1', 'https://www.youtube.com/playlist?list=PL123']) {
    const json = response(page, JSON.stringify(fixture()), url);
    assertClean(await json.json());
    assert.equal(json.url, url);
    assert.equal(json.status, 200);
    assert.equal(json.headers.get('Content-Type'), 'application/json');
    assert.equal(json.bodyUsed, true);
    await assert.rejects(json.json(), TypeError);
    assertClean(JSON.parse(await response(page, JSON.stringify(fixture()), url).text()));
  }
});

test('invalid JSON and network/body errors retain their original behavior', async () => {
  const page = createPage();
  assert.equal(await response(page, 'not JSON').text(), 'not JSON');
  await assert.rejects(response(page, 'not JSON').json(), SyntaxError);
});

test('fetch text covers nested serialized player configs', async () => {
  const page = createPage();
  const body = JSON.stringify({ args: { player_response: JSON.stringify(fixture()) } });
  const result = JSON.parse(await response(page, body).text());
  assertClean(JSON.parse(result.args.player_response));
});

test('non-player responses and non-YouTube lookalike URLs are left byte for byte intact', async () => {
  const page = createPage();
  const body = JSON.stringify(fixture(), null, 2);
  for (const url of ['https://example.com/youtubei/v1/player',
    'https://youtube.com.example.com/youtubei/v1/player',
    'https://m.youtube.com/youtubei/v1/browse',
    'https://video.googlevideo.com/videoplayback', 'http://m.youtube.com/youtubei/v1/player']) {
    assert.equal(await response(page, body, url).text(), body);
    assert.equal(JSON.stringify(await response(page, body, url).json()), JSON.stringify(fixture()));
  }
});

test('XHR text and JSON response getters filter SPA data without changing response type errors', () => {
  const page = createPage();
  const xhr = new page.XMLHttpRequest(playerUrl, JSON.stringify(fixture()));
  assertClean(JSON.parse(xhr.responseText));
  assertClean(JSON.parse(xhr.response));
  const json = new page.XMLHttpRequest(playerUrl, fixture(), 'json');
  assertClean(json.response);
  assert.throws(() => json.responseText, /InvalidStateError/);
  const other = new page.XMLHttpRequest('https://example.org/player', JSON.stringify(fixture()));
  assert.equal(other.response, JSON.stringify(fixture()));
});

test('repeat injection does not stack wrappers; cycles and frozen data do not throw', () => {
  const page = createPage();
  const parse = vm.runInContext('JSON.parse', page);
  vm.runInContext(script, page);
  assert.equal(vm.runInContext('JSON.parse', page), parse);
  const cyclic = fixture();
  cyclic.response = cyclic;
  page.ytInitialPlayerResponse = cyclic;
  assert.equal(cyclic.adPlacements, undefined);
  page.ytInitialPlayerResponse = Object.freeze(fixture());
  assert.equal(page.ytInitialPlayerResponse.videoDetails.videoId, 'video-id');
});

test('access restrictions and live-stream metadata are preserved', () => {
  const page = createPage();
  for (const status of ['LOGIN_REQUIRED', 'UNPLAYABLE', 'LIVE_STREAM_OFFLINE']) {
    const value = fixture();
    value.playabilityStatus = { status, reason: 'Unavailable', liveStreamability: { live: true } };
    page.ytInitialPlayerResponse = value;
    assert.deepEqual(page.ytInitialPlayerResponse.playabilityStatus, value.playabilityStatus);
  }
});

test('script does not install outside YouTube', () => {
  for (const host of ['example.com', 'youtube.com.example.com']) {
    const page = createPage(host);
    assert.equal(page.__streamshieldPlayerProtection, undefined);
    page.ytInitialPlayerResponse = fixture();
    assert.ok(page.ytInitialPlayerResponse.adPlacements);
  }
});

test('prefetched navigation player data is filtered in JSON, fetch, and XHR before a video opens', async () => {
  const page = createPage();
  const url = 'https://m.youtube.com/youtubei/v1/navigation/resolve_url?prettyPrint=false';
  const body = JSON.stringify({ endpoint: { watchEndpoint: {
    videoId: 'video-id', unserializedPrefetchData: { playerResponse: fixture() }
  } } });
  const unwrap = value => value.endpoint.watchEndpoint.unserializedPrefetchData.playerResponse;
  page.source = body;
  assertClean(unwrap(vm.runInContext('JSON.parse(source)', page)));
  assertClean(unwrap(await response(page, body, url).json()));
  assertClean(unwrap(JSON.parse(await response(page, body, url).text())));
  assertClean(unwrap(JSON.parse(new page.XMLHttpRequest(url, body).responseText)));
  const foreign = 'https://youtube.com.example.org/youtubei/v1/navigation/resolve_url';
  assert.equal(await response(page, body, foreign).text(), body);
});

// Model ad-state transitions and media metadata separately from DOM mutations.
// A blocked ad has no metadata/event to wake an observer; a normal content
// spinner has the same media state but must never trigger a seek or reload.
function recoveryPage({ ad = true, duration = NaN, readyState = 0, skip = null, paused = true,
  filtered = false, playerState, stats,
  playerResponse = fixture(), reloadAvailable = true, href } = {}) {
  let now = 0;
  let nextTimer = 0;
  let mutation;
  const timers = new Map();
  const listeners = new Map();
  const seeks = [];
  const reloads = [];
  let position = 0;
  const video = {
    duration, readyState, paused, error: null, currentSrc: 'https://ads.example/preroll',
    get currentTime() { return position; },
    set currentTime(value) { position = value; seeks.push(value); }
  };
  const state = { ad, video, skip, playerResponse, playerState, stats };
  const player = {
    classList: { contains: name => state.ad && name === 'ad-showing' },
    querySelector: selector => selector === 'video' ? state.video : state.skip,
    querySelectorAll: () => Array.isArray(state.skip) ? state.skip : state.skip ? [state.skip] : [],
    getPlayerResponse: () => state.playerResponse,
    getPlayerState: () => state.playerState,
    getStatsForNerds: () => state.stats
  };
  if (reloadAvailable) player.loadVideoById = (...args) => reloads.push(args);
  const page = createPage('m.youtube.com', {
    document: {
      getElementById: () => player,
      querySelector: () => player,
      addEventListener: (name, callback) => listeners.set(name, callback)
    },
    Date: class extends Date { static now() { return now; } },
    MutationObserver: class {
      constructor(callback) { mutation = callback; }
      observe() {}
    },
    setTimeout(callback, delay) {
      const id = ++nextTimer;
      timers.set(id, { callback, at: now + delay });
      return id;
    }
  });
  if (href) page.location.href = href;
  if (filtered) page.ytInitialPlayerResponse = playerResponse;
  function advance(milliseconds = 0) {
    const end = now + milliseconds;
    while (true) {
      const next = [...timers].sort((a, b) => a[1].at - b[1].at)[0];
      if (!next || next[1].at > end) break;
      now = next[1].at;
      timers.delete(next[0]);
      next[1].callback();
    }
    now = end;
  }
  return { page, player, state, seeks, reloads, timers, advance, mutate: () => mutation(),
    dispatch: (name, target = video) => listeners.get(name)?.({ target }) };
}

function skipButton({ disabled = false, visible = true, ariaDisabled = false, click = () => {} } = {}) {
  return { disabled, getClientRects: () => visible ? [{}] : [],
    getAttribute: () => ariaDisabled ? 'true' : null, click };
}

test('an available skip control is used immediately, once, without seeking the video', () => {
  let clicks = 0;
  const testPage = recoveryPage({ duration: 30, skip: skipButton({ click: () => { clicks++; } }) });
  testPage.advance();
  assert.equal(clicks, 1);
  assert.deepEqual(testPage.seeks, []);
  testPage.state.ad = false;
  testPage.mutate();
  testPage.advance(250);
  assert.equal(testPage.timers.size, 0, 'ad polling ends with content');
  assert.deepEqual(testPage.reloads, []);
});

test('a finite preroll is ended immediately and a second ad source is handled without waiting out either ad', () => {
  const testPage = recoveryPage({ duration: 30 });
  testPage.advance();
  assert.deepEqual(testPage.seeks, [30]);
  testPage.advance(500);
  assert.deepEqual(testPage.seeks, [30], 'do not repeatedly seek the same ad');
  testPage.state.video.currentSrc = 'https://ads.example/second';
  testPage.state.video.duration = 15;
  testPage.dispatch('loadedmetadata');
  testPage.advance(250);
  assert.deepEqual(testPage.seeks, [30, 15]);
});

test('hidden, disabled, and aria-disabled skip controls are not clicked', () => {
  for (const options of [{ visible: false }, { disabled: true }, { ariaDisabled: true }]) {
    const testPage = recoveryPage({ duration: 10, skip: skipButton({ ...options,
      click() { assert.fail('ineligible control clicked'); } }) });
    testPage.advance();
    assert.deepEqual(testPage.seeks, [10]);
  }
});

test('a hidden skip template does not mask a visible skip control', () => {
  let clicks = 0;
  const testPage = recoveryPage({ duration: 10, skip: [
    skipButton({ visible: false, click() { assert.fail('hidden template clicked'); } }),
    skipButton({ click() { clicks++; } })
  ] });
  testPage.advance();
  assert.equal(clicks, 1);
  assert.deepEqual(testPage.seeks, []);
});

test('a blocked preroll with no metadata retries the current content once instead of waiting forever', () => {
  const testPage = recoveryPage();
  testPage.advance(749);
  assert.deepEqual(testPage.reloads, []);
  testPage.advance(1);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
  assert.equal(testPage.state.playerResponse.adPlacements, undefined);
  testPage.advance(10000);
  testPage.mutate();
  testPage.advance(250);
  assert.equal(testPage.reloads.length, 1, 'no retry loop');
  assert.deepEqual(testPage.seeks, [], 'never seek an unknown duration');
});

test('an ad whose seek never finishes can use the bounded player retry', () => {
  const testPage = recoveryPage({ duration: 30 });
  testPage.advance(1000);
  assert.deepEqual(testPage.seeks, [30]);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
});

test('an ended ad with buffered media can recover if the player never transitions to content', () => {
  const testPage = recoveryPage({ duration: 30, readyState: 4 });
  testPage.advance();
  testPage.state.video.ended = true;
  testPage.advance(1000);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
});

test('late skip controls are noticed and repeated injection does not create another recovery loop', () => {
  const testPage = recoveryPage();
  testPage.advance();
  vm.runInContext(script, testPage.page);
  assert.equal(testPage.timers.size, 1);
  let clicks = 0;
  testPage.state.skip = skipButton({ click() { clicks++; testPage.state.ad = false; } });
  testPage.mutate();
  testPage.advance(1000);
  assert.equal(clicks, 1);
  assert.deepEqual(testPage.reloads, []);
  assert.equal(testPage.timers.size, 0);
});

test('ordinary playback, pause, buffering, errors, and live content are never sought or reloaded without an ad state', () => {
  for (const duration of [NaN, Infinity, 200]) {
    const testPage = recoveryPage({ ad: false, duration });
    testPage.advance(10000);
    testPage.dispatch('error');
    testPage.mutate();
    testPage.advance(1000);
    assert.deepEqual(testPage.seeks, []);
    assert.deepEqual(testPage.reloads, []);
    assert.equal(testPage.timers.size, 0);
  }
});

test('recovery keeps timestamps and the last content position instead of using an ad position', () => {
  for (const [suffix, seconds] of [['&t=1m30s', 90], ['&start=42', 42], ['&t=2h3m4s', 7384], ['&t=invalid', 0]]) {
    const testPage = recoveryPage({ href: `https://m.youtube.com/watch?v=video-id${suffix}` });
    testPage.advance(1000);
    assert.deepEqual(testPage.reloads, [['video-id', seconds]]);
  }
  const testPage = recoveryPage({ ad: false });
  testPage.advance();
  testPage.state.video.currentTime = 124;
  testPage.dispatch('timeupdate');
  testPage.state.ad = true;
  testPage.state.video.currentTime = 3;
  testPage.dispatch('timeupdate');
  testPage.mutate();
  testPage.advance(1000);
  assert.deepEqual(testPage.reloads, [['video-id', 124]]);
});

test('navigation resets retry budget and content position; stale player data cannot reload the previous video', () => {
  const testPage = recoveryPage();
  testPage.advance(1000);
  testPage.page.location.href = 'https://m.youtube.com/watch?v=next-id';
  testPage.dispatch('yt-navigate-finish');
  testPage.advance(1000);
  assert.equal(testPage.reloads.length, 1, 'stale response is ignored');
  testPage.state.playerResponse = fixture();
  testPage.state.playerResponse.videoDetails.videoId = 'next-id';
  testPage.advance(250);
  assert.deepEqual(testPage.reloads, [['video-id', 0], ['next-id', 0]]);
});

test('retry does not override access errors, live streams, unavailable APIs, or non-watch pages', () => {
  for (const status of ['LOGIN_REQUIRED', 'UNPLAYABLE', 'LIVE_STREAM_OFFLINE']) {
    const playerResponse = fixture();
    playerResponse.playabilityStatus.status = status;
    const testPage = recoveryPage({ playerResponse });
    testPage.advance(5000);
    assert.deepEqual(testPage.reloads, []);
  }
  const live = fixture();
  live.videoDetails.isLive = true;
  for (const options of [{ playerResponse: live }, { reloadAvailable: false }, { readyState: 4 },
    { href: 'https://m.youtube.com/shorts/video-id' }, { href: 'https://m.youtube.com/' }]) {
    const testPage = recoveryPage(options);
    testPage.advance(5000);
    assert.deepEqual(testPage.reloads, []);
  }
});

test('filtered startup with no ad marker retries once after eight seconds of requested playback without frames', () => {
  const testPage = recoveryPage({ ad: false, filtered: true, paused: false });
  testPage.advance(7999);
  assert.deepEqual(testPage.reloads, []);
  testPage.advance(1);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
  testPage.advance(60000);
  assert.equal(testPage.reloads.length, 1);
  assert.deepEqual(testPage.seeks, []);
  assert.equal(testPage.timers.size, 0);
});

test('player buffering state can detect requested startup while WebView still reports paused', () => {
  const testPage = recoveryPage({ ad: false, filtered: true, playerState: 3 });
  testPage.advance(8000);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
});

test('inline startup sources wake bounded recovery after their ad data is removed', () => {
  for (const source of ['playerResponse', 'populated', 'ytplayer']) {
    const testPage = recoveryPage({ ad: false, paused: false });
    testPage.advance();
    assert.equal(testPage.timers.size, 0);
    if (source === 'playerResponse') {
      testPage.page.playerResponse = fixture();
      testPage.state.playerResponse = testPage.page.playerResponse;
    } else if (source === 'populated') {
      const retained = {};
      testPage.page.ytInitialPlayerResponse = retained;
      Object.assign(retained, fixture());
      testPage.state.playerResponse = testPage.page.ytInitialPlayerResponse;
    } else {
      testPage.page.ytplayer = {};
      testPage.page.ytplayer.config = { args: { player_response: JSON.stringify(fixture()) } };
      testPage.state.playerResponse = JSON.parse(testPage.page.ytplayer.config.args.player_response);
    }
    assertClean(testPage.state.playerResponse);
    testPage.advance(7999);
    assert.deepEqual(testPage.reloads, [], source);
    testPage.advance(1);
    assert.deepEqual(testPage.reloads, [['video-id', 0]], source);
    testPage.advance(60000);
    assert.equal(testPage.reloads.length, 1, source);
    assert.deepEqual(testPage.seeks, [], source);
  }
});

test('get_watch fetch and XHR envelopes retain evidence for recovery after their ad fields are removed', async () => {
  const url = 'https://m.youtube.com/youtubei/v1/get_watch?prettyPrint=false';
  for (const transport of ['json', 'text', 'xhr']) {
    const testPage = recoveryPage({ ad: false, paused: false });
    testPage.advance();
    assert.equal(testPage.timers.size, 0, 'idle until ad data arrives');
    const body = JSON.stringify([{ playerResponse: fixture() }]);
    const result = transport === 'xhr'
      ? JSON.parse(new testPage.page.XMLHttpRequest(url, body).responseText)
      : transport === 'text'
        ? JSON.parse(await response(testPage.page, body, url).text())
        : await response(testPage.page, body, url).json();
    testPage.state.playerResponse = result[0].playerResponse;
    assertClean(testPage.state.playerResponse);
    testPage.advance(8000);
    assert.deepEqual(testPage.reloads, [['video-id', 0]], transport);
  }
});

test('no unmarked retry without removed ads, user playback, or while hidden, even after media events', () => {
  for (const options of [{ filtered: false, paused: false }, { filtered: true, paused: true },
    { filtered: true, paused: false, readyState: 4 }]) {
    const testPage = recoveryPage({ ad: false, ...options });
    for (const event of ['play', 'waiting', 'stalled', 'error']) testPage.dispatch(event);
    testPage.advance(20000);
    assert.deepEqual(testPage.reloads, []);
    assert.deepEqual(testPage.seeks, []);
  }
  const testPage = recoveryPage({ ad: false, filtered: true, paused: false });
  testPage.page.document.visibilityState = 'hidden';
  testPage.advance(20000);
  assert.deepEqual(testPage.reloads, []);
});

test('empty ad placeholders are not evidence for an unmarked startup retry', () => {
  const playerResponse = fixture();
  playerResponse.adPlacements = []; playerResponse.playerAds = []; playerResponse.adSlots = [];
  const testPage = recoveryPage({ ad: false, filtered: true, paused: false, playerResponse });
  testPage.advance(20000);
  assert.deepEqual(testPage.reloads, []);
});

test('content frames arriving before the deadline cancel startup recovery and later content buffering stays untouched', () => {
  const testPage = recoveryPage({ ad: false, filtered: true, paused: false });
  testPage.advance(7000);
  testPage.state.video.readyState = 4;
  testPage.dispatch('canplay');
  testPage.advance(250);
  testPage.state.video.readyState = 0;
  testPage.dispatch('waiting');
  testPage.advance(20000);
  assert.deepEqual(testPage.reloads, []);
  assert.equal(testPage.timers.size, 0);
});

test('pausing or replacing the startup media restarts the grace period', () => {
  const testPage = recoveryPage({ ad: false, filtered: true, paused: false });
  testPage.advance(7000);
  testPage.state.video.paused = true;
  testPage.dispatch('pause');
  testPage.advance(5000);
  assert.deepEqual(testPage.reloads, []);
  testPage.state.video.paused = false;
  testPage.dispatch('play');
  testPage.advance(7999);
  assert.deepEqual(testPage.reloads, []);
  testPage.advance(1);
  assert.equal(testPage.reloads.length, 1);

  const replacement = recoveryPage({ ad: false, filtered: true, paused: false });
  replacement.advance(7000);
  replacement.state.video = { ...replacement.state.video };
  replacement.advance(1000);
  assert.deepEqual(replacement.reloads, []);
  replacement.advance(7250);
  assert.equal(replacement.reloads.length, 1);
});

test('startup retry respects access errors, live streams, stale responses, and non-watch routes', () => {
  for (const status of ['LOGIN_REQUIRED', 'UNPLAYABLE', 'LIVE_STREAM_OFFLINE']) {
    const playerResponse = fixture();
    playerResponse.playabilityStatus.status = status;
    const testPage = recoveryPage({ ad: false, filtered: true, paused: false, playerResponse });
    testPage.advance(20000);
    assert.deepEqual(testPage.reloads, []);
  }
  const live = fixture(); live.videoDetails.isLive = true;
  for (const options of [{ playerResponse: live }, { reloadAvailable: false },
    { href: 'https://m.youtube.com/watch?v=other' }, { href: 'https://m.youtube.com/shorts/video-id' }]) {
    const testPage = recoveryPage({ ad: false, filtered: true, paused: false, ...options });
    testPage.advance(20000);
    assert.deepEqual(testPage.reloads, []);
  }
});

test('query-only changes preserve the retry budget and content position', () => {
  const testPage = recoveryPage();
  testPage.advance(1000);
  testPage.page.location.href += '&list=mix&pp=tracking';
  testPage.dispatch('yt-navigate-finish');
  testPage.advance(10000);
  assert.equal(testPage.reloads.length, 1);
});

test('SSAP ad diagnostics trigger a bounded retry even without an ad CSS marker', () => {
  const testPage = recoveryPage({ ad: false, duration: 300, stats: { debug_info: 'SSAP, AD, preroll' } });
  testPage.advance(1000);
  assert.deepEqual(testPage.reloads, [['video-id', 0]]);
  assert.deepEqual(testPage.seeks, []);
});
