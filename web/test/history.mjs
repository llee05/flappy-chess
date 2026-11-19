import assert from 'node:assert/strict';
import { sleep } from './browser.mjs';
import { desktopBirdY } from './flight.mjs';

export async function checkHistory(browser) {
  const page = await browser.page();
  const { send, evaluate, key: dispatchKey, until: pollUntil, screenshot } = page;
  const trace = [], held = new Set();
  const record = entry => {
    trace.push({ at: Date.now(), ...entry, held: [...held] });
    if (trace.length > 200) trace.shift();
  };
  const key = async (type, code, value, number) => {
    if (type === 'keyDown') held.add(code); else if (type === 'keyUp') held.delete(code);
    record({ type, code });
    return dispatchKey(type, code, value, number);
  };
  const press = async (code, value, number) => {
    await key('keyDown', code, value, number);
    await key('keyUp', code, value, number);
  };
  const until = async (check, message, timeout) => {
    try {
      return await pollUntil(check, message, timeout);
    } catch (error) {
      if (error.message.startsWith('Timed out:')) {
        let state;
        try {
          state = await evaluate(`(() => {
            const data = id => ({...document.getElementById(id).dataset});
            const feedback = document.getElementById('action-feedback');
            return {
              history: data('history-summary'),
              solo: data('solo-summary'),
              feedback: {text: feedback.textContent, data: {...feedback.dataset}}
            };
          })()`);
        } catch (diagnosticError) {
          state = { captureError: diagnosticError.message };
        }
        console.error('History timeout diagnostics:', JSON.stringify({ message, state, held: [...held], trace }, null, 2));
      }
      throw error;
    }
  };
  const history = () => evaluate("({...document.getElementById('history-summary').dataset})");
  const birdY = async white => {
    const y = await desktopBirdY(page, white);
    record({ type: 'sample', axis: 'y', side: white ? 'white' : 'black', value: y });
    return y;
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
  await key('keyDown', 'ArrowLeft', 'ArrowLeft', 37);
  await sleep(250);
  await key('keyUp', 'ArrowLeft', 'ArrowLeft', 37);
  await sleep(650);
  await reachRank(false, 100, 200);
  await press('Enter', 'Enter', 13);
  await reachRank(false, 300, 400);
  await press('Enter', 'Enter', 13);
  await until(async () => (await history()).last === 'd7d5', 'recording d5');
  await reachRank(true, 400, 500);
  await press('KeyE', 'e', 69);
  await press('Space', ' ', 32);
  await key('keyDown', 'KeyA', 'a', 65);
  await sleep(250);
  await key('keyUp', 'KeyA', 'a', 65);
  await sleep(400);
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
