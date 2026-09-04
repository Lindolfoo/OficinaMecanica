package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Espelho da tabela ITEM_OS (associativa N:N entre ORDEM_SERVICO e SERVICO).
 * 'servicoDescricao' e 'subtotal' são derivados (JOIN e cálculo), não colunas.
 */
public record ItemOs(
        Long id,
        Long ordemServicoId,
        Long servicoId,
        String servicoDescricao,
        String servicoTipo,
        int quantidade,
        BigDecimal valorUnitario) implements JsonSerializable {

    public BigDecimal subtotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("ordemServicoId", ordemServicoId);
        m.put("servicoId", servicoId);
        m.put("servicoDescricao", servicoDescricao);
        m.put("servicoTipo", servicoTipo);
        m.put("quantidade", quantidade);
        m.put("valorUnitario", valorUnitario);
        m.put("subtotal", subtotal());
        return m;
    }
}
