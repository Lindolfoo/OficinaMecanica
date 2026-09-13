package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.model.ItemOs;
import br.com.oficina.model.OrdemServico;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CRUD da ORDEM_SERVICO e de seus itens.
 *
 * É aqui que está a parte mais importante do trabalho em termos de banco:
 * a criação de uma OS com seus itens acontece dentro de uma TRANSAÇÃO explícita
 * (setAutoCommit(false) / commit / rollback), de modo que nunca exista no banco
 * uma OS gravada pela metade.
 */
public class OrdemServicoDao {

    /**
     * SELECT do cabeçalho com os dados do veículo e do cliente (JOIN) e o
     * valor total agregado dos itens (subconsulta com SUM).
     */
    private static final String SELECT_BASE = """
            SELECT os.id, os.veiculo_id, v.placa, v.marca, v.modelo, c.nome AS cliente_nome,
                   os.status, os.descricao_problema, os.km_atual,
                   os.data_abertura, os.data_conclusao, os.observacoes,
                   COALESCE((SELECT SUM(i.quantidade * i.valor_unitario)
                               FROM item_os i
                              WHERE i.ordem_servico_id = os.id), 0) AS valor_total
              FROM ordem_servico os
              JOIN veiculo v ON v.id = os.veiculo_id
              JOIN cliente c ON c.id = v.cliente_id
            """;

    /**
     * READ (lista), com filtros opcionais por status, por texto (placa/cliente/problema)
     * e por veículo — este último é o histórico de atendimentos do carro.
     */
    public List<OrdemServico> listar(String status, String busca, Long veiculoId) throws SQLException {
        String sql = SELECT_BASE
                + " WHERE (? IS NULL OR os.status = ?)"
                + "   AND (? IS NULL OR v.placa LIKE ? OR c.nome LIKE ? OR os.descricao_problema LIKE ?)"
                + "   AND (? IS NULL OR os.veiculo_id = ?)"
                + " ORDER BY os.data_abertura DESC, os.id DESC";
        String filtro = (busca == null || busca.isBlank()) ? null : "%" + busca.trim() + "%";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setString(3, filtro);
            ps.setString(4, filtro);
            ps.setString(5, filtro);
            ps.setString(6, filtro);
            ps.setObject(7, veiculoId, Types.BIGINT);
            ps.setObject(8, veiculoId, Types.BIGINT);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrdemServico> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs, List.of()));   // lista resumida: sem os itens
                }
                return lista;
            }
        }
    }

    /** READ (uma OS completa, com os itens). */
    public Optional<OrdemServico> buscarPorId(long id) throws SQLException {
        try (Connection c = Database.getConnection()) {
            return buscarPorId(c, id);
        }
    }

    private Optional<OrdemServico> buscarPorId(Connection c, long id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(SELECT_BASE + " WHERE os.id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapear(rs, listarItens(c, id)));
            }
        }
    }

    /**
     * CREATE em TRANSAÇÃO: grava o cabeçalho e todos os itens como uma
     * operação única. Qualquer falha (serviço inexistente, item duplicado,
     * quantidade inválida) desfaz tudo com rollback.
     *
     * O preço de cada item é copiado do catálogo no momento da gravação, para
     * que uma alteração futura no preço de tabela não altere OS já emitidas.
     */
    public OrdemServico inserirComItens(OrdemServico os, List<long[]> itens) throws SQLException {
        String sqlOs = "INSERT INTO ordem_servico (veiculo_id, status, descricao_problema, km_atual, observacoes)"
                + " VALUES (?, ?, ?, ?, ?)";
        String sqlItem = "INSERT INTO item_os (ordem_servico_id, servico_id, quantidade, valor_unitario)"
                + " SELECT ?, id, ?, preco FROM servico WHERE id = ? AND ativo = 1";

        Connection c = null;
        try {
            c = Database.getConnection();
            c.setAutoCommit(false);                       // ---- início da transação

            long idGerado;
            try (PreparedStatement ps = c.prepareStatement(sqlOs, Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, os.veiculoId());
                ps.setString(2, os.status());
                ps.setString(3, os.descricaoProblema());
                setIntOuNulo(ps, 4, os.kmAtual());
                ps.setString(5, os.observacoes());
                ps.executeUpdate();
                try (ResultSet chaves = ps.getGeneratedKeys()) {
                    chaves.next();
                    idGerado = chaves.getLong(1);
                }
            }

            try (PreparedStatement ps = c.prepareStatement(sqlItem)) {
                for (long[] item : itens) {              // item = {servicoId, quantidade}
                    ps.setLong(1, idGerado);
                    ps.setLong(2, item[1]);
                    ps.setLong(3, item[0]);
                    ps.addBatch();
                }
                for (int linhas : ps.executeBatch()) {
                    if (linhas == 0) {                   // serviço inexistente ou inativo
                        throw new SQLException("Serviço inválido ou inativo informado na ordem de serviço");
                    }
                }
            }

            OrdemServico criada = buscarPorId(c, idGerado).orElseThrow();
            c.commit();                                  // ---- fim da transação
            return criada;

        } catch (SQLException e) {
            if (c != null) {
                c.rollback();                            // desfaz cabeçalho + itens já inseridos
            }
            throw e;
        } finally {
            if (c != null) {
                c.setAutoCommit(true);
                c.close();
            }
        }
    }

    /**
     * UPDATE do cabeçalho. Quando o status vira CONCLUIDA, a data de conclusão
     * é preenchida pelo próprio SQL; nos demais status ela volta a NULL.
     */
    public boolean atualizar(OrdemServico os) throws SQLException {
        String sql = """
                UPDATE ordem_servico
                   SET veiculo_id = ?, status = ?, descricao_problema = ?, km_atual = ?, observacoes = ?,
                       data_conclusao = CASE WHEN ? = 'CONCLUIDA' THEN COALESCE(data_conclusao, NOW()) ELSE NULL END
                 WHERE id = ?
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, os.veiculoId());
            ps.setString(2, os.status());
            ps.setString(3, os.descricaoProblema());
            setIntOuNulo(ps, 4, os.kmAtual());
            ps.setString(5, os.observacoes());
            ps.setString(6, os.status());
            ps.setLong(7, os.id());
            return ps.executeUpdate() > 0;
        }
    }

    /** DELETE. Os itens saem junto por causa do ON DELETE CASCADE da FK. */
    public boolean excluir(long id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM ordem_servico WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ------------------------------------------------------------------ itens

    /** Adiciona um item copiando o preço atual do catálogo. */
    public boolean adicionarItem(long ordemId, long servicoId, int quantidade) throws SQLException {
        String sql = "INSERT INTO item_os (ordem_servico_id, servico_id, quantidade, valor_unitario)"
                + " SELECT ?, id, ?, preco FROM servico WHERE id = ? AND ativo = 1";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ordemId);
            ps.setInt(2, quantidade);
            ps.setLong(3, servicoId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Remove um item da OS. */
    public boolean removerItem(long ordemId, long itemId) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM item_os WHERE id = ? AND ordem_servico_id = ?")) {
            ps.setLong(1, itemId);
            ps.setLong(2, ordemId);
            return ps.executeUpdate() > 0;
        }
    }

    private List<ItemOs> listarItens(Connection c, long ordemId) throws SQLException {
        String sql = """
                SELECT i.id, i.ordem_servico_id, i.servico_id, s.descricao, s.tipo,
                       i.quantidade, i.valor_unitario
                  FROM item_os i
                  JOIN servico s ON s.id = i.servico_id
                 WHERE i.ordem_servico_id = ?
                 ORDER BY i.id
                """;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, ordemId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ItemOs> itens = new ArrayList<>();
                while (rs.next()) {
                    itens.add(new ItemOs(
                            rs.getLong("id"),
                            rs.getLong("ordem_servico_id"),
                            rs.getLong("servico_id"),
                            rs.getString("descricao"),
                            rs.getString("tipo"),
                            rs.getInt("quantidade"),
                            rs.getBigDecimal("valor_unitario")));
                }
                return itens;
            }
        }
    }

    // ------------------------------------------------------------------ apoio

    private static void setIntOuNulo(PreparedStatement ps, int posicao, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(posicao, Types.INTEGER);
        } else {
            ps.setInt(posicao, valor);
        }
    }

    private static OrdemServico mapear(ResultSet rs, List<ItemOs> itens) throws SQLException {
        Timestamp abertura = rs.getTimestamp("data_abertura");
        Timestamp conclusao = rs.getTimestamp("data_conclusao");
        int km = rs.getInt("km_atual");
        BigDecimal total = rs.getBigDecimal("valor_total");

        return new OrdemServico(
                rs.getLong("id"),
                rs.getLong("veiculo_id"),
                rs.getString("placa"),
                rs.getString("marca") + " " + rs.getString("modelo"),
                rs.getString("cliente_nome"),
                rs.getString("status"),
                rs.getString("descricao_problema"),
                rs.wasNull() ? null : km,
                abertura == null ? null : abertura.toLocalDateTime(),
                conclusao == null ? null : conclusao.toLocalDateTime(),
                rs.getString("observacoes"),
                total == null ? BigDecimal.ZERO : total,
                itens);
    }

    /** Usado pelo relatório de faturamento por período. */
    public record Faturamento(String status, long quantidade, BigDecimal total) {
    }

    /** Consulta agregada: total por status (demonstra GROUP BY). */
    public List<Faturamento> resumoPorStatus() throws SQLException {
        String sql = """
                SELECT os.status,
                       COUNT(DISTINCT os.id) AS qtd,
                       COALESCE(SUM(i.quantidade * i.valor_unitario), 0) AS total
                  FROM ordem_servico os
                  LEFT JOIN item_os i ON i.ordem_servico_id = os.id
                 GROUP BY os.status
                 ORDER BY os.status
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Faturamento> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(new Faturamento(rs.getString("status"), rs.getLong("qtd"), rs.getBigDecimal("total")));
            }
            return lista;
        }
    }
}
