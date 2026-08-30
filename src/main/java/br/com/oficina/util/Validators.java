package br.com.oficina.util;

import java.util.regex.Pattern;

/** Validações de entrada reutilizadas pelos handlers. */
public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+$");

    private Validators() {
    }

    public static String somenteDigitos(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    public static boolean emailValido(String email) {
        return email != null && EMAIL.matcher(email).matches();
    }
    /** Valida o CPF. Por enquanto confere apenas o formato: 11 dígitos. */
    public static boolean cpfValido(String cpf) {
        return cpf != null && cpf.length() == 11 && cpf.chars().allMatch(Character::isDigit);
    }
}
