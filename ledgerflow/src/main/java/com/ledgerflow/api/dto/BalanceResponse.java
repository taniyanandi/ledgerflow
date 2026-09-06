package com.ledgerflow.api.dto;

public record BalanceResponse(String accountCode, long balanceMinor, String currency) {}
