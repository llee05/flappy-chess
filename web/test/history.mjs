import assert from 'node:assert/strict';
import { readFile, rm, rename } from 'node:fs/promises';
import { resolve } from 'node:path';
import { sleep } from './browser.mjs';

export async function checkHistory(browser) {
  const page = await browser.page();
  const { send, evaluate, key, press, until, screenshot } = page;
  const history = () => evaluate("({...document.getElementById('history-summary').dataset})");
  const birdY = white => evaluate(`new Promise(resolve => requestAnimationFrame(() => {
    const canvas = document.getElementById('canvas');
    const context = new OffscreenCanvas(canvas.width, canvas.height).getContext('2d');
    context.drawImage(canvas, 0, 0);
    const pixels = context.getImageData(200, 0, 800, 800).data;
    const rgb = ${white ? '[255,229,161]' : '[198,240,231]'};
    let total = 0, count = 0;
    for (let i = 0; i < pixels.length; i += 4) {
      if (pixels[i] === rgb[0] && pixels[i + 1] === rgb[1] && pixels[i + 2] === rgb[2]) {
        total += Math.floor(i / 4 / 800);
        count++;
      }
    }
    resolve(count ? total / count : null);
  }))`);
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
  const finalFen = (await history()).fen;

  // Chromium advances the actual monotonic browser clock; session rules still declare timeout.
  await send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 61000, maxVirtualTimeTaskStarvationCount: 10000 });
  await until(async () => (await history()).ended === 'true', 'ending the game on time', 60000);
  const frame = () => send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 100 });
  await screenshot('history-result');
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 488, y: 428, button: 'left', clickCount: 1 });
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 488, y: 428, button: 'left', clickCount: 1 });
  await frame();
  await until(async () => (await history()).review === 'true', 'opening postgame review');
  await press('Home', 'Home', 36);
  await frame();
  await until(async () => (await history()).ply === '0', 'reviewing the starting position');
  assert.equal((await history()).whiteCaptured, '0');
  assert.match((await history()).fen, /^rnbqkbnr\/pppppppp/);
  await screenshot('history-review-start');
  await press('End', 'End', 35);
  await frame();
  await until(async () => (await history()).ply === '3', 'reviewing the final position');
  assert.equal((await history()).fen, finalFen);
  assert.equal((await history()).whiteCaptured, '1');
  await screenshot('history-review-final');
  await send('Input.dispatchMouseEvent', { type: 'mousePressed', x: 1100, y: 385, button: 'left', clickCount: 1 });
  await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: 1100, y: 385, button: 'left', clickCount: 1 });
  await frame();
  await until(async () => (await history()).ply === '2', 'clicking the previous move');
  await press('ArrowRight', 'ArrowRight', 39);
  await frame();
  await until(async () => (await history()).ply === '3', 'stepping forward again');

  const file = resolve(browser.reports, 'flappy-chess.pgn');
  await rm(file, { force: true });
  await send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: browser.reports });
  await press('KeyP', 'p', 80);
  await frame();
  await until(async () => { try { return (await readFile(file, 'utf8')).includes('exd5'); } catch { return false; } }, 'downloading PGN');
  const pgn = await readFile(file, 'utf8');
  assert.match(pgn, /\[TimeControl "60\+0"\]/);
  assert.match(pgn, /\[Result "1-0"\]/);
  assert.match(pgn, /1\. e4 d5 2\. exd5 1-0/);
  await rename(file, resolve(browser.reports, 'local-match.pgn'));
  await press('KeyV', 'v', 86);
  await frame();
  await until(async () => (await history()).review === 'false', 'returning to the result');
  assert.equal((await history()).ended, 'true');
  console.log('Browser history checks passed: capture, timeout, review, and PGN download.');
}
