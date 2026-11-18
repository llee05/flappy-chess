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

  // VS AI opens this picker; choosing a level updates Settings and asks the launcher to start.
  const chess = document.getElementById('solo-chess');
  const panel = document.getElementById('level-panel');
  const opener = document.getElementById('solo');
  const choices = document.getElementById('level-choices');
  const levelButtons = [...chess.options].map((option, index) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'level-choice';
    button.dataset.level = option.value;
    button.innerHTML = `<small>${index + 1}</small>`;
    button.append(option.textContent.toUpperCase());
    button.addEventListener('click', () => {
      chess.value = option.value;
      chess.dispatchEvent(new Event('change'));
      setLevelPanelOpen(false, false);
      panel.dispatchEvent(new CustomEvent('flappy-solo-start'));
    });
    choices.append(button);
    return button;
  });
  const setLevelPanelOpen = (open, restoreFocus = true) => {
    if (open) window.setLobbyChallengeOpen?.(false);
    panel.hidden = !open;
    document.getElementById('welcome').classList.toggle('level-open', open);
    opener.setAttribute('aria-expanded', String(open));
    if (open) {
      for (const button of levelButtons) {
        const current = button.dataset.level === chess.value;
        button.classList.toggle('current', current);
        if (current) button.setAttribute('aria-current', 'true'); else button.removeAttribute('aria-current');
      }
      (levelButtons.find(button => button.dataset.level === chess.value) || levelButtons[0]).focus();
    } else if (restoreFocus) opener.focus();
  };
  window.setLobbyLevelOpen = setLevelPanelOpen;
  opener.addEventListener('click', () => setLevelPanelOpen(panel.hidden));
  document.getElementById('level-back').addEventListener('click', () => setLevelPanelOpen(false));
  panel.addEventListener('keydown', event => {
    if (event.key === 'Escape') { event.preventDefault(); setLevelPanelOpen(false); return; }
    const index = levelButtons.indexOf(document.activeElement);
    const step = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -2, ArrowDown: 2 }[event.key];
    if (index < 0 || !step) return;
    event.preventDefault();
    levelButtons[Math.min(levelButtons.length - 1, Math.max(0, index + step))].focus();
  });
})();
