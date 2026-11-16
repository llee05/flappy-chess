(() => {
  'use strict';

  const canvas = document.getElementById('menu-flights');
  const welcome = document.getElementById('welcome');
  if (!canvas || !welcome) return;

  const context = canvas.getContext('2d', { alpha: true });
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const outline = '#202538';
  const palettes = {
    whiteBird: ['#b77345', '#efb95b', '#ffe5a1', '#d99045'],
    blackBird: ['#416788', '#78c4d0', '#c6f0e7', '#579eae']
  };
  const pieceAtlas = new Image();
  const pieceCellSize = 160;
  const wings = [
    'xx.../xxx../xxxx./.xxxx/..xxx',
    'xxxxx/xxxxx/.xxx.',
    '..xxx/.xxxx/xxxx./xxx../xx...'
  ];
  const body = '...xxx.../.xxxxxxx./xxxxxxxxx/xxxxxxxxx/xxxxxxxxx/.xxxxxxx./..xxxxx..';
  const tail = 'xx./xxx/.xx';
  const pieceChoices = [0, 1, 2, 3, 4, 5];
  const tracks = [0.18, 0.36, 0.62, 0.81];
  const mobileTracks = [0.69, 0.8, 0.9];
  const birds = [];
  const sprites = { birds: [[], []] };
  let width = 0;
  let height = 0;
  let ratio = 1;
  let animationFrame = 0;
  let lastTime = 0;

  function rows(pattern) { return pattern.split('/'); }

  function filled(patternRows, x, y) {
    return y >= 0 && y < patternRows.length && x >= 0 && x < patternRows[y].length
      && patternRows[y][x] === 'x';
  }

  function fillMask(spriteContext, pattern, x, y, palette) {
    const patternRows = rows(pattern);
    spriteContext.fillStyle = outline;
    for (let row = 0; row < patternRows.length; row++) {
      for (let col = 0; col < patternRows[row].length; col++) {
        if (filled(patternRows, col, row)) spriteContext.fillRect(x + col - 1, y + row - 1, 3, 3);
      }
    }
    for (let row = 0; row < patternRows.length; row++) {
      for (let col = 0; col < patternRows[row].length; col++) {
        if (!filled(patternRows, col, row)) continue;
        const shade = !filled(patternRows, col + 1, row) || !filled(patternRows, col, row + 1)
          ? palette[0]
          : !filled(patternRows, col - 1, row) || !filled(patternRows, col, row - 1)
            ? palette[2]
            : palette[1];
        spriteContext.fillStyle = shade;
        spriteContext.fillRect(x + col, y + row, 1, 1);
      }
    }
  }

  function makeBird(side, frame) {
    const sheet = document.createElement('canvas');
    sheet.width = 18;
    sheet.height = 18;
    const spriteContext = sheet.getContext('2d');
    const palette = palettes[side === 0 ? 'whiteBird' : 'blackBird'];
    fillMask(spriteContext, body, 4, 5, palette);
    fillMask(spriteContext, tail, 1, 9, palette);
    fillMask(spriteContext, wings[frame], 3, frame === 0 ? 2 : frame === 1 ? 7 : 8, palette);
    spriteContext.fillStyle = palette[2];
    spriteContext.fillRect(10, 6, 3, 3);
    spriteContext.fillStyle = outline;
    spriteContext.fillRect(12, 7, 1, 2);
    spriteContext.fillStyle = '#e99658';
    spriteContext.fillRect(13, 9, 3, 2);
    spriteContext.fillStyle = palette[0];
    spriteContext.fillRect(8, 13, 1, 1);
    spriteContext.fillRect(11, 13, 1, 1);

    const sprite = document.createElement('canvas');
    sprite.width = 16;
    sprite.height = 16;
    sprite.getContext('2d').drawImage(sheet, 0, 0, 16, 16, 0, 0, 16, 16);
    return sprite;
  }

  function buildSprites() {
    for (let side = 0; side < 2; side++) {
      for (let frame = 0; frame < 3; frame++) sprites.birds[side][frame] = makeBird(side, frame);
    }
  }

  function layout() {
    width = window.innerWidth;
    height = window.innerHeight;
    ratio = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(width * ratio);
    canvas.height = Math.round(height * ratio);
    context.setTransform(ratio, 0, 0, ratio, 0, 0);
    context.imageSmoothingEnabled = false;
    resetBirds();
  }

  function resetBird(bird, index, initial) {
    const mobile = width <= 720 && height > width;
    const availableTracks = mobile ? mobileTracks : tracks;
    bird.direction = index % 2 === 0 ? 1 : -1;
    bird.side = index % 2;
    bird.piece = pieceChoices[(index * 2 + Math.floor(Math.random() * 3)) % pieceChoices.length];
    bird.scale = mobile ? 0.72 + Math.random() * 0.13 : 0.78 + Math.random() * 0.3;
    bird.speed = (mobile ? 34 : 45) + Math.random() * (mobile ? 18 : 38);
    bird.y = height * availableTracks[(index + Math.floor(Math.random() * 2)) % availableTracks.length];
    bird.bob = Math.random() * Math.PI * 2;
    const margin = 110 * bird.scale;
    bird.x = initial
      ? width * (0.08 + index * (0.84 / Math.max(1, birds.length - 1)))
      : bird.direction > 0 ? -margin : width + margin;
  }

  function resetBirds() {
    birds.length = 0;
    const count = reducedMotion.matches ? 2 : width <= 720 ? 2 : 4;
    for (let index = 0; index < count; index++) birds.push({});
    for (let index = 0; index < count; index++) resetBird(birds[index], index, true);
    if (reducedMotion.matches) {
      birds[0].x = width * 0.13;
      birds[0].y = height * 0.74;
      birds[1].x = width * 0.86;
      birds[1].y = height * 0.87;
    }
  }

  function overlapsReadableContent(bird, size) {
    const title = welcome.querySelector('.title-logo');
    const menu = welcome.querySelector('.lobby-menu-card');
    const left = bird.x - size * 0.65;
    const right = bird.x + size * 0.65;
    const top = bird.y - size * 0.65;
    const bottom = bird.y + size * 0.95;
    return [title, menu].some(element => {
      const rect = element.getBoundingClientRect();
      return right > rect.left - 18 && left < rect.right + 18
        && bottom > rect.top - 14 && top < rect.bottom + 14;
    });
  }

  function drawBird(bird, time) {
    const size = Math.round(72 * bird.scale);
    const pieceSize = Math.round(size * 0.625);
    const bob = reducedMotion.matches ? 0 : Math.sin(time * 0.0034 + bird.bob) * 7 * bird.scale;
    const x = Math.round(bird.x);
    const y = Math.round(bird.y + bob);
    const frame = reducedMotion.matches ? 1 : Math.floor(time / 130 + bird.bob) % 3;
    const pieceX = Math.round(x - pieceSize / 2);
    const pieceY = Math.round(y + size * 0.34);

    context.save();
    context.globalAlpha = overlapsReadableContent(bird, size) ? 0.1 : 0.92;
    context.fillStyle = '#efb95b';
    context.fillRect(Math.round(x - 8 * bird.scale), Math.round(y + size * 0.2), Math.max(1, Math.round(2 * bird.scale)), Math.round(size * 0.34));
    context.fillRect(Math.round(x + 6 * bird.scale), Math.round(y + size * 0.2), Math.max(1, Math.round(2 * bird.scale)), Math.round(size * 0.34));
    if (pieceAtlas.complete && pieceAtlas.naturalWidth) {
      context.drawImage(pieceAtlas,
        bird.piece * pieceCellSize, bird.side * pieceCellSize, pieceCellSize, pieceCellSize,
        pieceX, pieceY, pieceSize, pieceSize);
    }
    context.translate(x, y);
    context.scale(bird.direction, 1);
    context.drawImage(sprites.birds[bird.side][frame], -size / 2, -size / 2, size, size);
    context.restore();
  }

  function draw(time) {
    context.clearRect(0, 0, width, height);
    for (const bird of birds) drawBird(bird, time);
  }

  function animate(time) {
    if (document.hidden || welcome.hidden || reducedMotion.matches) {
      animationFrame = 0;
      return;
    }
    const delta = lastTime ? Math.min((time - lastTime) / 1000, 0.05) : 0;
    lastTime = time;
    for (let index = 0; index < birds.length; index++) {
      const bird = birds[index];
      bird.x += bird.speed * bird.direction * delta;
      const margin = 100 * bird.scale;
      if ((bird.direction > 0 && bird.x > width + margin) || (bird.direction < 0 && bird.x < -margin)) {
        resetBird(bird, index, false);
      }
    }
    draw(time);
    animationFrame = requestAnimationFrame(animate);
  }

  function start() {
    if (animationFrame || document.hidden || welcome.hidden || reducedMotion.matches) return;
    lastTime = 0;
    animationFrame = requestAnimationFrame(animate);
  }

  function motionChanged() {
    if (animationFrame) cancelAnimationFrame(animationFrame);
    animationFrame = 0;
    resetBirds();
    draw(0);
    start();
  }

  buildSprites();
  pieceAtlas.addEventListener('load', () => draw(performance.now()));
  pieceAtlas.src = './assets/images/pieces.png';
  layout();
  draw(0);
  start();
  window.addEventListener('resize', layout);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      if (animationFrame) cancelAnimationFrame(animationFrame);
      animationFrame = 0;
    } else {
      start();
    }
  });
  new MutationObserver(() => {
    if (welcome.hidden) {
      if (animationFrame) cancelAnimationFrame(animationFrame);
      animationFrame = 0;
    } else {
      start();
    }
  }).observe(welcome, { attributes: true, attributeFilter: ['hidden'] });
  reducedMotion.addEventListener('change', motionChanged);
})();
