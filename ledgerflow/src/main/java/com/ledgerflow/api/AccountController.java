package com.ledgerflow.api;

import com.ledgerflow.api.dto.BalanceResponse;
import com.ledgerflow.service.LedgerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final LedgerService ledger;

    public AccountController(LedgerService ledger) {
        this.ledger = ledger;
    }

    /** Returns a balance derived live from the ledger lines — proof the double-entry books balance. */
    @GetMapping("/{code}/balance")
    public BalanceResponse balance(@PathVariable String code) {
        return new BalanceResponse(code, ledger.balanceMinor(code), ledger.currencyOf(code));
    }
}
