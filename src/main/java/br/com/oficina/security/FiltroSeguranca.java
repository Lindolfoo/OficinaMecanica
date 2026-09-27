package br.com.oficina.security;

import br.com.oficina.config.Config;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Cabeçalhos de segurança em toda resposta. São instruções para o navegador
 * fechar portas que a aplicação sozinha não controla.
 *
 * | Cabeçalho                | Fecha qual porta |
 * |--------------------------|------------------|
 * | Content-Security-Policy  | Só carrega script/CSS/imagem do próprio servidor. Mesmo que alguém consiga injetar um <script src="site-do-atacante">, o navegador recusa. |
 * | X-Content-Type-Options   | Proíbe o navegador de "adivinhar" o tipo do arquivo e executar como script algo que veio como texto. |
 * | X-Frame-Options          | Ninguém embute o sistema dentro de um iframe para enganar o usuário (clickjacking). |
 * | Referrer-Policy          | Não vaza a URL interna ao clicar em link para fora. |
 * | Permissions-Policy       | Desliga câmera, microfone e localização — o sistema não usa nada disso. |
 */
public final class FiltroSeguranca extends Filter {

    /**
     * 'unsafe-inline' aparece só em style-src porque o Bootstrap escreve estilo
     * direto no elemento ao abrir modais e toasts. Em script-src, que é o que
     * realmente importa contra XSS, a política continua fechada em 'self'.
     */
    private static final String CSP = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self' 'unsafe-inline'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'",
            "base-uri 'none'",
            "object-src 'none'");

    @Override
    public void doFilter(HttpExchange ex, Chain chain) throws IOException {
        if (!hostConfiavel(ex)) {
            byte[] corpo = "Host não permitido".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(403, corpo.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(corpo);
            }
            return;
        }

        Headers h = ex.getResponseHeaders();
        h.set("Content-Security-Policy", CSP);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        h.set("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        chain.doFilter(ex);
    }

    /**
     * Recusa requisição cujo cabeçalho Host não seja o próprio computador.
     *
     * O ataque que isso impede chama-se DNS rebinding: um site qualquer aponta
     * o próprio domínio para 127.0.0.1 e passa a conversar com esta aplicação.
     * Nesse caso o navegador trata tudo como MESMA ORIGEM, então nem o
     * SameSite=Strict nem o token anti-CSRF protegem — a página do atacante
     * conseguiria ler o token. Conferir o Host corta o ataque na porta.
     *
     * Quando o servidor é iniciado de propósito para a rede (HOST=0.0.0.0),
     * a conferência é dispensada: ali o nome de acesso é mesmo o da máquina.
     */
    private static boolean hostConfiavel(HttpExchange ex) {
        String configurado = Config.host();
        if (!configurado.startsWith("127.") && !configurado.equals("localhost")) {
            return true;                        // modo rede, documentado no README
        }
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) {
            return false;
        }
        String nome = host.startsWith("[")      // IPv6 vem como [::1]:8080
                ? host.substring(0, host.indexOf(']') + 1)
                : host.split(":")[0];
        return LOCAIS.contains(nome.toLowerCase());
    }

    private static final Set<String> LOCAIS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    @Override
    public String description() {
        return "Cabeçalhos de segurança e conferência do Host";
    }
}
