package com.example.flappychess.web;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Local preview server using only the JDK; the deployment itself is entirely static. */
public final class ServeWeb {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        int port = Integer.parseInt(args[1]);
        Map<String, String> types = Map.of(
                "html", "text/html; charset=utf-8", "js", "text/javascript; charset=utf-8",
                "css", "text/css; charset=utf-8", "wasm", "application/wasm", "png", "image/png");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                String method = exchange.getRequestMethod();
                boolean head = method.equals("HEAD");
                if (!method.equals("GET") && !head) {
                    exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String request = exchange.getRequestURI().getPath();
                Path file = root.resolve("." + (request.endsWith("/") ? request + "index.html" : request)).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                String name = file.getFileName().toString();
                String extension = name.substring(name.lastIndexOf('.') + 1);
                exchange.getResponseHeaders().set("Content-Type", types.getOrDefault(extension, "application/octet-stream"));
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, head ? -1 : Files.size(file));
                if (!head) Files.copy(file, exchange.getResponseBody());
            }
        });
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.println("Flappy Chess: http://localhost:" + port + "/ (Ctrl+C to stop)");
    }
}
