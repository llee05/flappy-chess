import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import vm from 'node:vm';

const source = await readFile(new URL('../src/main/webapp/network.js', import.meta.url), 'utf8');
const protocol = 123;
const room = 'flappychess-' + 'a'.repeat(32);

// Exercise the real lobby/data/status code without a browser, WebRTC, or a render loop.
async function inviteGuest(startProtocol = protocol) {
  const elements = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, {
      hidden: false, value: '', textContent: '', listeners: new Map(),
      addEventListener(name, callback) { this.listeners.set(name, callback); },
      focus() {}
    });
    return elements.get(id);
  };
  const readyLabels = [];
  const dataset = new Proxy({}, {
    set(target, key, value) {
      if (key === 'game' && value === 'ready') readyLabels.push(target.timeControl);
      target[key] = value;
      return true;
    }
  });
  class Events {
    listeners = new Map();
    on(name, callback) {
      const callbacks = this.listeners.get(name) || [];
      callbacks.push(callback);
      this.listeners.set(name, callbacks);
    }
    emit(name, value) { for (const callback of this.listeners.get(name) || []) callback(value); }
  }
  const candidate = new Events();
  candidate.open = true;
  const peers = [];
  class Peer extends Events {
    constructor() { super(); peers.push(this); }
    connect() { return candidate; }
  }
  const window = { RTCPeerConnection: {}, addEventListener() {} };
  const starts = [];
  vm.runInNewContext(source, {
    window, Peer, URL, URLSearchParams,
    location: { href: 'https://example.test/flappy-chess/', hash: '' },
    crypto: { randomUUID: () => 'b'.repeat(32) },
    document: {
      body: { dataset, classList: { add() {} } },
      getElementById: element,
      createElement: () => ({}),
      head: { appendChild: script => script.onload() }
    },
    setTimeout() {}, clearTimeout() {}, setInterval() {}, clearInterval() {}
  });
  const network = window.FlappyNetwork;
  network.install((host, quickMatch) => starts.push({ host, quickMatch }), protocol);
  element('room').value = room;
  element('join-form').listeners.get('submit')({ preventDefault() {} });
  await Promise.resolve(); // Resolve the mocked library load, without a timing delay.
  peers[0].emit('open');
  candidate.emit('data', JSON.stringify({ type: 'match-start', protocol: startProtocol }));
  assert.deepEqual(starts, startProtocol === protocol ? [{ host: false, quickMatch: false }] : []);
  assert.equal(dataset.connection, startProtocol === protocol ? 'open' : 'closed');
  assert.equal(dataset.game, undefined, 'match-start must only load the board');
  return { network, candidate, dataset, readyLabels, starts };
}

for (const label of ['1+5', 'UNTIMED', '60+60', '5+0']) {
  test(`delayed ${label} snapshot publishes its label before ready, without another frame`, async () => {
    const { network, candidate, dataset, readyLabels } = await inviteGuest();
    // A guest frame has displayed the temporary default while awaiting the host.
    dataset.timeControl = '5+0';
    network.renderStatus('Waiting', false, false, 'BLACK', '5+0');
    assert.equal(dataset.game, undefined);
    // PeerGame calls status immediately after PeerMatch restores the first state.
    network.bind(() => network.renderStatus('Playing', false, true, 'BLACK', label), () => {}, () => {});
    candidate.emit('data', JSON.stringify({ type: 'state' }));
    assert.equal(dataset.game, 'ready');
    assert.equal(dataset.timeControl, label);
    assert.deepEqual(readyLabels, [label], 'readiness must never expose the placeholder label');
  });

  test(`queued ${label} snapshot publishes settings during bind before the first frame`, async () => {
    const { network, candidate, dataset, readyLabels } = await inviteGuest();
    candidate.emit('data', JSON.stringify({ type: 'state' }));
    assert.equal(dataset.game, undefined);
    network.bind(() => network.renderStatus('Playing', false, true, 'BLACK', label), () => {}, () => {});
    assert.equal(dataset.timeControl, label);
    assert.deepEqual(readyLabels, [label]);
  });
}

test('incompatible invite start never launches a game', async () => {
  const { network, dataset, readyLabels } = await inviteGuest(protocol - 1);
  network.renderStatus('Playing', false, true, 'BLACK', '1+5');
  assert.equal(dataset.connection, 'closed');
  assert.equal(dataset.game, undefined);
  assert.deepEqual(readyLabels, []);
});

test('repeated match-start cannot launch a second board or mark the guest ready', async () => {
  const { candidate, dataset, starts } = await inviteGuest();
  candidate.emit('data', JSON.stringify({ type: 'match-start', protocol }));
  assert.deepEqual(starts, [{ host: false, quickMatch: false }]);
  assert.equal(dataset.game, undefined);
});
