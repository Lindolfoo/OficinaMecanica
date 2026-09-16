package br.com.oficina.api;

import br.com.oficina.dao.UsuarioDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.http.Json;
import br.com.oficina.model.Usuario;
import br.com.oficina.security.FiltroAutenticacao;
import br.com.oficina.security.Senhas;
import br.com.oficina.security.Sessoes;
import br.com.oficina.util.Validators;
import com.sun.net.httpserver.HttpExchange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Recurso /api/auth — entrar, sair e trocar a própria senha.
 *
 *   POST /api/auth/login     entra   (form: email, senha)
 *   POST /api/auth/logout    sai
 *   GET  /api/auth/sessao    quem está logado (401 se ninguém)
 *   POST /api/auth/senha     troca a própria senha (form: senhaAtual, novaSenha)
 */
public class AuthHandler extends BaseHandler {

    private final UsuarioDao dao = new UsuarioDao();

    /** Mensagem única para senha errada, e-mail inexistente ou conta inativa. */
    private static final String CREDENCIAL_INVALIDA = "E-mail ou senha inválidos.";

    @Override
    protected void get(HttpExchange ex) throws Exception {
        if (!rota(ex).equals("sessao")) {
            throw new ApiException(404, "Rota não encontrada");
        }
        Sessoes.Sessao s = (Sessoes.Sessao) ex.getAttribute(FiltroAutenticacao.ATRIBUTO_SESSAO);
        if (s == null) {
            throw new ApiException(401, "Ninguém autenticado");
        }
        HttpUtil.sendJson(ex, 200, corpoDaSessao(s));
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        switch (rota(ex)) {
            case "login" -> login(ex);
            case "logout" -> logout(ex);
            case "senha" -> trocarPropriaSenha(ex);
            default -> throw new ApiException(404, "Rota não encontrada");
        }
    }

    // ------------------------------------------------------------------ entrar

    private void login(HttpExchange ex) throws Exception {
        String ip = enderecoDe(ex);
        if (!Pedidos.aceitar(ip)) {
            throw new ApiException(429, "Tentativas demais deste computador. Aguarde um minuto.");
        }

        Map<String, String> f = HttpUtil.readForm(ex);
        String email = f.getOrDefault("email", "").trim().toLowerCase();
        String senha = f.getOrDefault("senha", "");

        Optional<Usuario> achado = dao.buscarPorEmail(email);
        if (achado.isEmpty()) {
            // Gasta o mesmo tempo de quem existe, para o relógio não denunciar
            // quais e-mails estão cadastrados.
            Senhas.gastarTempoDeVerificacao();
            throw new ApiException(401, CREDENCIAL_INVALIDA);
        }

        Usuario u = achado.get();
        if (u.bloqueado()) {
            throw new ApiException(429, "Conta bloqueada por tentativas seguidas de senha errada. "
                    + "Tente de novo em alguns minutos.");
        }
        if (!u.ativo()) {
            throw new ApiException(401, CREDENCIAL_INVALIDA);
        }
        if (!Senhas.confere(senha, u.senhaHash())) {
            dao.registrarErroDeSenha(u.id());
            System.out.println("[auth] senha incorreta para " + email + " (origem " + ip + ")");
            throw new ApiException(401, CREDENCIAL_INVALIDA);
        }

        dao.registrarAcertoDeSenha(u.id());
        Pedidos.zerar(ip);
        // "Manter a senha" marcado: a sessão dura uma semana em vez de meia hora parada.
        boolean lembrar = "true".equalsIgnoreCase(f.getOrDefault("manterConectado", "false"));
        Sessoes.Sessao s = Sessoes.criar(u, lembrar);
        Sessoes.enviarCookie(ex, s);
        System.out.println("[auth] " + email + " entrou (origem " + ip + ")");
        HttpUtil.sendJson(ex, 200, corpoDaSessao(s));
    }

    // ------------------------------------------------------------------ sair

    private void logout(HttpExchange ex) throws Exception {
        Sessoes.encerrar(Sessoes.lerCookie(ex, Sessoes.COOKIE));
        Sessoes.expirarCookie(ex);
        HttpUtil.sendJson(ex, 200, Json.obj("mensagem", "Sessão encerrada"));
    }

    // ------------------------------------------------------------------ trocar a própria senha

    private void trocarPropriaSenha(HttpExchange ex) throws Exception {
        Sessoes.Sessao s = (Sessoes.Sessao) ex.getAttribute(FiltroAutenticacao.ATRIBUTO_SESSAO);
        Map<String, String> f = HttpUtil.readForm(ex);
        String atual = f.getOrDefault("senhaAtual", "");
        String nova = f.getOrDefault("novaSenha", "");

        Usuario u = dao.buscarPorId(s.usuarioId())
                .orElseThrow(() -> new ApiException(404, "Usuário não encontrado"));
        if (!Senhas.confere(atual, u.senhaHash())) {
            throw new ApiException(400, "A senha atual está incorreta.");
        }
        if (!Validators.senhaForte(nova)) {
            throw new ApiException(400, Validators.REGRA_DA_SENHA);
        }
        if (Senhas.confere(nova, u.senhaHash())) {
            throw new ApiException(400, "A nova senha precisa ser diferente da atual.");
        }

        dao.alterarSenha(u.id(), nova);
        // Trocou a senha: derruba as outras sessões e abre uma nova aqui.
        Sessoes.encerrarDoUsuario(u.id());
        Sessoes.Sessao sessaoNova = Sessoes.criar(u, false);
        Sessoes.enviarCookie(ex, sessaoNova);
        System.out.println("[auth] " + u.email() + " trocou a própria senha");
        HttpUtil.sendJson(ex, 200, corpoDaSessao(sessaoNova));
    }

    // ------------------------------------------------------------------ apoio

    private static Map<String, Object> corpoDaSessao(Sessoes.Sessao s) {
        return Json.obj(
                "id", s.usuarioId(),
                "nome", s.nome(),
                "email", s.email(),
                "perfil", s.perfil(),
                "admin", s.ehAdmin(),
                "csrfToken", s.csrf());
    }

    private static String rota(HttpExchange ex) {
        List<String> partes = HttpUtil.pathParts(ex);
        return partes.isEmpty() ? "" : partes.get(0);
    }

    private static String enderecoDe(HttpExchange ex) {
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    /**
     * Freio por origem: no máximo 10 tentativas de login por minuto vindas do
     * mesmo endereço. O bloqueio por conta (em USUARIO) protege uma senha sendo
     * adivinhada; este aqui protege contra varrer muitas contas de uma vez.
     */
    private static final class Pedidos {

        private static final int LIMITE = 10;
        private static final Duration JANELA = Duration.ofMinutes(1);
        private static final Map<String, int[]> CONTAGEM = new ConcurrentHashMap<>();
        private static final Map<String, Instant> INICIO = new ConcurrentHashMap<>();

        static synchronized boolean aceitar(String ip) {
            Instant agora = Instant.now();
            Instant comeco = INICIO.get(ip);
            if (comeco == null || comeco.plus(JANELA).isBefore(agora)) {
                INICIO.put(ip, agora);
                CONTAGEM.put(ip, new int[]{0});
            }
            int[] contador = CONTAGEM.computeIfAbsent(ip, k -> new int[]{0});
            return ++contador[0] <= LIMITE;
        }

        static synchronized void zerar(String ip) {
            CONTAGEM.remove(ip);
            INICIO.remove(ip);
        }
    }
}
