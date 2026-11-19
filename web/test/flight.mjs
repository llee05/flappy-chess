// Bird positions read from the rendered canvas, and flights that wait on them.
// Specs use these instead of fixed delays so a slow runner only takes longer.

const color = white => white ? '[255,229,161]' : '[198,240,231]';

/** Bird center in the 800px board's top-left coordinates on the desktop layout, or null. */
export const desktopBirdY = (page, white) => page.evaluate(`new Promise(resolve => requestAnimationFrame(() => {
  const canvas = document.getElementById('canvas');
  const context = new OffscreenCanvas(canvas.width, canvas.height).getContext('2d');
  context.drawImage(canvas, 0, 0);
  const scale = Math.min(canvas.width / 1600, canvas.height / 800);
  const boardLeft = Math.round((canvas.width - 1600 * scale) / 2 + 400 * scale);
  const boardWidth = Math.round(800 * scale);
  const pixels = context.getImageData(boardLeft, 0, boardWidth, canvas.height).data;
  const rgb = ${color(white)};
  let total = 0, count = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i] === rgb[0] && pixels[i + 1] === rgb[1] && pixels[i + 2] === rgb[2]) {
      total += Math.floor(i / 4 / boardWidth);
      count++;
    }
  }
  if (!count) { resolve(null); return; }
  const renderedY = total / count;
  const viewportTop = (canvas.height - 800 * scale) / 2;
  const worldY = 800 - (renderedY - viewportTop) / scale;
  // Undo the inset board presentation so the existing rank thresholds keep
  // their legacy 800px-board meaning, including the sprite's 25px offset.
  resolve(800 - (worldY - 36) / 0.91);
}))`);

/** Bird center in 800px board coordinates on touch layouts that crop the canvas to the board, or null. */
export const touchBird = (page, white = true) => page.evaluate(`new Promise(resolve => requestAnimationFrame(() => {
  const canvas = document.getElementById('canvas');
  const edge = Math.min(canvas.width, canvas.height);
  const context = new OffscreenCanvas(800, 800).getContext('2d');
  context.drawImage(canvas, (canvas.width - edge) / 2, (canvas.height - edge) / 2, edge, edge, 0, 0, 800, 800);
  const pixels = context.getImageData(0, 0, 800, 800).data;
  const rgb = ${color(white)};
  let x = 0, y = 0, count = 0;
  for (let i = 0; i < pixels.length; i += 4) {
    if (pixels[i] === rgb[0] && pixels[i + 1] === rgb[1] && pixels[i + 2] === rgb[2]) {
      x += (i / 4) % 800; y += Math.floor(i / 4 / 800); count++;
    }
  }
  resolve(count ? {x: x / count, y: y / count} : null);
}))`);

// A flap climbs about 100px (Birb.JUMP_SPEED and GRAVITY). It replaces the
// current vertical velocity, so its peak is at most this far from where the
// bird is when the flap lands.
const FLAP_HEIGHT = 100;
// Wing animation moves the sampled pixels slightly; smaller changes are not motion.
const MOTION = 3;

/**
 * Flaps until the bird is rising at least 20px into the band [top, bottom]
 * (board y, 100px per rank), then returns so the caller can pick up or drop.
 * The rest of the climb and the fall back through the band absorb input latency.
 *
 * Decisions use only observed positions, so slow frames or late inputs just
 * cost another flap. A flap is sent whenever the bird is not climbing and a
 * full flap from there cannot overshoot the band, so it never relies on the
 * timing of a flap sent mid-fall.
 *
 * `birdY` reads the authoritative bird's board y (or null when not visible);
 * `flap` sends one flap through the normal controls.
 */
export async function flyToRank(page, { birdY, flap, white, top, bottom, label, timeout = 30000 }) {
  // Progress runs in the flap direction: White flaps toward smaller y, Black toward larger y.
  const [low, high] = white ? [-bottom, -top] : [top, bottom];
  let previous = null, lastFlap = 0;
  await page.until(async () => {
    const y = await birdY();
    if (y === null) return false;
    const progress = white ? -y : y;
    const rising = previous !== null && progress > previous + MOTION;
    if (previous === null || Math.abs(progress - previous) > MOTION) previous = progress;
    if (rising && progress >= low + 20 && progress <= high - 12) return true;
    if (rising || Date.now() - lastFlap < 400 || progress + FLAP_HEIGHT > high - 10) return false;
    await flap();
    lastFlap = Date.now();
    previous = progress;
    return false;
  }, label, timeout);
}
