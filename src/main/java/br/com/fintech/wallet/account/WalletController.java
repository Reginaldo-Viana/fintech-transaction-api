package br.com.fintech.wallet.account;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallet")
public class WalletController {

    private final WalletAccountRepository accountRepository;

    public WalletController(WalletAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public WalletResponse getWallet(Authentication authentication) {
        WalletAccount account = accountRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(AccountNotFoundException::new);
        return new WalletResponse(account.getId(), account.getName(), account.getEmail(),
                account.getBalance(), account.getCreatedAt());
    }

    public record WalletResponse(
            Long id, String name, String pixKey, java.math.BigDecimal balance, java.time.Instant createdAt) {
    }
}
