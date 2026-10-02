package br.com.fintech.wallet.transfer;

import br.com.fintech.wallet.account.WalletAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions", uniqueConstraints = {
        @UniqueConstraint(name = "transactions_sender_idempotency_unique",
                columnNames = {"from_account_id", "idempotency_key"})
})
public class PixTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_account_id")
    private WalletAccount sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_account_id", nullable = false)
    private WalletAccount recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 140)
    private String description;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PixTransfer() {
    }

    public PixTransfer(
            WalletAccount sender,
            WalletAccount recipient,
            BigDecimal amount,
            String description,
            String idempotencyKey) {
        this(sender, recipient, amount, description, idempotencyKey, TransactionType.PIX);
    }

    public PixTransfer(
            WalletAccount sender,
            WalletAccount recipient,
            BigDecimal amount,
            String description,
            String idempotencyKey,
            TransactionType type) {
        this.sender = sender;
        this.recipient = recipient;
        this.amount = amount;
        this.description = description;
        this.idempotencyKey = idempotencyKey;
        this.type = type;
        this.status = TransactionStatus.COMPLETED;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public WalletAccount getSender() {
        return sender;
    }

    public WalletAccount getRecipient() {
        return recipient;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
