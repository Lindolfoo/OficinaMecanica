package br.com.oficina.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Classe base dos handlers da API. Roteia pelo método HTTP e converte exceções
 * em respostas JSON com o status adequado, para que os handlers concretos
 * cuidem apenas de validação + DAO.
 */
public abstract class BaseHandler implements HttpHandler {

    @Override
    public final void handle(HttpExchange ex) throws IOException {
        try {
            switch (ex.getRequestMethod()) {
                case "GET" -> get(ex);
                case "POST" -> post(ex);
                case "PUT" -> put(ex);
                case "DELETE" -> delete(ex);
                default -> throw new ApiException(405, "Método não permitido");
            }
        } catch (ApiException e) {
            HttpUtil.sendJson(ex, e.status(), Json.obj("erro", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            HttpUtil.sendJson(ex, 500, Json.obj("erro", "Erro interno inesperado"));
        }
    }

    protected void get(HttpExchange ex) throws Exception {
        throw new ApiException(405, "Método não permitido");
    }

    protected void post(HttpExchange ex) throws Exception {
        throw new ApiException(405, "Método não permitido");
    }

    protected void put(HttpExchange ex) throws Exception {
        throw new ApiException(405, "Método não permitido");
    }

    protected void delete(HttpExchange ex) throws Exception {
        throw new ApiException(405, "Método não permitido");
    }
}
