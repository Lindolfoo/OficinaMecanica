package br.com.oficina.api;

import br.com.oficina.dao.VeiculoDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.model.Veiculo;
import com.sun.net.httpserver.HttpExchange;

import java.util.Map;

/**
 * Recurso /api/veiculos
 *
 *   GET    /api/veiculos            lista (?busca=texto, ?clienteId=1)
 *   GET    /api/veiculos/{id}       um veículo
 *   POST   /api/veiculos            cria
 *   PUT    /api/veiculos/{id}       altera
 *   DELETE /api/veiculos/{id}       exclui
 */
public class VeiculoHandler extends BaseHandler {

    private final VeiculoDao dao = new VeiculoDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            Map<String, String> q = HttpUtil.query(ex);
            String cli = q.get("clienteId");
            Long clienteId = (cli == null || cli.isBlank()) ? null : HttpUtil.parseId(cli);
            HttpUtil.sendJson(ex, 200, dao.listar(q.get("busca"), clienteId));
        } else {
            HttpUtil.sendJson(ex, 200, dao.buscarPorId(id)
                    .orElseThrow(() -> new ApiException(404, "Veículo não encontrado")));
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
            throw new ApiException(400, "Informe o ID do veículo na URL");
        }
        if (!dao.atualizar(validar(id, HttpUtil.readForm(ex)))) {
            throw new ApiException(404, "Veículo não encontrado");
        }
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
    }

    @Override
    protected void delete(HttpExchange ex) throws Exception {
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do veículo na URL");
        }
        if (!dao.excluir(id)) {
            throw new ApiException(404, "Veículo não encontrado");
        }
        HttpUtil.sendNoContent(ex);
    }

    private static Veiculo validar(Long id, Map<String, String> f) {
        long clienteId = HttpUtil.parseId(f.getOrDefault("clienteId", ""));
        String placa = f.getOrDefault("placa", "").toUpperCase().replaceAll("[^A-Z0-9]", "");
        String marca = f.getOrDefault("marca", "").trim();
        String modelo = f.getOrDefault("modelo", "").trim();
        String anoTexto = f.getOrDefault("ano", "").trim();
        String cor = f.getOrDefault("cor", "").trim();

        // Placa antiga (ABC1234) ou padrão Mercosul (ABC1D23)
        if (!placa.matches("^[A-Z]{3}[0-9][A-Z0-9][0-9]{2}$")) {
            throw new ApiException(400, "Placa inválida. Use o formato ABC1234 ou ABC1D23");
        }
        if (marca.isEmpty() || marca.length() > 50) {
            throw new ApiException(400, "Marca é obrigatória (até 50 caracteres)");
        }
        if (modelo.isEmpty() || modelo.length() > 60) {
            throw new ApiException(400, "Modelo é obrigatório (até 60 caracteres)");
        }

        int ano;
        try {
            ano = Integer.parseInt(anoTexto);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Ano inválido");
        }
        if (ano < 1950 || ano > 2100) {
            throw new ApiException(400, "Ano deve estar entre 1950 e 2100");
        }
        if (cor.length() > 30) {
            throw new ApiException(400, "Cor deve ter no máximo 30 caracteres");
        }

        return new Veiculo(id, clienteId, null, placa, marca, modelo, ano, cor.isEmpty() ? null : cor);
    }
}
