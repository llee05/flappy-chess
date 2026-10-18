import assert from 'node:assert/strict';
import { withBrowser, sleep } from './browser.mjs';

await withBrowser(async browser => {
  const host = await browser.page();
  const guest = await browser.page();
  const summary = page => page.evaluate(`({...document.getElementById('match-summary').dataset})`);
  const open = async (page, url) => {
    await page.send('Page.navigate', { url });
    await page.until(() => page.evaluate("document.getElementById('host')?.disabled === false"), 'loading lobby');
  };
  const failure = page => page.evaluate("document.getElementById('connection-failure').hidden === false");
  await open(host, browser.url);
  await host.evaluate("document.getElementById('host').click()");
  await host.until(() => host.evaluate("!!document.getElementById('invite-link').value"), 'creating a room');
  const invite = await host.evaluate("document.getElementById('invite-link').value");
  assert.match(invite, /#room=flappychess-[a-f0-9]{32}$/);
  await host.screenshot('online-room');
  await open(guest, invite);
  await guest.evaluate("document.getElementById('join').click()");
  for (const [page, side] of [[host, 'WHITE'], [guest, 'BLACK']]) {
    await page.until(async () => {
      assert.equal(await failure(page), false,
        await page.evaluate("document.getElementById('failure-message').textContent"));
      return page.evaluate("document.body.dataset.game === 'ready'");
    }, `starting ${side}`);
    assert.equal(await page.evaluate('document.body.dataset.side'), side);
    assert.equal(await page.evaluate('document.body.dataset.connection'), 'open');
  }
  // Both pages must display the host's pickup and legal pawn move.
  // The white bird starts over e2 and can reach e3 with a single flap.
  await host.press('KeyE', 'e', 69);
  await guest.until(async () => (await summary(guest)).carrier === 'White', 'synchronizing pickup');
  await host.press('Space', ' ', 32);
  await sleep(650);
  await host.press('KeyE', 'e', 69);
  await guest.until(async () => (await summary(guest)).lastMove === 'e3', 'synchronizing White move');
  assert.equal((await summary(host)).turn, 'Black');
  assert.equal((await summary(guest)).turn, 'Black');

  // Black is now at the top boundary. Flap to the e7 pawn, pick it up,
  // then flap to e6. The guest may use either keyboard layout for its bird.
  await guest.press('Space', ' ', 32);
  await sleep(650);
  await guest.press('KeyE', 'e', 69);
  await host.until(async () => (await summary(host)).carrier === 'Black', 'synchronizing guest pickup');
  await guest.press('ArrowUp', 'ArrowUp', 38);
  await sleep(650);
  await guest.press('Enter', 'Enter', 13);
  await host.until(async () => (await summary(host)).lastMove === 'e6', 'validating guest move on host');
  await guest.until(async () => (await summary(guest)).lastMove === 'e6', 'synchronizing Black move');
  assert.equal((await summary(guest)).moves, '2');
  assert.equal((await summary(guest)).turn, 'White');
  await host.screenshot('online-white');
  await guest.screenshot('online-black');
  for (const page of [host, guest]) {
    assert.equal(await page.evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  }

  // A third connection receives a clear rejection without replacing either player.
  const third = await browser.page();
  await open(third, invite);
  await third.evaluate("document.getElementById('join').click()");
  await third.until(() => failure(third), 'rejecting a third player');
  assert.match(await third.evaluate("document.getElementById('failure-message').textContent"), /already has two players/);
  assert.equal(await failure(host), false);
  assert.equal(await failure(guest), false);

  await guest.evaluate("document.getElementById('leave-room').click()");
  await guest.until(() => guest.evaluate("!location.hash && document.getElementById('host')?.disabled === false"), 'returning guest to lobby');
  await host.until(() => failure(host), 'notifying host of disconnect');
  await host.screenshot('online-disconnected');
  await host.evaluate("document.getElementById('return-lobby').click()");
  await host.until(() => host.evaluate("!document.body.dataset.game && document.getElementById('host')?.disabled === false"), 'returning host to lobby');

  await guest.evaluate("document.getElementById('room').value = 'flappychess-' + '0'.repeat(32); document.getElementById('join').click()");
  await guest.until(() => failure(guest), 'reporting a nonexistent room');
  assert.match(await guest.evaluate("document.getElementById('failure-message').textContent"), /Room not found/);
  console.log(`WebRTC peer checks passed. Screenshots: ${browser.reports}`);
}, { signaling: true });
