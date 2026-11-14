import assert from 'node:assert/strict';
import { withBrowser, sleep } from './browser.mjs';
import { readFile, rm, rename } from 'node:fs/promises';
import { resolve } from 'node:path';
import { touchControls } from './touch.mjs';

await withBrowser(async browser => {
  const host = await browser.page();
  const guest = await browser.page();
  await guest.send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 3, mobile: true });
  await guest.send('Emulation.setTouchEmulationEnabled', { enabled: true, maxTouchPoints: 5 });
  const touch = touchControls(guest);
  const summary = page => page.evaluate(`({...document.getElementById('match-summary').dataset})`);
  const open = async (page, url) => {
    await page.send('Page.navigate', { url });
    await page.until(() => page.evaluate("document.getElementById('host')?.disabled === false"), 'loading lobby');
  };
  const failure = page => page.evaluate("document.getElementById('connection-failure').hidden === false");
  const third = await browser.page();
  await open(host, browser.url);
  await host.evaluate("document.getElementById('match-minutes').value = '1'; document.getElementById('match-increment').value = '5'; document.getElementById('match-increment').dispatchEvent(new Event('change'))");
  await host.evaluate("document.getElementById('matchmake').click()");
  await host.until(() => host.evaluate("document.body.dataset.roomState === 'searching' && !document.getElementById('search-room').hidden"), 'entering random matchmaking search');
  await host.screenshot('online-searching');
  await host.evaluate("document.getElementById('cancel-search').click()");
  await host.until(() => host.evaluate("!document.body.dataset.roomState && document.getElementById('search-room').hidden && document.getElementById('matchmake').disabled === false"), 'cancelling random matchmaking search');

  await open(guest, browser.url);
  await open(third, browser.url);
  await host.evaluate("document.getElementById('matchmake').click()");
  await host.until(() => host.evaluate("document.body.dataset.roomState === 'searching'"), 'waiting in matchmaking queue');
  await guest.evaluate("document.getElementById('matchmake').click()");
  for (const [page, side] of [[host, 'WHITE'], [guest, 'BLACK']]) {
    await page.until(async () => {
      assert.equal(await failure(page), false,
        await page.evaluate("document.getElementById('failure-message').textContent"));
      return page.evaluate("document.body.dataset.game === 'ready'");
    }, `matching ${side}`);
    assert.equal(await page.evaluate('document.body.dataset.side'), side);
    assert.equal(await page.evaluate('document.body.dataset.connection'), 'open');
    assert.equal(await page.evaluate('document.body.dataset.timeControl'), '5+0', 'Random matchmaking must use the default 5+0 time control');
  }
  await third.evaluate("document.getElementById('matchmake').click()");
  await third.until(() => third.evaluate("document.body.dataset.roomState === 'searching' && !document.getElementById('search-room').hidden"), 'third player searching while first two are matched');
  await host.press('KeyE', 'e', 69);
  await guest.until(async () => (await summary(guest)).carrier === 'White', 'synchronizing pickup in matched game');
  await sleep(300);
  assert.equal(await failure(third), false);
  assert.equal(await third.evaluate('document.body.dataset.roomState'), 'searching', 'Third player must continue searching');
  assert.equal(await third.evaluate('!document.body.dataset.game'), true);
  await third.evaluate("document.getElementById('cancel-search').click()");
  await third.until(() => third.evaluate("!document.body.dataset.roomState && document.getElementById('search-room').hidden && document.getElementById('matchmake').disabled === false"), 'cancelling third player search');
  await third.evaluate("document.getElementById('matchmake').click()");
  await third.until(() => third.evaluate("document.body.dataset.roomState === 'searching'"), 'third player resuming search');
  await host.evaluate("document.getElementById('leave-room').click()");
  await host.until(() => host.evaluate("!document.body.dataset.game && document.getElementById('host')?.disabled === false"), 'returning matched host to lobby');
  await guest.until(() => failure(guest), 'notifying matched guest of disconnect');
  await guest.evaluate("document.getElementById('return-lobby').click()");
  await guest.until(() => guest.evaluate("!document.body.dataset.game && document.getElementById('host')?.disabled === false"), 'returning matched guest to lobby');
  await guest.evaluate("document.getElementById('matchmake').click()");
  for (const [page, side] of [[third, 'WHITE'], [guest, 'BLACK']]) {
    await page.until(async () => {
      assert.equal(await failure(page), false,
        await page.evaluate("document.getElementById('failure-message').textContent"));
      return page.evaluate("document.body.dataset.game === 'ready'");
    }, `pairing waiting third player as ${side}`);
    assert.equal(await page.evaluate('document.body.dataset.side'), side);
  }
  await third.evaluate("document.getElementById('leave-room').click()");
  await third.until(() => third.evaluate("!document.body.dataset.game && document.getElementById('host')?.disabled === false"), 'returning third player to lobby');
  await guest.until(() => failure(guest), 'notifying guest of third player disconnect');
  await guest.evaluate("document.getElementById('return-lobby').click()");
  await guest.until(() => guest.evaluate("!document.body.dataset.game && document.getElementById('host')?.disabled === false"), 'returning guest to lobby after second match');

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
    assert.equal(await page.evaluate('document.body.dataset.timeControl'), '1+5', 'Both peers must use the creator\'s time control');
  }
  // Both pages must display the host's pickup and legal pawn move.
  // The white bird starts over e2 and can reach e3 with a single flap.
  await host.press('KeyE', 'e', 69);
  await guest.until(async () => (await summary(guest)).carrier === 'White', 'synchronizing pickup');
  await guest.until(() => guest.evaluate("document.getElementById('action-feedback').textContent.startsWith('Picked up')"), 'synchronizing pickup feedback');
  // Drop at the back rank after falling: the pawn must stay carried on both peers.
  await sleep(1000);
  await host.press('KeyE', 'e', 69);
  await guest.until(() => guest.evaluate("document.getElementById('action-feedback').textContent.startsWith('Illegal drop')"), 'synchronizing invalid-drop feedback');
  assert.equal((await summary(guest)).carrier, 'White');
  await guest.screenshot('online-illegal-drop');
  await host.press('Space', ' ', 32);
  await sleep(650);
  await host.press('Space', ' ', 32);
  await sleep(650);
  await host.press('KeyE', 'e', 69);
  await guest.until(async () => (await summary(guest)).lastMove === 'e3', 'synchronizing White move');
  assert.equal((await summary(host)).turn, 'Black');
  assert.equal((await summary(guest)).turn, 'Black');
  await sleep(2000);
  assert.equal(await guest.evaluate("document.getElementById('action-feedback').textContent"), '', 'Repeated snapshots must not replay expired feedback');

  // Black is now at the top boundary. Flap to the e7 pawn, pick it up,
  // then flap to e6 using the phone guest's single touch pad.
  assert.equal(await guest.evaluate("document.getElementById('touch-second').hidden"), true);
  assert.equal(await guest.evaluate("document.getElementById('touch-first').dataset.side"), 'BLACK');
  await touch.tap('[data-hold="SPACE"]');
  await sleep(650);
  await touch.tap('[data-hold="E"]');
  await host.until(async () => (await summary(host)).carrier === 'Black', 'synchronizing guest pickup');
  await touch.tap('[data-hold="SPACE"]');
  await sleep(650);
  await touch.tap('[data-hold="E"]');
  await host.until(async () => (await summary(host)).lastMove === 'e6', 'validating guest move on host');
  await guest.until(async () => (await summary(guest)).lastMove === 'e6', 'synchronizing Black move');
  assert.equal((await summary(guest)).moves, '2');
  assert.equal((await summary(guest)).turn, 'White');
  await host.screenshot('online-white');
  await guest.screenshot('online-black');
  assert.equal(await guest.evaluate("document.getElementById('history-summary').dataset.last"), 'e7e6');
  const download = resolve(browser.reports, 'flappy-chess.pgn');
  await rm(download, { force: true });
  await guest.send('Browser.setDownloadBehavior', { behavior: 'allow', downloadPath: browser.reports });
  await guest.press('KeyP', 'p', 80);
  await guest.until(async () => { try { return (await readFile(download, 'utf8')).includes('1. e3 e6 *'); } catch { return false; } }, 'exporting the synchronized game from the guest');
  assert.match(await readFile(download, 'utf8'), /\[TimeControl "60\+5"\]/);
  await rename(download, resolve(browser.reports, 'online-match.pgn'));
  for (const page of [host, guest]) {
    assert.equal(await page.evaluate("document.getElementById('canvas').getContext('webgl').getError()"), 0);
  }

  // A third connection receives a clear rejection without replacing either player.
  await open(third, invite);
  await third.evaluate("document.getElementById('join').click()");
  await third.until(() => failure(third), 'rejecting a third player');
  assert.match(await third.evaluate("document.getElementById('failure-message').textContent"), /already has two players/);
  assert.equal(await failure(host), false);
  assert.equal(await failure(guest), false);

  // Advance both real browser clocks together, leaving time for WebRTC heartbeats.
  const advance = async budget => {
    const starts = await Promise.all([host, guest].map(page => page.evaluate('performance.now()')));
    await Promise.all([host, guest].map(page => page.send('Emulation.setVirtualTimePolicy',
      { policy: 'advance', budget, maxVirtualTimeTaskStarvationCount: 1000 })));
    await Promise.all([host, guest].map((page, i) => page.until(async () =>
      await page.evaluate('performance.now()') >= starts[i] + budget - 1, 'advancing browser clock', 60000)));
  };
  for (let i = 0; i < 9; i++) {
    await advance(9000);
    await sleep(350);
  }
  await guest.until(() => guest.evaluate("document.getElementById('history-summary').dataset.ended === 'true'"), 'synchronizing timeout');
  await host.press('KeyX', 'x', 88);
  await advance(100);
  await sleep(200);
  await guest.press('KeyX', 'x', 88);
  await advance(100);
  await sleep(200);
  await advance(100);
  for (const [page, side] of [[host, 'BLACK'], [guest, 'WHITE']]) {
    await page.until(() => page.evaluate("document.getElementById('series-summary').dataset.round === '2'"), 'agreeing a swap over WebRTC');
    assert.equal(await page.evaluate('document.body.dataset.side'), side);
    assert.equal(await page.evaluate("document.getElementById('series-summary').dataset.secondPoints"), '1');
  }
  assert.equal(await guest.evaluate("document.getElementById('touch-first').dataset.side"), 'WHITE');
  await touch.tap('[data-hold="E"]');
  await advance(100);
  await sleep(200);
  await advance(100);
  assert.equal((await summary(host)).carrier, 'White', 'Guest owns White after swapping');
  await guest.screenshot('online-swapped');

  const pumpUntil = (page, expression, message) => page.until(async () => {
    await page.send('Emulation.setVirtualTimePolicy', { policy: 'advance', budget: 100 });
    await sleep(50);
    return page.evaluate(expression);
  }, message);

  await guest.evaluate("document.getElementById('leave-room').click()");
  await pumpUntil(guest, "!location.hash && document.getElementById('host')?.disabled === false", 'returning guest to lobby');
  await host.until(() => failure(host), 'notifying host of disconnect');
  await host.screenshot('online-disconnected');
  await host.evaluate("document.getElementById('return-lobby').click()");
  await pumpUntil(host, "!document.body.dataset.game && document.getElementById('host')?.disabled === false", 'returning host to lobby');

  await guest.evaluate("document.getElementById('room').value = 'flappychess-' + '0'.repeat(32); document.getElementById('join').click()");
  await pumpUntil(guest, "document.getElementById('connection-failure').hidden === false", 'reporting a nonexistent room');
  assert.match(await guest.evaluate("document.getElementById('failure-message').textContent"), /Room not found/);
  console.log(`WebRTC peer checks passed. Screenshots: ${browser.reports}`);
}, { signaling: true });
