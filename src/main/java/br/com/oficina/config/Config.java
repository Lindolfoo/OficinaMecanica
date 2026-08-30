package br.com.oficina.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuração da aplicação.
 * Prioridade: variável de ambiente > config.properties (classpath) > valor padrão.
 */
public final class Config {

    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = Config.class.getResourceAsStream("/config.properties")) {
            if (in != null) {
                PROPS.load(in);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler config.properties", e);
        }
    }

    private Config() {
    }

    public static String get(String chave, String variavelAmbiente, String padrao) {
        String env = System.getenv(variavelAmbiente);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        return PROPS.getProperty(chave, padrao);
    }

    public static int port() {
        return Integer.parseInt(get("server.port", "PORT", "8080"));
    }

    public static String dbUrl() {
        return get("db.url", "DB_URL",
                "jdbc:mysql://localhost:3306/oficina?sslMode=DISABLED&allowPublicKeyRetrieval=true&characterEncoding=UTF-8");
    }

    public static String dbUser() {
        return get("db.user", "DB_USER", "root");
    }

    public static String dbPassword() {
        return get("db.password", "DB_PASSWORD", "root");
    }

    public static boolean dbAutoSchema() {
        return Boolean.parseBoolean(get("db.autoSchema", "DB_AUTO_SCHEMA", "true"));
    }
}
