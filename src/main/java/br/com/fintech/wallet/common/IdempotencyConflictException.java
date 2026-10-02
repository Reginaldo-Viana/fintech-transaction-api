package br.com.fintech.wallet.common;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key já foi usada com dados diferentes");
    }
}
