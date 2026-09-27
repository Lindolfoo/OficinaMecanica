package br.com.oficina.desktop;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * Ícone na área de notificação, com "Abrir" e "Sair".
 *
 * Sem isto, o servidor ficaria rodando para sempre depois que o usuário
 * fechasse a janela do navegador — sem janela, sem console e sem jeito óbvio
 * de encerrar, a não ser pelo Gerenciador de Tarefas.
 *
 * O ícone é desenhado em código de propósito: evita carregar arquivo de imagem
 * e funciona em qualquer tamanho que o sistema peça.
 */
public final class Bandeja {

    private Bandeja() {
    }

    /**
     * Instala o ícone. Devolve false quando não há área de notificação
     * (servidor sem ambiente gráfico, por exemplo), e nesse caso a aplicação
     * simplesmente segue sem ele.
     */
    public static boolean instalar(String url, Runnable aoSair) {
        if (java.awt.GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            return false;
        }
        try {
            PopupMenu menu = new PopupMenu();

            MenuItem abrir = new MenuItem("Abrir o sistema");
            abrir.addActionListener(e -> Navegador.abrir(url));
            menu.add(abrir);

            menu.addSeparator();

            MenuItem sair = new MenuItem("Sair");
            sair.addActionListener(e -> aoSair.run());
            menu.add(sair);

            TrayIcon icone = new TrayIcon(desenharIcone(), "Oficina Mecânica — " + url, menu);
            icone.setImageAutoSize(true);
            icone.addActionListener(e -> Navegador.abrir(url));   // duplo clique abre
            SystemTray.getSystemTray().add(icone);
            return true;
        } catch (Exception e) {
            System.err.println("[bandeja] não foi possível instalar o ícone: " + e.getMessage());
            return false;
        }
    }

    /** Círculo laranja da identidade visual com a letra O. */
    private static BufferedImage desenharIcone() {
        int t = 64;
        BufferedImage img = new BufferedImage(t, t, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(0xE2, 0x59, 0x0B));          // laranja da oficina
        g.fillOval(0, 0, t - 1, t - 1);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 38));
        var fm = g.getFontMetrics();
        String letra = "O";
        g.drawString(letra,
                (t - fm.stringWidth(letra)) / 2,
                (t - fm.getHeight()) / 2 + fm.getAscent());

        g.dispose();
        return img;
    }
}
