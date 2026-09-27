package br.com.oficina.desktop;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Garante que só exista uma cópia do programa rodando.
 *
 * Sem isso, clicar duas vezes no atalho subiria um segundo servidor que
 * brigaria pela porta e abriria um segundo acesso ao mesmo arquivo de banco.
 *
 * O mecanismo é um FileLock do sistema operacional, não um arquivo "existe/não
 * existe": se o programa fechar de forma anormal, ou mesmo se o computador
 * desligar na tomada, o sistema solta a trava sozinho. Um arquivo-sinalizador
 * comum ficaria preso para sempre e exigiria apagar na mão.
 *
 * A porta em uso fica registrada ao lado, para que a segunda tentativa saiba
 * onde abrir o navegador em vez de simplesmente reclamar.
 */
public final class InstanciaUnica {

    private final Path arquivoTrava;
    private final Path arquivoPorta;
    private FileChannel canal;
    private FileLock trava;

    public InstanciaUnica(Path pastaDados) {
        this.arquivoTrava = pastaDados.resolve("oficina.lock");
        this.arquivoPorta = pastaDados.resolve("porta.txt");
    }

    /** Tenta assumir a vez. Devolve false se já houver outra cópia rodando. */
    public boolean assumir() {
        try {
            Files.createDirectories(arquivoTrava.getParent());
            canal = FileChannel.open(arquivoTrava,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            trava = canal.tryLock();
            if (trava == null) {
                canal.close();
                return false;
            }
            // Solta a trava e apaga o registro da porta ao encerrar.
            Runtime.getRuntime().addShutdownHook(new Thread(this::liberar));
            return true;
        } catch (IOException e) {
            // Sem conseguir criar a trava, é melhor deixar rodar do que travar
            // o programa por causa de um detalhe de permissão de pasta.
            System.err.println("[instancia] não foi possível criar a trava: " + e.getMessage());
            return true;
        }
    }

    public void registrarPorta(int porta) {
        try {
            Files.writeString(arquivoPorta, Integer.toString(porta), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[instancia] não foi possível registrar a porta: " + e.getMessage());
        }
    }

    /** Porta da cópia que já está rodando, ou 0 se não der para saber. */
    public int portaEmUso() {
        try {
            return Integer.parseInt(Files.readString(arquivoPorta, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private void liberar() {
        try {
            if (trava != null && trava.isValid()) {
                trava.release();
            }
            if (canal != null && canal.isOpen()) {
                canal.close();
            }
            Files.deleteIfExists(arquivoPorta);
        } catch (IOException e) {
            // encerrando de qualquer forma
        }
    }
}
