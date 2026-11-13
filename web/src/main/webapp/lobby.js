(() => {
  'use strict';

  const byId = id => document.getElementById(id);
  const modal = byId('join-modal');
  const opener = byId('join-open');
  const closeButton = byId('join-close');
  const room = byId('room');
  const returnFocus = opener;

  function openJoinModal() {
    modal.hidden = false;
    document.body.classList.add('join-open');
    byId('join-status').textContent = '';
    requestAnimationFrame(() => room.focus());
  }

  function closeJoinModal() {
    if (modal.hidden) return;
    modal.hidden = true;
    document.body.classList.remove('join-open');
    returnFocus?.focus();
  }

  opener.addEventListener('click', openJoinModal);
  closeButton.addEventListener('click', closeJoinModal);
  modal.addEventListener('click', event => { if (event.target === modal) closeJoinModal(); });
  modal.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      event.preventDefault();
      closeJoinModal();
      return;
    }
    if (event.key !== 'Tab') return;
    const focusable = [...modal.querySelectorAll('button:not(:disabled), input:not(:disabled)')];
    const first = focusable[0], last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  });

  const menu = byId('lobby-menu');
  menu.addEventListener('click', () => {
    const welcome = byId('welcome');
    const open = welcome.classList.toggle('menu-open');
    menu.setAttribute('aria-expanded', String(open));
    menu.setAttribute('aria-label', open ? 'Hide match settings' : 'Show match settings');
    if (open) byId('match-minutes').focus();
  });
  document.addEventListener('keydown', event => {
    if (event.key !== 'Escape' || !byId('welcome').classList.contains('menu-open') || !modal.hidden) return;
    byId('welcome').classList.remove('menu-open');
    menu.setAttribute('aria-expanded', 'false');
    menu.setAttribute('aria-label', 'Show match settings');
    menu.focus();
  });

  window.FlappyLobby = { openJoinModal, closeJoinModal };

  const board = byId('preview-board');
  const files = 'abcdefgh';
  const initial = {
    a8: '♖', b8: '♘', c8: '♗', d8: '♕', e8: '♔', f8: '♗', g8: '♘', h8: '♖',
    a7: '♙', b7: '♙', c7: '♙', d7: '♙', e7: '♙', f7: '♙', g7: '♙', h7: '♙',
    a2: '♙', b2: '♙', c2: '♙', d2: '♙', e2: '♙', f2: '♙', g2: '♙', h2: '♙',
    a1: '♖', b1: '♘', c1: '♗', d1: '♕', e1: '♔', f1: '♗', g1: '♘', h1: '♖'
  };
  const cells = new Map();
  for (let rank = 8; rank >= 1; rank--) {
    for (let file = 0; file < 8; file++) {
      const name = files[file] + rank;
      const cell = document.createElement('span');
      cell.className = 'preview-square' + ((file + rank) % 2 ? ' dark' : '');
      cell.dataset.square = name;
      board.appendChild(cell);
      cells.set(name, cell);
    }
  }

  const whiteBird = byId('preview-white');
  const blackBird = byId('preview-black');
  const sky = board.parentElement;
  const pieceAt = (name, piece, black = false) => {
    const cell = cells.get(name);
    cell.textContent = piece || '';
    cell.classList.toggle('black-piece', !!piece && black);
  };
  const placeBird = (bird, name) => {
    const file = files.indexOf(name[0]);
    const fromTop = 8 - Number(name[1]);
    bird.style.setProperty('--bird-x', `${23.75 + (file + .5) * 6.5625}%`);
    bird.style.setProperty('--bird-y', `${6 + (fromTop + .5) * 10.5}%`);
  };
  const carry = (bird, piece) => {
    bird.classList.toggle('carrying', !!piece);
    bird.querySelector('.carried-piece').textContent = piece || '';
  };
  const clearMarks = () => cells.forEach(cell => cell.classList.remove('selected', 'target', 'last'));
  const mark = (name, className) => cells.get(name).classList.add(className);
  const reset = () => {
    cells.forEach((cell, name) => pieceAt(name, initial[name], Number(name[1]) >= 7));
    clearMarks();
    carry(whiteBird, '');
    carry(blackBird, '');
    placeBird(whiteBird, 'e2');
    placeBird(blackBird, 'e7');
  };

  reset();
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
    pieceAt('e2', '');
    pieceAt('e7', '');
    pieceAt('e4', '♙');
    pieceAt('e5', '♙', true);
    mark('e2', 'last');
    mark('e4', 'last');
    mark('e7', 'last');
    mark('e5', 'last');
    placeBird(whiteBird, 'e4');
    placeBird(blackBird, 'e5');
    return;
  }

  const steps = [
    () => {
      sky.classList.add('resetting');
      reset();
      requestAnimationFrame(() => requestAnimationFrame(() => sky.classList.remove('resetting')));
    },
    () => {
      clearMarks();
      pieceAt('e2', '');
      mark('e2', 'selected');
      mark('e3', 'target');
      mark('e4', 'target');
      carry(whiteBird, '♙');
    },
    () => placeBird(whiteBird, 'e4'),
    () => {
      carry(whiteBird, '');
      pieceAt('e4', '♙');
      clearMarks();
      mark('e2', 'last');
      mark('e4', 'last');
    },
    () => {
      pieceAt('e7', '');
      clearMarks();
      mark('e7', 'selected');
      mark('e6', 'target');
      mark('e5', 'target');
      carry(blackBird, '♙');
    },
    () => placeBird(blackBird, 'e5'),
    () => {
      carry(blackBird, '');
      pieceAt('e5', '♙', true);
      clearMarks();
      mark('e7', 'last');
      mark('e5', 'last');
    },
    () => {},
    () => {}
  ];
  let step = 0;
  const advance = () => {
    if (document.hidden) return;
    steps[step]();
    step = (step + 1) % steps.length;
  };
  advance();
  setInterval(advance, 1050);
})();
