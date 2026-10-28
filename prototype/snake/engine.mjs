export const SIZE = 20;
export const DIRECTIONS = { up: [0, -1], down: [0, 1], left: [-1, 0], right: [1, 0] };
const same = (a, b) => a.x === b.x && a.y === b.y;
export function spawnFood(snake, size = SIZE, random = Math.random) {
  const free = [];
  for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
    if (!snake.some(cell => same(cell, { x, y }))) free.push({ x, y });
  }
  return free.length ? free[Math.min(free.length - 1, Math.floor(random() * free.length))] : null;
}
export function createGame(random = Math.random) {
  const snake = [{ x: 7, y: 10 }, { x: 6, y: 10 }, { x: 5, y: 10 }];
  return { snake, direction: 'right', queue: [], food: spawnFood(snake, SIZE, random), score: 0, status: 'ready' };
}
export function turn(state, direction) {
  if (state.status !== 'running' || !DIRECTIONS[direction] || state.queue.length >= 2) return state;
  const previous = state.queue.at(-1) || state.direction;
  const a = DIRECTIONS[previous], b = DIRECTIONS[direction];
  if (previous === direction || (a[0] + b[0] === 0 && a[1] + b[1] === 0)) return state;
  return { ...state, queue: [...state.queue, direction] };
}
export function tick(state, random = Math.random, size = SIZE) {
  if (state.status !== 'running') return state;
  const direction = state.queue[0] || state.direction;
  const [dx, dy] = DIRECTIONS[direction];
  const head = { x: (state.snake[0].x + dx + size) % size, y: (state.snake[0].y + dy + size) % size };
  const eating = state.food && same(head, state.food);
  const body = eating ? state.snake : state.snake.slice(0, -1);
  if (body.some(cell => same(cell, head))) {
    return { ...state, direction, queue: [], status: 'over' };
  }
  const snake = [head, ...state.snake];
  if (!eating) snake.pop();
  const food = eating ? spawnFood(snake, size, random) : state.food;
  return { ...state, snake, direction, queue: state.queue.slice(1), food,
    score: state.score + (eating ? 10 : 0), status: food ? 'running' : 'won' };
}
export function togglePause(state) {
  if (state.status === 'running') return { ...state, status: 'paused' };
  if (state.status === 'paused' || state.status === 'ready') return { ...state, status: 'running' };
  return state;
}
