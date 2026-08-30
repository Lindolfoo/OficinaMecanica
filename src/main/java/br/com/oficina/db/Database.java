package br.com.oficina.db;

import br.com.oficina.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco via JDBC puro (java.sql). Nenhum ORM, nenhum pool de conexões:
 * cada operação abre e fecha sua própria conexão com try-with-resources.
 */
public final class Database {

    private Database() {
    }

    /** Abre uma nova conexão com o schema configurado (db.url). */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(Config.dbUrl(), Config.dbUser(), Config.dbPassword());
    }

    /**
     * Executa sql/01_schema.sql (empacotado no jar). O script é idempotente
     * (CREATE ... IF NOT EXISTS), então pode rodar em toda subida da aplicação.
     * Conecta na raiz do servidor (sem schema) para permitir o CREATE DATABASE.
     */
    public static void ensureSchema() throws SQLException, IOException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("/sql/01_schema.sql")) {
            if (in == null) {
                throw new IOException("Arquivo sql/01_schema.sql não encontrado no classpath");
            }
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // "jdbc:mysql://host:3306/oficina?x=y" -> "jdbc:mysql://host:3306/?x=y"
        String urlServidor = Config.dbUrl().replaceFirst("://([^/]+)/[^?]*", "://$1/");

        try (Connection c = DriverManager.getConnection(urlServidor, Config.dbUser(), Config.dbPassword());
             Statement st = c.createStatement()) {
            for (String comando : dividirComandos(script)) {
                st.execute(comando);
            }
        }
    }

    /** Remove comentários de linha e divide o script em comandos pelo ';'. */
    static List<String> dividirComandos(String script) {
        StringBuilder semComentarios = new StringBuilder();
        for (String linha : script.split("\\R")) {
            if (!linha.strip().startsWith("--")) {
                semComentarios.append(linha).append('\n');
            }
        }
        List<String> comandos = new ArrayList<>();
        for (String parte : semComentarios.toString().split(";")) {
            // remove comentários no fim das linhas (ex.: "cpf CHAR(11), -- somente dígitos")
            String limpo = parte.replaceAll("--[^\\n]*", "").strip();
            if (!limpo.isEmpty()) {
                comandos.add(limpo);
            }
        }
        return comandos;
    }
}
