package br.com.oficina.http;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilitários para ler requisições e escrever respostas com o HttpServer do JDK. */
public final class HttpUtil {

    private HttpUtil() {
    }

    // ------------------------------------------------------------------ respostas

    public static void sendJson(HttpExchange ex, int status, Object corpo) throws IOException {
        byte[] bytes = Json.write(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static void sendNoContent(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(204, -1);
        ex.close();
    }

    // ------------------------------------------------------------------ entrada

    /** Lê o corpo application/x-www-form-urlencoded (o que o jQuery envia por padrão). */
    public static Map<String, String> readForm(HttpExchange ex) throws IOException {
        String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseUrlEncoded(corpo);
    }

    /**
     * Lê o corpo do formulário preservando chaves repetidas.
     * Necessário para receber vários itens de uma OS de uma só vez
     * (servicoId=1&quantidade=2&servicoId=4&quantidade=1).
     */
    public static Map<String, List<String>> readFormMulti(HttpExchange ex) throws IOException {
        String corpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, List<String>> mapa = new LinkedHashMap<>();
        if (corpo.isBlank()) {
            return mapa;
        }
        for (String par : corpo.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int i = par.indexOf('=');
            String chave = URLDecoder.decode(i < 0 ? par : par.substring(0, i), StandardCharsets.UTF_8);
            String valor = i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
            mapa.computeIfAbsent(chave, k -> new ArrayList<>()).add(valor.trim());
        }
        return mapa;
    }

    /** Query string (?busca=ana&status=ABERTA) como mapa. */
    public static Map<String, String> query(HttpExchange ex) {
        return parseUrlEncoded(ex.getRequestURI().getRawQuery());
    }

    /**
     * Segmentos do caminho depois do contexto do handler.
     * Contexto "/api/ordens" e URL "/api/ordens/5/itens/2" -> ["5", "itens", "2"].
     */
    public static List<String> pathParts(HttpExchange ex) {
        String contexto = ex.getHttpContext().getPath();
        String caminho = ex.getRequestURI().getPath();
        String resto = caminho.startsWith(contexto) ? caminho.substring(contexto.length()) : caminho;
        List<String> partes = new ArrayList<>();
        for (String p : resto.split("/")) {
            if (!p.isBlank()) {
                partes.add(p);
            }
        }
        return partes;
    }

    /** ID numérico logo após o contexto (/api/clientes/12 -> 12) ou null se não houver. */
    public static Long pathId(HttpExchange ex) {
        List<String> partes = pathParts(ex);
        return partes.isEmpty() ? null : parseId(partes.get(0));
    }

    public static long parseId(String texto) {
        try {
            long id = Long.parseLong(texto);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException(400, "ID inválido: " + texto);
        }
    }

    private static Map<String, String> parseUrlEncoded(String texto) {
        Map<String, String> mapa = new HashMap<>();
        if (texto == null || texto.isBlank()) {
            return mapa;
        }
        for (String par : texto.split("&")) {
            if (par.isEmpty()) {
                continue;
            }
            int i = par.indexOf('=');
            String chave = URLDecoder.decode(i < 0 ? par : par.substring(0, i), StandardCharsets.UTF_8);
            String valor = i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);
            mapa.put(chave, valor.trim());
        }
        return mapa;
    }
}
