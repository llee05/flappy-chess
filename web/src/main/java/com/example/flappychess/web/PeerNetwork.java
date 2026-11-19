package com.example.flappychess.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The only bridge between the portable match controller and browser WebRTC. */
final class PeerNetwork {
    @JSFunctor interface Start extends JSObject { void run(boolean host, boolean quickMatch); }
    @JSFunctor interface Message extends JSObject { void run(String text); }
    @JSFunctor interface Tick extends JSObject { void run(); }

    @JSBody(params = {"start", "protocolVersion"}, script = "window.FlappyNetwork.install(start, protocolVersion);")
    static native void install(Start start, int protocolVersion);

    @JSBody(params = {"receive", "closed", "pulse"}, script = "window.FlappyNetwork.bind(receive, closed, pulse);")
    static native void bind(Message receive, Message closed, Tick pulse);

    @JSBody(params = {"text"}, script = "window.FlappyNetwork.send(text);")
    static native void send(String text);

    @JSBody(params = {"status", "host", "started", "side"}, script = "window.FlappyNetwork.renderStatus(status, host, started, side);")
    static native void status(String status, boolean host, boolean started, String side);

    @JSBody(params = {"label", "quality", "rttMillis", "ageMillis"},
            script = "window.FlappyNetwork.renderHealth(label, quality, rttMillis, ageMillis);")
    static native void health(String label, String quality, int rttMillis, int ageMillis);

    @JSBody(params = {"turn", "lastMove", "moves", "carrier", "promotion", "result"},
            script = "window.FlappyNetwork.describeMatch(turn, lastMove, moves, carrier, promotion, result);")
    static native void describe(String turn, String lastMove, int moves, String carrier, boolean promotion, String result);

    @JSBody(params = {"text"}, script = "window.FlappyNetwork.interruption(text);")
    static native void interruption(String text);

    @JSBody(params = {"reason"}, script = "window.FlappyNetwork.fail(reason);")
    static native void fail(String reason);
}
