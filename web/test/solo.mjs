import assert from 'node:assert/strict';
import { sleep } from './browser.mjs';

export async function checkSolo(browser) {
  const page = await browser.page();
  const { evaluate, send, until, press, screenshot } = page;
  await send('Page.navigate', { url: browser.url });
  await until(() => evaluate("document.getElementById('solo')?.disabled === false"), 'loading solo setup');
  await evaluate("document.getElementById('match-minutes').value = '0'; document.getElementById('match-minutes').dispatchEvent(new Event('change')); document.getElementById('solo-side').value = 'BLACK'; document.getElementById('solo-chess').value = 'HAWK'; document.getElementById('solo-chess').dispatchEvent(new Event('change')); document.getElementById('solo-flight').value = 'EASY'; document.getElementById('solo-flight').dispatchEvent(new Event('change')); document.getElementById('solo').click()");
  await until(() => evaluate("!document.getElementById('level-panel').hidden"), 'opening the VS AI level picker');
  assert.deepEqual(await evaluate("[...document.querySelectorAll('#level-choices .level-choice')].map(button => button.dataset.level)"),
    ['EGG', 'HATCHLING', 'SPARROW', 'PIGEON', 'RAVEN', 'HAWK', 'EAGLE', 'PHOENIX']);
  assert.equal(await evaluate("document.activeElement.dataset.level"), 'HAWK', 'Picker preselects the Settings level');
  assert.equal(await evaluate("document.body.dataset.mode || ''"), '', 'Opening the picker does not start a game');
  await screenshot('solo-level-picker');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("document.getElementById('level-panel').hidden && document.activeElement.id === 'solo'"), 'backing out of the level picker');
  await evaluate("document.getElementById('solo').click()");
  await press('ArrowRight', 'ArrowRight', 39);
  assert.equal(await evaluate("document.activeElement.dataset.level"), 'EAGLE', 'Arrow keys move between levels');
  await evaluate("document.activeElement.click()");
  await until(() => evaluate("document.body.dataset.mode === 'solo'"), 'starting solo');
  const solo = () => evaluate("({...document.getElementById('solo-summary').dataset})");
  assert.equal((await solo()).side, 'WHITE');
  assert.equal((await solo()).chess, 'EAGLE');
  assert.equal(await evaluate("document.getElementById('solo-chess').value"), 'EAGLE', 'Picker keeps Settings in sync');
  assert.equal(await evaluate("JSON.parse(localStorage.getItem('flappy-chess-solo')).chess"), 'EAGLE', 'Picker choice persists');
  assert.equal((await solo()).flight, 'EASY');
  let carried = false;
  let sawStockfish = false;
  await until(async () => {
    const state = await solo();
    carried ||= state.carrier === 'WHITE';
    if (state.phase?.startsWith('STOCKFISH')) sawStockfish = true;
    assert(!state.phase?.startsWith('FALLBACK'), `AI must use Stockfish, not fallback; phase was ${state.phase}`);
    return evaluate("document.getElementById('history-summary').dataset.moves === '1'");
  }, 'computer flying, picking up, and completing its move', 45000);
  assert.equal(sawStockfish, true, 'AI must enter the STOCKFISH phase');
  assert.equal(carried, true, 'AI must visibly carry the piece');
  assert.equal(await evaluate("document.getElementById('solo-fallback-note')?.hidden ?? true"), true,
    'Backup AI note must remain hidden when Stockfish succeeds');
  await screenshot('solo-ai-move');
  assert.equal(await evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("document.getElementById('solo')?.disabled === false && !document.body?.dataset.game"), 'returning to solo setup');
  await evaluate("document.getElementById('solo-side').value = 'WHITE'; document.getElementById('solo-flight').value = 'MEDIUM'; document.getElementById('solo-flight').dispatchEvent(new Event('change')); document.getElementById('solo').click()");
  await until(() => evaluate("!document.getElementById('level-panel').hidden"), 'reopening the level picker');
  assert.equal(await evaluate("document.activeElement.dataset.level"), 'EAGLE', 'Picker preselects the last choice');
  await evaluate("document.querySelector('#level-choices [data-level=EGG]').click()");
  await until(async () => (await solo()).side === 'BLACK', 'starting solo with the opposite gravity');
  assert.equal((await solo()).chess, 'EGG');
  assert.equal((await solo()).flight, 'MEDIUM');
  await sleep(200);
  assert.equal(await evaluate("document.getElementById('history-summary').dataset.moves"), '0', 'Computer waits for the human to move');
  await press('KeyE', 'e', 69);
  await until(async () => (await solo()).carrier === 'WHITE', 'human picks up using either layout');
  await screenshot('solo-human-white');
  await press('Escape', 'Escape', 27);
  await until(() => evaluate("document.getElementById('solo')?.disabled === false && !document.body?.dataset.game"), 'leaving solo');
  assert.equal(await evaluate('window.FlappySettings.solo().side'), 'WHITE', 'Solo lobby preferences persist');
  console.log('Solo browser checks passed: level picker, independent difficulties, physical AI delivery, lobby setup, controls, and exit.');
}
