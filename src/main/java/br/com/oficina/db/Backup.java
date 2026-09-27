package br.com.oficina.db;

import br.com.oficina.config.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cópias de segurança do banco.
 *
 * O comando é VACUUM INTO, e a escolha não é detalhe: copiar o arquivo .db
 * enquanto alguém grava pode gerar uma cópia quebrada, porque parte da
 * transação ainda está no arquivo -wal. O VACUUM INTO pede ao próprio SQLite
 * que escreva um banco novo, íntegro e já compactado, com o sistema rodando.
 *
 * Política: uma cópia automática por dia, na subida, mantendo as 30 últimas.
 */
public final class Backup {

    // Com precisão de minutos, um backup manual feito no mesmo minuto do
    // automático colidiria: o VACUUM INTO recusa gravar sobre arquivo existente.
    private static final DateTimeFormatter CARIMBO = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int MANTER = 30;

    private Backup() {
    }

    public static Path pasta() {
        return Config.pastaDados().resolve("backups");
    }

    /** Uma cópia por dia: evita encher o disco quem abre o programa dez vezes. */
    public static boolean jaTemDeHoje() {
        String hoje = LocalDate.now().toString();
        return listar().stream().anyMatch(b -> b.nome().contains(hoje));
    }

    /**
     * Gera a cópia e devolve o arquivo criado.
     *
     * Confere a integridade ANTES: sem isso, um banco já corrompido seria
     * copiado trinta vezes, e no dia da emergência todas as cópias estariam
     * ruins.
     */
    public static Path fazer() throws SQLException, IOException {
        Files.createDirectories(pasta());
        String base = "oficina_" + LocalDateTime.now().format(CARIMBO);
        Path destino = pasta().resolve(base + ".db");
        // Dois cliques no mesmo segundo ainda colidiriam; o sufixo resolve.
        for (int n = 2; Files.exists(destino); n++) {
            destino = pasta().resolve(base + "_" + n + ".db");
        }

        try (Connection c = Database.getConnection();
             Statement st = c.createStatement()) {

            try (ResultSet rs = st.executeQuery("PRAGMA integrity_check")) {
                if (rs.next() && !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new SQLException("O banco não passou na verificação de integridade: " + rs.getString(1));
                }
            }
            // O caminho entra no comando entre aspas simples; dobrar a aspa é o
            // escape do SQLite, e protege pasta com apóstrofo no nome.
            st.execute("VACUUM INTO '" + destino.toString().replace("'", "''") + "'");
        }

        rotacionar();
        return destino;
    }

    /** Apaga as cópias mais antigas, mantendo as 30 últimas. */
    private static void rotacionar() {
        List<Info> copias = listar();
        for (int i = MANTER; i < copias.size(); i++) {
            try {
                Files.deleteIfExists(copias.get(i).caminho());
            } catch (IOException e) {
                System.err.println("[backup] não foi possível apagar " + copias.get(i).nome() + ": " + e.getMessage());
            }
        }
    }

    /** Cópias existentes, da mais recente para a mais antiga. */
    public static List<Info> listar() {
        if (!Files.isDirectory(pasta())) {
            return List.of();
        }
        try (Stream<Path> arquivos = Files.list(pasta())) {
            List<Info> lista = new ArrayList<>();
            for (Path p : arquivos.filter(p -> p.getFileName().toString().endsWith(".db")).toList()) {
                lista.add(new Info(p.getFileName().toString(), p, Files.size(p),
                        LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(),
                                java.time.ZoneId.systemDefault())));
            }
            lista.sort(Comparator.comparing(Info::quando).reversed());
            return lista;
        } catch (IOException e) {
            System.err.println("[backup] não foi possível listar: " + e.getMessage());
            return List.of();
        }
    }

    /** Roda na subida: garante a cópia do dia sem travar o início do programa. */
    public static void diarioEmSegundoPlano() {
        Thread.ofVirtual().start(() -> {
            try {
                if (!jaTemDeHoje()) {
                    System.out.println("Backup do dia: " + fazer().getFileName());
                }
            } catch (Exception e) {
                System.err.println("[backup] falhou: " + e.getMessage());
            }
        });
    }

    public record Info(String nome, Path caminho, long bytes, LocalDateTime quando) {
    }
}
