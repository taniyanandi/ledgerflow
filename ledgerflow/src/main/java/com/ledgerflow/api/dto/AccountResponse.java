package com.ledgerflow.api.dto;

import com.ledgerflow.domain.LedgerAccount;

public record AccountResponse(String code, String name, String type, String currency) {
    public static AccountResponse from(LedgerAccount account) {
        return new AccountResponse(account.getCode(), account.getName(), account.getType().name(), account.getCurrency());
    }
}