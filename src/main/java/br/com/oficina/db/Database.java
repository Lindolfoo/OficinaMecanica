package br.com.oficina.db;

import br.com.oficina.config.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
}
