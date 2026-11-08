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
  vm.runInContext('window = globalThis', page);
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
