package br.com.fintech.wallet.transfer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PixTransferRepository extends JpaRepository<PixTransfer, Long> {

    Optional<PixTransfer> findBySenderIdAndIdempotencyKey(Long senderId, String idempotencyKey);

    Optional<PixTransfer> findByRecipientIdAndTypeAndIdempotencyKey(
            Long recipientId, TransactionType type, String idempotencyKey);

    @Query(
            value = """
                    select transfer from PixTransfer transfer
                    left join fetch transfer.sender
                    join fetch transfer.recipient
                    where transfer.sender.id = :accountId or transfer.recipient.id = :accountId
                    """,
            countQuery = """
                    select count(transfer) from PixTransfer transfer
                    where transfer.sender.id = :accountId or transfer.recipient.id = :accountId
                    """)
    Page<PixTransfer> findStatementByAccountId(@Param("accountId") Long accountId, Pageable pageable);
}
