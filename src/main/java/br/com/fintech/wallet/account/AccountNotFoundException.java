package br.com.fintech.wallet.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Conta não encontrada");
    }
}
