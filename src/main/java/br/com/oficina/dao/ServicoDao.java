package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.model.Servico;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD da tabela SERVICO. */
public class ServicoDao {

    private static final String COLUNAS = "id, descricao, tipo, preco, ativo";

    /** READ (lista). 'apenasAtivos' é usado ao montar uma OS. */
    public List<Servico> listar(String busca, boolean apenasAtivos) throws SQLException {
        String sql = "SELECT " + COLUNAS + " FROM servico"
                + " WHERE (? IS NULL OR descricao LIKE ?)"
                + (apenasAtivos ? " AND ativo = 1" : "")
                + " ORDER BY tipo, descricao";
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim() + "%";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, filtro);
            ps.setString(2, filtro);
            try (ResultSet rs = ps.executeQuery()) {
                List<Servico> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /** READ (um). */
    public Optional<Servico> buscarPorId(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUNAS + " FROM servico WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** CREATE. */
    public Servico inserir(Servico s) throws SQLException {
        String sql = "INSERT INTO servico (descricao, tipo, preco, ativo) VALUES (?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, s);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return buscarPorId(chaves.getLong(1)).orElseThrow();
            }
        }
    }

    /** UPDATE. */
    public boolean atualizar(Servico s) throws SQLException {
        String sql = "UPDATE servico SET descricao = ?, tipo = ?, preco = ?, ativo = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            preencher(ps, s);
            ps.setLong(5, s.id());
            return ps.executeUpdate() > 0;
        }
    }

    /** DELETE (bloqueado pelo banco se o serviço já foi usado em alguma OS). */
    public boolean excluir(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM servico WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static void preencher(PreparedStatement ps, Servico s) throws SQLException {
        ps.setString(1, s.descricao());
        ps.setString(2, s.tipo());
        ps.setBigDecimal(3, s.preco());
        ps.setBoolean(4, s.ativo());
    }

    private static Servico mapear(ResultSet rs) throws SQLException {
        BigDecimal preco = rs.getBigDecimal("preco");
        return new Servico(
                rs.getLong("id"),
                rs.getString("descricao"),
                rs.getString("tipo"),
                preco,
                rs.getBoolean("ativo"));
    }
}
