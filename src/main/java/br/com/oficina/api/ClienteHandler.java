package br.com.oficina.api;

import br.com.oficina.dao.ClienteDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.model.Cliente;
import br.com.oficina.util.Validators;
import com.sun.net.httpserver.HttpExchange;

import java.util.Map;

/**
 * Recurso /api/clientes
 *
 *   GET    /api/clientes            lista (opcional ?busca=texto)
 *   GET    /api/clientes/{id}       um cliente
 *   POST   /api/clientes            cria   (form: nome, cpf, telefone, email)
 *   PUT    /api/clientes/{id}       altera (mesmos campos)
 *   DELETE /api/clientes/{id}       exclui
 */
public class ClienteHandler extends BaseHandler {

    private final ClienteDao dao = new ClienteDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            String busca = HttpUtil.query(ex).get("busca");
            HttpUtil.sendJson(ex, 200, dao.listar(busca));
        } else {
            Cliente cliente = dao.buscarPorId(id)
                    .orElseThrow(() -> new ApiException(404, "Cliente não encontrado"));
            HttpUtil.sendJson(ex, 200, cliente);
        }
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        Cliente novo = validar(null, HttpUtil.readForm(ex));
        HttpUtil.sendJson(ex, 201, dao.inserir(novo));
    }

    @Override
    protected void put(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do cliente na URL");
        }
        Cliente cliente = validar(id, HttpUtil.readForm(ex));
        if (!dao.atualizar(cliente)) {
            throw new ApiException(404, "Cliente não encontrado");
        }
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
    }

    @Override
    protected void delete(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do cliente na URL");
        }
        if (!dao.excluir(id)) {
            throw new ApiException(404, "Cliente não encontrado");
        }
        HttpUtil.sendNoContent(ex);
    }

    /**
     * Validação no servidor. A validação do HTML/JS é só conveniência para o usuário:
     * a regra de verdade fica aqui e nas constraints do banco.
     */
    private static Cliente validar(Long id, Map<String, String> f) {
        String nome = f.getOrDefault("nome", "").trim();
        String cpf = Validators.somenteDigitos(f.get("cpf"));
        String telefone = f.getOrDefault("telefone", "").trim();
        String email = f.getOrDefault("email", "").trim();

        if (nome.length() < 2 || nome.length() > 100) {
            throw new ApiException(400, "Nome deve ter entre 2 e 100 caracteres");
        }
        if (!Validators.cpfValido(cpf)) {
            throw new ApiException(400, "CPF inválido");
        }
        if (telefone.length() > 20) {
            throw new ApiException(400, "Telefone deve ter no máximo 20 caracteres");
        }
        if (!email.isEmpty() && (email.length() > 120 || !Validators.emailValido(email))) {
            throw new ApiException(400, "E-mail inválido");
        }

        return new Cliente(id, nome, cpf,
                telefone.isEmpty() ? null : telefone,
                email.isEmpty() ? null : email,
                null);
    }
}
