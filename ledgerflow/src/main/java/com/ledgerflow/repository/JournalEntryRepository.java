package com.ledgerflow.repository;

import com.ledgerflow.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {

    /**
     * Derives an account balance in minor units directly from the ledger lines.
     * DEBIT is treated as positive, CREDIT as negative — the natural sign for an
     * asset account. For a liability, the caller simply negates the result. The
     * point is that balances are computed, never stored, so they cannot drift.
     */
    @Query("""
           select coalesce(sum(case when l.direction = com.ledgerflow.domain.Enums.Direction.DEBIT
                                     then l.amountMinor else -l.amountMinor end), 0)
           from LedgerLine l
           where l.account.code = :code
           """)
    long signedBalanceByAccountCode(@Param("code") String code);
}
