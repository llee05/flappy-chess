package com.example.flappychess.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** The only bridge between the portable match controller and browser WebRTC. */
final class PeerNetwork {
    @JSFunctor interface Start extends JSObject { void run(boolean host); }
    @JSFunctor interface Message extends JSObject { void run(String text); }
    @JSFunctor interface Tick extends JSObject { void run(); }

    @JSBody(params = {"start"}, script = "window.FlappyNetwork.install(start);")
    static native void install(Start start);

    @JSBody(params = {"receive", "closed", "pulse"}, script = "window.FlappyNetwork.bind(receive, closed, pulse);")
    static native void bind(Message receive, Message closed, Tick pulse);

    @JSBody(params = {"text"}, script = "window.FlappyNetwork.send(text);")
    static native void send(String text);

    @JSBody(params = {"status", "host", "started", "side"}, script = "window.FlappyNetwork.renderStatus(status, host, started, side);")
    static native void status(String status, boolean host, boolean started, String side);

    @JSBody(params = {"turn", "lastMove", "moves", "carrier", "promotion", "result"},
            script = "window.FlappyNetwork.describeMatch(turn, lastMove, moves, carrier, promotion, result);")
    static native void describe(String turn, String lastMove, int moves, String carrier, boolean promotion, String result);

    @JSBody(params = {"reason"}, script = "window.FlappyNetwork.fail(reason);")
    static native void fail(String reason);
}
