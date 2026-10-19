import assert from 'node:assert/strict';
import { sleep } from './browser.mjs';

export async function checkSolo(browser) {
  const page = await browser.page();
  const { evaluate, send, until, press, screenshot } = page;
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('solo')?.disabled === false"), 'loading solo setup');
  await evaluate("document.getElementById('match-minutes').value = '0'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('solo-side').value = 'BLACK'; document.getElementById('solo-chess').value = 'HARD'; document.getElementById('solo-flight').value = 'EASY'; document.getElementById('solo-flight').dispatchEvent(new Event('change')); document.getElementById('solo').click()");
  await until(() => evaluate("document.body.dataset.mode === 'solo'"), 'starting solo');
  const solo = () => evaluate("({...document.getElementById('solo-summary').dataset})");
  assert.equal((await solo()).side, 'WHITE');
  assert.equal((await solo()).chess, 'HARD');
  assert.equal((await solo()).flight, 'EASY');
  let carried = false;
  await until(async () => {
    const state = await solo();
    carried ||= state.carrier === 'WHITE';
    return evaluate("document.getElementById('history-summary').dataset.moves === '1'");
  }, 'computer flying, picking up, and completing its move', 45000);
  assert.equal(carried, true, 'AI must visibly carry the piece');
  await screenshot('solo-ai-move');
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  await press('KeyS', 's', 83);
  await press('KeyW', 'w', 87);
  await press('KeyE', 'e', 69);
  await press('KeyF', 'f', 70);
  await press('Enter', 'Enter', 13);
  await until(async () => (await solo()).side === 'BLACK', 'choosing the opposite gravity in match setup');
  assert.equal((await solo()).chess, 'EASY');
  assert.equal((await solo()).flight, 'MEDIUM');
  await sleep(200);
  assert.equal(await evaluate("document.getElementById('history-summary').dataset.moves"), '0', 'Computer waits for the human to move');
  await press('KeyE', 'e', 69);
  await until(async () => (await solo()).carrier === 'WHITE', 'human picks up using either layout');
  await screenshot('solo-human-white');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("document.getElementById('solo')?.disabled === false && !document.body.dataset.game"), 'leaving solo');
  assert.equal(await evaluate('window.FlappySettings.solo().side'), 'BLACK', 'Solo lobby preferences persist');
  console.log('Solo browser checks passed: independent difficulties, physical AI delivery, setup, controls, and exit.');
}
