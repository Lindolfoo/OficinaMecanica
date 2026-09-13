package br.com.oficina.api;

import br.com.oficina.dao.OrdemServicoDao;
import br.com.oficina.dao.ServicoDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.http.Json;
import br.com.oficina.model.OrdemServico;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recurso /api/ordens
 *
 *   GET    /api/ordens                       lista (?status=ABERTA, ?busca=texto, ?veiculoId=1)
 *   GET    /api/ordens/resumo                total e quantidade por status (GROUP BY)
 *   GET    /api/ordens/{id}                  uma OS com seus itens
 *   POST   /api/ordens                       cria OS + itens (transação)
 *   PUT    /api/ordens/{id}                  altera o cabeçalho / muda o status
 *   DELETE /api/ordens/{id}                  exclui a OS (itens saem em cascata)
 *   POST   /api/ordens/{id}/itens            adiciona um item
 *   DELETE /api/ordens/{id}/itens/{itemId}   remove um item
 */
public class OrdemServicoHandler extends BaseHandler {

    private static final Set<String> STATUS_VALIDOS =
            Set.of("ABERTA", "EM_ANDAMENTO", "CONCLUIDA", "CANCELADA");

    private final OrdemServicoDao dao = new OrdemServicoDao();
    private final ServicoDao servicoDao = new ServicoDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        List<String> partes = HttpUtil.pathParts(ex);

        if (partes.isEmpty()) {
            Map<String, String> q = HttpUtil.query(ex);
            String status = q.get("status");
            if (status != null && !status.isBlank() && !STATUS_VALIDOS.contains(status)) {
                throw new ApiException(400, "Status inválido: " + status);
            }
            String veiculo = q.get("veiculoId");
            Long veiculoId = (veiculo == null || veiculo.isBlank()) ? null : HttpUtil.parseId(veiculo);
            HttpUtil.sendJson(ex, 200,
                    dao.listar(status == null || status.isBlank() ? null : status, q.get("busca"), veiculoId));
            return;
        }

        if (partes.get(0).equals("resumo")) {
            List<Map<String, Object>> resumo = new ArrayList<>();
            for (OrdemServicoDao.Faturamento f : dao.resumoPorStatus()) {
                resumo.add(Json.obj("status", f.status(), "quantidade", f.quantidade(), "total", f.total()));
            }
            HttpUtil.sendJson(ex, 200, resumo);
            return;
        }

        long id = HttpUtil.parseId(partes.get(0));
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id)
                .orElseThrow(() -> new ApiException(404, "Ordem de serviço não encontrada")));
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        List<String> partes = HttpUtil.pathParts(ex);

        // POST /api/ordens/{id}/itens
        if (partes.size() == 2 && partes.get(1).equals("itens")) {
            long ordemId = HttpUtil.parseId(partes.get(0));
            Map<String, String> f = HttpUtil.readForm(ex);
            long servicoId = HttpUtil.parseId(f.getOrDefault("servicoId", ""));
            int quantidade = quantidadeValida(f.getOrDefault("quantidade", "1"));

            dao.buscarPorId(ordemId).orElseThrow(() -> new ApiException(404, "Ordem de serviço não encontrada"));
            exigirServicoAtivo(servicoId);
            if (!dao.adicionarItem(ordemId, servicoId, quantidade)) {
                throw new ApiException(400, "Serviço não encontrado ou inativo");
            }
            HttpUtil.sendJson(ex, 201, dao.buscarPorId(ordemId).orElseThrow());
            return;
        }

        if (!partes.isEmpty()) {
            throw new ApiException(404, "Rota não encontrada");
        }

        // POST /api/ordens — cabeçalho + itens em uma única transação
        Map<String, List<String>> f = HttpUtil.readFormMulti(ex);
        OrdemServico os = validarCabecalho(null, f);

        List<String> servicos = f.getOrDefault("servicoId", List.of());
        List<String> quantidades = f.getOrDefault("quantidade", List.of());
        List<long[]> itens = new ArrayList<>();
        for (int i = 0; i < servicos.size(); i++) {
            long servicoId = HttpUtil.parseId(servicos.get(i));
            exigirServicoAtivo(servicoId);
            int qtd = quantidadeValida(i < quantidades.size() ? quantidades.get(i) : "1");
            itens.add(new long[]{servicoId, qtd});
        }

        HttpUtil.sendJson(ex, 201, dao.inserirComItens(os, itens));
    }

    @Override
    protected void put(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID da ordem de serviço na URL");
        }
        OrdemServico os = validarCabecalho(id, HttpUtil.readFormMulti(ex));
        if (!dao.atualizar(os)) {
            throw new ApiException(404, "Ordem de serviço não encontrada");
        }
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
    }

    @Override
    protected void delete(HttpExchange ex) throws Exception {
        List<String> partes = HttpUtil.pathParts(ex);
        if (partes.isEmpty()) {
            throw new ApiException(400, "Informe o ID da ordem de serviço na URL");
        }
        long ordemId = HttpUtil.parseId(partes.get(0));

        // DELETE /api/ordens/{id}/itens/{itemId}
        if (partes.size() == 3 && partes.get(1).equals("itens")) {
            long itemId = HttpUtil.parseId(partes.get(2));
            if (!dao.removerItem(ordemId, itemId)) {
                throw new ApiException(404, "Item não encontrado nesta ordem de serviço");
            }
            HttpUtil.sendJson(ex, 200, dao.buscarPorId(ordemId).orElseThrow());
            return;
        }

        if (partes.size() != 1) {
            throw new ApiException(404, "Rota não encontrada");
        }
        if (!dao.excluir(ordemId)) {
            throw new ApiException(404, "Ordem de serviço não encontrada");
        }
        HttpUtil.sendNoContent(ex);
    }

    // ------------------------------------------------------------------ validação

    private static OrdemServico validarCabecalho(Long id, Map<String, List<String>> f) {
        long veiculoId = HttpUtil.parseId(primeiro(f, "veiculoId", ""));
        String status = primeiro(f, "status", "ABERTA").toUpperCase();
        String problema = primeiro(f, "descricaoProblema", "").trim();
        String kmTexto = primeiro(f, "kmAtual", "").trim();
        String observacoes = primeiro(f, "observacoes", "").trim();

        if (!STATUS_VALIDOS.contains(status)) {
            throw new ApiException(400, "Status deve ser ABERTA, EM_ANDAMENTO, CONCLUIDA ou CANCELADA");
        }
        if (problema.length() < 5 || problema.length() > 500) {
            throw new ApiException(400, "Descrição do problema deve ter entre 5 e 500 caracteres");
        }
        if (observacoes.length() > 500) {
            throw new ApiException(400, "Observações devem ter no máximo 500 caracteres");
        }

        Integer km = null;
        if (!kmTexto.isEmpty()) {
            try {
                km = Integer.parseInt(kmTexto.replaceAll("\\D", ""));
            } catch (NumberFormatException e) {
                throw new ApiException(400, "Quilometragem inválida");
            }
            if (km < 0 || km > 9_999_999) {
                throw new ApiException(400, "Quilometragem fora do intervalo permitido");
            }
        }

        return new OrdemServico(id, veiculoId, null, null, null, status, problema, km,
                null, null, observacoes.isEmpty() ? null : observacoes, null, List.of());
    }

    /** Garante que o serviço existe e está ativo, devolvendo 400 com a causa exata. */
    private void exigirServicoAtivo(long servicoId) throws java.sql.SQLException {
        var servico = servicoDao.buscarPorId(servicoId)
                .orElseThrow(() -> new ApiException(400, "Serviço " + servicoId + " não existe"));
        if (!servico.ativo()) {
            throw new ApiException(400, "O serviço \"" + servico.descricao() + "\" está inativo e não pode ser usado");
        }
    }

    private static int quantidadeValida(String texto) {
        int qtd;
        try {
            qtd = Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Quantidade inválida");
        }
        if (qtd < 1 || qtd > 999) {
            throw new ApiException(400, "Quantidade deve estar entre 1 e 999");
        }
        return qtd;
    }

    private static String primeiro(Map<String, List<String>> f, String chave, String padrao) {
        List<String> valores = f.get(chave);
        return (valores == null || valores.isEmpty()) ? padrao : valores.get(0);
    }
}
