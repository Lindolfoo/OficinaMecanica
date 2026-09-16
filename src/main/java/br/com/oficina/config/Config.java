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

    /**
     * Endereço em que o servidor aceita conexões. 127.0.0.1 atende só este computador;
     * 0.0.0.0 libera a rede, o que só é seguro quando a aplicação tiver login.
     */
    public static String host() {
        return get("server.host", "HOST", "127.0.0.1");
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

    // ------------------------------------------------------------------ autenticação

    /** Minutos de inatividade até a sessão cair sozinha. */
    public static int sessaoMinutos() {
        return Integer.parseInt(get("auth.sessaoMinutos", "SESSAO_MINUTOS", "30"));
    }

    /**
     * Marca o cookie de sessão como Secure (só trafega em HTTPS). Fica desligado
     * por padrão porque em sala a aplicação roda em http://localhost — com Secure
     * ligado, o navegador simplesmente não guardaria o cookie. Publicou em HTTPS,
     * ligue.
     */
    public static boolean cookieSeguro() {
        return Boolean.parseBoolean(get("auth.cookieSeguro", "COOKIE_SEGURO", "false"));
    }

    /** Dados do administrador criado automaticamente na primeira execução. */
    public static String adminNome() {
        return get("auth.adminNome", "ADMIN_NOME", "Administrador");
    }

    public static String adminEmail() {
        return get("auth.adminEmail", "ADMIN_EMAIL", "admin@oficina.local");
    }

    public static String adminSenha() {
        return get("auth.adminSenha", "ADMIN_SENHA", "oficina2026");
    }
}
