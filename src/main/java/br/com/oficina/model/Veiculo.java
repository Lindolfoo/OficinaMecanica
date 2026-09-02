package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Espelho da tabela VEICULO.
 * 'clienteNome' não existe na tabela: vem do JOIN com CLIENTE para exibição na tela.
 */
public record Veiculo(
        Long id,
        Long clienteId,
        String clienteNome,
        String placa,
        String marca,
        String modelo,
        Integer ano,
        String cor) implements JsonSerializable {

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("clienteId", clienteId);
        m.put("clienteNome", clienteNome);
        m.put("placa", placa);
        m.put("marca", marca);
        m.put("modelo", modelo);
        m.put("ano", ano);
        m.put("cor", cor);
        return m;
    }
}
