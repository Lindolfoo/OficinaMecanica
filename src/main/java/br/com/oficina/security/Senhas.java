package br.com.oficina.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Guarda de senhas. A senha em si NUNCA é gravada: o que vai para a coluna
 * usuario.senha_hash é o resultado de um PBKDF2 com sal aleatório.
 *
 * Formato gravado (tudo em uma string, como o Django faz):
 *
 *     pbkdf2_sha256$210000$c2FsRW1CYXNlNjQ=$aGFzaEVtQmFzZTY0...
 *      algoritmo     iterações   sal            hash
 *
 * Guardar as iterações junto permite aumentar o custo no futuro sem invalidar
 * as senhas já cadastradas. Usa apenas javax.crypto, que já vem no JDK — o
 * projeto continua com uma única dependência (o driver do MySQL).
 */
public final class Senhas {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String MARCA = "pbkdf2_sha256";

    /** Custo do hash. Alto de propósito: é o que torna a força bruta cara. */
    private static final int ITERACOES = 210_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_CHAVE = 256;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getEncoder();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private Senhas() {
    }

    /** Gera o hash de uma senha nova, com sal aleatório. */
    public static String gerarHash(String senha) {
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(senha, sal, ITERACOES);
        return MARCA + "$" + ITERACOES + "$" + B64.encodeToString(sal) + "$" + B64.encodeToString(hash);
    }

    /**
     * Confere a senha digitada contra o hash gravado.
     *
     * A comparação é feita com {@link MessageDigest#isEqual} (tempo constante):
     * um equals() comum pára no primeiro byte diferente e, pelo tempo de resposta,
     * entregaria pistas sobre o hash correto.
     */
    public static boolean confere(String senha, String hashGravado) {
        if (senha == null || hashGravado == null) {
            return false;
        }
        String[] partes = hashGravado.split("\\$");
        if (partes.length != 4 || !MARCA.equals(partes[0])) {
            return false;                       // hash em formato desconhecido: recusa
        }
        try {
            int iteracoes = Integer.parseInt(partes[1]);
            byte[] sal = B64_DEC.decode(partes[2]);
            byte[] esperado = B64_DEC.decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(senha, sal, iteracoes));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Gasta o mesmo tempo de um {@link #confere} sem ter usuário para conferir.
     *
     * Serve contra enumeração de usuários: sem isso, um e-mail inexistente
     * responderia na hora e um e-mail existente demoraria o tempo do PBKDF2,
     * e essa diferença diria ao atacante quais e-mails estão cadastrados.
     */
    public static void gastarTempoDeVerificacao() {
        derivar("senha-que-nao-existe", new byte[BYTES_SAL], ITERACOES);
    }

    private static byte[] derivar(String senha, byte[] sal, int iteracoes) {
        PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), sal, iteracoes, BITS_CHAVE);
        try {
            return SecretKeyFactory.getInstance(ALGORITMO).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Falha ao calcular o hash da senha", e);
        } finally {
            spec.clearPassword();               // tira a senha da memória assim que possível
        }
    }
}
