package br.com.oficina.api;

import br.com.oficina.dao.UsuarioDao;
import br.com.oficina.http.ApiException;
import br.com.oficina.http.BaseHandler;
import br.com.oficina.http.HttpUtil;
import br.com.oficina.model.Usuario;
import br.com.oficina.security.FiltroAutenticacao;
import br.com.oficina.security.Sessoes;
import br.com.oficina.util.Validators;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recurso /api/usuarios — administração de contas. Só perfil ADMIN entra aqui.
 *
 *   GET    /api/usuarios             lista (?busca=texto)
 *   GET    /api/usuarios/{id}        um usuário
 *   POST   /api/usuarios             cria   (form: nome, email, senha, perfil, ativo)
 *   PUT    /api/usuarios/{id}        altera (form: nome, email, perfil, ativo)
 *   PUT    /api/usuarios/{id}/senha  redefine a senha (form: novaSenha)
 *   DELETE /api/usuarios/{id}        exclui
 *
 * As regras de proteção da conta ficam todas aqui: não dá para se auto-excluir,
 * nem para deixar o sistema sem nenhum administrador ativo.
 */
public class UsuarioHandler extends BaseHandler {

    private static final Set<String> PERFIS = Set.of(Usuario.ADMIN, Usuario.ATENDENTE);

    private final UsuarioDao dao = new UsuarioDao();

    @Override
    protected void get(HttpExchange ex) throws Exception {
        exigirAdmin(ex);
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            HttpUtil.sendJson(ex, 200, dao.listar(HttpUtil.query(ex).get("busca")));
        } else {
            HttpUtil.sendJson(ex, 200, dao.buscarPorId(id)
                    .orElseThrow(() -> new ApiException(404, "Usuário não encontrado")));
        }
    }

    @Override
    protected void post(HttpExchange ex) throws Exception {
        exigirAdmin(ex);
        Map<String, String> f = HttpUtil.readForm(ex);
        String senha = f.getOrDefault("senha", "");
        if (!Validators.senhaForte(senha)) {
            throw new ApiException(400, Validators.REGRA_DA_SENHA);
        }
        HttpUtil.sendJson(ex, 201, dao.inserir(validar(null, f), senha));
    }

    @Override
    protected void put(HttpExchange ex) throws Exception {
        Sessoes.Sessao eu = exigirAdmin(ex);
        List<String> partes = HttpUtil.pathParts(ex);
        if (partes.isEmpty()) {
            throw new ApiException(400, "Informe o ID do usuário na URL");
        }
        long id = HttpUtil.parseId(partes.get(0));
        Usuario alvo = dao.buscarPorId(id).orElseThrow(() -> new ApiException(404, "Usuário não encontrado"));

        // PUT /api/usuarios/{id}/senha — o administrador redefine a senha de alguém
        if (partes.size() == 2 && partes.get(1).equals("senha")) {
            String nova = HttpUtil.readForm(ex).getOrDefault("novaSenha", "");
            if (!Validators.senhaForte(nova)) {
                throw new ApiException(400, Validators.REGRA_DA_SENHA);
            }
            dao.alterarSenha(id, nova);
            Sessoes.encerrarDoUsuario(id);      // a senha mudou: as sessões antigas caem
            System.out.println("[auth] " + eu.email() + " redefiniu a senha de " + alvo.email());
            HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
            return;
        }
        if (partes.size() != 1) {
            throw new ApiException(404, "Rota não encontrada");
        }

        Usuario alterado = validar(id, HttpUtil.readForm(ex));
        // Tirar o último administrador deixaria o sistema sem quem administra.
        boolean perdeuPoder = alvo.ehAdmin() && (!alterado.ehAdmin() || !alterado.ativo());
        if (perdeuPoder && dao.contarAdminsAtivos(id) == 0) {
            throw new ApiException(409, "Este é o último administrador ativo: "
                    + "promova outro usuário a ADMIN antes de alterar este.");
        }
        if (!dao.atualizar(alterado)) {
            throw new ApiException(404, "Usuário não encontrado");
        }
        // Mudou perfil ou foi inativado: derruba a sessão para não seguir com o poder antigo.
        if (!alvo.perfil().equals(alterado.perfil()) || !alterado.ativo()) {
            Sessoes.encerrarDoUsuario(id);
        }
        HttpUtil.sendJson(ex, 200, dao.buscarPorId(id).orElseThrow());
    }

    @Override
    protected void delete(HttpExchange ex) throws Exception {
        Sessoes.Sessao eu = exigirAdmin(ex);
        Long id = HttpUtil.pathId(ex);
        if (id == null) {
            throw new ApiException(400, "Informe o ID do usuário na URL");
        }
        if (id == eu.usuarioId()) {
            throw new ApiException(409, "Você não pode excluir a própria conta.");
        }
        Usuario alvo = dao.buscarPorId(id).orElseThrow(() -> new ApiException(404, "Usuário não encontrado"));
        if (alvo.ehAdmin() && dao.contarAdminsAtivos(id) == 0) {
            throw new ApiException(409, "Este é o último administrador ativo e não pode ser excluído.");
        }
        if (!dao.excluir(id)) {
            throw new ApiException(404, "Usuário não encontrado");
        }
        Sessoes.encerrarDoUsuario(id);
        System.out.println("[auth] " + eu.email() + " excluiu o usuário " + alvo.email());
        HttpUtil.sendNoContent(ex);
    }

    // ------------------------------------------------------------------ apoio

    /** Devolve a sessão de quem chamou, exigindo que seja ADMIN. */
    private static Sessoes.Sessao exigirAdmin(HttpExchange ex) {
        Sessoes.Sessao s = (Sessoes.Sessao) ex.getAttribute(FiltroAutenticacao.ATRIBUTO_SESSAO);
        if (s == null) {
            throw new ApiException(401, "Sessão expirada. Entre novamente.");
        }
        if (!s.ehAdmin()) {
            throw new ApiException(403, "Só o perfil ADMIN administra usuários.");
        }
        return s;
    }

    private static Usuario validar(Long id, Map<String, String> f) {
        String nome = f.getOrDefault("nome", "").trim();
        String email = f.getOrDefault("email", "").trim().toLowerCase();
        String perfil = f.getOrDefault("perfil", Usuario.ATENDENTE).trim().toUpperCase();
        boolean ativo = !"false".equalsIgnoreCase(f.getOrDefault("ativo", "true"));

        if (nome.length() < 2 || nome.length() > 100) {
            throw new ApiException(400, "Nome deve ter entre 2 e 100 caracteres");
        }
        if (email.length() > 120 || !Validators.emailValido(email)) {
            throw new ApiException(400, "E-mail inválido");
        }
        if (!PERFIS.contains(perfil)) {
            throw new ApiException(400, "Perfil deve ser ADMIN ou ATENDENTE");
        }
        return new Usuario(id, nome, email, null, perfil, ativo, 0, null, null, null);
    }
}
