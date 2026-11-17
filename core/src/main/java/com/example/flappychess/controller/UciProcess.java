package com.example.flappychess.controller;

import java.util.function.Consumer;

/** A separate UCI process, such as the browser's Stockfish Web Worker. */
public interface UciProcess extends AutoCloseable {
    void listen(Consumer<String> output, Consumer<String> failure);
    void send(String command);
    @Override void close();
}
