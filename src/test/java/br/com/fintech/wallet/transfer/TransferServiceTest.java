package br.com.fintech.wallet.transfer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.fintech.wallet.account.AccountNotFoundException;
import br.com.fintech.wallet.account.InsufficientFundsException;
import br.com.fintech.wallet.account.WalletAccount;
import br.com.fintech.wallet.account.WalletAccountRepository;
import br.com.fintech.wallet.common.IdempotencyConflictException;
import br.com.fintech.wallet.transfer.TransactionStatus;
import br.com.fintech.wallet.transfer.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferServiceTest {

    private WalletAccountRepository accountRepository;
    private PixTransferRepository transferRepository;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        accountRepository = mock(WalletAccountRepository.class);
        transferRepository = mock(PixTransferRepository.class);
        transferService = new TransferService(accountRepository, transferRepository);
    }

    @Test
    void returnsTheOriginalTransferForAnExactIdempotentRetry() {
        WalletAccount sender = account(1L, "ana@example.com", "100.00");
        WalletAccount recipient = account(2L, "bruno@example.com", "0.00");
        PixTransfer original = mock(PixTransfer.class);
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(sender));
        when(transferRepository.findBySenderIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(original));
        when(original.getRecipient()).thenReturn(recipient);
        when(original.getSender()).thenReturn(sender);
        when(original.getAmount()).thenReturn(new BigDecimal("20.00"));
        when(original.getDescription()).thenReturn("Lunch");
        when(original.getType()).thenReturn(TransactionType.PIX);
        when(original.getStatus()).thenReturn(TransactionStatus.COMPLETED);
        when(original.getId()).thenReturn(33L);
        when(original.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));

        TransferService.TransferResponse response = transferService.transfer(
                "ana@example.com",
                "request-1",
                new TransferService.TransferRequest("bruno@example.com", new BigDecimal("20.00"), " Lunch "));

        assertEquals(33L, response.id());
        assertEquals(new BigDecimal("20.00"), response.amount());
        verify(accountRepository, never()).findAllByIdForUpdate(org.mockito.ArgumentMatchers.anyCollection());
        verify(transferRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsReusingAnIdempotencyKeyForDifferentTransferData() {
        WalletAccount sender = account(1L, "ana@example.com", "100.00");
        WalletAccount recipient = account(2L, "bruno@example.com", "0.00");
        PixTransfer original = mock(PixTransfer.class);
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(sender));
        when(transferRepository.findBySenderIdAndIdempotencyKey(1L, "request-1"))
                .thenReturn(Optional.of(original));
        when(original.getRecipient()).thenReturn(recipient);
        when(original.getAmount()).thenReturn(new BigDecimal("20.00"));
        when(original.getDescription()).thenReturn("Lunch");

        assertThrows(
                IdempotencyConflictException.class,
                () -> transferService.transfer(
                        "ana@example.com",
                        "request-1",
                        new TransferService.TransferRequest("bruno@example.com", new BigDecimal("21.00"), "Lunch")));
    }

    @Test
    void rejectsTransferWhenSenderHasInsufficientFunds() {
        WalletAccount sender = account(1L, "ana@example.com", "10.00");
        WalletAccount recipient = account(2L, "bruno@example.com", "0.00");
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(sender));
        when(accountRepository.findByEmailIgnoreCase("bruno@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(sender, recipient));
        when(transferRepository.findBySenderIdAndIdempotencyKey(1L, "request-2"))
                .thenReturn(Optional.empty());
        when(sender.getBalance()).thenReturn(new BigDecimal("10.00"));

        assertThrows(
                InsufficientFundsException.class,
                () -> transferService.transfer(
                        "ana@example.com",
                        "request-2",
                        new TransferService.TransferRequest("bruno@example.com", new BigDecimal("20.00"), null)));
        verify(transferRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void debitsAndCreditsTheLockedAccountsForANewTransfer() {
        WalletAccount sender = account(1L, "ana@example.com", "50.00");
        WalletAccount recipient = account(2L, "bruno@example.com", "5.00");
        PixTransfer persistedTransfer = mock(PixTransfer.class);
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(sender));
        when(accountRepository.findByEmailIgnoreCase("bruno@example.com")).thenReturn(Optional.of(recipient));
        when(accountRepository.findAllByIdForUpdate(List.of(1L, 2L))).thenReturn(List.of(sender, recipient));
        when(transferRepository.findBySenderIdAndIdempotencyKey(1L, "request-4"))
                .thenReturn(Optional.empty());
        when(transferRepository.save(org.mockito.ArgumentMatchers.any(PixTransfer.class)))
                .thenReturn(persistedTransfer);
        when(persistedTransfer.getSender()).thenReturn(sender);
        when(persistedTransfer.getRecipient()).thenReturn(recipient);
        when(persistedTransfer.getAmount()).thenReturn(new BigDecimal("15.00"));
        when(persistedTransfer.getId()).thenReturn(44L);
        when(persistedTransfer.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(persistedTransfer.getType()).thenReturn(TransactionType.PIX);
        when(persistedTransfer.getStatus()).thenReturn(TransactionStatus.COMPLETED);

        TransferService.TransferResponse response = transferService.transfer(
                "ana@example.com",
                "request-4",
                new TransferService.TransferRequest("bruno@example.com", new BigDecimal("15.00"), "Dinner"));

        assertEquals(44L, response.id());
        verify(sender).debit(new BigDecimal("15.00"));
        verify(recipient).credit(new BigDecimal("15.00"));
    }

    @Test
    void reportsMissingRecipient() {
        WalletAccount sender = account(1L, "ana@example.com", "10.00");
        when(accountRepository.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(sender));
        when(transferRepository.findBySenderIdAndIdempotencyKey(1L, "request-3"))
                .thenReturn(Optional.empty());
        when(accountRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> transferService.transfer(
                        "ana@example.com",
                        "request-3",
                        new TransferService.TransferRequest("missing@example.com", new BigDecimal("1.00"), null)));
    }

    private WalletAccount account(Long id, String email, String balance) {
        WalletAccount account = mock(WalletAccount.class);
        when(account.getId()).thenReturn(id);
        when(account.getEmail()).thenReturn(email);
        when(account.getBalance()).thenReturn(new BigDecimal(balance));
        return account;
    }
}
