package br.com.oficina.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
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

    /**
     * Pasta onde ficam banco, logs e backups.
     *
     * NÃO pode ser a pasta do programa: instalado em C:\Program Files, o
     * Windows recusa a gravação e o banco não abriria. Cada sistema tem o
     * seu lugar certo para dado de aplicativo, e é esse que usamos.
     */
    public static Path pastaDados() {
        String configurado = get("db.pasta", "OFICINA_DADOS", "");
        if (!configurado.isBlank()) {
            return Path.of(configurado);
        }
        String so = System.getProperty("os.name", "").toLowerCase();
        String casa = System.getProperty("user.home");
        if (so.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return Path.of(local == null || local.isBlank() ? casa : local, "OficinaMecanica");
        }
        if (so.contains("mac")) {
            return Path.of(casa, "Library", "Application Support", "OficinaMecanica");
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        return xdg == null || xdg.isBlank()
                ? Path.of(casa, ".local", "share", "OficinaMecanica")
                : Path.of(xdg, "OficinaMecanica");
    }

    /** Arquivo do banco. Um arquivo só, que é todo o banco de dados. */
    public static Path arquivoBanco() {
        return pastaDados().resolve("oficina.db");
    }

    public static String dbUrl() {
        String url = get("db.url", "DB_URL", "");
        return url.isBlank() ? "jdbc:sqlite:" + arquivoBanco() : url;
    }

    public static boolean dbAutoSchema() {
        return Boolean.parseBoolean(get("db.autoSchema", "DB_AUTO_SCHEMA", "true"));
    }

    /** Carrega os dados de exemplo quando o banco nasce vazio. */
    public static boolean dbSeedInicial() {
        return Boolean.parseBoolean(get("db.seedInicial", "DB_SEED_INICIAL", "true"));
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

}
