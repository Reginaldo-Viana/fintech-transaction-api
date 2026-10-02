package br.com.fintech.wallet.account;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException() {
        super("Saldo insuficiente para realizar a transferência");
    }
}
