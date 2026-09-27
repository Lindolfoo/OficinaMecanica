package br.com.oficina.api;

import br.com.oficina.db.Backup;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.http.Json;
import br.com.oficina.security.FiltroAutenticacao;
import br.com.oficina.security.Sessoes;
import com.sun.net.httpserver.HttpExchange;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Recurso /api/backup — cópias de segurança. Só perfil ADMIN.
 *
 *   GET  /api/backup   lista as cópias e diz onde ficam
 *   POST /api/backup   faz uma cópia agora
 */
public class BackupHandler extends BaseHandler {

    @Override
    protected void get(HttpExchange ex) throws Exception {
        exigirAdmin(ex);
        HttpUtil.sendJson(ex, 200, Json.obj(
                "pasta", Backup.pasta().toString(),
                "copias", emJson(Backup.listar())));
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        Sessoes.Sessao eu = exigirAdmin(ex);

        // POST /api/backup/restaurar — marca a cópia; a troca acontece na
        // próxima abertura, porque o banco está em uso agora.
        if (!HttpUtil.pathParts(ex).isEmpty() && HttpUtil.pathParts(ex).get(0).equals("restaurar")) {
            String nome = HttpUtil.readForm(ex).getOrDefault("arquivo", "");
            try {
                Backup.marcarParaRestaurar(nome);
            } catch (IllegalArgumentException e) {
                throw new ApiException(400, e.getMessage());
            } catch (java.sql.SQLException e) {
                throw new ApiException(400, "Cópia recusada: " + e.getMessage());
            }
            System.out.println("[backup] " + eu.email() + " marcou " + nome + " para restauração");
            HttpUtil.sendJson(ex, 200, Json.obj("mensagem",
                    "Cópia validada e marcada. Feche o programa e abra de novo para concluir "
                    + "a restauração. O banco atual será guardado antes da troca."));
            return;
        }

        Path criado = Backup.fazer();
        System.out.println("[backup] " + eu.email() + " gerou " + criado.getFileName());
        HttpUtil.sendJson(ex, 201, Json.obj(
                "mensagem", "Cópia de segurança criada.",
                "arquivo", criado.getFileName().toString(),
                "pasta", Backup.pasta().toString(),
                "copias", emJson(Backup.listar())));
    }

    private static List<Map<String, Object>> emJson(List<Backup.Info> copias) {
        List<Map<String, Object>> lista = new ArrayList<>();
        for (Backup.Info c : copias) {
            lista.add(Json.obj(
                    "nome", c.nome(),
                    "bytes", c.bytes(),
                    "quando", c.quando().toString()));
        }
        return lista;
    }

    private static Sessoes.Sessao exigirAdmin(HttpExchange ex) {
        Sessoes.Sessao s = (Sessoes.Sessao) ex.getAttribute(FiltroAutenticacao.ATRIBUTO_SESSAO);
        if (s == null) {
            throw new ApiException(401, "Sessão expirada. Entre novamente.");
        }
        if (!s.ehAdmin()) {
            throw new ApiException(403, "Só o perfil ADMIN faz cópias de segurança.");
        }
        return s;
    }
}
