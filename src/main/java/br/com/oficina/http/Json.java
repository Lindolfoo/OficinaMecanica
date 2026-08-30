package br.com.oficina.http;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializador JSON mínimo, escrito à mão para o projeto não depender de
 * nenhuma biblioteca externa. Suporta: null, String, Number, Boolean,
 * Map, Collection, {@link JsonSerializable} e qualquer outro objeto via toString()
 * (enums, LocalDateTime, etc.).
 *
 * A entrada (requisições) chega como application/x-www-form-urlencoded
 * (formato padrão do jQuery), então não é necessário um parser de JSON.
 */
public final class Json {

    private Json() {
    }

    /** Cria um mapa ordenado a partir de pares chave/valor: obj("id", 1, "nome", "Ana"). */
    public static Map<String, Object> obj(Object... chaveValor) {
        if (chaveValor.length % 2 != 0) {
            throw new IllegalArgumentException("obj() exige pares chave/valor");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < chaveValor.length; i += 2) {
            m.put(String.valueOf(chaveValor[i]), chaveValor[i + 1]);
        }
        return m;
    }

    public static String write(Object valor) {
        StringBuilder sb = new StringBuilder();
        write(valor, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(s, sb);
        } else if (v instanceof Boolean || v instanceof Integer || v instanceof Long || v instanceof Short
                || v instanceof Byte) {
            sb.append(v);
        } else if (v instanceof BigDecimal bd) {
            sb.append(bd.toPlainString());
        } else if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                sb.append("null");
            } else {
                sb.append(v);
            }
        } else if (v instanceof Number n) {
            sb.append(n);
        } else if (v instanceof JsonSerializable js) {
            write(js.toJsonMap(), sb);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                writeString(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean primeiro = true;
            for (Object item : c) {
                if (!primeiro) {
                    sb.append(',');
                }
                primeiro = false;
                write(item, sb);
            }
            sb.append(']');
        } else {
            writeString(v.toString(), sb);
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}
