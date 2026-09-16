package br.com.oficina.security;

import br.com.oficina.http.HttpUtil;
import br.com.oficina.http.Json;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Porteiro da aplicação: nada passa sem sessão válida.
 *
 * O mesmo filtro atende os dois tipos de rota, porque a resposta a quem não
 * está logado precisa ser diferente em cada caso:
 *
 *   modo API     -> responde 401 em JSON, para o JavaScript tratar
 *   modo PÁGINA  -> redireciona o navegador para a tela de login
 *
 * Além da sessão, o filtro confere o token anti-CSRF em toda requisição que
 * altera dados (POST/PUT/DELETE). Ver {@link #exigirTokenCsrf}.
 */
public final class FiltroAutenticacao extends Filter {

    /** Rotas de API liberadas: são justamente as que servem para entrar. */
    private static final Set<String> API_PUBLICA = Set.of("/api/auth/login", "/api/auth/sessao");

    /** Páginas e arquivos liberados: a tela de login e o que ela precisa para se desenhar. */
    private static final Set<String> PAGINA_PUBLICA = Set.of("/login.html", "/favicon.ico");
    private static final List<String> PREFIXO_PUBLICO = List.of("/css/", "/js/", "/vendor/", "/img/");

    /** Atributo em que a sessão fica disponível para os handlers. */
    public static final String ATRIBUTO_SESSAO = "sessao";

    private final boolean modoApi;

    public FiltroAutenticacao(boolean modoApi) {
        this.modoApi = modoApi;
    }

    @Override
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        String caminho = ex.getRequestURI().getPath();
        Optional<Sessoes.Sessao> sessao = Sessoes.valida(Sessoes.lerCookie(ex, Sessoes.COOKIE));

        if (sessao.isPresent()) {
            ex.setAttribute(ATRIBUTO_SESSAO, sessao.get());
        }

        if (modoApi) {
            filtrarApi(ex, chain, caminho, sessao);
        } else {
            filtrarPagina(ex, chain, caminho, sessao);
        }
    }

    // ------------------------------------------------------------------ API

    private void filtrarApi(HttpExchange ex, Chain chain, String caminho,
                            Optional<Sessoes.Sessao> sessao) throws IOException {
        if (API_PUBLICA.contains(caminho)) {
            chain.doFilter(ex);
            return;
        }
        if (sessao.isEmpty()) {
            HttpUtil.sendJson(ex, 401, Json.obj("erro", "Sessão expirada. Entre novamente."));
            return;
        }
        if (alteraDados(ex) && !exigirTokenCsrf(ex, sessao.get())) {
            HttpUtil.sendJson(ex, 403, Json.obj("erro", "Requisição sem token de segurança (CSRF)."));
            return;
        }
        chain.doFilter(ex);
    }

    /**
     * Confere o token anti-CSRF.
     *
     * O ataque que isso impede: um site qualquer, aberto em outra aba, dispara
     * um POST para /api/clientes. O navegador mandaria junto o cookie da sessão
     * e o servidor obedeceria, achando que foi o usuário. O token muda isso —
     * ele não viaja no cookie, mas num cabeçalho que só o JavaScript desta
     * aplicação sabe preencher, e a política de origem do navegador impede o
     * site do atacante de lê-lo.
     */
    private static boolean exigirTokenCsrf(HttpExchange ex, Sessoes.Sessao sessao) {
        String enviado = ex.getRequestHeaders().getFirst("X-CSRF-Token");
        if (enviado == null) {
            return false;
        }
        // comparação em tempo constante, pelo mesmo motivo das senhas
        return MessageDigest.isEqual(enviado.getBytes(StandardCharsets.UTF_8),
                sessao.csrf().getBytes(StandardCharsets.UTF_8));
    }

    private static boolean alteraDados(HttpExchange ex) {
        String metodo = ex.getRequestMethod();
        return "POST".equals(metodo) || "PUT".equals(metodo) || "DELETE".equals(metodo);
    }

    // ------------------------------------------------------------------ páginas

    private void filtrarPagina(HttpExchange ex, Chain chain, String caminho,
                               Optional<Sessoes.Sessao> sessao) throws IOException {
        boolean publica = PAGINA_PUBLICA.contains(caminho)
                || PREFIXO_PUBLICO.stream().anyMatch(caminho::startsWith);

        if (sessao.isPresent() && caminho.equals("/login.html")) {
            redirecionar(ex, "/");              // já entrou: não faz sentido ver o login de novo
            return;
        }
        if (sessao.isEmpty() && !publica) {
            redirecionar(ex, "/login.html");
            return;
        }
        chain.doFilter(ex);
    }

    private static void redirecionar(HttpExchange ex, String destino) throws IOException {
        ex.getResponseHeaders().set("Location", destino);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    @Override
    public String description() {
        return modoApi ? "Exige sessão e token CSRF na API" : "Exige sessão nas páginas";
    }
}
