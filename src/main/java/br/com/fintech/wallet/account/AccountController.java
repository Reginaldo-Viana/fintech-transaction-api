package br.com.fintech.wallet.account;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.AccountResponse create(@Valid @RequestBody AccountService.CreateAccountRequest request) {
        return accountService.create(request);
    }

    @GetMapping("/{id}")
    public AccountService.AccountResponse get(@PathVariable Long id, Authentication authentication) {
        return accountService.get(id, authentication.getName());
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.TransactionResponse deposit(
            @PathVariable Long id,
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody AccountService.DepositRequest request) {
        String key = idempotencyKey == null ? request.idempotencyKey() : idempotencyKey;
        return accountService.deposit(id, authentication.getName(), request, key);
    }

    @GetMapping("/{id}/statement")
    public Page<AccountService.TransactionResponse> statement(
            @PathVariable Long id,
            Authentication authentication,
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20") @jakarta.validation.constraints.Min(1)
                    @jakarta.validation.constraints.Max(100) int size) {
        return accountService.statement(id, authentication.getName(), page, size);
    }
}