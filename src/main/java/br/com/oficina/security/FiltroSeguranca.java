package br.com.oficina.security;

import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.IOException;

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
        Headers h = ex.getResponseHeaders();
        h.set("Content-Security-Policy", CSP);
        h.set("X-Content-Type-Options", "nosniff");
        h.set("X-Frame-Options", "DENY");
        h.set("Referrer-Policy", "same-origin");
        h.set("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        chain.doFilter(ex);
    }

    @Override
    public String description() {
        return "Cabeçalhos de segurança";
    }
}
