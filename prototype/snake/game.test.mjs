import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
import * as engine from './engine.mjs';

// Exercise the real browser controller with a deterministic clock and minimal DOM.
const source = readFileSync(new URL('./game.mjs', import.meta.url), 'utf8')
  .replace(/^import .* from '.\/engine.mjs';\n/, '');
function setup() {
  let now = 0, cells = [], nextFrame = 0;
  const frames = new Map(), stored = new Map();
  const element = (dataset = {}) => ({
    dataset, handlers: {}, style: {}, firstChild: {}, textContent: '', disabled: false,
    addEventListener(type, handler) { this.handlers[type] = handler; },
    matches() { return false; },
    closest(selector) { return selector === 'button' && this.button ? this : null; },
    setPointerCapture() {},
  });
  const ids = new Map();
  const get = id => { if (!ids.has(id)) ids.set(id, element()); return ids.get(id); };
  const pad = ['up','down','left','right'].map(direction => element({ direction }));
  for (const button of [...pad, get('play'), get('pause'), get('restart')]) button.button = true;
  const ctx = new Proxy({
    clearRect() { cells = []; },
    roundRect(x, y) { cells.push([x, y]); },
  }, { get: (target, key) => key in target ? target[key] : () => {} });
  get('board').width = get('board').height = 600;
  get('board').getContext = () => ctx;
  const document = { ...element(), getElementById: get, querySelectorAll: () => pad };
  const window = element();
  runInNewContext(source, {
    ...engine, createGame: () => engine.createGame(() => 0), tick: state => engine.tick(state, () => 0),
    document, window, performance: { now: () => now },
    localStorage: { getItem: key => stored.get(key) ?? null, setItem: (key, value) => stored.set(key, value) },
    requestAnimationFrame(callback) { frames.set(++nextFrame, callback); },
  });
  return {
    get, pad, document, window,
    head: () => cells[0],
    click(id) { get(id).handlers.click(); },
    key(key, target = element(), repeat = false) {
      const event = { key, target, repeat, preventDefault() { this.prevented = true; } };
      document.handlers.keydown(event);
      // Native Space activation occurs on keyup if keydown wasn't cancelled.
      if (key === ' ' && !event.prevented && target.button && !target.disabled) target.handlers.click();
      return event;
    },
    advance(ms = 145) {
      now += ms;
      const pending = [...frames.values()]; frames.clear();
      for (const callback of pending) callback(now);
      assert.equal(frames.size, 1, 'exactly one animation loop remains scheduled');
    },
    pointer(type, x, y, id = 1) {
      get('arena').handlers[type]({ target: get('board'), clientX:x, clientY:y, pointerId:id });
    },
  };
}

test('Space pauses when Restart or a direction button has focus', () => {
  for (const focus of ['restart', 'direction']) {
    const app = setup(); app.click('play'); app.advance();
    app.key(' ', focus === 'restart' ? app.get('restart') : app.pad[0]);
    assert.equal(app.get('status').textContent, 'Taking a breath');
    const head = app.head(); app.advance(1000); assert.deepEqual(app.head(), head);
  }
});

test('a swipe started before restart cannot turn the new run', () => {
  const app = setup(); app.click('play');
  app.pointer('pointerdown', 100, 100); app.click('restart');
  app.pointer('pointerup', 100, 40); app.advance();
  assert.deepEqual(app.head(), [242, 302]);
});

test('pause/resume and pointer cancellation discard unfinished swipes', () => {
  for (const cancel of ['pause', 'pointercancel']) {
    const app = setup(); app.click('play'); app.pointer('pointerdown', 100, 100);
    if (cancel === 'pause') { app.key('p'); app.key('p'); }
    else app.pointer('pointercancel', 100, 100);
    app.pointer('pointerup', 100, 40); app.advance();
    assert.deepEqual(app.head(), [242, 302]);
  }
});

test('a second finger cannot replace or cancel the active swipe', () => {
  const app = setup(); app.click('play');
  app.pointer('pointerdown', 100, 100, 1);
  app.pointer('pointerdown', 20, 20, 2); app.pointer('pointercancel', 20, 20, 2);
  app.pointer('pointerup', 100, 40, 1); app.advance();
  assert.deepEqual(app.head(), [212, 272]);
});

test('restart resets score, queued turns, position and tick clock', () => {
  const app = setup(); app.click('play'); app.advance(140);
  app.key('w'); app.key('a'); app.click('restart');
  assert.equal(app.get('score').textContent, '000');
  assert.deepEqual(app.head(), [212, 302]);
  app.advance(5); assert.deepEqual(app.head(), [212, 302]);
  app.advance(140); assert.deepEqual(app.head(), [242, 302]);
});

test('pause/resume cycles preserve position and never multiply loops', () => {
  const app = setup(); app.click('play');
  for (let i = 0; i < 5; i++) {
    app.key('p'); const head = app.head(); app.advance(1000);
    assert.deepEqual(app.head(), head); app.key('p');
  }
  app.advance(); assert.deepEqual(app.head(), [242, 302]);
});

test('keyboard and pad inputs obey the same turn queue and reversal rules', () => {
  const keyboard = setup(), touch = setup(); keyboard.click('play'); touch.click('play');
  for (const [key, direction] of [['a','left'],['w','up'],['s','down'],['a','left'],['s','down']]) {
    keyboard.key(key); touch.pad.find(button => button.dataset.direction === direction).handlers.click();
  }
  for (let i = 0; i < 2; i++) {
    keyboard.advance(); touch.advance(); assert.deepEqual(keyboard.head(), touch.head());
  }
  assert.deepEqual(keyboard.head(), [182,272]);
});

test('game over stops movement and scoring, replay starts a fresh run', () => {
  const app = setup(); app.click('play');
  for (let i = 0; i < 13; i++) app.advance(); // Wrap to x=0.
  app.key('w'); for (let i = 0; i < 10; i++) app.advance(); // Eat at (0,0).
  app.key('d'); app.advance(); // Eat at (1,0).
  assert.equal(app.get('score').textContent, '020');
  app.key('s'); app.advance(); app.key('a'); app.advance(); // Hit body at (0,1).
  assert.equal(app.get('status').textContent, 'Run complete');
  const head = app.head(); app.key('w'); app.pad[0].handlers.click();
  for (let i = 0; i < 3; i++) app.advance();
  assert.deepEqual(app.head(), head); assert.equal(app.get('score').textContent, '020');
  app.click('play'); assert.equal(app.get('score').textContent, '000');
  assert.deepEqual(app.head(), [212,302]); assert.equal(app.get('best').textContent, '020');
  app.advance(100); assert.deepEqual(app.head(), [212,302]);
});

test('held keyboard keys cannot queue turns or repeatedly toggle pause', () => {
  const app=setup(); app.click('play');
  app.key('w',undefined,true); app.advance(); assert.deepEqual(app.head(),[242,302]);
  app.key('p'); app.key('p',undefined,true);
  assert.equal(app.get('status').textContent,'Taking a breath');
  app.advance(1000); assert.deepEqual(app.head(),[242,302]);
});
