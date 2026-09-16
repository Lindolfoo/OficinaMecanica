package br.com.oficina.api;

import br.com.oficina.dao.DashboardDao;
import br.com.oficina.dao.OrdemServicoDao;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.http.Json;
import com.sun.net.httpserver.HttpExchange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recurso /api/dashboard — tudo o que a tela inicial precisa, em uma resposta só
 * (indicadores, distribuição por status, movimento por mês e itens que mais
 * faturam). Uma única chamada evita a tela piscar em quatro etapas.
 */
public class DashboardHandler extends BaseHandler {

    private final DashboardDao dao = new DashboardDao();
    private final OrdemServicoDao ordemDao = new OrdemServicoDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        List<Map<String, Object>> porStatus = new ArrayList<>();
        for (OrdemServicoDao.Faturamento f : ordemDao.resumoPorStatus()) {
            porStatus.add(Json.obj("status", f.status(), "quantidade", f.quantidade(), "total", f.total()));
        }

        HttpUtil.sendJson(ex, 200, Json.obj(
                "indicadores", dao.indicadores(),
                "porStatus", porStatus,
                "porMes", dao.faturamentoPorMes(),
                "topServicos", dao.topServicos(),
                "ultimasOrdens", ordemDao.listar(null, null, null).stream().limit(5).toList()));
    }
}
