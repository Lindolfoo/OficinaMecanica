package br.com.oficina;

import br.com.oficina.api.AuthHandler;
import br.com.oficina.api.ClienteHandler;
import br.com.oficina.api.DashboardHandler;
import br.com.oficina.api.OrdemServicoHandler;
import br.com.oficina.api.ServicoHandler;
import br.com.oficina.api.UsuarioHandler;
import br.com.oficina.api.VeiculoHandler;
import br.com.oficina.config.Config;
import br.com.oficina.dao.UsuarioDao;
import br.com.oficina.db.Database;
import br.com.oficina.http.StaticHandler;
import br.com.oficina.security.FiltroAutenticacao;
import br.com.oficina.security.FiltroSeguranca;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ponto de entrada. Sobe um servidor HTTP do próprio JDK (com.sun.net.httpserver)
 * que serve o front-end estático e a API JSON. Sem frameworks.
 */
public final class App {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Oficina Mecânica - NP1 Banco de Dados ===");

        // 1) Banco: garante o schema e testa a conexão antes de aceitar requisições
        try {
            if (Config.dbAutoSchema()) {
                Database.ensureSchema();
                System.out.println("Schema verificado (sql/01_schema.sql).");
            }
            try (Connection c = Database.getConnection()) {
                System.out.println("Conectado ao banco: " + c.getMetaData().getDatabaseProductName()
                        + " " + c.getMetaData().getDatabaseProductVersion());
            }
            criarAdminSeNecessario();
        } catch (SQLException e) {
            System.err.println("ERRO: não foi possível conectar ao MySQL -> " + e.getMessage());
            System.err.println("Verifique se o MySQL está no ar (docker compose up -d) e as credenciais em"
                    + " src/main/resources/config.properties ou nas variáveis DB_URL / DB_USER / DB_PASSWORD.");
            System.exit(1);
        }

        // 2) Rotas
        HttpServer server = HttpServer.create(new InetSocketAddress(Config.host(), Config.port()), 0);

        Map<String, HttpHandler> api = new LinkedHashMap<>();
        api.put("/api/auth", new AuthHandler());
        api.put("/api/usuarios", new UsuarioHandler());
        api.put("/api/dashboard", new DashboardHandler());
        api.put("/api/clientes", new ClienteHandler());
        api.put("/api/veiculos", new VeiculoHandler());
        api.put("/api/servicos", new ServicoHandler());
        api.put("/api/ordens", new OrdemServicoHandler());

        // 3) Filtros: cabeçalhos de segurança em tudo; sessão exigida na API e nas páginas.
        //    A ordem importa — o porteiro só é chamado depois que os cabeçalhos já estão postos.
        FiltroSeguranca seguranca = new FiltroSeguranca();
        for (Map.Entry<String, HttpHandler> rota : api.entrySet()) {
            HttpContext contexto = server.createContext(rota.getKey(), rota.getValue());
            contexto.getFilters().add(seguranca);
            contexto.getFilters().add(new FiltroAutenticacao(true));
        }
        HttpContext estatico = server.createContext("/", new StaticHandler());
        estatico.getFilters().add(seguranca);
        estatico.getFilters().add(new FiltroAutenticacao(false));

        // 4) Uma thread virtual por requisição (Java 21)
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("Servidor no ar: http://localhost:" + Config.port());
        if (!Config.host().startsWith("127.")) {
            System.out.println("ATENÇÃO: aceitando conexões de outros computadores (" + Config.host() + ").");
        }
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    /**
     * Na primeira execução a tabela usuario está vazia e ninguém conseguiria
     * entrar. Criamos então o administrador padrão — e avisamos, bem alto, que
     * a senha precisa ser trocada.
     */
    private static void criarAdminSeNecessario() throws SQLException {
        boolean criou = new UsuarioDao().garantirAdminPadrao(
                Config.adminNome(), Config.adminEmail(), Config.adminSenha());
        if (criou) {
            System.out.println();
            System.out.println("  Primeiro acesso — administrador criado:");
            System.out.println("      e-mail: " + Config.adminEmail());
            System.out.println("      senha:  " + Config.adminSenha());
            System.out.println("  Troque essa senha no menu do usuário assim que entrar.");
            System.out.println();
        }
    }
}
