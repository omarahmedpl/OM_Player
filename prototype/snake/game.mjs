import { SIZE, createGame, tick, turn, togglePause } from './engine.mjs';
const $ = id => document.getElementById(id);
const canvas = $('board'), ctx = canvas.getContext('2d');
let state = createGame(), lastTime = 0, best = 0, storageAvailable = true, swipe = null;
const storageKey = 'om-snake-high-score';
try {
  const stored = Number(localStorage.getItem(storageKey));
  if (Number.isSafeInteger(stored) && stored >= 0) best = stored;
} catch { storageAvailable = false; }
function saveBest() {
  if (state.score <= best) return;
  best = state.score;
  try { localStorage.setItem(storageKey, String(best)); } catch { storageAvailable = false; }
}
const format = n => String(n).padStart(3, '0');
function draw() {
  const unit = canvas.width / SIZE;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.strokeStyle = '#a7f3d00a'; ctx.lineWidth = 1;
  for (let i = 1; i < SIZE; i++) {
    ctx.beginPath(); ctx.moveTo(i * unit, 0); ctx.lineTo(i * unit, canvas.height); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(0, i * unit); ctx.lineTo(canvas.width, i * unit); ctx.stroke();
  }
  if (state.food) {
    ctx.shadowColor = '#f43f5e'; ctx.shadowBlur = 18; ctx.fillStyle = '#fb7185';
    ctx.beginPath(); ctx.arc((state.food.x + .5) * unit, (state.food.y + .5) * unit, unit * .23, 0, Math.PI * 2); ctx.fill();
  }
  ctx.shadowBlur = 0;
  state.snake.forEach((cell, i) => {
    ctx.fillStyle = i === 0 ? '#a7f3d0' : '#34d399';
    ctx.beginPath(); ctx.roundRect(cell.x * unit + 2, cell.y * unit + 2, unit - 4, unit - 4, i === 0 ? 7 : 4); ctx.fill();
  });
  const head = state.snake[0], [dx, dy] = { right:[1,0],left:[-1,0],up:[0,-1],down:[0,1] }[state.direction];
  ctx.fillStyle = '#06251b';
  for (const side of [-1, 1]) {
    ctx.beginPath(); ctx.arc((head.x + .5 + dx * .19 + dy * side * .17) * unit,
      (head.y + .5 + dy * .19 + dx * side * .17) * unit, 2.2, 0, Math.PI * 2); ctx.fill();
  }
}
function render() {
  saveBest(); draw();
  $('score').textContent = format(state.score); $('best').textContent = format(best);
  $('storage-note').textContent = storageAvailable ? 'Saved on this device' : 'Best kept for this visit only';
  const messages = { ready:'Ready to play', running:'In the flow', paused:'Taking a breath', over:'Run complete', won:'Board complete' };
  $('status').textContent = messages[state.status];
  $('status-light').style.background = state.status === 'running' ? 'var(--neon)' : 'var(--text-3)';
  $('overlay').hidden = state.status === 'running';
  $('pause').disabled = !['running','paused'].includes(state.status);
  $('pause').firstChild.textContent = state.status === 'paused' ? 'Resume ' : 'Pause ';
  const content = {
    ready:['Find your flow.', 'Eat. Grow. Keep going.', 'Start game'],
    paused:['A little breather.', 'Your run is right where you left it.', 'Resume game'],
    over:['One more bite?', `You scored ${state.score}. A fresh start is waiting.`, 'Play again'],
    won:['Room for no more.', `All ${SIZE * SIZE} cells. You cleared the board!`, 'Play again']
  }[state.status];
  if (content) {
    $('overlay-title').textContent = content[0]; $('overlay-copy').textContent = content[1];
    $('play').firstChild.textContent = content[2] + ' ';
    $('announcement').textContent = `${content[0]} ${content[1]}`;
  }
}
function play() {
  if (state.status === 'running') return;
  swipe = null;
  if (['over','won'].includes(state.status)) state = createGame();
  state = togglePause(state); lastTime = performance.now(); render();
}
function pause() { swipe = null; state = togglePause(state); lastTime = performance.now(); render(); }
function restart() { swipe = null; state = { ...createGame(), status:'running' }; lastTime = performance.now(); render(); }
$('play').addEventListener('click', play);
$('pause').addEventListener('click', pause);
$('restart').addEventListener('click', restart);
const keys = { ArrowUp:'up',w:'up',ArrowDown:'down',s:'down',ArrowLeft:'left',a:'left',ArrowRight:'right',d:'right' };
document.addEventListener('keydown', event => {
  if (event.altKey || event.ctrlKey || event.metaKey || event.target.matches('input,textarea,select,[contenteditable]')) return;
  const direction = keys[event.key] || keys[event.key.toLowerCase()];
  if (direction) { event.preventDefault(); if (!event.repeat) state = turn(state, direction); }
  if (event.key === ' ' || event.key.toLowerCase() === 'p') {
    // Space always controls the run, including when Restart or the pad has focus.
    event.preventDefault(); if (!event.repeat) state.status === 'running' ? pause() : play();
  }
});
document.querySelectorAll('[data-direction]').forEach(button => button.addEventListener('click', () => {
  state = turn(state, button.dataset.direction);
}));
$('arena').addEventListener('pointerdown', event => {
  if (state.status !== 'running' || swipe || event.target.closest('button')) return;
  swipe = { x:event.clientX, y:event.clientY, id:event.pointerId };
  $('arena').setPointerCapture(event.pointerId);
});
$('arena').addEventListener('pointerup', event => {
  if (!swipe || swipe.id !== event.pointerId) return;
  const dx = event.clientX - swipe.x, dy = event.clientY - swipe.y; swipe = null;
  if (Math.max(Math.abs(dx), Math.abs(dy)) < 16) return;
  state = turn(state, Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 'right':'left') : (dy > 0 ? 'down':'up'));
});
$('arena').addEventListener('pointercancel', event => {
  if (swipe?.id === event.pointerId) swipe = null;
});
function autoPause() { if (state.status === 'running') pause(); }
document.addEventListener('visibilitychange', () => { if (document.hidden) autoPause(); });
window.addEventListener('blur', autoPause);
function frame(time) {
  if (state.status === 'running' && time - lastTime >= Math.max(75, 145 - state.score / 5)) {
    lastTime = time; state = tick(state); render();
  }
  requestAnimationFrame(frame);
}
render(); requestAnimationFrame(frame);
