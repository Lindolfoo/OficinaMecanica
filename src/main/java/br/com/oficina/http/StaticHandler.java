package br.com.oficina.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Entrega os arquivos de src/main/resources/static (index.html, css, js) a partir do classpath. */
public final class StaticHandler implements HttpHandler {

    private static final Map<String, String> TIPOS = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "application/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("map", "application/json"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"));

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            ex.close();
            return;
        }

        String caminho = ex.getRequestURI().getPath();
        if (caminho.equals("/") || caminho.isEmpty()) {
            caminho = "/index.html";
        }

        try (InputStream in = StaticHandler.class.getResourceAsStream("/static" + caminho)) {
            if (in == null) {
                enviar(ex, 404, "text/plain; charset=utf-8",
                        ("404 - não encontrado: " + caminho).getBytes(StandardCharsets.UTF_8));
                return;
            }
            String ext = caminho.substring(caminho.lastIndexOf('.') + 1).toLowerCase();
            enviar(ex, 200, TIPOS.getOrDefault(ext, "application/octet-stream"), in.readAllBytes());
        }
    }

    private static void enviar(HttpExchange ex, int status, String tipo, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", tipo);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
