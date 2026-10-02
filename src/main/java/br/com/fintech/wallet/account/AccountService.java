package br.com.fintech.wallet.account;

import br.com.fintech.wallet.common.ResourceConflictException;
import br.com.fintech.wallet.transfer.PixTransfer;
import br.com.fintech.wallet.transfer.PixTransferRepository;
import br.com.fintech.wallet.transfer.TransactionType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final WalletAccountRepository accountRepository;
    private final PixTransferRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(
            WalletAccountRepository accountRepository,
            PixTransferRepository transactionRepository,
            PasswordEncoder passwordEncoder) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("A senha não pode exceder 72 bytes em UTF-8");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String document = CpfValidator.normalizeAndValidate(request.document());
        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new ResourceConflictException("Este e-mail já está cadastrado");
        }
        if (accountRepository.existsByDocument(document)) {
            throw new ResourceConflictException("Este CPF já está cadastrado");
        }

        WalletAccount account = accountRepository.save(new WalletAccount(
                request.name().trim(), email, document, passwordEncoder.encode(request.password())));
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse get(Long accountId, String authenticatedEmail) {
        WalletAccount account = accountRepository.findById(accountId)
                .filter(found -> found.getEmail().equalsIgnoreCase(authenticatedEmail))
                .orElseThrow(AccountNotFoundException::new);
        return AccountResponse.from(account);
    }

    @Transactional
    public TransactionResponse deposit(
            Long accountId, String authenticatedEmail, DepositRequest request, String idempotencyKey) {
        WalletAccount account = accountRepository
                .findByIdAndEmailForUpdate(accountId, authenticatedEmail.toLowerCase(Locale.ROOT))
                .orElseThrow(AccountNotFoundException::new);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            if (idempotencyKey.length() > 100) {
                throw new IllegalArgumentException("Idempotency-Key não pode exceder 100 caracteres");
            }
            var previousDeposit = transactionRepository.findByRecipientIdAndTypeAndIdempotencyKey(
                    accountId, TransactionType.DEPOSIT, idempotencyKey);
            if (previousDeposit.isPresent()) {
                PixTransfer previous = previousDeposit.get();
                if (previous.getAmount().compareTo(request.amount()) != 0) {
                    throw new ResourceConflictException("Idempotency-Key já foi usada com outro valor");
                }
                return TransactionResponse.from(previous);
            }
        }

        account.credit(request.amount());
        PixTransfer transaction = transactionRepository.save(
                new PixTransfer(null, account, request.amount(), "Depósito", idempotencyKey, TransactionType.DEPOSIT));
        return TransactionResponse.from(transaction);
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<TransactionResponse> statement(
            Long accountId, String authenticatedEmail, int page, int size) {
        accountRepository.findById(accountId)
                .filter(found -> found.getEmail().equalsIgnoreCase(authenticatedEmail))
                .orElseThrow(AccountNotFoundException::new);
        return transactionRepository
                .findStatementByAccountId(accountId, org.springframework.data.domain.PageRequest.of(
                        page, size, org.springframework.data.domain.Sort.by(
                                org.springframework.data.domain.Sort.Direction.DESC, "createdAt")
                                .and(org.springframework.data.domain.Sort.by(
                                        org.springframework.data.domain.Sort.Direction.DESC, "id"))))
                .map(TransactionResponse::from);
    }

    public record CreateAccountRequest(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 120) String name,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email
                    @jakarta.validation.constraints.Size(max = 254) String email,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 11, max = 14)
                    String document,
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min = 8, max = 72)
                    String password) {
    }

    public record DepositRequest(
            @jakarta.validation.constraints.NotNull @jakarta.validation.constraints.DecimalMin(value = "0.01")
                    @jakarta.validation.constraints.Digits(integer = 17, fraction = 2)
                    BigDecimal amount,
            @jakarta.validation.constraints.Size(max = 100) String idempotencyKey) {
    }

    public record AccountResponse(
            Long id, String name, String email, String document, BigDecimal balance, java.time.Instant createdAt) {
        static AccountResponse from(WalletAccount account) {
            return new AccountResponse(account.getId(), account.getName(), account.getEmail(), account.getDocument(),
                    account.getBalance(), account.getCreatedAt());
        }
    }

    public record TransactionResponse(
            Long id,
            Long fromAccountId,
            Long toAccountId,
            BigDecimal amount,
            TransactionType type,
            String idempotencyKey,
            String status,
            java.time.Instant createdAt) {
        static TransactionResponse from(PixTransfer transaction) {
            return new TransactionResponse(
                    transaction.getId(),
                    transaction.getSender() == null ? null : transaction.getSender().getId(),
                    transaction.getRecipient().getId(),
                    transaction.getAmount(),
                    transaction.getType(),
                    transaction.getIdempotencyKey(),
                    transaction.getStatus().name(),
                    transaction.getCreatedAt());
        }
    }
}