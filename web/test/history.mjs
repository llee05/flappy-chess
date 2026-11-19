import assert from 'node:assert/strict';
import { desktopBirdX, desktopBirdY } from './flight.mjs';

export async function checkHistory(browser) {
  const page = await browser.page();
  const { send, evaluate, key, press, until, screenshot } = page;
  const history = () => evaluate("({...document.getElementById('history-summary').dataset})");
  const birdY = white => desktopBirdY(page, white);
  const reachFile = async (white, file) => {
    const left = white ? ['KeyA', 'a', 65] : ['ArrowLeft', 'ArrowLeft', 37];
    const right = white ? ['KeyD', 'd', 68] : ['ArrowRight', 'ArrowRight', 39];
    let held = null, previous = null, settled = 0;
    const hold = async direction => {
      if (held === direction) return;
      if (held) await key('keyUp', ...held);
      held = direction;
      if (held) await key('keyDown', ...held);
    };
    try {
      await until(async () => {
        const x = await desktopBirdX(page, white);
        if (x === null) return false;
        const center = file * 100 + 50;
        if (x > center + 40) {
          settled = 0;
          await hold(left);
        } else if (x < center - 40) {
          settled = 0;
          await hold(right);
        } else {
          await hold(null);
          // Wing animation shifts the sampled color centroid by a few pixels.
          settled = previous !== null && Math.abs(x - previous) < 6 ? settled + 1 : 0;
        }
        previous = x;
        return settled >= 3;
      }, `flying ${white ? 'White' : 'Black'} to file ${String.fromCharCode(97 + file)}`, 15000);
    } catch (error) {
      throw new Error(`${error.message}\nLast observed bird X: ${previous}`);
    } finally {
      await hold(null);
    }
  };
  const reachRank = async (white, top, bottom) => {
    let lastFlap = 0;
    await until(async () => {
      const y = await birdY(white);
      if (y === null) return false;
      if (y >= top + 12 && y <= bottom - 12) return true;
      if ((white ? y > bottom - 12 : y < top + 12) && Date.now() - lastFlap > 550) {
        await press(white ? 'Space' : 'ArrowUp', white ? ' ' : 'ArrowUp', white ? 32 : 38);
        lastFlap = Date.now();
      }
      return false;
    }, `flying ${white ? 'White' : 'Black'} to rank ${8 - top / 100}`, 15000);
  };
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('play')?.disabled === false"), 'loading history test');
  await evaluate("document.getElementById('match-minutes').value = '1'; document.getElementById('match-increment').value = '0'; document.getElementById('match-increment').dispatchEvent(new Event('change')); document.getElementById('play').click()");
  await until(() => evaluate("document.body.dataset.game === 'ready'"), 'starting a one-minute game');
  // A real three-ply capture: e4, d5, exd5. Birds travel using normal controls.
  await press('KeyE', 'e', 69);
  await until(() => evaluate("document.getElementById('action-feedback').textContent.startsWith('Picked up')"), 'picking up the test pawn');
  await reachRank(true, 400, 500);
  await press('KeyE', 'e', 69);
  await screenshot('history-after-e4');
  await until(async () => (await history()).last === 'e2e4', 'recording e4');
  await reachFile(false, 3);
  await reachRank(false, 100, 200);
  await press('Enter', 'Enter', 13);
  await reachRank(false, 300, 400);
  await press('Enter', 'Enter', 13);
  await until(async () => (await history()).last === 'd7d5', 'recording d5');
  await reachRank(true, 400, 500);
  await press('KeyE', 'e', 69);
  await press('Space', ' ', 32);
  await reachFile(true, 3);
  await reachRank(true, 300, 400);
  await press('KeyE', 'e', 69);
  await until(async () => (await history()).last === 'e4d5', 'recording the capture');
  assert.equal((await history()).whiteCaptured, '1');
  await screenshot('history-capture');
  // Chromium advances the actual monotonic browser clock; session rules still declare timeout.
  await send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 61000, maxVirtualTimeTaskStarvationCount: 10000 });
  await until(async () => (await history()).ended === 'true', 'ending the game on time', 60000);
  const frame = () => send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 100 });
  await screenshot('history-result');
  assert.equal((await history()).ended, 'true');
  await press('KeyX', 'x', 88);
  await frame();
  await until(() => evaluate("document.getElementById('series-summary').dataset.round === '2'"), 'starting a color-swap rematch');
  assert.equal(await evaluate("document.getElementById('series-summary').dataset.firstWhite"), 'false');
  assert.equal(await evaluate("document.getElementById('series-summary').dataset.firstPoints"), '1');
  assert.equal((await history()).moves, '0');
  assert.equal((await history()).ended, 'false');
  await screenshot('series-swapped');
  console.log('Browser history checks passed: capture, timeout, and color-swap rematch.');
}
