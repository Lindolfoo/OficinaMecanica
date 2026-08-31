package br.com.oficina;

import br.com.oficina.api.ClienteHandler;
import br.com.oficina.config.Config;
import br.com.oficina.db.Database;
import br.com.oficina.http.StaticHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
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
        } catch (SQLException e) {
            System.err.println("ERRO: não foi possível conectar ao MySQL -> " + e.getMessage());
            System.err.println("Verifique se o MySQL está no ar (docker compose up -d) e as credenciais em"
                    + " src/main/resources/config.properties ou nas variáveis DB_URL / DB_USER / DB_PASSWORD.");
            System.exit(1);
        }

        // 2) Rotas
        HttpServer server = HttpServer.create(new InetSocketAddress(Config.port()), 0);
        server.createContext("/api/clientes", new ClienteHandler());
        server.createContext("/", new StaticHandler());

        // 3) Uma thread virtual por requisição (Java 21)
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("Servidor no ar: http://localhost:" + Config.port());
        System.out.println("Pressione Ctrl+C para encerrar.");
    }
}
