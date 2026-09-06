package com.ledgerflow.service;

import com.ledgerflow.domain.*;
import com.ledgerflow.exception.ApiException;
import com.ledgerflow.repository.JournalEntryRepository;
import com.ledgerflow.repository.LedgerAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns all money movement. Every public method here posts one balanced journal
 * entry (equal debits and credits) or throws — there is no path that mutates a
 * balance directly.
 */
@Service
public class LedgerService {

    public static final String ACQUIRER_CASH = "ACQUIRER_CASH";
    public static final String MERCHANT_PAYABLE = "MERCHANT_PAYABLE";

    private final LedgerAccountRepository accounts;
    private final JournalEntryRepository journals;

    public LedgerService(LedgerAccountRepository accounts, JournalEntryRepository journals) {
        this.accounts = accounts;
        this.journals = journals;
    }

    /** Capture: cash received from the acquirer becomes a payable owed to the merchant. */
    @Transactional
    public void postCapture(Payment payment) {
        post(payment, ACQUIRER_CASH, MERCHANT_PAYABLE,
                "Capture for payment " + payment.getId());
    }

    /** Refund: reverse the capture — reduce the payable, return the cash. */
    @Transactional
    public void postRefund(Payment payment) {
        post(payment, MERCHANT_PAYABLE, ACQUIRER_CASH,
                "Refund for payment " + payment.getId());
    }

    private void post(Payment payment, String debitCode, String creditCode, String description) {
        LedgerAccount debitAccount = require(debitCode);
        LedgerAccount creditAccount = require(creditCode);

        JournalEntry entry = new JournalEntry(
                UUID.randomUUID(), description, payment.getId(), Instant.now());
        entry.addLine(new LedgerLine(UUID.randomUUID(), debitAccount,
                Enums.Direction.DEBIT, payment.getAmountMinor(), payment.getCurrency()));
        entry.addLine(new LedgerLine(UUID.randomUUID(), creditAccount,
                Enums.Direction.CREDIT, payment.getAmountMinor(), payment.getCurrency()));

        entry.assertBalanced();   // invariant enforced before it can touch the DB
        journals.save(entry);
    }

    @Transactional(readOnly = true)
    public long balanceMinor(String accountCode) {
        LedgerAccount account = require(accountCode);
        long signed = journals.signedBalanceByAccountCode(accountCode);
        // Present a liability's balance as a positive number (its natural sign is credit).
        return account.getType() == Enums.AccountType.LIABILITY ? -signed : signed;
    }

    public String currencyOf(String accountCode) {
        return require(accountCode).getCurrency();
    }

    private LedgerAccount require(String code) {
        return accounts.findByCode(code).orElseThrow(() ->
                new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ledger account not configured: " + code));
    }
}
