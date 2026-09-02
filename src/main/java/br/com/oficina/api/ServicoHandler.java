package br.com.oficina.api;

import br.com.oficina.dao.ServicoDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.model.Servico;
import com.sun.net.httpserver.HttpExchange;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Recurso /api/servicos
 *
 *   GET    /api/servicos            lista (?busca=texto, ?ativos=true)
 *   GET    /api/servicos/{id}       um serviço
 *   POST   /api/servicos            cria
 *   PUT    /api/servicos/{id}       altera
 *   DELETE /api/servicos/{id}       exclui
 */
public class ServicoHandler extends BaseHandler {

    private final ServicoDao dao = new ServicoDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            Map<String, String> q = HttpUtil.query(ex);
            boolean apenasAtivos = "true".equalsIgnoreCase(q.get("ativos"));
            HttpUtil.sendJson(ex, 200, dao.listar(q.get("busca"), apenasAtivos));
        } else {
            HttpUtil.sendJson(ex, 200, dao.buscarPorId(id)
                    .orElseThrow(() -> new ApiException(404, "Serviço não encontrado")));
        }
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        HttpUtil.sendJson(ex, 201, dao.inserir(validar(null, HttpUtil.readForm(ex))));
    }

    @Override
    protected void put(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do serviço na URL");
        }
        if (!dao.atualizar(validar(id, HttpUtil.readForm(ex)))) {
            throw new ApiException(404, "Serviço não encontrado");
        }
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
    }

    @Override
    protected void delete(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do serviço na URL");
        }
        if (!dao.excluir(id)) {
            throw new ApiException(404, "Serviço não encontrado");
        }
        HttpUtil.sendNoContent(ex);
    }

    private static Servico validar(Long id, Map<String, String> f) {
        String descricao = f.getOrDefault("descricao", "").trim();
        String tipo = f.getOrDefault("tipo", "MAO_DE_OBRA").trim().toUpperCase();
        String precoTexto = f.getOrDefault("preco", "").trim().replace("R$", "").replace(".", "").replace(',', '.').trim();
        boolean ativo = !"false".equalsIgnoreCase(f.getOrDefault("ativo", "true"));

        if (descricao.length() < 3 || descricao.length() > 120) {
            throw new ApiException(400, "Descrição deve ter entre 3 e 120 caracteres");
        }
        if (!tipo.equals("MAO_DE_OBRA") && !tipo.equals("PECA")) {
            throw new ApiException(400, "Tipo deve ser MAO_DE_OBRA ou PECA");
        }

        BigDecimal preco;
        try {
            preco = new BigDecimal(precoTexto);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Preço inválido");
        }
        if (preco.signum() < 0) {
            throw new ApiException(400, "Preço não pode ser negativo");
        }
        if (preco.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new ApiException(400, "Preço acima do limite permitido");
        }

        return new Servico(id, descricao, tipo, preco.setScale(2, java.math.RoundingMode.HALF_UP), ativo);
    }
}
