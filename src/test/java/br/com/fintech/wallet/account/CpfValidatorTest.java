package br.com.fintech.wallet.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CpfValidatorTest {

    @Test
    void normalizesAndAcceptsAValidCpf() {
        assertEquals("52998224725", CpfValidator.normalizeAndValidate("529.982.247-25"));
    }

    @Test
    void rejectsAnInvalidCpf() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CpfValidator.normalizeAndValidate("111.111.111-11"));
    }
}
