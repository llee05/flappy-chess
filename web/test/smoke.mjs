import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { readFile, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

// No npm dependencies: drive an installed Chromium through its DevTools protocol.
const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const output = resolve(web, 'build/dist');
const reports = resolve(web, 'build/reports/browser');
await readFile(resolve(output, 'game.js'));
await mkdir(reports, { recursive: true });
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.png': 'image/png' };
const failures = [];
const server = createServer(async (request, response) => {
  try {
    const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
    if (path === '/favicon.ico') { response.writeHead(204).end(); return; }
    // Exercise project-site paths, not just a domain-root deployment.
    assert(path.startsWith('/flappy-chess/'));
    const relative = path.slice('/flappy-chess/'.length) || 'index.html';
    const file = resolve(output, relative);
    assert(file.startsWith(output + sep));
    const body = await readFile(file);
    response.writeHead(200, { 'Content-Type': types[extname(file)] || 'application/octet-stream' }).end(body);
  } catch (error) {
    failures.push(`HTTP ${request.url}: ${error.message}`);
    response.writeHead(404).end();
  }
});
await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(0, '127.0.0.1', resolve);
});
const profile = await mkdtemp(resolve(tmpdir(), 'flappy-chess-chromium-'));
const browser = spawn(process.env.CHROME_BIN || 'chromium', [
  '--headless=new', '--no-sandbox', '--disable-dev-shm-usage', '--no-first-run',
  '--no-default-browser-check', '--enable-unsafe-swiftshader', '--use-angle=swiftshader',
  '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'
], { stdio: ['ignore', 'ignore', 'pipe'] });
let browserLog = '';
browser.stderr.on('data', chunk => { browserLog = (browserLog + chunk).slice(-6000); });
browser.on('error', error => failures.push(error.message));
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(check, message, timeout = 30000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (await check()) return;
    if (failures.length) throw new Error(failures.join('\n'));
    await sleep(50);
  }
  throw new Error(`Timed out: ${message}\n${browserLog}`);
}
let socket;
try {
  let port;
  await until(async () => {
    try { port = (await readFile(resolve(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; return true; }
    catch { return false; }
  }, 'starting Chromium');
  const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.onopen = resolve; socket.onerror = reject; });
  const pending = new Map();
  let nextId = 0;
  socket.onmessage = event => {
    const result = JSON.parse(event.data);
    if (result.id) {
      const task = pending.get(result.id);
      if (!task) return;
      clearTimeout(task.timer);
      pending.delete(result.id);
      result.error ? task.reject(new Error(JSON.stringify(result.error))) : task.resolve(result.result);
    } else if (result.method === 'Runtime.exceptionThrown') {
      const details = result.params.exceptionDetails;
      failures.push(details.exception?.description || details.text);
    } else if (result.method === 'Runtime.consoleAPICalled' && result.params.type === 'error') {
      failures.push(result.params.args.map(arg => arg.value || arg.description).join(' '));
    }
  };
  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++nextId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error(`DevTools timeout: ${method}`)); }, 15000);
    pending.set(id, { resolve, reject, timer });
    socket.send(JSON.stringify({ id, method, params }));
  });
  const evaluate = async expression => {
    const response = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (response.exceptionDetails) throw new Error(response.exceptionDetails.text);
    return response.result.value;
  };
  const key = async (type, code, key, windowsVirtualKeyCode) => {
    await send('Input.dispatchKeyEvent', { type, code, key, windowsVirtualKeyCode });
  };
  const press = async (code, value, number) => {
    await key('keyDown', code, value, number);
    await key('keyUp', code, value, number);
  };
  const screenshot = async name => {
    const result = await send('Page.captureScreenshot', { format: 'png' });
    const png = Buffer.from(result.data, 'base64');
    assert(png.length > 1000, 'Expected a rendered screenshot');
    await writeFile(resolve(reports, `${name}.png`), png);
  };
  const birdX = () => evaluate(`new Promise(resolve => requestAnimationFrame(() => {
    const canvas = document.getElementById('canvas');
    const image = new OffscreenCanvas(canvas.width, canvas.height);
    const context = image.getContext('2d');
    context.drawImage(canvas, 0, 0);
    const pixels = context.getImageData(200, 0, 800, 800).data;
    let total = 0, count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      // The gold bird's highlight is distinct from the ivory chess pieces.
      if (pixels[i] === 255 && pixels[i + 1] === 229 && pixels[i + 2] === 161) {
        total += (i / 4) % 800 + 200;
        count++;
      }
    }
    resolve(count ? total / count : null);
  }))`);
  await send('Page.enable');
  await send('Runtime.enable');
  await send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 800, deviceScaleFactor: 1, mobile: false });
  await send('Page.navigate', { url: `http://127.0.0.1:${server.address().port}/flappy-chess/` });
  await until(() => evaluate("document.getElementById('play')?.disabled === false"), 'loading the compiled game');
  await screenshot('welcome');
  await evaluate("document.getElementById('play').click()");
  await until(() => evaluate("document.body.dataset.game === 'ready'"), 'creating the game');
  // Exercise chesslib legal-target generation and a carried pawn in the browser.
  await press('KeyE', 'e', 69);
  await press('Space', ' ', 32);
  await sleep(650);
  await press('Space', ' ', 32);
  await sleep(650);
  await press('KeyE', 'e', 69);
  await screenshot('gameplay');
  assert.deepEqual(await evaluate("[document.getElementById('canvas').width, document.getElementById('canvas').height]"), [1200, 800]);
  await evaluate("document.addEventListener('keydown', e => { window.arrowCaught = e.defaultPrevented; }, { once: true })");
  await press('ArrowUp', 'ArrowUp', 38);
  assert.equal(await evaluate('window.arrowCaught'), true, 'Arrow keys must not scroll the page');
  const initialX = await birdX();
  assert.notEqual(initialX, null, 'Expected the gold bird on the rendered board');
  await key('keyDown', 'KeyD', 'd', 68);
  await sleep(300);
  const movingX = await birdX();
  assert(movingX > initialX + 25, 'A held movement key must move the bird');
  await evaluate("window.dispatchEvent(new Event('blur'))");
  await sleep(1200);
  const releasedX = await birdX();
  assert(releasedX > movingX + 35 && releasedX < movingX + 85, 'Focus loss must release movement and let the bird coast to rest');
  await press('KeyD', 'd', 68);
  for (const [name, width, height] of [['small', 600, 400], ['wide', 1600, 900]]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
    await until(async () => (await evaluate("[document.getElementById('canvas').width, document.getElementById('canvas').height]")).join() === [width, height].join(), 'resizing the canvas');
    await screenshot(name);
  }
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0, 'Expected no WebGL error');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("!document.body.dataset.game && document.getElementById('play')?.disabled === false"), 'returning to the welcome screen');
  assert.deepEqual(failures, []);
  console.log(`Browser smoke checks passed. Screenshots: ${reports}`);
} finally {
  socket?.close();
  browser.kill('SIGTERM');
  await new Promise(resolve => server.close(resolve));
  await sleep(300);
  await rm(profile, { recursive: true, force: true, maxRetries: 3 });
}
