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

    /** Valida o CPF pelos dígitos verificadores (módulo 11). Espera 11 dígitos. */
    public static boolean cpfValido(String cpf) {
        if (cpf == null || cpf.length() != 11 || !cpf.chars().allMatch(Character::isDigit)) {
            return false;
        }
        if (cpf.chars().distinct().count() == 1) { // 111.111.111-11 etc.
            return false;
        }
        int dv1 = digitoVerificador(cpf, 9, 10);
        int dv2 = digitoVerificador(cpf, 10, 11);
        return dv1 == cpf.charAt(9) - '0' && dv2 == cpf.charAt(10) - '0';
    }

    private static int digitoVerificador(String cpf, int tamanho, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < tamanho; i++) {
            soma += (cpf.charAt(i) - '0') * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
