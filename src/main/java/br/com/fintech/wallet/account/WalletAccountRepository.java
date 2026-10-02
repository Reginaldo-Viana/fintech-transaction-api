package br.com.fintech.wallet.account;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, Long> {

    Optional<WalletAccount> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByDocument(String document);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from WalletAccount account where account.id = :id")
    Optional<WalletAccount> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from WalletAccount account where account.id = :id and account.email = :email")
    Optional<WalletAccount> findByIdAndEmailForUpdate(
            @Param("id") Long id, @Param("email") String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from WalletAccount account where account.id in :ids order by account.id")
    List<WalletAccount> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
