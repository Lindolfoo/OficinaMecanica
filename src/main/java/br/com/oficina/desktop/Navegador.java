package br.com.oficina.desktop;

import java.awt.Desktop;
import java.net.URI;

/**
 * Abre o sistema numa janela, não numa aba perdida entre outras vinte.
 *
 * A ordem de tentativa importa:
 *
 *   1. Edge/Chrome em modo aplicativo (--app=URL). A janela abre sem barra de
 *      endereço, sem abas e com ícone próprio — parece um programa, não um site.
 *      O Edge já vem instalado no Windows 10 e 11, então é o caminho comum.
 *   2. Navegador padrão (Desktop.browse). Funciona sempre, mas abre como aba.
 *
 * Nada disso é essencial: se tudo falhar, o endereço fica no console e o
 * usuário abre na mão. Por isso nenhum erro aqui derruba a aplicação.
 */
public final class Navegador {

    /** Caminhos onde o Edge e o Chrome costumam estar em cada sistema. */
    private static final String[] CANDIDATOS_WINDOWS = {
        System.getenv("ProgramFiles(x86)") + "\\Microsoft\\Edge\\Application\\msedge.exe",
        System.getenv("ProgramFiles") + "\\Microsoft\\Edge\\Application\\msedge.exe",
        System.getenv("ProgramFiles") + "\\Google\\Chrome\\Application\\chrome.exe",
        System.getenv("ProgramFiles(x86)") + "\\Google\\Chrome\\Application\\chrome.exe",
    };

    private static final String[] CANDIDATOS_UNIX = {
        "/usr/bin/microsoft-edge", "/usr/bin/microsoft-edge-stable",
        "/usr/bin/google-chrome", "/usr/bin/chromium", "/usr/bin/chromium-browser",
        "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    };

    private Navegador() {
    }

    public static void abrir(String url) {
        if (emModoJanela(url)) {
            return;
        }
        if (emAba(url)) {
            return;
        }
        System.out.println("Abra no navegador: " + url);
    }

    /** Tenta o modo aplicativo, que é o que dá cara de programa instalado. */
    private static boolean emModoJanela(String url) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (String caminho : windows ? CANDIDATOS_WINDOWS : CANDIDATOS_UNIX) {
            if (caminho == null || caminho.startsWith("null")) {
                continue;
            }
            if (!new java.io.File(caminho).canExecute()) {
                continue;
            }
            try {
                new ProcessBuilder(caminho, "--app=" + url).start();
                return true;
            } catch (Exception e) {
                // navegador presente mas recusou abrir: cai para a próxima opção
            }
        }
        return false;
    }

    private static boolean emAba(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return true;
            }
        } catch (Exception e) {
            // sem ambiente gráfico (servidor, terminal puro): segue sem abrir
        }
        return false;
    }
}
