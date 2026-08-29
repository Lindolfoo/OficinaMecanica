package br.com.oficina.http;

import java.util.Map;

/** Implementado pelos modelos para definir como cada um é representado em JSON. */
public interface JsonSerializable {
    Map<String, Object> toJsonMap();
}
