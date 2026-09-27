package br.com.oficina.db;

import br.com.oficina.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acesso ao banco via JDBC puro (java.sql). Nenhum ORM, nenhum pool de conexões:
 * cada operação abre e fecha sua própria conexão com try-with-resources.
 *
 * O banco é um arquivo SQLite. Não há servidor para instalar nem credencial
 * para configurar — o "banco de dados" é oficina.db dentro da pasta de dados
 * do usuário (ver Config.pastaDados()).
 */
public final class Database {

    private Database() {
    }

    /**
     * Abre uma nova conexão com o arquivo do banco.
     *
     * Os dois PRAGMAs valem por CONEXÃO e precisam ser repetidos sempre:
     *
     *   foreign_keys = ON   No SQLite as chaves estrangeiras vêm DESLIGADAS.
     *                       Sem isto, excluir um cliente com veículo passaria
     *                       sem erro nenhum e as RN04, RN06 e RN15 virariam
     *                       letra morta — em silêncio, que é o pior caso.
     *
     *   busy_timeout        Quem escreve tranca o arquivo. Sem espera, uma
     *                       segunda operação simultânea falharia na hora com
     *                       "database is locked"; com o tempo limite, ela
     *                       aguarda a vez.
     */
    public static Connection getConnection() throws SQLException {
        Connection c = DriverManager.getConnection(Config.dbUrl());
        try (Statement st = c.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        } catch (SQLException e) {
            c.close();
            throw e;
        }
        return c;
    }

    /**
     * Cria a pasta de dados, aplica sql/01_schema.sql (empacotado no jar) e,
     * se o banco nasceu vazio, carrega os dados de exemplo.
     *
     * O script é idempotente (CREATE ... IF NOT EXISTS), então pode rodar em
     * toda subida da aplicação.
     */
    public static void ensureSchema() throws SQLException, IOException {
        Path arquivo = Path.of(Config.arquivoBanco().toString());
        if (arquivo.getParent() != null) {
            Files.createDirectories(arquivo.getParent());
        }

        try (Connection c = getConnection();
             Statement st = c.createStatement()) {

            // WAL fica gravado no próprio arquivo, então basta ligar uma vez.
            // Permite ler enquanto alguém escreve, em vez de travar os dois.
            st.execute("PRAGMA journal_mode = WAL");

            for (String comando : dividirComandos(ler("/sql/01_schema.sql"))) {
                st.execute(comando);
            }

            if (Config.dbSeedInicial() && vazio(st)) {
                for (String comando : dividirComandos(ler("/sql/02_seed.sql"))) {
                    st.execute(comando);
                }
                System.out.println("Banco novo: dados de exemplo carregados (sql/02_seed.sql).");
            }
        }
    }

    /** Onde o arquivo do banco está, para a aplicação informar na subida. */
    public static Path arquivo() {
        return Config.arquivoBanco();
    }

    private static boolean vazio(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cliente")) {
            return rs.next() && rs.getInt(1) == 0;
        }
    }

    private static String ler(String recurso) throws IOException {
        try (InputStream in = Database.class.getResourceAsStream(recurso)) {
            if (in == null) {
                throw new IOException("Arquivo " + recurso + " não encontrado no classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
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
            // remove comentários no fim das linhas (ex.: "cpf TEXT, -- somente dígitos")
            String limpo = parte.replaceAll("--[^\\n]*", "").strip();
            if (!limpo.isEmpty()) {
                comandos.add(limpo);
            }
        }
        return comandos;
    }
}
