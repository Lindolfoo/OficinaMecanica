package br.com.oficina.dao;

import br.com.oficina.db.Database;
import br.com.oficina.http.Json;
import br.com.oficina.util.Dinheiro;

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
                  (SELECT COALESCE(SUM(i.quantidade * i.valor_unitario_centavos), 0)
                     FROM item_os i
                     JOIN ordem_servico o ON o.id = i.ordem_servico_id
                    WHERE o.status = 'CONCLUIDA')                                     AS faturamento,
                  (SELECT COALESCE(SUM(i.quantidade * i.valor_unitario_centavos), 0)
                     FROM item_os i
                     JOIN ordem_servico o ON o.id = i.ordem_servico_id
                    WHERE o.status IN ('ABERTA', 'EM_ANDAMENTO'))                     AS em_aberto
                """;
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            long concluidas = rs.getLong("os_concluidas");
            long faturamentoCentavos = rs.getLong("faturamento");
            // O ticket médio é dividido em CENTAVOS e só então vira reais:
            // dividir depois de converter arrastaria erro de arredondamento.
            long ticketCentavos = concluidas == 0 ? 0 : Math.round((double) faturamentoCentavos / concluidas);
            return Json.obj(
                    "osAbertas", rs.getLong("os_abertas"),
                    "osConcluidas", concluidas,
                    "clientes", rs.getLong("clientes"),
                    "veiculos", rs.getLong("veiculos"),
                    "servicosAtivos", rs.getLong("servicos_ativos"),
                    "faturamento", Dinheiro.deCentavos(faturamentoCentavos),
                    "emAberto", Dinheiro.deCentavos(rs.getLong("em_aberto")),
                    "ticketMedio", Dinheiro.deCentavos(ticketCentavos));
        }
    }

    /** Movimento dos últimos 6 meses, para o gráfico de barras. */
    public List<Map<String, Object>> faturamentoPorMes() throws SQLException {
        String sql = """
                SELECT strftime('%Y-%m', o.data_abertura)                              AS mes,
                       COUNT(DISTINCT o.id)                                            AS quantidade,
                       COALESCE(SUM(i.quantidade * i.valor_unitario_centavos), 0)       AS total
                  FROM ordem_servico o
                  LEFT JOIN item_os i ON i.ordem_servico_id = o.id
                 WHERE o.data_abertura >= date('now', 'localtime', '-5 months', 'start of month')
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
                        "total", Dinheiro.deCentavos(rs.getLong("total"))));
            }
            return lista;
        }
    }

    /** Os 5 itens que mais faturam — JOIN com o catálogo e GROUP BY. */
    public List<Map<String, Object>> topServicos() throws SQLException {
        String sql = """
                SELECT s.descricao, s.tipo,
                       SUM(i.quantidade)                              AS quantidade,
                       SUM(i.quantidade * i.valor_unitario_centavos)  AS total
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
                        "total", Dinheiro.deCentavos(rs.getLong("total"))));
            }
            return lista;
        }
    }
}
