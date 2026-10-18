import assert from 'node:assert/strict';
import { withBrowser, sleep } from './browser.mjs';
import { checkHistory } from './history.mjs';

await withBrowser(async browser => {
  const { send, evaluate, key, press, screenshot, until } = await browser.page();
  const birdX = (releaseFocus = false) => evaluate(`new Promise(resolve => requestAnimationFrame(() => {
    ${releaseFocus ? "window.dispatchEvent(new Event('blur'));" : ''}
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
  assert.deepEqual(await evaluate("window.FlappySettings.read()"), { minutes: 5, increment: 0 });
  await evaluate("document.getElementById('match-minutes').value = '10'; document.getElementById('match-increment').value = '5'; document.getElementById('match-increment').dispatchEvent(new Event('change'))");
  await evaluate("document.getElementById('play').click()");
  await until(() => evaluate("document.body.dataset.game === 'ready'"), 'creating the game');
  assert.equal(await evaluate("document.body.dataset.timeControl"), '10+5');
  await evaluate("(() => { window.effectPlays = 0; for (const sound of Howler._howls) sound.on('play', () => window.effectPlays++); })()");
  // Exercise chesslib legal-target generation and a carried pawn in the browser.
  await press('KeyE', 'e', 69);
  await until(() => evaluate("document.getElementById('action-feedback').textContent.startsWith('Picked up')"), 'showing pickup feedback');
  await until(() => evaluate('window.effectPlays > 0'), 'playing a sound effect');
  await press('Space', ' ', 32);
  await sleep(650);
  await press('Space', ' ', 32);
  await sleep(650);
  await press('KeyE', 'e', 69);
  await screenshot('gameplay');
  await until(() => evaluate("Howler._howls.length === 7 && Howler._howls.every(sound => sound.state() === 'loaded')"), 'decoding all sound assets');
  assert.equal(await evaluate('Howler.ctx.state'), 'running', 'Sound must unlock on keyboard input');
  await key('keyDown', 'KeyM', 'm', 77);
  await key('keyDown', 'KeyM', 'm', 77);
  await key('keyUp', 'KeyM', 'm', 77);
  await until(() => evaluate("document.body.dataset.sound === 'off'"), 'muting without repeated-key toggles');
  const playedBeforeMute = await evaluate('window.effectPlays');
  await press('Space', ' ', 32);
  await sleep(150);
  assert.equal(await evaluate('window.effectPlays'), playedBeforeMute, 'Muted actions must not play sounds');
  const clickSound = async () => {
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 70, y: 132, button: 'left', clickCount: 1 });
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 70, y: 132, button: 'left', clickCount: 1 });
  };
  await clickSound();
  await until(() => evaluate("document.body.dataset.sound === 'on'"), 'unmuting with the Sound control');
  await clickSound();
  await until(() => evaluate("document.body.dataset.sound === 'off'"), 'muting with the Sound control');
  assert.deepEqual(await evaluate("[document.getElementById('canvas').width, document.getElementById('canvas').height]"), [1200, 800]);
  await evaluate("document.addEventListener('keydown', e => { window.arrowCaught = e.defaultPrevented; }, { once: true })");
  await press('ArrowUp', 'ArrowUp', 38);
  assert.equal(await evaluate('window.arrowCaught'), true, 'Arrow keys must not scroll the page');
  const initialX = await birdX();
  assert.notEqual(initialX, null, 'Expected the gold bird on the rendered board');
  await key('keyDown', 'KeyD', 'd', 68);
  await sleep(300);
  const movingX = await birdX(true);
  assert(movingX > initialX + 25, 'A held movement key must move the bird');
  await sleep(1200);
  const releasedX = await birdX();
  await screenshot('focus-release');
  assert(releasedX > movingX + 35 && releasedX < movingX + 85,
    `Focus loss must release movement and let the bird coast to rest: moving=${movingX}, released=${releasedX}`);
  await press('KeyD', 'd', 68);
  for (const [name, width, height] of [['small', 600, 400], ['wide', 1600, 900]]) {
    await send('Emulation.setDeviceMetricsOverride', { width, height, deviceScaleFactor: 1, mobile: false });
    await until(async () => (await evaluate("[document.getElementById('canvas').width, document.getElementById('canvas').height]")).join() === [width, height].join(), 'resizing the canvas');
    await screenshot(name);
  }
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0, 'Expected no WebGL error');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("!document.body.dataset.game && document.getElementById('play')?.disabled === false"), 'returning to the welcome screen');
  await evaluate("document.getElementById('tutorial').click()");
  await until(() => evaluate("document.getElementById('lesson-summary').dataset.stage === 'WHITE_FLY'"), 'starting Flight School');
  assert.equal(await evaluate("document.body.dataset.mode"), 'tutorial');
  assert.equal(await evaluate("document.body.dataset.timeControl"), 'UNTIMED');
  assert.equal(await evaluate("document.body.dataset.sound"), 'off', 'Mute preference must survive returning to the lobby');
  await press('KeyM', 'm', 77);
  assert.match(await evaluate("document.getElementById('lesson-summary').textContent"), /SPACE/);
  await press('Space', ' ', 32);
  await screenshot('flight-school');
  await press('KeyT', 't', 84);
  await until(() => evaluate("document.getElementById('lesson-summary').dataset.stage === 'WHITE_FLY'"), 'restarting Flight School');
  const stage = expected => until(() => evaluate(`document.getElementById('lesson-summary').dataset.stage === '${expected}'`), `lesson: ${expected}`);
  // Fly using real keyboard input and the normal fixed-step physics. Align horizontally
  // at the boundary, then use two separate flaps to reach and deliver each pawn.
  await key('keyDown', 'KeyD', 'd', 68);
  await sleep(1150);
  await key('keyUp', 'KeyD', 'd', 68);
  await sleep(800);
  await press('Space', ' ', 32);
  await stage('WHITE_PICKUP');
  await press('KeyE', 'e', 69);
  await stage('WHITE_DELIVER');
  await press('Space', ' ', 32);
  await sleep(620);
  await press('KeyE', 'e', 69);
  await stage('BLACK_FLY');
  await screenshot('flight-school-black');
  await key('keyDown', 'ArrowLeft', 'ArrowLeft', 37);
  await sleep(1150);
  await key('keyUp', 'ArrowLeft', 'ArrowLeft', 37);
  await sleep(800);
  await press('ArrowUp', 'ArrowUp', 38);
  await stage('BLACK_PICKUP');
  await press('Enter', 'Enter', 13);
  await stage('BLACK_DELIVER');
  await press('ArrowUp', 'ArrowUp', 38);
  await sleep(620);
  await press('Enter', 'Enter', 13);
  await stage('COMPLETE');
  await screenshot('flight-school-complete');
  await press('Enter', 'Enter', 13);
  await until(() => evaluate("document.body.dataset.mode === 'local'"), 'starting a match after Flight School');
  assert.equal(await evaluate("document.body.dataset.timeControl"), '10+5');
  await press('KeyS', 's', 83);
  await until(() => evaluate("document.body.dataset.setup === '10+5'"), 'opening match setup');
  await screenshot('match-setup');
  await press('ArrowLeft', 'ArrowLeft', 37);
  await press('ArrowDown', 'ArrowDown', 40);
  await press('Enter', 'Enter', 13);
  await until(() => evaluate("document.body.dataset.timeControl === '5+2' && !document.body.dataset.setup"), 'starting a configured match');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("!document.body.dataset.game && document.getElementById('tutorial')?.disabled === false"), 'returning to the lobby after the lesson');
  await evaluate("document.getElementById('match-minutes').value = '0'; document.getElementById('match-minutes').dispatchEvent(new Event('change'))");
  assert.equal(await evaluate("document.getElementById('match-increment').disabled"), true);
  await evaluate("document.getElementById('play').click()");
  await until(() => evaluate("document.body.dataset.game === 'ready' && document.body.dataset.timeControl === 'UNTIMED'"), 'starting untimed practice');
  await screenshot('untimed-practice');
  await checkHistory(browser);
  console.log(`Browser smoke checks passed. Screenshots: ${browser.reports}`);
});
