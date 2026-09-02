package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Espelho da tabela SERVICO (catálogo de mão de obra e peças). */
public record Servico(
        Long id,
        String descricao,
        String tipo,          // MAO_DE_OBRA | PECA
        BigDecimal preco,
        boolean ativo) implements JsonSerializable {

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("descricao", descricao);
        m.put("tipo", tipo);
        m.put("preco", preco);
        m.put("ativo", ativo);
        return m;
    }
}
