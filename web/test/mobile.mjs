import assert from 'node:assert/strict';
import { sleep } from './browser.mjs';
import { touchControls } from './touch.mjs';
import { flyToRank, touchBird } from './flight.mjs';

export async function checkMobile(browser) {
  const page = await browser.page();
  const { send, evaluate, press, until, screenshot } = page;
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
  const bird = (white = true) => touchBird(page, white);
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
  const landed = await bird();
  assert(landed && landed.y > start.y - 12,
    `Holding Flap must not repeat a flap: resting ${start.y} -> ${landed?.y}`);
  await touch.up(2);
  assert.equal(await evaluate('Howler.ctx.state'), 'running', 'Touch input must unlock audio');
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
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("document.getElementById('play')?.disabled === false && !document.body?.dataset.game"), 'returning to the mobile lobby');
  await evaluate("document.getElementById('match-minutes').value = '1'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('play').click()");
  await until(() => evaluate("document.body.dataset.game === 'ready' && document.getElementById('white-clock').classList.contains('active')"), 'starting a fresh mobile match');
  await touch.tap('[data-hold="E"]');
  await until(() => evaluate("document.getElementById('action-feedback').textContent.startsWith('Picked up')"), 'picking up with touch');
  await flyToRank(page, { birdY: async () => (await bird())?.y ?? null,
    flap: () => touch.tap('[data-hold="SPACE"]'), white: true,
    top: 500, bottom: 600, label: 'flying to e3 by touch', timeout: 15000 });
  await touch.tap('[data-hold="E"]');
  await until(async () => (await history()).last === 'e2e3', 'completing a legal move with touch');
  await screenshot('mobile-delivery');
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  await send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 61000, maxVirtualTimeTaskStarvationCount: 10000 });
  await until(async () => (await history()).ended === 'true', 'mobile timeout result', 60000);
  const frame = () => send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 100 });
  await screenshot('mobile-result');
  await touch.tap('#mobile-result [data-key="X"]');
  await frame();
  await until(() => evaluate("document.getElementById('touch-first').dataset.side === 'BLACK'"), 'touch pad following swapped player colors');
  assert.match(await evaluate("document.querySelector('#touch-first .flap').textContent"), /↓/);
  await press('Escape', 'Escape', 27);
  await until(async () => { await frame(); return evaluate("document.getElementById('solo')?.disabled === false && !document.body?.dataset.game"); }, 'leaving mobile play with Escape');
  await evaluate("document.getElementById('match-minutes').value = '0'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('solo-side').value = 'BLACK'; document.getElementById('solo-side').dispatchEvent(new Event('change')); document.getElementById('solo').click()");
  await until(() => evaluate("!document.getElementById('level-panel').hidden"), 'opening the mobile level picker');
  await screenshot('mobile-level-picker');
  assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true, 'Level picker must fit a phone');
  await touch.tap('#level-choices [data-level="SPARROW"]');
  await until(async () => { await frame(); return evaluate("document.body.dataset.mode === 'solo'"); }, 'starting mobile solo as Black');
  assert.equal(await evaluate("document.getElementById('touch-second').hidden"), true);
  assert.equal(await evaluate("document.getElementById('touch-first').dataset.side"), 'BLACK');
  assert.equal(await evaluate("document.getElementById('solo-summary').dataset.chess"), 'SPARROW', 'Touch picks the solo level');
  await screenshot('mobile-solo-black');
  await checkTouchLesson(browser);
  console.log('Mobile checks passed: responsive layouts, multi-touch, single flaps, cancellation, focus, delivery, swaps, Escape, the touch level picker, and solo ownership.');
}

async function checkTouchLesson(browser) {
  const page = await browser.page();
  const { send, evaluate, until, screenshot } = page;
  const touch = touchControls(page);
  const fly = (white, top, bottom, label) => flyToRank(page, { birdY: async () => (await touchBird(page, white))?.y ?? null,
    flap: () => touch.tap(white ? '[data-hold="SPACE"]' : '[data-hold="UP"]'), white, top, bottom, label });
  await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
  await send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('tutorial')?.disabled === false"), 'loading touch Flight School');
  await evaluate("document.getElementById('tutorial').click()");
  const stage = expected => until(() => evaluate(`document.getElementById('lesson-summary').dataset.stage === '${expected}'`), 'touch lesson: ' + expected);
  await stage('WHITE_FLY');
  assert.match(await evaluate("document.getElementById('lesson-summary').textContent"), /A \/ D steer.*SPACE/);
  assert.equal(await evaluate("document.getElementById('white-time').textContent"), 'Untimed');
  await touch.down('[data-hold="D"]');
  await sleep(1150);
  await touch.up();
  await sleep(800);
  await touch.tap('[data-hold="SPACE"]');
  await stage('WHITE_PICKUP');
  await touch.tap('[data-hold="E"]');
  await stage('WHITE_DELIVER');
  await fly(true, 400, 600, 'touch lesson: flying to E3 or E4');
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
  await fly(false, 200, 400, 'touch lesson: flying to D6 or D5');
  await touch.tap('[data-hold="ENTER"]');
  await stage('COMPLETE');
  await screenshot('mobile-flight-school-complete');
  await touch.tap('#mobile-complete [data-key="ENTER"]');
  await until(() => evaluate("document.body.dataset.mode === 'local'"), 'starting a match after the touch lesson');
}
