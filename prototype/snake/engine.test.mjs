import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createGame, tick, turn, spawnFood, togglePause } from './engine.mjs';
const running = overrides => ({ ...createGame(() => 0), status:'running', ...overrides });
test('food grows snake and awards exactly ten points', () => {
 const before = running({food:{x:8,y:10}}), after = tick(before, () => 0);
 assert.equal(after.score,10); assert.equal(after.snake.length,4);
 assert.ok(!after.snake.some(cell => cell.x === after.food.x && cell.y === after.food.y));
 assert.equal(before.score,0);
});
test('ordinary movement keeps length and score', () => {
 const after = tick(running()); assert.equal(after.score,0); assert.equal(after.snake.length,3);
 assert.deepEqual(after.snake[0],{x:8,y:10});
});
test('all four edges wrap without ending the run or scoring', () => {
 for (const [direction,head,expected] of [['left',{x:0,y:4},{x:19,y:4}],['right',{x:19,y:4},{x:0,y:4}],['up',{x:4,y:0},{x:4,y:19}],['down',{x:4,y:19},{x:4,y:0}]]) {
  const state = running({snake:[head],direction}); const after = tick(state);
  assert.equal(after.status,'running'); assert.deepEqual(after.snake,[expected]); assert.equal(after.score,0);
 }
});
test('wrapping into the body still ends the run', () => {
 const state=running({snake:[{x:19,y:4},{x:0,y:4},{x:1,y:4}],direction:'right'});
 const after=tick(state); assert.equal(after.status,'over'); assert.equal(after.score,0);
 assert.equal(tick(after),after);
});
test('food across an edge grows the snake and scores', () => {
 const after=tick(running({snake:[{x:19,y:4}],food:{x:0,y:4}}));
 assert.equal(after.status,'running'); assert.equal(after.score,10);
 assert.deepEqual(after.snake,[{x:0,y:4},{x:19,y:4}]);
});
test('body collision ends the run', () => {
 const state=running({snake:[{x:2,y:2},{x:2,y:3},{x:3,y:3},{x:3,y:2},{x:4,y:2}]});
 assert.equal(tick(state).status,'over');
});
test('moving into the vacating tail is legal', () => {
 const state=running({snake:[{x:2,y:2},{x:2,y:3},{x:3,y:3},{x:3,y:2}]});
 assert.equal(tick(state).status,'running');
});
test('rapid turns are bounded and cannot reverse direction', () => {
 let state=running(); assert.equal(turn(state,'left'),state);
 state=turn(state,'up'); assert.equal(turn(state,'down'),state);
 state=turn(state,'left'); assert.equal(turn(state,'down'),state);
 state=tick(state); assert.deepEqual(state.snake[0],{x:7,y:9});
 state=tick(state); assert.deepEqual(state.snake[0],{x:6,y:9});
});
test('ready and paused states never advance; resume preserves run', () => {
 let state=createGame(); assert.equal(tick(state),state);
 state=togglePause(state); state=togglePause(state); assert.equal(state.status,'paused');
 assert.equal(tick(state),state); assert.equal(turn(state,'up'),state);
 assert.equal(togglePause(state).status,'running');
});
test('filling the board wins, scores, and terminates', () => {
 const state=running({snake:[{x:0,y:0},{x:0,y:1},{x:1,y:1}],food:{x:1,y:0}});
 const after=tick(state,()=>0,2); assert.equal(after.status,'won'); assert.equal(after.score,10);
 assert.equal(after.food,null); assert.equal(tick(after),after);
});
test('food uses only empty cells, full boards return null', () => {
 assert.deepEqual(spawnFood([{x:0,y:0},{x:0,y:1},{x:1,y:1}],2,()=>.99),{x:1,y:0});
 assert.equal(spawnFood([{x:0,y:0}],1),null);
});
test('fresh game resets all transient state', () => {
 const old=tick(running({food:{x:8,y:10}})); const fresh=createGame();
 assert.equal(old.score,10); assert.equal(fresh.score,0); assert.equal(fresh.status,'ready');
 assert.deepEqual(fresh.queue,[]); assert.equal(fresh.snake.length,3);
});

test('every direction rejects its direct opposite and duplicate input', () => {
 for (const [direction, opposite] of [['up','down'],['down','up'],['left','right'],['right','left']]) {
  const state=running({direction});
  assert.equal(turn(state,opposite),state); assert.equal(turn(state,direction),state);
 }
});

test('every possible food choice is inside the board and outside the snake', () => {
 const state=createGame(); const emptyCount=20*20-state.snake.length;
 const choices=new Set();
 for (let i=0;i<emptyCount;i++) {
  const food=spawnFood(state.snake,20,()=>(i+.5)/emptyCount);
  assert.ok(food.x>=0 && food.x<20 && food.y>=0 && food.y<20);
  assert.ok(!state.snake.some(cell=>cell.x===food.x && cell.y===food.y));
  choices.add(`${food.x},${food.y}`);
 }
 assert.equal(choices.size,emptyCount);
});
