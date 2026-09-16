package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.http.Json;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Consultas agregadas da tela inicial. Nenhum CRUD aqui: só leitura, e de
 * propósito toda a conta é feita no banco (COUNT, SUM, AVG, GROUP BY) em vez de
 * trazer as linhas para somar no Java.
 */
public class DashboardDao {

    /** Os números grandes do topo da tela, todos em uma ida só ao banco. */
    public Map<String, Object> indicadores() throws SQLException {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM ordem_servico
                    WHERE status IN ('ABERTA', 'EM_ANDAMENTO'))                       AS os_abertas,
                  (SELECT COUNT(*) FROM ordem_servico WHERE status = 'CONCLUIDA')     AS os_concluidas,
                  (SELECT COUNT(*) FROM cliente)                                      AS clientes,
                  (SELECT COUNT(*) FROM veiculo)                                      AS veiculos,
                  (SELECT COUNT(*) FROM servico WHERE ativo = 1)                      AS servicos_ativos,
                  (SELECT COALESCE(SUM(i.quantidade * i.valor_unitario), 0)
                     FROM item_os i
                     JOIN ordem_servico o ON o.id = i.ordem_servico_id
                    WHERE o.status = 'CONCLUIDA')                                     AS faturamento,
                  (SELECT COALESCE(SUM(i.quantidade * i.valor_unitario), 0)
                     FROM item_os i
                     JOIN ordem_servico o ON o.id = i.ordem_servico_id
                    WHERE o.status IN ('ABERTA', 'EM_ANDAMENTO'))                     AS em_aberto
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            long concluidas = rs.getLong("os_concluidas");
            java.math.BigDecimal faturamento = rs.getBigDecimal("faturamento");
            return Json.obj(
                    "osAbertas", rs.getLong("os_abertas"),
                    "osConcluidas", concluidas,
                    "clientes", rs.getLong("clientes"),
                    "veiculos", rs.getLong("veiculos"),
                    "servicosAtivos", rs.getLong("servicos_ativos"),
                    "faturamento", faturamento,
                    "emAberto", rs.getBigDecimal("em_aberto"),
                    "ticketMedio", concluidas == 0
                            ? java.math.BigDecimal.ZERO
                            : faturamento.divide(java.math.BigDecimal.valueOf(concluidas), 2,
                                    java.math.RoundingMode.HALF_UP));
        }
    }

    /** Movimento dos últimos 6 meses, para o gráfico de barras. */
    public List<Map<String, Object>> faturamentoPorMes() throws SQLException {
        String sql = """
                SELECT DATE_FORMAT(o.data_abertura, '%Y-%m')                   AS mes,
                       COUNT(DISTINCT o.id)                                    AS quantidade,
                       COALESCE(SUM(i.quantidade * i.valor_unitario), 0)        AS total
                  FROM ordem_servico o
                  LEFT JOIN item_os i ON i.ordem_servico_id = o.id
                 WHERE o.data_abertura >= DATE_SUB(CURDATE(), INTERVAL 5 MONTH)
                 GROUP BY mes
                 ORDER BY mes
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(Json.obj(
                        "mes", rs.getString("mes"),
                        "quantidade", rs.getLong("quantidade"),
                        "total", rs.getBigDecimal("total")));
            }
            return lista;
        }
    }

    /** Os 5 itens que mais faturam — JOIN com o catálogo e GROUP BY. */
    public List<Map<String, Object>> topServicos() throws SQLException {
        String sql = """
                SELECT s.descricao, s.tipo,
                       SUM(i.quantidade)                        AS quantidade,
                       SUM(i.quantidade * i.valor_unitario)     AS total
                  FROM item_os i
                  JOIN servico s ON s.id = i.servico_id
                 GROUP BY s.id, s.descricao, s.tipo
                 ORDER BY total DESC
                 LIMIT 5
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(Json.obj(
                        "descricao", rs.getString("descricao"),
                        "tipo", rs.getString("tipo"),
                        "quantidade", rs.getLong("quantidade"),
                        "total", rs.getBigDecimal("total")));
            }
            return lista;
        }
    }
}
