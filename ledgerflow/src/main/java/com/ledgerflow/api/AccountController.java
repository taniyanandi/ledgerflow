package com.ledgerflow.api;

import com.ledgerflow.api.dto.AccountResponse;
import com.ledgerflow.api.dto.BalanceResponse;
import com.ledgerflow.service.LedgerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    /** Lists every ledger account (chart of accounts). */
    @GetMapping
    public List<AccountResponse> list() {
        return ledger.listAccounts().stream()
                .map(AccountResponse::from)
                .toList();
    }

    /** Returns a balance derived live from the ledger lines — proof the double-entry books balance. */
    @GetMapping("/{code}/balance")
    public BalanceResponse balance(@PathVariable String code) {
        return new BalanceResponse(code, ledger.balanceMinor(code), ledger.currencyOf(code));
    }
}