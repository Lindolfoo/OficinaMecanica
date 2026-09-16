package br.com.oficina.security;

import br.com.oficina.config.Config;
import br.com.oficina.model.Usuario;
import com.sun.net.httpserver.HttpExchange;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sessões em memória. Quem entrou recebe um token aleatório em cookie; o
 * servidor guarda aqui de quem é esse token. Reiniciar a aplicação derruba
 * todo mundo — o que é aceitável (e até desejável) neste projeto.
 *
 * Por que token aleatório e não os dados do usuário no cookie: o cookie fica na
 * máquina do usuário e pode ser editado. Um cookie com "perfil=ADMIN" viraria
 * promoção instantânea. Com token, o cookie não diz nada — quem sabe quem é
 * cada token é o servidor.
 *
 * O cookie sai com três proteções:
 *   HttpOnly        JavaScript da página não consegue ler o token (blinda contra XSS)
 *   SameSite=Strict o navegador não manda o cookie em requisição vinda de outro site (CSRF)
 *   Secure          só trafega em HTTPS (ligado por configuração, ver auth.cookieSeguro)
 */
public final class Sessoes {

    public static final String COOKIE = "sessao";

    /** Tempo máximo de uma sessão, mesmo em uso constante. */
    private static final Duration VALIDADE_ABSOLUTA = Duration.ofHours(12);

    /** Quando o usuário marca "manter a senha", a sessão passa a valer por uma semana. */
    private static final Duration VALIDADE_LEMBRADO = Duration.ofDays(7);

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Map<String, Sessao> ATIVAS = new ConcurrentHashMap<>();
    private static final AtomicInteger ACESSOS = new AtomicInteger();

    private Sessoes() {
    }

    /** Uma sessão aberta. */
    public static final class Sessao {

        private final String token;
        private final String csrf;
        private final long usuarioId;
        private final String nome;
        private final String email;
        private final String perfil;
        private final Instant criadaEm;
        private final Duration janelaInatividade;
        private final Duration validadeTotal;
        private volatile Instant ultimoUso;

        private Sessao(String token, String csrf, Usuario u, boolean lembrar) {
            this.token = token;
            this.csrf = csrf;
            this.usuarioId = u.id();
            this.nome = u.nome();
            this.email = u.email();
            this.perfil = u.perfil();
            this.criadaEm = Instant.now();
            this.ultimoUso = this.criadaEm;
            this.janelaInatividade = lembrar
                    ? VALIDADE_LEMBRADO
                    : Duration.ofMinutes(Config.sessaoMinutos());
            this.validadeTotal = lembrar ? VALIDADE_LEMBRADO : VALIDADE_ABSOLUTA;
        }

        /** Quanto tempo o cookie deve durar no navegador, em segundos. */
        public long segundosDeVida() {
            return validadeTotal.toSeconds();
        }

        public String token() {
            return token;
        }

        /** Token anti-CSRF: este o JavaScript PRECISA ler, então vai no corpo da resposta. */
        public String csrf() {
            return csrf;
        }

        public long usuarioId() {
            return usuarioId;
        }

        public String nome() {
            return nome;
        }

        public String email() {
            return email;
        }

        public String perfil() {
            return perfil;
        }

        public boolean ehAdmin() {
            return Usuario.ADMIN.equals(perfil);
        }
    }

    /**
     * Abre uma sessão nova para o usuário que acabou de entrar.
     * Sempre com token novo: reaproveitar um token já existente abriria a porta
     * para fixação de sessão (o atacante planta o token antes do login).
     */
    public static Sessao criar(Usuario u, boolean lembrar) {
        Sessao s = new Sessao(tokenAleatorio(), tokenAleatorio(), u, lembrar);
        ATIVAS.put(s.token(), s);
        return s;
    }

    /** Devolve a sessão do token, se ainda valer. Renova a janela de inatividade. */
    public static Optional<Sessao> valida(String token) {
        limpezaPeriodica();
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Sessao s = ATIVAS.get(token);
        if (s == null) {
            return Optional.empty();
        }
        if (expirou(s)) {
            ATIVAS.remove(token);
            return Optional.empty();
        }
        s.ultimoUso = Instant.now();
        return Optional.of(s);
    }

    public static void encerrar(String token) {
        if (token != null) {
            ATIVAS.remove(token);
        }
    }

    /**
     * Derruba todas as sessões de um usuário. Chamado quando a conta é excluída,
     * inativada, tem o perfil alterado ou troca de senha: sem isso, uma sessão já
     * aberta continuaria valendo com os poderes antigos.
     */
    public static void encerrarDoUsuario(long usuarioId) {
        ATIVAS.values().removeIf(s -> s.usuarioId() == usuarioId);
    }

    public static int quantidadeAtiva() {
        return ATIVAS.size();
    }

    // ------------------------------------------------------------------ cookies

    public static String lerCookie(HttpExchange ex, String nome) {
        List<String> cabecalhos = ex.getRequestHeaders().get("Cookie");
        if (cabecalhos == null) {
            return null;
        }
        for (String cabecalho : cabecalhos) {
            for (String par : cabecalho.split(";")) {
                int i = par.indexOf('=');
                if (i > 0 && par.substring(0, i).trim().equals(nome)) {
                    return par.substring(i + 1).trim();
                }
            }
        }
        return null;
    }

    /** Manda o cookie da sessão. Precisa vir ANTES de sendResponseHeaders. */
    public static void enviarCookie(HttpExchange ex, Sessao sessao) {
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + sessao.token() + atributosDoCookie()
                + "; Max-Age=" + sessao.segundosDeVida());
    }

    /** Apaga o cookie no navegador (logout). */
    public static void expirarCookie(HttpExchange ex) {
        ex.getResponseHeaders().add("Set-Cookie", COOKIE + "=" + atributosDoCookie() + "; Max-Age=0");
    }

    private static String atributosDoCookie() {
        return "; Path=/; HttpOnly; SameSite=Strict" + (Config.cookieSeguro() ? "; Secure" : "");
    }

    // ------------------------------------------------------------------ apoio

    private static String tokenAleatorio() {
        byte[] bytes = new byte[32];            // 256 bits: não dá para adivinhar
        ALEATORIO.nextBytes(bytes);
        return B64.encodeToString(bytes);
    }

    private static boolean expirou(Sessao s) {
        Instant agora = Instant.now();
        return s.ultimoUso.plus(s.janelaInatividade).isBefore(agora)
                || s.criadaEm.plus(s.validadeTotal).isBefore(agora);
    }

    /** De tempos em tempos varre o mapa, para sessões abandonadas não se acumularem. */
    private static void limpezaPeriodica() {
        if (ACESSOS.incrementAndGet() % 200 == 0) {
            ATIVAS.values().removeIf(Sessoes::expirou);
        }
    }
}
