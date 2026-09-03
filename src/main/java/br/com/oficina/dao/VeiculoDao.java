package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.model.Veiculo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** CRUD da tabela VEICULO. As consultas fazem JOIN com CLIENTE para trazer o nome do dono. */
public class VeiculoDao {

    private static final String SELECT_BASE = """
            SELECT v.id, v.cliente_id, c.nome AS cliente_nome,
                   v.placa, v.marca, v.modelo, v.ano, v.cor
              FROM veiculo v
              JOIN cliente c ON c.id = v.cliente_id
            """;

    /** READ (lista), com filtro opcional por placa/marca/modelo e por cliente. */
    public List<Veiculo> listar(String busca, Long clienteId) throws SQLException {
        String sql = SELECT_BASE
                + " WHERE (? IS NULL OR v.placa LIKE ? OR v.marca LIKE ? OR v.modelo LIKE ?)"
                + "   AND (? IS NULL OR v.cliente_id = ?)"
                + " ORDER BY v.placa";
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim() + "%";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, filtro);
            ps.setString(2, filtro);
            ps.setString(3, filtro);
            ps.setString(4, filtro);
            if (clienteId == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setLong(5, clienteId);
                ps.setLong(6, clienteId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<Veiculo> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /** READ (um). */
    public Optional<Veiculo> buscarPorId(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(SELECT_BASE + " WHERE v.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** CREATE. */
    public Veiculo inserir(Veiculo v) throws SQLException {
        String sql = "INSERT INTO veiculo (cliente_id, placa, marca, modelo, ano, cor) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            preencher(ps, v);
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                return buscarPorId(chaves.getLong(1)).orElseThrow();
            }
        }
    }

    /** UPDATE. */
    public boolean atualizar(Veiculo v) throws SQLException {
        String sql = "UPDATE veiculo SET cliente_id = ?, placa = ?, marca = ?, modelo = ?, ano = ?, cor = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            preencher(ps, v);
            ps.setLong(7, v.id());
            return ps.executeUpdate() > 0;
        }
    }

    /** DELETE (bloqueado pelo banco se houver ordens de serviço vinculadas). */
    public boolean excluir(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM veiculo WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static void preencher(PreparedStatement ps, Veiculo v) throws SQLException {
        ps.setLong(1, v.clienteId());
        ps.setString(2, v.placa());
        ps.setString(3, v.marca());
        ps.setString(4, v.modelo());
        ps.setInt(5, v.ano());
        ps.setString(6, v.cor());
    }

    private static Veiculo mapear(ResultSet rs) throws SQLException {
        return new Veiculo(
                rs.getLong("id"),
                rs.getLong("cliente_id"),
                rs.getString("cliente_nome"),
                rs.getString("placa"),
                rs.getString("marca"),
                rs.getString("modelo"),
                rs.getInt("ano"),
                rs.getString("cor"));
    }
}
