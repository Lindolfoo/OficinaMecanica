package br.com.oficina.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Conversão entre reais e centavos.
 *
 * No banco, dinheiro é INTEGER de centavos. O SQLite não tem tipo decimal de
 * verdade: uma coluna DECIMAL(10,2) acaba guardando ponto flutuante, e somar
 * dinheiro em float pode render centavo errado. Com inteiro, SUM() é exato.
 *
 * Do lado do Java continua tudo em BigDecimal, para a API e as telas não
 * mudarem. Esta classe é a fronteira entre os dois mundos, e existe para que
 * a multiplicação por 100 não fique espalhada pelos DAOs.
 */
public final class Dinheiro {

    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private Dinheiro() {
    }

    /** R$ 45,90 -> 4590. Arredonda para o centavo mais próximo. */
    public static long paraCentavos(BigDecimal reais) {
        if (reais == null) {
            return 0L;
        }
        return reais.multiply(CEM).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    /** 4590 -> R$ 45,90, sempre com duas casas. */
    public static BigDecimal deCentavos(long centavos) {
        return BigDecimal.valueOf(centavos).divide(CEM, 2, RoundingMode.UNNECESSARY);
    }
}
