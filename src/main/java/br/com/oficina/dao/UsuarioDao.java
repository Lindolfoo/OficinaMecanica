package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.model.Usuario;
import br.com.oficina.security.Senhas;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD da tabela USUARIO em JDBC puro, mais as operações de controle de acesso
 * (registro de acerto/erro de senha e bloqueio temporário da conta).
 *
 * Como em todos os DAOs do projeto, todo SQL usa PreparedStatement.
 */
public class UsuarioDao {

    private static final String COLUNAS =
            "id, nome, email, senha_hash, perfil, ativo, tentativas_falhas, bloqueado_ate, ultimo_acesso, criado_em";

    /** Quantos erros seguidos de senha bloqueiam a conta. */
    public static final int MAX_TENTATIVAS = 5;

    /** Por quantos minutos a conta fica bloqueada depois de estourar o limite. */
    public static final int MINUTOS_BLOQUEIO = 15;

    /** READ (lista). Se 'busca' vier preenchida, filtra por nome ou e-mail. */
    public List<Usuario> listar(String busca) throws SQLException {
        String sql = "SELECT " + COLUNAS + " FROM usuario"
                + " WHERE (? IS NULL OR nome LIKE ? OR email LIKE ?)"
                + " ORDER BY nome";
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim() + "%";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, filtro);
            ps.setString(2, filtro);
            ps.setString(3, filtro);
            try (ResultSet rs = ps.executeQuery()) {
                List<Usuario> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    public Optional<Usuario> buscarPorId(long id) throws SQLException {
        return buscarPor("id = ?", id);
    }

    /** Usado no login. O e-mail é gravado sempre em minúsculas. */
    public Optional<Usuario> buscarPorEmail(String email) throws SQLException {
        return buscarPor("email = ?", email == null ? "" : email.trim().toLowerCase());
    }

    private Optional<Usuario> buscarPor(String condicao, Object valor) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM usuario WHERE " + condicao)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** CREATE. Recebe a senha em texto e grava apenas o hash. */
    public Usuario inserir(Usuario u, String senhaEmTexto) throws SQLException {
        String sql = "INSERT INTO usuario (nome, email, senha_hash, perfil, ativo) VALUES (?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.nome());
            ps.setString(2, u.email());
            ps.setString(3, Senhas.gerarHash(senhaEmTexto));
            ps.setString(4, u.perfil());
            ps.setBoolean(5, u.ativo());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return buscarPorId(chaves.getLong(1)).orElseThrow();
            }
        }
    }

    /** UPDATE dos dados cadastrais. A senha tem caminho próprio ({@link #alterarSenha}). */
    public boolean atualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET nome = ?, email = ?, perfil = ?, ativo = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u.nome());
            ps.setString(2, u.email());
            ps.setString(3, u.perfil());
            ps.setBoolean(4, u.ativo());
            ps.setLong(5, u.id());
            return ps.executeUpdate() > 0;
        }
    }

    /** Troca a senha e, de quebra, destrava a conta. */
    public boolean alterarSenha(long id, String senhaNova) throws SQLException {
        String sql = "UPDATE usuario SET senha_hash = ?, tentativas_falhas = 0, bloqueado_ate = NULL WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, Senhas.gerarHash(senhaNova));
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean excluir(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM usuario WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------ controle de acesso

    /** Login aceito: zera o contador de erros e carimba o último acesso. */
    public void registrarAcertoDeSenha(long id) throws SQLException {
        String sql = "UPDATE usuario SET tentativas_falhas = 0, bloqueado_ate = NULL, ultimo_acesso = NOW()"
                + " WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    /**
     * Login recusado: soma um erro e, ao chegar no limite, bloqueia a conta por
     * alguns minutos. O próprio SQL decide o bloqueio, em uma única instrução,
     * para não abrir espaço entre a leitura e a gravação do contador.
     */
    public void registrarErroDeSenha(long id) throws SQLException {
        String sql = """
                UPDATE usuario
                   SET tentativas_falhas = tentativas_falhas + 1,
                       bloqueado_ate = CASE WHEN tentativas_falhas + 1 >= ?
                                            THEN DATE_ADD(NOW(), INTERVAL ? MINUTE)
                                            ELSE bloqueado_ate END
                 WHERE id = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, MAX_TENTATIVAS);
            ps.setInt(2, MINUTOS_BLOQUEIO);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }

    /** Quantos ADMIN ativos existem, ignorando um id (o que está sendo alterado). */
    public int contarAdminsAtivos(Long exceto) throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuario WHERE perfil = 'ADMIN' AND ativo = 1 AND (? IS NULL OR id <> ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, exceto);
            ps.setObject(2, exceto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public int contar() throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM usuario");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Cria o administrador inicial se a tabela estiver vazia — sem isso não
     * haveria como entrar no sistema na primeira execução. Devolve true se
     * criou, para a App avisar no console que a senha padrão precisa ser trocada.
     */
    public boolean garantirAdminPadrao(String nome, String email, String senha) throws SQLException {
        if (contar() > 0) {
            return false;
        }
        inserir(new Usuario(null, nome, email.trim().toLowerCase(), null, Usuario.ADMIN, true,
                0, null, null, null), senha);
        return true;
    }

    private static Usuario mapear(ResultSet rs) throws SQLException {
        Timestamp bloqueado = rs.getTimestamp("bloqueado_ate");
        Timestamp acesso = rs.getTimestamp("ultimo_acesso");
        Timestamp criado = rs.getTimestamp("criado_em");
        return new Usuario(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("email"),
                rs.getString("senha_hash"),
                rs.getString("perfil"),
                rs.getBoolean("ativo"),
                rs.getInt("tentativas_falhas"),
                bloqueado == null ? null : bloqueado.toLocalDateTime(),
                acesso == null ? null : acesso.toLocalDateTime(),
                criado == null ? null : criado.toLocalDateTime());
    }
}
