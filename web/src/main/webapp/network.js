(() => {
  'use strict';
  const PROTOCOL = 5;
  const QUEUE_SLOTS = 8;
  const SLOT_CONNECT_TIMEOUT = 2500;
  const SLOT_CONFIRM_TIMEOUT = 2500;
  const SLOT_HOST_FRONT_WAIT = 10000;
  const SLOT_HOST_BACK_WAIT = 1400;
  const DEFAULT_SEARCH_TIMEOUT = 60000;
  let startGame, receive, closed, pulse;
  let peer, connection, timer, connectTimeout, started = false, ended = false;
  let searching = false, searchId = '', searchTimer, slotTimer, confirmTimer, activeCandidate = null;
  const staleSlots = new Map();
  const pending = [];
  const byId = id => document.getElementById(id);
  const status = text => { byId('status').textContent = text; };
  const controls = disabled => {
    for (const id of ['play', 'tutorial', 'solo', 'matchmake', 'host', 'join']) {
      const element = byId(id);
      if (element) element.disabled = disabled;
    }
    byId('match-settings').disabled = disabled;
  };
  const roomFrom = value => {
    value = value.trim();
    if (/^flappychess-[a-f0-9]{32}$/.test(value)) return value;
    try { return new URLSearchParams(new URL(value, location.href).hash.slice(1)).get('room') || ''; }
    catch { return ''; }
  };
  const queueSlot = index => `flappychess-queue-v${PROTOCOL}-${index}`;
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
  function cleanupSearchPeer() {
    clearTimeout(slotTimer);
    clearTimeout(confirmTimer);
    activeCandidate = null;
    if (peer) {
      const old = peer;
      peer = null;
      try { old.destroy(); } catch {}
    }
  }
  function stopSearch(message) {
    if (!searching || started || ended) return;
    searching = false;
    clearTimeout(searchTimer);
    cleanupSearchPeer();
    byId('search-room').hidden = true;
    byId('cancel-search').disabled = false;
    delete document.body.dataset.roomState;
    controls(false);
    status(message);
  }
  function fail(reason) {
    if (ended) return;
    ended = true;
    searching = false;
    clearTimeout(connectTimeout);
    clearTimeout(searchTimer);
    clearTimeout(slotTimer);
    clearTimeout(confirmTimer);
    clearInterval(timer);
    closed?.(reason);
    peer?.destroy();
    byId('failure-message').textContent = reason;
    byId('connection-failure').hidden = false;
    byId('return-lobby').focus();
    document.body.dataset.connection = 'closed';
  }
  function handleMatchData(data) {
    if (ended) return;
    if (typeof data !== 'string' || data.length > 262144) {
      fail('The other player sent incompatible match data.');
      return;
    }
    try {
      const packet = JSON.parse(data);
      if (packet.type === 'busy') { fail(packet.reason || 'This room already has two players.'); return; }
      if (packet.type === 'match-offer' || packet.type === 'match-accept' || packet.type === 'match-start') return;
    } catch { /* The portable protocol decoder reports malformed packets. */ }
    if (receive) receive(data);
    else if (pending.length < 128) pending.push(data);
    else fail('The match could not synchronize. Please try another room.');
  }
  function finalizeMatch(candidate, host, quickMatch = false) {
    if (ended || started) return;
    clearTimeout(connectTimeout);
    clearTimeout(slotTimer);
    clearTimeout(confirmTimer);
    clearTimeout(searchTimer);
    searching = false;
    activeCandidate = null;
    connection = candidate;
    started = true;
    byId('share-room').hidden = true;
    byId('search-room').hidden = true;
    delete document.body.dataset.roomState;
    document.body.classList.add('online');
    document.body.dataset.mode = 'online';
    document.body.dataset.side = host ? 'WHITE' : 'BLACK';
    document.body.dataset.connection = 'open';
    byId('network-bar').hidden = false;
    status('Preparing the online board...');
    startGame(host, quickMatch);
  }
  function attach(candidate, host) {
    connection = candidate;
    candidate.on('data', handleMatchData);
    candidate.on('open', () => {
      if (ended) { candidate.close(); return; }
      finalizeMatch(candidate, host, false);
    });
    candidate.on('close', () => fail('Your opponent left or the connection was lost. Return to the lobby to play again.'));
    candidate.on('error', () => fail('The peer connection failed. Ask your friend to try another room.'));
  }
  function nextSlotIndex(fromIndex) {
    const now = Date.now();
    for (let offset = 0; offset < QUEUE_SLOTS; offset++) {
      const idx = (fromIndex + offset) % QUEUE_SLOTS;
      if ((staleSlots.get(idx) || 0) <= now) return idx;
    }
    staleSlots.clear();
    return fromIndex % QUEUE_SLOTS;
  }
  function firstNonStaleSlot() {
    return nextSlotIndex(0);
  }
  function tryClaimSlot(slotIndex, sameSlotRetries = 0) {
    if (!searching || started || ended) return;
    cleanupSearchPeer();
    const slotId = queueSlot(slotIndex);
    const currentPeer = new Peer(slotId, { debug: 0, ...window.FLAPPY_PEER_OPTIONS });
    peer = currentPeer;
    const pendingCandidates = new Set();
    slotTimer = setTimeout(() => {
      if (peer === currentPeer && searching && !started) {
        fail('Could not reach the room service. Please try again shortly.');
      }
    }, 15000);

    const scheduleHostWait = () => {
      clearTimeout(slotTimer);
      const front = firstNonStaleSlot();
      const waitMs = slotIndex === front
        ? SLOT_HOST_FRONT_WAIT + Math.floor(Math.random() * 2000)
        : SLOT_HOST_BACK_WAIT + Math.floor(Math.random() * 800);
      slotTimer = setTimeout(() => {
        if (peer !== currentPeer || !searching || started || ended) return;
        if (activeCandidate) {
          scheduleHostWait();
          return;
        }
        const currentFront = firstNonStaleSlot();
        if (slotIndex === currentFront && !currentPeer.disconnected && !currentPeer.destroyed) {
          scheduleHostWait();
          return;
        }
        tryClaimSlot(currentFront, 0);
      }, waitMs);
    };

    currentPeer.on('open', () => {
      if (peer !== currentPeer || !searching || started || ended) return;
      status('Searching for an online opponent...');
      scheduleHostWait();
    });

    currentPeer.on('connection', candidate => {
      if (peer !== currentPeer || ended) {
        candidate.on('open', () => candidate.close());
        return;
      }
      if (candidate.metadata?.searchId && candidate.metadata.searchId === searchId) {
        candidate.on('open', () => candidate.close());
        return;
      }
      const compatible = candidate.metadata?.app === 'flappy-chess' && candidate.metadata?.protocol === PROTOCOL;
      if (!compatible || started || !searching) {
        candidate.on('open', () => {
          try {
            candidate.send(JSON.stringify({ type: 'busy', reason: compatible
              ? 'This room already has two players.' : 'Game versions differ. Both players should reload.' }));
          } catch {}
          setTimeout(() => candidate.close(), 200);
        });
        return;
      }
      pendingCandidates.add(candidate);
      const candidateTimeout = setTimeout(() => {
        if (!started && activeCandidate !== candidate) {
          pendingCandidates.delete(candidate);
          try { candidate.close(); } catch {}
        }
      }, SLOT_CONNECT_TIMEOUT);

      candidate.on('data', data => {
        if (ended) return;
        if (started && connection === candidate) {
          handleMatchData(data);
          return;
        }
        if (peer !== currentPeer || !searching || started || activeCandidate !== candidate) return;
        if (typeof data !== 'string' || data.length > 262144) return;
        try {
          const packet = JSON.parse(data);
          if (packet.type === 'match-accept' && packet.protocol === PROTOCOL) {
            clearTimeout(confirmTimer);
            clearTimeout(slotTimer);
            clearTimeout(searchTimer);
            searching = false;
            byId('cancel-search').disabled = true;
            for (const other of pendingCandidates) {
              if (other !== candidate) {
                try {
                  if (other.open) other.send(JSON.stringify({ type: 'busy', reason: 'This room already has two players.' }));
                  other.close();
                } catch {}
              }
            }
            pendingCandidates.clear();
            candidate.send(JSON.stringify({ type: 'match-start', protocol: PROTOCOL }));
            // Free the queue slot on PeerServer while keeping the active WebRTC data channel open.
            try { currentPeer.disconnect(); } catch {}
            finalizeMatch(candidate, true, true);
          }
        } catch {}
      });

      candidate.on('open', () => {
        clearTimeout(candidateTimeout);
        if (peer !== currentPeer || ended) { candidate.close(); return; }
        if (!searching || started || activeCandidate && activeCandidate !== candidate) {
          pendingCandidates.delete(candidate);
          try {
            candidate.send(JSON.stringify({ type: 'busy', reason: 'This room already has two players.' }));
          } catch {}
          setTimeout(() => candidate.close(), 200);
          return;
        }
        activeCandidate = candidate;
        clearTimeout(confirmTimer);
        confirmTimer = setTimeout(() => {
          if (activeCandidate === candidate && !started) {
            activeCandidate = null;
            pendingCandidates.delete(candidate);
            try { candidate.close(); } catch {}
          }
        }, SLOT_CONFIRM_TIMEOUT);
        try {
          candidate.send(JSON.stringify({ type: 'match-offer', protocol: PROTOCOL }));
        } catch {
          activeCandidate = null;
          pendingCandidates.delete(candidate);
          clearTimeout(confirmTimer);
          try { candidate.close(); } catch {}
        }
      });

      candidate.on('close', () => {
        clearTimeout(candidateTimeout);
        pendingCandidates.delete(candidate);
        if (started && connection === candidate) {
          fail('Your opponent left or the connection was lost. Return to the lobby to play again.');
          return;
        }
        if (activeCandidate === candidate) {
          clearTimeout(confirmTimer);
          activeCandidate = null;
        }
      });

      candidate.on('error', () => {
        clearTimeout(candidateTimeout);
        pendingCandidates.delete(candidate);
        if (started && connection === candidate) {
          fail('The peer connection failed. Return to the lobby to play again.');
          return;
        }
        if (activeCandidate === candidate) {
          clearTimeout(confirmTimer);
          activeCandidate = null;
        }
      });
    });

    currentPeer.on('error', error => {
      if (peer !== currentPeer || ended) return;
      if (started && connection?.open) return;
      if (searching && error.type === 'unavailable-id') {
        clearTimeout(slotTimer);
        tryJoinSlot(slotIndex, sameSlotRetries);
        return;
      }
      if (searching && ['network', 'server-error', 'socket-error', 'socket-closed', 'disconnected'].includes(error.type)) {
        clearTimeout(slotTimer);
        slotTimer = setTimeout(() => {
          if (peer === currentPeer && searching && !started) tryClaimSlot(firstNonStaleSlot(), 0);
        }, 500);
        return;
      }
      fail('Could not reach the room service. Please try again shortly.');
    });

    currentPeer.on('disconnected', () => {
      if (peer !== currentPeer || ended || started || !searching) return;
      setTimeout(() => {
        if (peer === currentPeer && searching && !started && currentPeer.disconnected && !currentPeer.destroyed) {
          try { currentPeer.reconnect(); } catch { tryClaimSlot(firstNonStaleSlot(), 0); }
        }
      }, 500);
    });
  }
  function tryJoinSlot(slotIndex, sameSlotRetries = 0) {
    if (!searching || started || ended) return;
    cleanupSearchPeer();
    const slotId = queueSlot(slotIndex);
    const guestId = 'flappychess-' + crypto.randomUUID().replaceAll('-', '');
    const currentPeer = new Peer(guestId, { debug: 0, ...window.FLAPPY_PEER_OPTIONS });
    peer = currentPeer;
    let candidate = null;
    let offered = false;

    const advanceSlot = markStale => {
      if (peer !== currentPeer || !searching || started || ended) return;
      if (markStale) staleSlots.set(slotIndex, Date.now() + 15000);
      const next = nextSlotIndex((slotIndex + 1) % QUEUE_SLOTS);
      tryClaimSlot(next, 0);
    };

    slotTimer = setTimeout(() => advanceSlot(true), SLOT_CONNECT_TIMEOUT);

    currentPeer.on('open', () => {
      if (peer !== currentPeer || !searching || started || ended) return;
      candidate = currentPeer.connect(slotId, {
        reliable: true,
        serialization: 'raw',
        metadata: { app: 'flappy-chess', protocol: PROTOCOL, searchId }
      });

      candidate.on('data', data => {
        if (ended) return;
        if (started && connection === candidate) {
          handleMatchData(data);
          return;
        }
        if (peer !== currentPeer || !searching || started) return;
        if (typeof data !== 'string' || data.length > 262144) return;
        try {
          const packet = JSON.parse(data);
          if (packet.type === 'busy') {
            if (/versions differ/i.test(packet.reason || '')) {
              fail(packet.reason);
              return;
            }
            advanceSlot(false);
            return;
          }
          if (packet.type === 'match-offer' && packet.protocol === PROTOCOL && !offered) {
            offered = true;
            byId('cancel-search').disabled = true;
            clearTimeout(slotTimer);
            slotTimer = setTimeout(() => {
              if (peer === currentPeer && searching && !started) {
                byId('cancel-search').disabled = false;
                advanceSlot(false);
              }
            }, SLOT_CONFIRM_TIMEOUT);
            candidate.send(JSON.stringify({ type: 'match-accept', protocol: PROTOCOL }));
            return;
          }
          if (packet.type === 'match-start' && packet.protocol === PROTOCOL) {
            clearTimeout(slotTimer);
            clearTimeout(searchTimer);
            searching = false;
            byId('cancel-search').disabled = true;
            try { currentPeer.disconnect(); } catch {}
            finalizeMatch(candidate, false, true);
          }
        } catch {}
      });

      candidate.on('close', () => {
        if (started && connection === candidate) {
          fail('Your opponent left or the connection was lost. Return to the lobby to play again.');
          return;
        }
        if (peer === currentPeer && searching && !started) {
          byId('cancel-search').disabled = false;
          advanceSlot(false);
        }
      });

      candidate.on('error', () => {
        if (started && connection === candidate) {
          fail('The peer connection failed. Return to the lobby to play again.');
          return;
        }
        if (peer === currentPeer && searching && !started) {
          byId('cancel-search').disabled = false;
          advanceSlot(true);
        }
      });
    });

    currentPeer.on('error', error => {
      if (peer !== currentPeer || ended) return;
      if (started && connection?.open) return;
      if (searching && error.type === 'peer-unavailable') {
        clearTimeout(slotTimer);
        if (sameSlotRetries < 2) tryClaimSlot(slotIndex, sameSlotRetries + 1);
        else advanceSlot(false);
        return;
      }
      if (searching) {
        advanceSlot(true);
        return;
      }
      fail('Could not reach the room service. Please try again shortly.');
    });
  }
  async function findMatch() {
    if (started || peer || searching || ended) return;
    searching = true;
    searchId = crypto.randomUUID();
    staleSlots.clear();
    controls(true);
    byId('cancel-search').disabled = false;
    byId('search-room').hidden = false;
    document.body.dataset.roomState = 'searching';
    status('Searching for an online opponent...');
    try {
      await loadPeer();
      if (!searching || started || ended) return;
      if (!window.RTCPeerConnection) throw new Error('This browser does not support peer-to-peer play. Try a current browser.');
      const timeoutMs = window.FLAPPY_PEER_OPTIONS?.matchTimeoutMillis || DEFAULT_SEARCH_TIMEOUT;
      searchTimer = setTimeout(() => {
        stopSearch('No online opponent found right now. Try Find Random Match again or share a room link.');
      }, timeoutMs);
      tryClaimSlot(0, 0);
    } catch (error) { fail(error.message); }
  }
  async function connect(host, room) {
    if (started || peer || searching || ended) return;
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
      byId('matchmake').disabled = false;
      byId('host').disabled = false;
      byId('join').disabled = false;
      byId('matchmake').addEventListener('click', () => findMatch());
      byId('cancel-search').addEventListener('click', () => this.cancelSearch());
      byId('host').addEventListener('click', () => connect(true));
      byId('join-form').addEventListener('submit', event => {
        event.preventDefault();
        const room = roomFrom(byId('room').value);
        if (!/^flappychess-[a-f0-9]{32}$/.test(room)) { status('Paste a Flappy Chess room link or room code.'); return; }
        connect(false, room);
      });
      const applyInviteHash = () => {
        const invited = new URLSearchParams(location.hash.slice(1)).get('room');
        if (invited) {
          byId('room').value = invited;
          byId('join').textContent = 'JOIN FRIEND';
        }
      };
      applyInviteHash();
      window.addEventListener('hashchange', applyInviteHash);
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
    renderStatus(text, host, playing, side) {
      if (ended) return;
      byId('player-role').textContent = (host ? 'P1 · HOST · ' : 'P2 · GUEST · ') + side;
      document.body.dataset.side = side;
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
    findMatch,
    cancelSearch() {
      stopSearch('Search cancelled. Try Flight School, the computer, or a friend.');
    },
    fail,
    leave() {
      ended = true;
      searching = false;
      clearInterval(timer);
      clearTimeout(connectTimeout);
      clearTimeout(searchTimer);
      clearTimeout(slotTimer);
      clearTimeout(confirmTimer);
      peer?.destroy();
      const destination = new URL(location.href);
      destination.hash = '';
      history.replaceState(null, '', destination.href);
      location.reload();
    }
  };
})();
