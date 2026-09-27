package br.com.oficina.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;

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
        } catch (SQLException e) {
            // O detalhe técnico fica no log do servidor. Para o navegador vai uma
            // mensagem neutra: o texto do driver descreve o schema e não deve sair daqui.
            System.err.println("[SQL] " + e.getMessage());
            String msg = e.getMessage() == null ? "" : e.getMessage();

            if (msg.contains("SQLITE_CONSTRAINT_UNIQUE") || msg.contains("UNIQUE constraint failed")) {
                HttpUtil.sendJson(ex, 409, Json.obj("erro",
                        "Já existe um registro com esse valor (campo único duplicado, ex.: CPF ou placa)."));
            } else if (msg.contains("SQLITE_CONSTRAINT_FOREIGNKEY") || msg.contains("FOREIGN KEY constraint failed")) {
                // O SQLite não diz de que lado a FK falhou, então a mensagem cobre os dois.
                HttpUtil.sendJson(ex, 409, Json.obj("erro",
                        "Operação bloqueada por vínculo entre registros: "
                        + "ou existem registros dependentes (veículos, ordens de serviço), "
                        + "ou o registro relacionado informado não existe."));
            } else if (msg.contains("SQLITE_CONSTRAINT_CHECK") || msg.contains("CHECK constraint failed")) {
                HttpUtil.sendJson(ex, 400, Json.obj("erro", traduzirCheck(msg)));
            } else if (msg.contains("SQLITE_CONSTRAINT_NOTNULL") || msg.contains("NOT NULL constraint failed")) {
                HttpUtil.sendJson(ex, 400, Json.obj("erro", "Campo obrigatório não informado."));
            } else {
                HttpUtil.sendJson(ex, 500, Json.obj("erro",
                        "Erro ao acessar o banco de dados. Verifique o log do servidor."));
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

    /**
     * O SQLite informa o NOME da restrição que falhou ("CHECK constraint failed:
     * ck_cliente_cpf"). Como demos nome a todas elas no DDL, dá para devolver
     * uma mensagem que o usuário entende, sem expor o texto do banco.
     */
    private static String traduzirCheck(String msg) {
        if (msg.contains("ck_cliente_cpf")) {
            return "CPF inválido: informe os 11 dígitos.";
        }
        if (msg.contains("ck_veiculo_ano")) {
            return "Ano do veículo deve estar entre 1950 e 2100.";
        }
        if (msg.contains("ck_servico_preco") || msg.contains("ck_item_os_valor")) {
            return "O valor não pode ser negativo.";
        }
        if (msg.contains("ck_item_os_qtd")) {
            return "A quantidade deve ser maior que zero.";
        }
        if (msg.contains("ck_os_status") || msg.contains("ck_servico_tipo") || msg.contains("ck_usuario_perfil")) {
            return "Valor fora das opções permitidas.";
        }
        if (msg.contains("ck_os_conclusao")) {
            return "A data de conclusão não pode ser anterior à de abertura.";
        }
        if (msg.contains("ck_usuario_senha")) {
            return "A senha precisa ser gravada com hash (erro interno de segurança).";
        }
        if (msg.contains("ck_usuario_email")) {
            return "E-mail inválido.";
        }
        return "Valor rejeitado por uma regra do banco de dados.";
    }
}
