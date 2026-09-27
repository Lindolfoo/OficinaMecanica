package br.com.oficina;

import br.com.oficina.api.AuthHandler;
import br.com.oficina.api.ClienteHandler;
import br.com.oficina.api.DashboardHandler;
import br.com.oficina.api.OrdemServicoHandler;
import br.com.oficina.api.ServicoHandler;
import br.com.oficina.api.UsuarioHandler;
import br.com.oficina.api.VeiculoHandler;
import br.com.oficina.config.Config;
import br.com.oficina.dao.UsuarioDao;
import br.com.oficina.db.Database;
import br.com.oficina.desktop.Bandeja;
import br.com.oficina.desktop.InstanciaUnica;
import br.com.oficina.desktop.Navegador;
import br.com.oficina.http.StaticHandler;
import br.com.oficina.security.FiltroAutenticacao;
import br.com.oficina.security.FiltroSeguranca;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Ponto de entrada. Sobe um servidor HTTP do próprio JDK (com.sun.net.httpserver)
 * que serve o front-end estático e a API JSON. Sem frameworks.
 */
public final class App {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Oficina Mecânica - NP1 Banco de Dados ===");

        // 0) Uma cópia por vez. Clicar duas vezes no atalho não sobe um segundo
        //    servidor: a segunda tentativa só abre a janela da que já roda.
        InstanciaUnica instancia = new InstanciaUnica(Config.pastaDados());
        if (!instancia.assumir()) {
            int porta = instancia.portaEmUso();
            System.out.println("O sistema já está aberto. Trazendo a janela para frente.");
            if (porta > 0) {
                Navegador.abrir("http://localhost:" + porta);
            }
            return;
        }

        // 1) Banco: garante o schema e testa a conexão antes de aceitar requisições
        try {
            if (Config.dbAutoSchema()) {
                Database.ensureSchema();
                System.out.println("Schema verificado (sql/01_schema.sql).");
            }
            try (Connection c = Database.getConnection()) {
                System.out.println("Banco: " + c.getMetaData().getDatabaseProductName()
                        + " " + c.getMetaData().getDatabaseProductVersion());
                System.out.println("Arquivo: " + Database.arquivo());
            }
            avisarPrimeiroAcesso();
        } catch (SQLException e) {
            System.err.println("ERRO: não foi possível abrir o banco -> " + e.getMessage());
            System.err.println("O banco é o arquivo " + Database.arquivo()
                    + ". Verifique se a pasta pode ser gravada, ou informe outra em OFICINA_DADOS.");
            System.exit(1);
        }

        // 2) Rotas
        HttpServer server = abrirServidor();

        Map<String, HttpHandler> api = new LinkedHashMap<>();
        api.put("/api/auth", new AuthHandler());
        api.put("/api/usuarios", new UsuarioHandler());
        api.put("/api/dashboard", new DashboardHandler());
        api.put("/api/clientes", new ClienteHandler());
        api.put("/api/veiculos", new VeiculoHandler());
        api.put("/api/servicos", new ServicoHandler());
        api.put("/api/ordens", new OrdemServicoHandler());

        // 3) Filtros: cabeçalhos de segurança em tudo; sessão exigida na API e nas páginas.
        //    A ordem importa — o porteiro só é chamado depois que os cabeçalhos já estão postos.
        FiltroSeguranca seguranca = new FiltroSeguranca();
        for (Map.Entry<String, HttpHandler> rota : api.entrySet()) {
            HttpContext contexto = server.createContext(rota.getKey(), rota.getValue());
            contexto.getFilters().add(seguranca);
            contexto.getFilters().add(new FiltroAutenticacao(true));
        }
        HttpContext estatico = server.createContext("/", new StaticHandler());
        estatico.getFilters().add(seguranca);
        estatico.getFilters().add(new FiltroAutenticacao(false));

        // 4) Uma thread virtual por requisição (Java 21)
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        int porta = server.getAddress().getPort();
        String url = "http://localhost:" + porta;
        instancia.registrarPorta(porta);

        System.out.println("Servidor no ar: " + url);
        if (!Config.host().startsWith("127.")) {
            System.out.println("ATENÇÃO: aceitando conexões de outros computadores (" + Config.host() + ").");
        }

        // 5) Cara de programa: ícone na bandeja e janela do navegador.
        //    Sem ambiente gráfico nada disso acontece, e a aplicação segue
        //    funcionando normalmente pelo endereço acima.
        Runnable encerrar = () -> {
            System.out.println("Encerrando a pedido do usuário.");
            server.stop(0);
            System.exit(0);
        };
        boolean temBandeja = Config.appBandeja() && Bandeja.instalar(url, encerrar);
        if (Config.appAbrirNavegador()) {
            Navegador.abrir(url);
        }
        System.out.println(temBandeja
                ? "Para encerrar, use o ícone na área de notificação (Sair)."
                : "Pressione Ctrl+C para encerrar.");
    }

    /**
     * Sobe o servidor na porta configurada. Se ela estiver ocupada por outro
     * programa, usa uma porta livre qualquer em vez de recusar a abrir — quem
     * informa o endereço final é o console, e o navegador é aberto nele.
     */
    private static HttpServer abrirServidor() throws java.io.IOException {
        try {
            return HttpServer.create(new InetSocketAddress(Config.host(), Config.port()), 0);
        } catch (java.net.BindException e) {
            System.out.println("Porta " + Config.port() + " ocupada; escolhendo uma livre.");
            return HttpServer.create(new InetSocketAddress(Config.host(), 0), 0);
        }
    }

    /**
     * Não existe senha padrão: se ainda não há conta, quem define o
     * administrador é o usuário, na própria tela de entrada.
     */
    private static void avisarPrimeiroAcesso() throws SQLException {
        if (new UsuarioDao().contar() == 0) {
            System.out.println();
            System.out.println("  Primeiro acesso: abra o sistema no navegador para criar");
            System.out.println("  o administrador (nome, e-mail e senha de sua escolha).");
            System.out.println();
        }
    }
}
