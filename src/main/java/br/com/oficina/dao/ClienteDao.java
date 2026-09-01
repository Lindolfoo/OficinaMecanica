package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.model.Cliente;

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
 * CRUD da tabela CLIENTE em JDBC puro.
 * Todo SQL usa PreparedStatement (parâmetros "?"), nunca concatenação de strings.
 */
public class ClienteDao {

    private static final String COLUNAS = "id, nome, cpf, telefone, email, criado_em";

    /** READ (lista). Se 'busca' vier preenchida, filtra por nome ou CPF. */
    public List<Cliente> listar(String busca) throws SQLException {
        String sql = "SELECT " + COLUNAS + " FROM cliente"
                + " WHERE (? IS NULL OR nome LIKE ? OR cpf LIKE ?)"
                + " ORDER BY nome";
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim() + "%";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, filtro);
            ps.setString(2, filtro);
            ps.setString(3, filtro);
            try (ResultSet rs = ps.executeQuery()) {
                List<Cliente> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /** READ (um). */
    public Optional<Cliente> buscarPorId(long id) throws SQLException {
        String sql = "SELECT " + COLUNAS + " FROM cliente WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /** CREATE. Retorna o cliente com o id gerado pelo AUTO_INCREMENT. */
    public Cliente inserir(Cliente cli) throws SQLException {
        String sql = "INSERT INTO cliente (nome, cpf, telefone, email) VALUES (?, ?, ?, ?)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cli.nome());
            ps.setString(2, cli.cpf());
            ps.setString(3, cli.telefone());
            ps.setString(4, cli.email());
            ps.executeUpdate();
            try (ResultSet chaves = ps.getGeneratedKeys()) {
                chaves.next();
                long id = chaves.getLong(1);
                return buscarPorId(id).orElseThrow();
            }
        }
    }

    /** UPDATE. Retorna false se o id não existir. */
    public boolean atualizar(Cliente cli) throws SQLException {
        String sql = "UPDATE cliente SET nome = ?, cpf = ?, telefone = ?, email = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cli.nome());
            ps.setString(2, cli.cpf());
            ps.setString(3, cli.telefone());
            ps.setString(4, cli.email());
            ps.setLong(5, cli.id());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * DELETE. Retorna false se o id não existir.
     * Se o cliente tiver veículos, o MySQL lança SQLIntegrityConstraintViolationException
     * (FK ON DELETE RESTRICT), tratada no BaseHandler como HTTP 409.
     */
    public boolean excluir(long id) throws SQLException {
        String sql = "DELETE FROM cliente WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private static Cliente mapear(ResultSet rs) throws SQLException {
        Timestamp criado = rs.getTimestamp("criado_em");
        return new Cliente(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("cpf"),
                rs.getString("telefone"),
                rs.getString("email"),
                criado == null ? null : criado.toLocalDateTime());
    }
}
