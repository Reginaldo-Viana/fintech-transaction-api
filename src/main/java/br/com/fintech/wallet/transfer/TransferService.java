package br.com.fintech.wallet.transfer;

import br.com.fintech.wallet.account.AccountNotFoundException;
import br.com.fintech.wallet.account.InsufficientFundsException;
import br.com.fintech.wallet.account.WalletAccount;
import br.com.fintech.wallet.account.WalletAccountRepository;
import br.com.fintech.wallet.common.IdempotencyConflictException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final WalletAccountRepository accountRepository;
    private final PixTransferRepository transferRepository;

    public TransferService(WalletAccountRepository accountRepository, PixTransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    @Transactional
    public TransferResponse transfer(String senderEmail, String headerIdempotencyKey, TransferRequest request) {
        String idempotencyKey = request.idempotencyKey() == null
                ? headerIdempotencyKey
                : request.idempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key deve ter entre 1 e 100 caracteres");
        }

        WalletAccount sender = accountRepository.findByEmailIgnoreCase(senderEmail)
                .orElseThrow(AccountNotFoundException::new);
        if (request.from() != null && !sender.getId().equals(request.from())) {
            throw new IllegalArgumentException("A conta de origem deve corresponder à conta autenticada");
        }

        var existingTransfer = transferRepository.findBySenderIdAndIdempotencyKey(sender.getId(), idempotencyKey);
        if (existingTransfer.isPresent()) {
            return resolveIdempotentResult(existingTransfer.get(), request);
        }

        WalletAccount recipient = request.to() != null
                ? accountRepository.findById(request.to()).orElseThrow(AccountNotFoundException::new)
                : request.recipientKey() == null
                        ? null
                        : accountRepository.findByEmailIgnoreCase(request.recipientKey().trim())
                                .orElseThrow(AccountNotFoundException::new);
        if (recipient == null) {
            throw new IllegalArgumentException("Informe o ID da conta destinatária");
        }
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("Não é permitido transferir para a própria conta");
        }

        List<WalletAccount> lockedAccounts =
                accountRepository.findAllByIdForUpdate(List.of(sender.getId(), recipient.getId()));
        if (lockedAccounts.size() != 2) {
            throw new AccountNotFoundException();
        }
        WalletAccount lockedSender = lockedAccounts.stream()
                .filter(account -> account.getId().equals(sender.getId()))
                .findFirst()
                .orElseThrow(AccountNotFoundException::new);
        WalletAccount lockedRecipient = lockedAccounts.stream()
                .filter(account -> account.getId().equals(recipient.getId()))
                .findFirst()
                .orElseThrow(AccountNotFoundException::new);

        existingTransfer = transferRepository.findBySenderIdAndIdempotencyKey(lockedSender.getId(), idempotencyKey);
        if (existingTransfer.isPresent()) {
            return resolveIdempotentResult(existingTransfer.get(), request);
        }

        BigDecimal amount = request.amount();
        if (lockedSender.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        lockedSender.debit(amount);
        lockedRecipient.credit(amount);
        PixTransfer transfer = transferRepository.save(new PixTransfer(
                lockedSender,
                lockedRecipient,
                amount,
                normalizeDescription(request.description()),
                idempotencyKey,
                request.to() == null ? TransactionType.PIX : TransactionType.TRANSFER));
        return TransferResponse.from(transfer);
    }

    @Transactional(readOnly = true)
    public Page<StatementEntry> statement(String email, int page, int size) {
        WalletAccount account = accountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(AccountNotFoundException::new);
        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return transferRepository.findStatementByAccountId(account.getId(), pageRequest)
                .map(transfer -> StatementEntry.from(transfer, account.getId()));
    }

    private TransferResponse resolveIdempotentResult(PixTransfer existing, TransferRequest request) {
        boolean sameRecipient = request.to() != null
                ? existing.getRecipient().getId().equals(request.to())
                : request.recipientKey() != null
                        && existing.getRecipient().getEmail().equalsIgnoreCase(request.recipientKey().trim());
        boolean sameAmount = existing.getAmount().compareTo(request.amount()) == 0;
        boolean sameDescription = java.util.Objects.equals(
                existing.getDescription(), normalizeDescription(request.description()));
        if (!sameRecipient || !sameAmount || !sameDescription) {
            throw new IdempotencyConflictException();
        }
        return TransferResponse.from(existing);
    }

    private String normalizeDescription(String description) {
        return description == null || description.isBlank() ? null : description.trim();
    }

    public record TransferRequest(
            @jakarta.validation.constraints.Positive Long from,
            @jakarta.validation.constraints.Positive Long to,
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.Size(max = 254) String recipientKey,
            @jakarta.validation.constraints.NotNull
            @jakarta.validation.constraints.DecimalMin(value = "0.01")
            @jakarta.validation.constraints.Digits(integer = 17, fraction = 2) BigDecimal amount,
            @jakarta.validation.constraints.Size(max = 100) String idempotencyKey,
            @jakarta.validation.constraints.Size(max = 140) String description) {
        public TransferRequest(String recipientKey, BigDecimal amount, String description) {
            this(null, null, recipientKey, amount, null, description);
        }

        public TransferRequest(Long from, Long to, BigDecimal amount, String idempotencyKey) {
            this(from, to, null, amount, idempotencyKey, null);
        }
    }

    public record TransferResponse(
            Long id,
            Long senderAccountId,
            String senderKey,
            Long recipientAccountId,
            String recipientKey,
            BigDecimal amount,
            String description,
            java.time.Instant createdAt,
            TransactionType type,
            String status,
            String idempotencyKey) {
        static TransferResponse from(PixTransfer transfer) {
            return new TransferResponse(
                    transfer.getId(),
                    transfer.getSender().getId(),
                    transfer.getSender().getEmail(),
                    transfer.getRecipient().getId(),
                    transfer.getRecipient().getEmail(),
                    transfer.getAmount(),
                    transfer.getDescription(),
                    transfer.getCreatedAt(),
                    transfer.getType(),
                    transfer.getStatus().name(),
                    transfer.getIdempotencyKey());
        }
    }

    public record StatementEntry(
            Long transferId,
            String direction,
            String counterpartyKey,
            BigDecimal amount,
            String description,
            java.time.Instant createdAt,
            TransactionType type,
            String status,
            Long fromAccountId,
            Long toAccountId,
            String idempotencyKey) {
        static StatementEntry from(PixTransfer transfer, Long accountId) {
            boolean outgoing = transfer.getSender() != null && transfer.getSender().getId().equals(accountId);
            return new StatementEntry(
                    transfer.getId(),
                    outgoing ? "OUTGOING" : "INCOMING",
                    outgoing ? transfer.getRecipient().getEmail()
                            : transfer.getSender() == null ? null : transfer.getSender().getEmail(),
                    transfer.getAmount(),
                    transfer.getDescription(),
                    transfer.getCreatedAt(),
                    transfer.getType(),
                    transfer.getStatus().name(),
                    transfer.getSender() == null ? null : transfer.getSender().getId(),
                    transfer.getRecipient().getId(),
                    transfer.getIdempotencyKey());
        }
    }
}
