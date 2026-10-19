import assert from 'node:assert/strict';
import { sleep } from './browser.mjs';
import { touchControls } from './touch.mjs';

export async function checkMobile(browser) {
  const page = await browser.page();
  const { send, evaluate, until, screenshot } = page;
  const touch = touchControls(page);
  const size = (width, height) => send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 3, mobile: true });
  await size(390, 844);
  await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('play')?.disabled === false"), 'loading mobile lobby');
  assert.equal(await evaluate("document.body.classList.contains('mobile')"), true);
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'Lobby must fit a phone');
  await screenshot('mobile-lobby');
  await evaluate("document.getElementById('match-minutes').value = '1'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('play').click()");
  // The active clock may already have ticked by the first rendered frame on a
  // slower browser. Readiness comes from the game and HUD, not an exact second.
  await until(() => evaluate("document.body.dataset.game === 'ready' && document.getElementById('white-clock').classList.contains('active')"), 'starting mobile local play');
  assert.equal(await evaluate('document.body.dataset.timeControl'), '1+0');
  assert.match(await evaluate("document.getElementById('white-time').textContent"), /^(1:00|0:[0-5]\d)$/);
  assert.equal(await evaluate("document.getElementById('black-time').textContent"), '1:00', 'The waiting player must retain their full minute');
  const history = () => evaluate("({...document.getElementById('history-summary').dataset})");
  const bird = (white = true) => evaluate(`new Promise(resolve => requestAnimationFrame(() => {
    const canvas = document.getElementById('canvas');
    const edge = Math.min(canvas.width, canvas.height);
    const context = new OffscreenCanvas(800, 800).getContext('2d');
    context.drawImage(canvas, (canvas.width - edge) / 2, (canvas.height - edge) / 2, edge, edge, 0, 0, 800, 800);
    const pixels = context.getImageData(0, 0, 800, 800).data;
    const rgb = ${white ? '[255,229,161]' : '[198,240,231]'};
    let x = 0, y = 0, count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i] === rgb[0] && pixels[i + 1] === rgb[1] && pixels[i + 2] === rgb[2]) {
        x += (i / 4) % 800; y += Math.floor(i / 4 / 800); count++;
      }
    }
    resolve(count ? {x: x / count, y: y / count} : null);
  }))`);
  const layoutFits = async () => {
    const rects = await evaluate(`['canvas', 'mobile-hud', 'touch-controls'].map(id => {
      const rect = document.getElementById(id).getBoundingClientRect();
      return {x: rect.x, y: rect.y, right: rect.right, bottom: rect.bottom, width: rect.width, height: rect.height};
    })`);
    const viewport = await evaluate('({width: innerWidth, height: innerHeight})');
    for (const rect of rects) {
      assert(rect.width > 0 && rect.height > 0 && rect.x >= 0 && rect.y >= 0
        && rect.right <= viewport.width + 1 && rect.bottom <= viewport.height + 1, JSON.stringify({rect, viewport}));
    }
    assert(rects[0].y >= rects[1].bottom - 1 || rects[0].right <= rects[1].x + 1, 'HUD must sit outside the board');
    assert(rects[0].bottom <= rects[2].y + 1 || rects[0].right <= rects[2].x + 1, 'Touch pads must sit outside the board');
    assert.equal(await evaluate("[...document.querySelectorAll('.pad-buttons button')].every(b => b.getBoundingClientRect().height >= 44)"), true);
  };
  await layoutFits();
  await sleep(800);
  const start = await bird();
  assert(start, 'The board crop must show the white bird');
  await touch.down('[data-hold="D"]');
  await touch.down('[data-hold="SPACE"]', 2);
  await sleep(350);
  const flying = await bird();
  assert(flying.x > start.x + 25 && flying.y < start.y - 40, 'Two fingers must steer and flap simultaneously');
  await touch.up(1);
  await sleep(1300);
  assert((await bird()).y > 730, 'Holding Flap must not repeat a flap');
  await touch.up(2);
  assert.equal(await evaluate('Howler.ctx.state'), 'running', 'Touch input must unlock audio');
  await touch.tap('#mobile-sound');
  await until(() => evaluate("document.body.dataset.sound === 'off'"), 'muting through the mobile toolbar');
  await touch.down('[data-hold="A"]');
  await sleep(250);
  await touch.cancel();
  await sleep(800);
  const stopped = await bird();
  await sleep(450);
  const afterCancel = await bird();
  assert(Math.abs(afterCancel.x - stopped.x) < 12, `Cancelled touch must coast to rest: ${stopped.x} -> ${afterCancel.x}`);
  assert.equal(await evaluate("document.querySelectorAll('.held').length"), 0);
  await touch.down('[data-hold="D"]');
  await sleep(150);
  await evaluate("window.dispatchEvent(new Event('blur'))");
  await touch.up();
  await sleep(800);
  const blurred = await bird();
  await sleep(350);
  const afterBlur = await bird();
  assert(Math.abs(afterBlur.x - blurred.x) < 12, `Focus loss must release touch motion: ${blurred.x} -> ${afterBlur.x}`);
  await screenshot('mobile-local');

  for (const [width, height, name] of [[320, 568, 'small-phone'], [844, 390, 'landscape'], [768, 1024, 'tablet']]) {
    await size(width, height);
    await until(() => evaluate(`Math.abs(document.getElementById('canvas').width - document.getElementById('canvas').getBoundingClientRect().width) < 1`), 'resizing mobile canvas');
    await layoutFits();
    assert.equal(await evaluate("document.querySelectorAll('.held').length"), 0);
    await screenshot('mobile-' + name);
  }
  await size(390, 844);
  await touch.tap('#mobile-menu');
  await touch.tap('[data-key="S"].local-action');
  await until(() => evaluate("!document.getElementById('mobile-setup').hidden"), 'opening mobile match setup');
  await screenshot('mobile-setup');
  await touch.tap('#mobile-setup [data-key="ENTER"]');
  await until(() => evaluate("!document.body.dataset.setup"), 'starting a configured match by touch');
  await touch.tap('[data-hold="E"]');
  await until(() => evaluate("document.getElementById('action-feedback').textContent.startsWith('Picked up')"), 'picking up with touch');
  let lastFlap = 0;
  await until(async () => {
    const position = await bird();
    if (position.y >= 512 && position.y <= 588) return true;
    if (position.y > 588 && Date.now() - lastFlap > 550) {
      await touch.tap('[data-hold="SPACE"]');
      lastFlap = Date.now();
    }
    return false;
  }, 'flying to e3 by touch', 15000);
  await touch.tap('[data-hold="E"]');
  await until(async () => (await history()).last === 'e2e3', 'completing a legal move with touch');
  assert.match(await evaluate("document.getElementById('mobile-moves').textContent"), /1\. e3/);
  await screenshot('mobile-delivery');
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  await send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 61000, maxVirtualTimeTaskStarvationCount: 10000 });
  await until(async () => (await history()).ended === 'true', 'mobile timeout result', 60000);
  const frame = () => send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 100 });
  await screenshot('mobile-result');
  await touch.tap('#mobile-result [data-key="V"]');
  await frame();
  await until(async () => (await history()).review === 'true', 'opening review by touch');
  await touch.tap('#mobile-review [data-key="HOME"]');
  await frame();
  await until(async () => (await history()).ply === '0', 'reviewing the starting position by touch');
  await screenshot('mobile-review');
  await touch.tap('#mobile-review [data-key="V"]');
  await until(async () => { await frame(); return (await history()).review === 'false'; }, 'returning to the result by touch');
  await touch.tap('#mobile-result [data-key="X"]');
  await frame();
  await until(() => evaluate("document.getElementById('touch-first').dataset.side === 'BLACK'"), 'touch pad following swapped player colors');
  assert.match(await evaluate("document.querySelector('#touch-first .flap').textContent"), /↓/);
  await touch.tap('#mobile-leave');
  await until(async () => { await frame(); return evaluate("document.getElementById('solo')?.disabled === false && !document.body.dataset.game"); }, 'leaving through mobile Lobby');
  await evaluate("document.getElementById('match-minutes').value = '0'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('solo-side').value = 'BLACK'; document.getElementById('solo').click()");
  await until(async () => { await frame(); return evaluate("document.body.dataset.mode === 'solo'"); }, 'starting mobile solo as Black');
  assert.equal(await evaluate("document.getElementById('touch-second').hidden"), true);
  assert.equal(await evaluate("document.getElementById('touch-first').dataset.side"), 'BLACK');
  await screenshot('mobile-solo-black');
  await checkTouchLesson(browser);
  console.log('Mobile checks passed: responsive layouts, multi-touch, single flaps, cancellation, focus, delivery, setup, review, swaps, and solo ownership.');
}

async function checkTouchLesson(browser) {
  const page = await browser.page();
  const { send, evaluate, until, screenshot } = page;
  const touch = touchControls(page);
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('tutorial')?.disabled === false"), 'loading touch Flight School');
  await evaluate("document.getElementById('tutorial').click()");
  const stage = expected => until(() => evaluate(`document.getElementById('lesson-summary').dataset.stage === '${expected}'`), 'touch lesson: ' + expected);
  await stage('WHITE_FLY');
  assert.match(await evaluate("document.getElementById('mobile-feedback').textContent"), /Hold ◀ \/ ▶/);
  assert.equal(await evaluate("document.getElementById('white-time').textContent"), 'Untimed');
  await touch.down('[data-hold="D"]');
  await sleep(1150);
  await touch.up();
  await sleep(800);
  await touch.tap('[data-hold="SPACE"]');
  await stage('WHITE_PICKUP');
  await touch.tap('[data-hold="E"]');
  await stage('WHITE_DELIVER');
  await touch.tap('[data-hold="SPACE"]');
  await sleep(620);
  await touch.tap('[data-hold="E"]');
  await stage('BLACK_FLY');
  await screenshot('mobile-flight-school');
  await touch.down('[data-hold="LEFT"]');
  await sleep(1150);
  await touch.up();
  await sleep(800);
  await touch.tap('[data-hold="UP"]');
  await stage('BLACK_PICKUP');
  await touch.tap('[data-hold="ENTER"]');
  await stage('BLACK_DELIVER');
  await touch.tap('[data-hold="UP"]');
  await sleep(620);
  await touch.tap('[data-hold="ENTER"]');
  await stage('COMPLETE');
  await screenshot('mobile-flight-school-complete');
  await touch.tap('#mobile-complete [data-key="ENTER"]');
  await until(() => evaluate("document.body.dataset.mode === 'local'"), 'starting a match after the touch lesson');
}
