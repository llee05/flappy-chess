package com.example.flappychess.web;

import com.example.flappychess.controller.UciProcess;
import java.util.function.Consumer;
import org.teavm.jso.core.JSString;
import org.teavm.jso.workers.Worker;

/** Runs the separately distributed single-threaded Stockfish build in a Web Worker. */
final class BrowserStockfishProcess implements UciProcess {
    private final Worker worker;
    private Consumer<String> output = ignored -> { };
    private Consumer<String> failure = ignored -> { };

    BrowserStockfishProcess(String url) {
        worker = new Worker(url);
        worker.onMessage(event -> output.accept(event.getDataAsString()));
        worker.addEventListener("error", event -> failure.accept("Stockfish worker failed to load"));
        worker.addEventListener("messageerror", event -> failure.accept("Stockfish worker returned an unreadable message"));
    }

    @Override public void listen(Consumer<String> output, Consumer<String> failure) {
        this.output = output;
        this.failure = failure;
    }

    @Override public void send(String command) { worker.postMessage(JSString.valueOf(command)); }
    @Override public void close() { worker.terminate(); }
}
