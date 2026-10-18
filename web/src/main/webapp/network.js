(() => {
  'use strict';
  const PROTOCOL = 3;
  let startGame, receive, closed, pulse;
  let peer, connection, timer, connectTimeout, started = false, ended = false;
  const pending = [];
  const byId = id => document.getElementById(id);
  const status = text => { byId('status').textContent = text; };
  const controls = disabled => {
    for (const id of ['play', 'tutorial', 'host', 'join']) byId(id).disabled = disabled;
    byId('match-settings').disabled = disabled;
  };
  const roomFrom = value => {
    value = value.trim();
    if (/^flappychess-[a-f0-9]{32}$/.test(value)) return value;
    try { return new URLSearchParams(new URL(value, location.href).hash.slice(1)).get('room') || ''; }
    catch { return ''; }
  };
  let library;
  function loadPeer() {
    if (!library) library = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = './vendor/peerjs-1.5.5.min.js';
      script.onload = resolve;
      script.onerror = () => reject(new Error('The connection library could not load. Refresh to retry.'));
      document.head.appendChild(script);
    });
    return library;
  }
  function fail(reason) {
    if (ended) return;
    ended = true;
    clearTimeout(connectTimeout);
    clearInterval(timer);
    closed?.(reason);
    peer?.destroy();
    byId('failure-message').textContent = reason;
    byId('connection-failure').hidden = false;
    byId('return-lobby').focus();
    document.body.dataset.connection = 'closed';
  }
  function attach(candidate, host) {
    connection = candidate;
    candidate.on('data', data => {
      if (ended) return;
      if (typeof data !== 'string' || data.length > 65536) {
        fail('The other player sent incompatible match data.');
        return;
      }
      try {
        const packet = JSON.parse(data);
        if (packet.type === 'busy') { fail(packet.reason || 'This room already has two players.'); return; }
      } catch { /* The portable protocol decoder reports malformed packets. */ }
      if (receive) receive(data);
      else if (pending.length < 128) pending.push(data);
      else fail('The match could not synchronize. Please try another room.');
    });
    candidate.on('open', () => {
      if (ended) { candidate.close(); return; }
      clearTimeout(connectTimeout);
      started = true;
      document.body.classList.add('online');
      document.body.dataset.mode = 'online';
      document.body.dataset.side = host ? 'WHITE' : 'BLACK';
      document.body.dataset.connection = 'open';
      byId('network-bar').hidden = false;
      status('Preparing the online board...');
      startGame(host);
    });
    candidate.on('close', () => fail('Your opponent left or the connection was lost. Return to the lobby to play again.'));
    candidate.on('error', () => fail('The peer connection failed. Ask your friend to try another room.'));
  }
  async function connect(host, room) {
    if (started || peer || ended) return;
    controls(true);
    status(host ? 'Creating your room...' : 'Connecting to your friend...');
    try {
      await loadPeer();
      if (!window.RTCPeerConnection) throw new Error('This browser does not support peer-to-peer play. Try a current browser.');
      const id = 'flappychess-' + crypto.randomUUID().replaceAll('-', '');
      // PeerJS defaults provide hosted signaling and ICE servers. A site owner can override them.
      peer = new Peer(id, { debug: 0, ...window.FLAPPY_PEER_OPTIONS });
      connectTimeout = setTimeout(() => fail('The room could not connect. Check that your friend has the room open and try again.'), 30000);
      peer.on('open', () => {
        if (ended || started) return;
        if (host) {
          clearTimeout(connectTimeout);
          const invite = new URL(location.href);
          invite.hash = new URLSearchParams({ room: id }).toString();
          byId('invite-link').value = invite.href;
          byId('share-room').hidden = false;
          document.body.dataset.roomState = 'waiting';
          status('You are White. Share the room link and wait for your friend.');
        } else {
          attach(peer.connect(room, { reliable: true, serialization: 'raw',
            metadata: { app: 'flappy-chess', protocol: PROTOCOL } }), false);
        }
      });
      peer.on('connection', candidate => {
        const compatible = candidate.metadata?.app === 'flappy-chess' && candidate.metadata?.protocol === PROTOCOL;
        if (!host || connection || !compatible) {
          candidate.on('open', () => {
            candidate.send(JSON.stringify({ type: 'busy', reason: compatible
              ? 'This room already has two players.' : 'Game versions differ. Both players should reload.' }));
            setTimeout(() => candidate.close(), 200);
          });
          return;
        }
        attach(candidate, true);
        connectTimeout = setTimeout(() => fail('Your friend could not finish connecting. Please create another room.'), 30000);
      });
      peer.on('error', error => {
        // Signaling may reconnect independently after the data channel is established.
        if (connection?.open && ['network', 'server-error', 'socket-error', 'socket-closed'].includes(error.type)) return;
        fail(error.type === 'peer-unavailable'
          ? 'Room not found. Ask your friend to create a room and share its link.'
          : 'Could not reach the room service. Please try again shortly.');
      });
      peer.on('disconnected', () => {
        if (!ended && peer && !peer.destroyed) {
          setTimeout(() => { if (!ended && peer.disconnected) peer.reconnect(); }, 1000);
        }
      });
    } catch (error) { fail(error.message); }
  }
  window.FlappyNetwork = {
    install(start) {
      startGame = start;
      byId('host').disabled = false;
      byId('join').disabled = false;
      byId('host').addEventListener('click', () => connect(true));
      byId('join-form').addEventListener('submit', event => {
        event.preventDefault();
        const room = roomFrom(byId('room').value);
        if (!/^flappychess-[a-f0-9]{32}$/.test(room)) { status('Paste a Flappy Chess room link or room code.'); return; }
        connect(false, room);
      });
      const invited = new URLSearchParams(location.hash.slice(1)).get('room');
      if (invited) {
        byId('room').value = invited;
        byId('join').textContent = 'JOIN FRIEND';
      }
      byId('copy-room').addEventListener('click', async () => {
        const input = byId('invite-link');
        try { await navigator.clipboard.writeText(input.value); status('Room link copied. Send it to your friend.'); }
        catch { input.select(); status('Select and copy the room link above.'); }
      });
      for (const id of ['cancel-room', 'leave-room', 'return-lobby']) byId(id).addEventListener('click', () => this.leave());
    },
    bind(onMessage, onClosed, onPulse) {
      receive = onMessage;
      closed = onClosed;
      pulse = onPulse;
      if (ended) { closed('The connection ended while loading. Return to the lobby to retry.'); return; }
      while (pending.length) receive(pending.shift());
      timer = setInterval(() => pulse(), 250);
    },
    send(text) {
      if (!connection?.open || ended) return;
      const buffered = connection.dataChannel?.bufferedAmount || 0;
      if (buffered > 1048576) { fail('The connection is too slow to keep the match synchronized.'); return; }
      if (buffered > 262144 && JSON.parse(text).type === 'state') return;
      try { connection.send(text); }
      catch { fail('The peer connection closed. Return to the lobby to play again.'); }
    },
    renderStatus(text, host, playing) {
      if (ended) return;
      byId('player-role').textContent = host ? 'WHITE · HOST' : 'BLACK · GUEST';
      if (byId('network-status').textContent !== text) byId('network-status').textContent = text;
      if (playing) {
        byId('welcome').hidden = true;
        if (document.body.dataset.game !== 'ready') {
          document.body.dataset.game = 'ready';
          byId('canvas').focus();
        }
      }
    },
    describeMatch(turn, lastMove, moves, carrier, promotion, result) {
      const summary = byId('match-summary');
      const text = result || `${turn} to move. ${moves} moves played.`
        + (lastMove ? ` Last move: ${lastMove}.` : '')
        + (carrier ? ` ${carrier} is carrying a piece.` : '')
        + (promotion ? ' Choose promotion: Q, R, B or N.' : '');
      if (summary.textContent !== text) summary.textContent = text;
      summary.dataset.turn = turn;
      summary.dataset.lastMove = lastMove;
      summary.dataset.moves = moves;
      summary.dataset.carrier = carrier;
    },
    fail,
    leave() {
      ended = true;
      clearInterval(timer);
      clearTimeout(connectTimeout);
      peer?.destroy();
      const destination = new URL(location.href);
      destination.hash = '';
      history.replaceState(null, '', destination.href);
      location.reload();
    }
  };
})();
