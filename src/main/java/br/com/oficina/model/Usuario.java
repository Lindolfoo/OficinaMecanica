package br.com.oficina.model;

import br.com.oficina.http.JsonSerializable;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Espelho da tabela USUARIO — quem pode entrar no sistema.
 *
 * O campo senhaHash existe no record porque o DAO precisa dele para conferir o
 * login, mas ele é deliberadamente omitido do {@link #toJsonMap()}: nenhum hash
 * de senha sai pela API, nem para o próprio dono da conta.
 */
public record Usuario(
        Long id,
        String nome,
        String email,
        String senhaHash,
        String perfil,          // ADMIN ou ATENDENTE
        boolean ativo,
        int tentativasFalhas,
        LocalDateTime bloqueadoAte,
        LocalDateTime ultimoAcesso,
        LocalDateTime criadoEm) implements JsonSerializable {

    public static final String ADMIN = "ADMIN";
    public static final String ATENDENTE = "ATENDENTE";

    /** ADMIN administra usuários e exclui registros; ATENDENTE opera o dia a dia. */
    public boolean ehAdmin() {
        return ADMIN.equals(perfil);
    }

    /** Conta temporariamente barrada por excesso de tentativas de login. */
    public boolean bloqueado() {
        return bloqueadoAte != null && bloqueadoAte.isAfter(LocalDateTime.now());
    }

    @Override
    public Map<String, Object> toJsonMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("email", email);
        m.put("perfil", perfil);
        m.put("ativo", ativo);
        m.put("bloqueado", bloqueado());
        m.put("ultimoAcesso", ultimoAcesso == null ? null : ultimoAcesso.toString());
        m.put("criadoEm", criadoEm == null ? null : criadoEm.toString());
        return m;                               // senhaHash fica de fora, de propósito
    }
}
