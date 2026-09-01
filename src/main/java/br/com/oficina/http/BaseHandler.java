package br.com.oficina.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

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
        } catch (SQLIntegrityConstraintViolationException e) {
            // UNIQUE / FOREIGN KEY: a regra está no banco, aqui só traduzimos a mensagem
            HttpUtil.sendJson(ex, 409, Json.obj("erro", traduzirViolacao(e)));
        } catch (SQLException e) {
            System.err.println("[SQL] " + e.getMessage());
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("Check constraint") || (msg.contains("CONSTRAINT") && msg.contains("failed"))) {
                HttpUtil.sendJson(ex, 400, Json.obj("erro", "Valor rejeitado por uma regra do banco (CHECK): " + msg));
            } else {
                HttpUtil.sendJson(ex, 500, Json.obj("erro", "Erro de banco de dados: " + msg));
            }
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

    private static String traduzirViolacao(SQLIntegrityConstraintViolationException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("Duplicate entry")) {
            return "Já existe um registro com esse valor (campo único duplicado, ex.: CPF ou placa).";
        }
        if (m.contains("Cannot delete or update a parent row")) {
            return "Não é possível excluir: existem registros vinculados (ex.: veículos ou ordens de serviço).";
        }
        if (m.contains("Cannot add or update a child row")) {
            return "Registro relacionado não existe (ex.: cliente, veículo ou serviço inválido).";
        }
        return "Violação de integridade: " + m;
    }
}
