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
  // Mirror the in-game PixelSprites silhouettes and palettes in this lightweight DOM preview.
  const pieceTypes = ['king', 'queen', 'rook', 'bishop', 'knight', 'pawn'];
  const pieceMasks = [
    '......xx....../.....xxxx...../......xx....../....xxxxxx..../...xxxxxxxx.../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.',
    '..xx..xx..xx../..xx..xx..xx../..xxxxxxxxxx../...xxxxxxxx.../....xxxxxx..../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx./.xxxxxxxxxxxx.',
    '............../............../.xxx.xxxx.xxx./.xxx.xxxx.xxx./.xxxxxxxxxxxx./..xxxxxxxxxx../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.',
    '......xx....../.....xxxx...../....xxxx....../...xxxx..xx.../...xxx..xxx.../....xxxxxx..../.....xxxx...../....xxxxxx..../.....xxxx...../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.',
    '.....xx..xx.../.....xxxxxx.../....xxxxxxxx../...xxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxx../.xxxx..xxxxx../..xx...xxxxx../......xxxxxx../.....xxxxxx.../....xxxxxxx.../...xxxxxxxx.../...xxxxxxxx.../..xxxxxxxxxx../..xxxxxxxxxx../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.',
    '............../............../............../............../.....xxxx...../....xxxxxx..../....xxxxxx..../....xxxxxx..../.....xxxx...../....xxxxxx..../.....xxxx...../.....xxxx...../....xxxxxx..../....xxxxxx..../...xxxxxxxx.../..xxxxxxxxxx../.xxxxxxxxxxxx./.xxxxxxxxxxxx.'
  ];
  const palettes = [
    ['#b99c79', '#f1dfb3', '#fff3d1', '#e6aa5d'],
    ['#414568', '#737fa4', '#b0c4db', '#7ed0d6']
  ];
  const outline = '#202538';
  const occupied = (rows, x, y) => y >= 0 && y < rows.length && x >= 0 && x < rows[y].length && rows[y][x] === 'x';
  const pieceAtlas = document.createElement('canvas');
  pieceAtlas.width = 108;
  pieceAtlas.height = 44;
  const atlas = pieceAtlas.getContext('2d');
  pieceMasks.forEach((mask, piece) => palettes.forEach((palette, side) => {
    const rows = mask.split('/'), originX = piece * 18, originY = side * 22;
    atlas.fillStyle = outline;
    rows.forEach((row, y) => [...row].forEach((pixel, x) => {
      if (pixel === 'x') atlas.fillRect(originX + x, originY + y, 3, 3);
    }));
    rows.forEach((row, y) => [...row].forEach((pixel, x) => {
      if (pixel !== 'x') return;
      const shade = !occupied(rows, x + 1, y) || !occupied(rows, x, y + 1) ? 0
        : !occupied(rows, x - 1, y) || !occupied(rows, x, y - 1) ? 2 : 1;
      atlas.fillStyle = palette[shade];
      atlas.fillRect(originX + x + 1, originY + y + 1, 1, 1);
    }));
    atlas.fillStyle = palette[3];
    atlas.fillRect(originX + 4, originY + 16, 8, 1);
    if (piece < 2) atlas.fillRect(originX + 7, originY + 5, 1, 1);
    if (piece === 4) {
      atlas.fillStyle = outline;
      atlas.fillRect(originX + 5, originY + 4, 1, 1);
    }
  }));
  const createPiece = (type, side) => {
    const sprite = document.createElement('canvas');
    sprite.width = 16;
    sprite.height = 20;
    sprite.className = `preview-piece ${side}`;
    sprite.getContext('2d').drawImage(pieceAtlas, pieceTypes.indexOf(type) * 18,
      side === 'white' ? 0 : 22, 16, 20, 0, 0, 16, 20);
    return sprite;
  };
  const initial = new Map();
  const backRank = ['rook', 'knight', 'bishop', 'queen', 'king', 'bishop', 'knight', 'rook'];
  [...files].forEach((file, index) => {
    initial.set(file + '8', { type: backRank[index], side: 'black' });
    initial.set(file + '7', { type: 'pawn', side: 'black' });
    initial.set(file + '2', { type: 'pawn', side: 'white' });
    initial.set(file + '1', { type: backRank[index], side: 'white' });
  });
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
  const pieceAt = (name, type, side = 'white') => {
    const cell = cells.get(name);
    cell.replaceChildren(...(type ? [createPiece(type, side)] : []));
  };
  const placeBird = (bird, name) => {
    const square = cells.get(name).getBoundingClientRect();
    const preview = sky.getBoundingClientRect();
    bird.dataset.square = name;
    bird.style.setProperty('--bird-x', `${square.left - preview.left + square.width / 2}px`);
    bird.style.setProperty('--bird-y', `${square.top - preview.top + square.height / 2}px`);
  };
  const carry = (bird, type, side = 'white') => {
    bird.classList.toggle('carrying', !!type);
    bird.querySelector('.carried-piece').replaceChildren(...(type ? [createPiece(type, side)] : []));
  };
  const clearMarks = () => cells.forEach(cell => cell.classList.remove('selected', 'target', 'last'));
  const mark = (name, className) => cells.get(name).classList.add(className);
  const reset = () => {
    cells.forEach((cell, name) => {
      const piece = initial.get(name);
      pieceAt(name, piece?.type, piece?.side);
    });
    clearMarks();
    carry(whiteBird, '');
    carry(blackBird, '');
    placeBird(whiteBird, 'e2');
    placeBird(blackBird, 'e7');
  };
  window.addEventListener('resize', () => requestAnimationFrame(() => {
    sky.classList.add('resetting');
    placeBird(whiteBird, whiteBird.dataset.square);
    placeBird(blackBird, blackBird.dataset.square);
    requestAnimationFrame(() => sky.classList.remove('resetting'));
  }));

  reset();
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) {
    pieceAt('e2', '');
    pieceAt('e7', '');
    pieceAt('e4', 'pawn');
    pieceAt('e5', 'pawn', 'black');
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
      carry(whiteBird, 'pawn');
    },
    () => placeBird(whiteBird, 'e4'),
    () => {
      carry(whiteBird, '');
      pieceAt('e4', 'pawn');
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
      carry(blackBird, 'pawn', 'black');
    },
    () => placeBird(blackBird, 'e5'),
    () => {
      carry(blackBird, '');
      pieceAt('e5', 'pawn', 'black');
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
