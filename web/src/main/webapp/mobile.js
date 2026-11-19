(() => {
  'use strict';
  const byId = id => document.getElementById(id);
  const media = matchMedia('(max-width: 720px), (pointer: coarse)');
  const pointers = new Map();
  let sendKey, releaseGame, controlState = '';
  const text = (id, value) => {
    const element = byId(id);
    if (element.textContent !== value) element.textContent = value;
  };
  const releasePointer = id => {
    const held = pointers.get(id);
    if (!held) return;
    pointers.delete(id);
    if (![...pointers.values()].some(other => other.key === held.key)) {
      sendKey?.(held.key, false);
      held.button.classList.remove('held');
    }
  };
  const release = () => {
    for (const id of [...pointers.keys()]) releasePointer(id);
    releaseGame?.();
  };
  const updateLayout = () => {
    release();
    document.body.classList.toggle('mobile', media.matches);
  };
  updateLayout();
  media.addEventListener('change', updateLayout);
  window.addEventListener('resize', release);
  window.visualViewport?.addEventListener('resize', release);
  window.addEventListener('blur', release);
  window.addEventListener('pagehide', release);
  document.addEventListener('visibilitychange', () => { if (document.hidden) release(); });

  for (const button of document.querySelectorAll('[data-hold]')) {
    button.addEventListener('pointerdown', event => {
      if (!sendKey || button.disabled || event.button !== 0) return;
      event.preventDefault();
      const key = button.dataset.hold;
      const alreadyHeld = [...pointers.values()].some(held => held.key === key);
      pointers.set(event.pointerId, { key, button });
      button.setPointerCapture(event.pointerId);
      button.classList.add('held');
      if (!alreadyHeld) sendKey(key, true);
    });
    for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) {
      button.addEventListener(type, event => releasePointer(event.pointerId));
    }
    button.addEventListener('contextmenu', event => event.preventDefault());
    // Keyboard and assistive-technology activation still produces a single press.
    button.addEventListener('click', event => {
      if (event.detail === 0 && !button.disabled) {
        sendKey?.(button.dataset.hold, true);
        sendKey?.(button.dataset.hold, false);
      }
    });
  }
  for (const button of document.querySelectorAll('[data-key]')) {
    button.addEventListener('click', () => {
      if (button.disabled) return;
      release();
      sendKey?.(button.dataset.key, true);
      sendKey?.(button.dataset.key, false);
    });
  }
  const pad = (id, side, label, carrier) => {
    const element = byId(id);
    element.dataset.side = side;
    text(id + '-label', `${label} · ${side === 'WHITE' ? 'White ↓' : 'Black ↑'}`);
    const flap = element.querySelector('.flap'), carry = element.querySelector('.carry');
    const flapText = side === 'WHITE' ? 'Flap ↑' : 'Flap ↓';
    const carryText = carrier === side ? 'Drop' : 'Pick / drop';
    if (flap.textContent !== flapText) flap.textContent = flapText;
    if (carry.textContent !== carryText) carry.textContent = carryText;
    const actions = ['move left', 'move right', 'flap', carrier === side ? 'drop piece' : 'pick up or drop piece'];
    element.querySelectorAll('button').forEach((button, i) => button.setAttribute('aria-label', `${label} ${actions[i]}`));
  };
  window.FlappyMobile = {
    bind(key, releaseInput) { sendKey = key; releaseGame = releaseInput; },
    release,
    present(state) {
      if (!media.matches) return;
      const history = byId('history-summary').dataset;
      const series = byId('series-summary').dataset;
      const review = history.review === 'true';
      const ended = history.ended === 'true';
      const single = state.solo || !!state.ownSide;
      const ownSide = state.ownSide || (state.solo
        ? (byId('solo-summary').dataset.side === 'WHITE' ? 'BLACK' : 'WHITE') : '');
      const firstSide = state.tutorial || series.firstWhite !== 'false' ? 'WHITE' : 'BLACK';
      const secondSide = firstSide === 'WHITE' ? 'BLACK' : 'WHITE';
      const nextControlState = [single, ownSide, firstSide, state.promotion, state.complete, ended, review, series.round].join();
      if (controlState !== nextControlState) { release(); controlState = nextControlState; }
      text('white-time', state.white);
      text('black-time', state.black);
      for (const side of ['WHITE', 'BLACK']) {
        const element = byId(side.toLowerCase() + '-clock');
        element.classList.toggle('active', !ended && state.turn === side);
        element.classList.toggle('warning', /^0:/.test(state[side.toLowerCase()]) && Number(state[side.toLowerCase()].slice(2)) <= 30);
      }
      pad('touch-first', ownSide || firstSide, single ? 'You' : 'Player 1', state.carrier);
      pad('touch-second', secondSide, 'Player 2', state.carrier);
      const blocked = ended || review || state.promotion || state.complete;
      byId('touch-first').hidden = blocked;
      byId('touch-second').hidden = blocked || single;
      for (const button of document.querySelectorAll('[data-hold]')) button.disabled = blocked;
      const dialog = state.promotion || state.complete || ended && !review;
      byId('mobile-dialog').hidden = !dialog;
      byId('mobile-promotion').hidden = !state.promotion || single && ownSide !== state.turn;
      byId('mobile-result').hidden = !ended || review || state.complete;
      byId('mobile-complete').hidden = !state.complete;
      text('mobile-dialog-title', state.promotion ? 'Choose promotion'
        : state.complete ? 'Ready to play!' : state.result || '');
      text('mobile-dialog-note', state.promotion ? single && ownSide !== state.turn ? 'Your opponent is choosing a piece.' : 'Your clock is running. Choose your new piece.'
          : state.complete ? 'Both deliveries made. Start a match or practice again.'
            : `${state.reason || ''}${state.ownSide ? ' Both players must choose the same rematch option. ' + byId('network-status').textContent : ''}`);
    }
  };
})();
