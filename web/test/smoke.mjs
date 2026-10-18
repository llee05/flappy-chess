import assert from 'node:assert/strict';
import { withBrowser, sleep } from './browser.mjs';

await withBrowser(async browser => {
  const { send, evaluate, key, press, screenshot, until } = await browser.page();
  const birdX = () => evaluate(`new Promise(resolve => requestAnimationFrame(() => {
    const canvas = document.getElementById('canvas');
    const context = new OffscreenCanvas(canvas.width, canvas.height).getContext('2d');
    context.drawImage(canvas, 0, 0);
    const pixels = context.getImageData(200, 0, 800, 800).data;
    let total = 0, count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i] === 255 && pixels[i + 1] === 229 && pixels[i + 2] === 161) {
        total += (i / 4) % 800 + 200;
        count++;
      }
    }
    resolve(count ? total / count : null);
  }))`);
  await send('Page.navigate', { url: browser.url });
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
  console.log(`Browser smoke checks passed. Screenshots: ${browser.reports}`);
});
