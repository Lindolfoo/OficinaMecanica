package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Espelho da tabela CLIENTE. */
public record Cliente(
        Long id,
        String nome,
        String cpf,          // somente dígitos (11)
        String telefone,
        String email,
        LocalDateTime criadoEm) implements JsonSerializable {

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("cpf", cpf);
        m.put("telefone", telefone);
        m.put("email", email);
        m.put("criadoEm", criadoEm == null ? null : criadoEm.toString());
        return m;
    }
}
