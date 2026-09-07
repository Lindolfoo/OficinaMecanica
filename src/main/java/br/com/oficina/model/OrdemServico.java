package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Espelho da tabela ORDEM_SERVICO.
 * O valor total NÃO é armazenado: é sempre calculado a partir dos itens
 * (SUM(quantidade * valor_unitario)), evitando dado redundante no banco.
 */
public record OrdemServico(
        Long id,
        Long veiculoId,
        String veiculoPlaca,
        String veiculoDescricao,
        String clienteNome,
        String status,                 // ABERTA | EM_ANDAMENTO | CONCLUIDA | CANCELADA
        String descricaoProblema,
        Integer kmAtual,
        LocalDateTime dataAbertura,
        LocalDateTime dataConclusao,
        String observacoes,
        BigDecimal valorTotal,
        List<ItemOs> itens) implements JsonSerializable {

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("veiculoId", veiculoId);
        m.put("veiculoPlaca", veiculoPlaca);
        m.put("veiculoDescricao", veiculoDescricao);
        m.put("clienteNome", clienteNome);
        m.put("status", status);
        m.put("descricaoProblema", descricaoProblema);
        m.put("kmAtual", kmAtual);
        m.put("dataAbertura", dataAbertura == null ? null : dataAbertura.toString());
        m.put("dataConclusao", dataConclusao == null ? null : dataConclusao.toString());
        m.put("observacoes", observacoes);
        m.put("valorTotal", valorTotal);
        m.put("itens", itens);
        return m;
    }
}
