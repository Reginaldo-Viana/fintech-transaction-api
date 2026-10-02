package br.com.fintech.wallet.account;

import java.util.regex.Pattern;

public final class CpfValidator {

    private static final Pattern NON_DIGIT = Pattern.compile("\\D");

    private CpfValidator() {
    }

    public static String normalizeAndValidate(String document) {
        String digits = NON_DIGIT.matcher(document).replaceAll("");
        if (digits.length() != 11 || digits.chars().distinct().count() == 1
                || !hasValidCheckDigits(digits)) {
            throw new IllegalArgumentException("Documento deve ser um CPF válido");
        }
        return digits;
    }

    private static boolean hasValidCheckDigits(String cpf) {
        int firstDigit = calculateDigit(cpf, 9);
        int secondDigit = calculateDigit(cpf, 10);
        return cpf.charAt(9) - '0' == firstDigit && cpf.charAt(10) - '0' == secondDigit;
    }

    private static int calculateDigit(String cpf, int length) {
        int sum = 0;
        for (int index = 0; index < length; index++) {
            sum += (cpf.charAt(index) - '0') * (length + 1 - index);
        }
        int remainder = (sum * 10) % 11;
        return remainder == 10 ? 0 : remainder;
    }
}
