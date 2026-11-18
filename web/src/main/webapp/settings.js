(() => {
  'use strict';
  const minutes = document.getElementById('match-minutes');
  const increment = document.getElementById('match-increment');
  const read = () => ({ minutes: Number(minutes.value), increment: minutes.value === '0' ? 0 : Number(increment.value) });
  const update = () => {
    if (minutes.value === '0') increment.value = '0';
    increment.disabled = minutes.value === '0';
    const choice = read();
    document.getElementById('time-control-note').textContent = !choice.minutes
      ? 'Untimed: practice at your own pace.'
      : `${choice.minutes} minutes per side. ${choice.increment ? `+${choice.increment} seconds after each completed move.` : 'No bonus.'}`;
    try { localStorage.setItem('flappy-chess-time-control', JSON.stringify(choice)); } catch { /* Storage is optional. */ }
  };
  try {
    const saved = JSON.parse(localStorage.getItem('flappy-chess-time-control'));
    if (saved && [...minutes.options].some(option => option.value === String(saved.minutes))
        && [...increment.options].some(option => option.value === String(saved.increment))) {
      minutes.value = String(saved.minutes);
      increment.value = String(saved.increment);
    }
  } catch { /* Keep the five-minute default if storage is unavailable or invalid. */ }
  minutes.addEventListener('change', update);
  increment.addEventListener('change', update);
  update();
  const soloFields = ['side', 'chess', 'flight'];
  const solo = () => Object.fromEntries(soloFields.map(name => [name, document.getElementById('solo-' + name).value]));
  // Levels were renamed to birds; keep a saved pre-rename choice at the same strength.
  const renamedLevels = { HATCHLING: 'EGG', BEGINNER: 'HATCHLING', CASUAL: 'SPARROW', CLUB: 'PIGEON',
    CHALLENGER: 'RAVEN', EXPERT: 'HAWK', MASTER: 'EAGLE', STOCKFISH: 'PHOENIX' };
  try {
    const saved = JSON.parse(localStorage.getItem('flappy-chess-solo'));
    if (saved && !saved.levelNames && renamedLevels[saved.chess]) saved.chess = renamedLevels[saved.chess];
    for (const name of soloFields) {
      const field = document.getElementById('solo-' + name);
      if (saved && [...field.options].some(option => option.value === saved[name])) field.value = saved[name];
    }
  } catch { /* Preferences are optional. */ }
  for (const name of soloFields) document.getElementById('solo-' + name).addEventListener('change', () => {
    try { localStorage.setItem('flappy-chess-solo', JSON.stringify({ ...solo(), levelNames: 'birds' })); } catch { /* Optional storage. */ }
  });
  window.FlappySettings = { read, solo };
})();
