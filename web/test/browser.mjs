import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { createServer } from 'node:http';
import { readFile, mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, extname, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

export const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const web = resolve(dirname(fileURLToPath(import.meta.url)), '..');

// Real Chromium pages, WebGL and WebRTC, controlled without a browser automation dependency.
export async function withBrowser(run, { signaling = false } = {}) {
  const output = resolve(web, 'build/dist');
  const reports = resolve(web, 'build/reports/browser');
  await readFile(resolve(output, 'game.js'));
  await mkdir(reports, { recursive: true });
  const failures = [], sockets = [], browsers = [], profiles = [];
  let browserLog = '', signal, signalPort, server;
  const until = async (check, message, timeout = 30000) => {
    const deadline = Date.now() + timeout;
    while (Date.now() < deadline) {
      if (failures.length) throw new Error(failures.join('\n'));
      if (await check()) return;
      await sleep(50);
    }
    throw new Error(`Timed out: ${message}\n${browserLog}`);
  };
  try {
    if (signaling) {
      // A local PeerServer removes public-service availability and NAT from CI.
      // Run in a child process so all of its WebSocket/expiry timers exit together.
      signal = spawn(process.execPath, ['--input-type=module', '-e',
        "import { PeerServer } from 'peer'; PeerServer({host:'127.0.0.1',port:0,path:'/peerjs'}, server => console.log(server.address().port));"
      ], { cwd: web, stdio: ['ignore', 'pipe', 'pipe'] });
      signal.on('error', error => failures.push(error.message));
      signal.stderr.on('data', data => { browserLog += data; });
      signal.stdout.on('data', data => { signalPort = Number(String(data).trim()); });
      await until(() => signalPort > 0, 'starting local signaling (run npm ci --prefix web first)');
    }
    const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.wasm': 'application/wasm', '.png': 'image/png' };
    server = createServer(async (request, response) => {
      try {
        const path = decodeURIComponent(new URL(request.url, 'http://localhost').pathname);
        if (path === '/favicon.ico') { response.writeHead(204).end(); return; }
        assert(path.startsWith('/flappy-chess/'));
        const relative = path.slice('/flappy-chess/'.length) || 'index.html';
        const file = resolve(output, relative);
        assert(file.startsWith(output + sep));
        const body = signaling && relative === 'peer-config.js'
          ? `window.FLAPPY_PEER_OPTIONS = ${JSON.stringify({ host: '127.0.0.1', port: signalPort,
            path: '/peerjs', secure: false, config: { iceServers: [] } })};`
          : await readFile(file);
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
    const page = async () => {
      // Separate browser processes represent separate devices and keep both RAF loops active.
      const profile = await mkdtemp(resolve(tmpdir(), 'flappy-chess-chromium-'));
      const browser = spawn(process.env.CHROME_BIN || 'chromium', [
        '--headless=new', '--no-sandbox', '--disable-dev-shm-usage', '--no-first-run',
        '--no-default-browser-check', '--enable-unsafe-swiftshader', '--use-angle=swiftshader',
        '--disable-background-timer-throttling', '--disable-renderer-backgrounding',
        '--disable-backgrounding-occluded-windows',
        '--remote-debugging-port=0', `--user-data-dir=${profile}`, 'about:blank'
      ], { stdio: ['ignore', 'ignore', 'pipe'] });
      browsers.push(browser);
      profiles.push(profile);
      browser.stderr.on('data', chunk => { browserLog = (browserLog + chunk).slice(-6000); });
      browser.on('error', error => failures.push(error.message));
      let port;
      await until(async () => {
        try { port = (await readFile(resolve(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]; return true; }
        catch { return false; }
      }, 'starting Chromium');
      const target = await (await fetch(`http://127.0.0.1:${port}/json/new?about:blank`, { method: 'PUT' })).json();
      const socket = new WebSocket(target.webSocketDebuggerUrl);
      sockets.push(socket);
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
          failures.push(JSON.stringify(details));
        } else if (result.method === 'Runtime.consoleAPICalled') {
          const message = result.params.args.map(arg => arg.value || arg.description).join(' ');
          browserLog = (browserLog + '\n' + message).slice(-6000);
          if (result.params.type === 'error' || message.includes('Fatal Error')) failures.push(message);
        } else if (result.method === 'Network.responseReceived') {
          browserLog = (browserLog + '\nHTTP ' + result.params.response.status + ' ' + result.params.response.url).slice(-6000);
        }
      };
      const send = (method, params = {}) => new Promise((resolve, reject) => {
        const id = ++nextId;
        const timer = setTimeout(() => { pending.delete(id); reject(new Error(`DevTools timeout: ${method}`)); }, 15000);
        pending.set(id, { resolve, reject, timer });
        socket.send(JSON.stringify({ id, method, params }));
      });
      const evaluate = async expression => {
        const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
        if (result.exceptionDetails) throw new Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
        return result.result.value;
      };
      const key = (type, code, key, windowsVirtualKeyCode) => send('Input.dispatchKeyEvent', { type, code, key, windowsVirtualKeyCode });
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
      await send('Page.enable');
      await send('Runtime.enable');
      await send('Network.enable');
      // Each page represents a separate player's foreground browser.
      await send('Emulation.setFocusEmulationEnabled', { enabled: true });
      await send('Emulation.setDeviceMetricsOverride', { width: 1200, height: 800, deviceScaleFactor: 1, mobile: false });
      return { send, evaluate, key, press, screenshot, until };
    };
    await run({ page, reports, url: `http://127.0.0.1:${server.address().port}/flappy-chess/` });
    assert.deepEqual(failures, []);
  } finally {
    for (const socket of sockets) socket.close();
    for (const browser of browsers) browser.kill('SIGTERM');
    signal?.kill('SIGTERM');
    if (server) await new Promise(resolve => server.close(resolve));
    await sleep(300);
    for (const profile of profiles) await rm(profile, { recursive: true, force: true, maxRetries: 3 });
  }
}
