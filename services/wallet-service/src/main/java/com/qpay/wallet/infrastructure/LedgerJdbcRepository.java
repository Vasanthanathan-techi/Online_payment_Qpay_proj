package com.qpay.wallet.infrastructure;

import com.qpay.wallet.application.JournalView;
import com.qpay.wallet.application.LedgerPostingException;
import com.qpay.wallet.application.PostJournalCommand;
import com.qpay.wallet.application.WalletView;
import com.qpay.wallet.domain.EntrySide;
import com.qpay.wallet.domain.LedgerLine;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class LedgerJdbcRepository {
    private final JdbcClient jdbc;

    public LedgerJdbcRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean claimEvent(String consumerName, UUID eventId, Instant now) {
        return jdbc.sql("""
                INSERT IGNORE INTO inbox_event (consumer_name, event_id, processed_at)
                VALUES (:consumer, UUID_TO_BIN(:eventId, 1), :now)
                """)
                .param("consumer", consumerName)
                .param("eventId", eventId.toString())
                .param("now", now)
                .update() == 1;
    }

    public UUID placeHold(UUID walletId, String reference, long amountMinor, String currency, Instant now) {
        jdbc.sql("SELECT id FROM wallet WHERE id=UUID_TO_BIN(:id,1) FOR UPDATE")
                .param("id", walletId.toString()).query().singleValue();
        int changed=jdbc.sql("""
                UPDATE wallet SET available_minor=available_minor-:amount, held_minor=held_minor+:amount,
                 version=version+1, updated_at=:now WHERE id=UUID_TO_BIN(:id,1) AND status='ACTIVE'
                 AND currency=:currency AND available_minor>=:amount
                """).param("amount",amountMinor).param("now",now).param("id",walletId.toString())
                .param("currency",currency).update();
        if(changed!=1) throw new LedgerPostingException("wallet has insufficient available funds");
        UUID holdId=UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO fund_hold(id,wallet_id,business_reference,amount_minor,currency,status,version,created_at,updated_at)
                VALUES(UUID_TO_BIN(:holdId,1),UUID_TO_BIN(:walletId,1),:reference,:amount,:currency,'HELD',0,:now,:now)
                """).param("holdId",holdId.toString()).param("walletId",walletId.toString()).param("reference",reference)
                .param("amount",amountMinor).param("currency",currency).param("now",now).update();
        return holdId;
    }

    public HoldRow lockHold(UUID holdId) {
        return jdbc.sql("""
                SELECT BIN_TO_UUID(id,1) id,BIN_TO_UUID(wallet_id,1) wallet_id,business_reference,
                 amount_minor,currency,status FROM fund_hold WHERE id=UUID_TO_BIN(:id,1) FOR UPDATE
                """).param("id",holdId.toString()).query((rs,n)->new HoldRow(UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("wallet_id")),rs.getString("business_reference"),rs.getLong("amount_minor"),
                rs.getString("currency"),rs.getString("status"))).single();
    }

    public void restoreHoldToAvailable(HoldRow hold, String finalStatus, Instant now) {
        if(!"HELD".equals(hold.status())) return;
        jdbc.sql("SELECT id FROM wallet WHERE id=UUID_TO_BIN(:id,1) FOR UPDATE").param("id",hold.walletId().toString()).query().singleValue();
        jdbc.sql("""
                UPDATE wallet SET available_minor=available_minor+:amount,held_minor=held_minor-:amount,
                 version=version+1,updated_at=:now WHERE id=UUID_TO_BIN(:id,1) AND held_minor>=:amount
                """).param("amount",hold.amountMinor()).param("now",now).param("id",hold.walletId().toString()).update();
        jdbc.sql("UPDATE fund_hold SET status=:status,version=version+1,updated_at=:now WHERE id=UUID_TO_BIN(:id,1)")
                .param("status",finalStatus).param("now",now).param("id",hold.id().toString()).update();
    }

    public record HoldRow(UUID id,UUID walletId,String reference,long amountMinor,String currency,String status){}

    public WalletView createWallet(String ownerType, UUID ownerId, Currency currency) {
        UUID walletId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO wallet
                    (id, owner_type, owner_id, currency, status, available_minor, held_minor, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(:id, 1), :ownerType, UUID_TO_BIN(:ownerId, 1), :currency,
                        'ACTIVE', 0, 0, 0, :now, :now)
                """)
                .param("id", walletId.toString())
                .param("ownerType", ownerType)
                .param("ownerId", ownerId.toString())
                .param("currency", currency.getCurrencyCode())
                .param("now", now)
                .update();

        jdbc.sql("""
                INSERT INTO ledger_account
                    (id, wallet_id, account_code, account_type, normal_side, currency, status, created_at)
                VALUES (UUID_TO_BIN(:id, 1), UUID_TO_BIN(:walletId, 1), :code,
                        'WALLET_LIABILITY', 'CREDIT', :currency, 'ACTIVE', :now)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("walletId", walletId.toString())
                .param("code", "WALLET:" + walletId)
                .param("currency", currency.getCurrencyCode())
                .param("now", now)
                .update();

        return new WalletView(walletId, ownerType, ownerId, currency, "ACTIVE", 0, 0, 0, now);
    }

    public Optional<WalletView> findWallet(UUID walletId) {
        return jdbc.sql("""
                SELECT BIN_TO_UUID(id, 1) id, owner_type, BIN_TO_UUID(owner_id, 1) owner_id,
                       currency, status, available_minor, held_minor, version, updated_at
                  FROM wallet WHERE id = UUID_TO_BIN(:id, 1)
                """)
                .param("id", walletId.toString())
                .query((rs, rowNum) -> new WalletView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("owner_type"),
                        UUID.fromString(rs.getString("owner_id")),
                        Currency.getInstance(rs.getString("currency")),
                        rs.getString("status"),
                        rs.getLong("available_minor"),
                        rs.getLong("held_minor"),
                        rs.getLong("version"),
                        rs.getTimestamp("updated_at").toInstant()))
                .optional();
    }

    public JournalView postJournal(PostJournalCommand command) {
        List<String> codes = command.lines().stream().map(LedgerLine::accountCode).distinct().toList();
        Map<String, AccountRow> accounts = jdbc.sql("""
                SELECT BIN_TO_UUID(id, 1) id, account_code, normal_side, currency,
                       BIN_TO_UUID(wallet_id, 1) wallet_id
                  FROM ledger_account
                 WHERE account_code IN (:codes) AND status = 'ACTIVE'
                """)
                .param("codes", codes)
                .query((rs, rowNum) -> new AccountRow(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("account_code"),
                        EntrySide.valueOf(rs.getString("normal_side")),
                        rs.getString("currency"),
                        rs.getString("wallet_id") == null ? null : UUID.fromString(rs.getString("wallet_id"))))
                .list().stream().collect(Collectors.toMap(AccountRow::accountCode, Function.identity()));

        if (accounts.size() != codes.size()) {
            throw new LedgerPostingException("one or more ledger accounts are missing or inactive");
        }
        accounts.values().forEach(account -> {
            if (!account.currency().equals(command.currency().getCurrencyCode())) {
                throw new LedgerPostingException("account currency does not match journal currency");
            }
        });

        accounts.values().stream().map(AccountRow::walletId).filter(java.util.Objects::nonNull)
                .distinct().sorted(Comparator.comparing(UUID::toString))
                .forEach(walletId -> jdbc.sql("SELECT id FROM wallet WHERE id = UUID_TO_BIN(:id, 1) FOR UPDATE")
                        .param("id", walletId.toString()).query().singleValue());

        UUID journalId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.sql("""
                INSERT INTO journal
                    (id, journal_type, business_reference, operation, currency, status,
                     correlation_id, description, posted_at, created_at)
                VALUES (UUID_TO_BIN(:id, 1), :type, :reference, :operation, :currency, 'POSTED',
                        UUID_TO_BIN(:correlationId, 1), :description, :now, :now)
                """)
                .param("id", journalId.toString())
                .param("type", command.journalType())
                .param("reference", command.businessReference())
                .param("operation", command.operation())
                .param("currency", command.currency().getCurrencyCode())
                .param("correlationId", command.correlationId().toString())
                .param("description", command.description())
                .param("now", now)
                .update();

        short sequence = 1;
        for (LedgerLine line : command.lines()) {
            AccountRow account = accounts.get(line.accountCode());
            jdbc.sql("""
                    INSERT INTO ledger_entry
                        (id, journal_id, account_id, entry_side, amount_minor, currency, sequence_no, created_at)
                    VALUES (UUID_TO_BIN(:id, 1), UUID_TO_BIN(:journalId, 1), UUID_TO_BIN(:accountId, 1),
                            :side, :amount, :currency, :sequence, :now)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("journalId", journalId.toString())
                    .param("accountId", account.id().toString())
                    .param("side", line.side().name())
                    .param("amount", line.amountMinor())
                    .param("currency", command.currency().getCurrencyCode())
                    .param("sequence", sequence++)
                    .param("now", now)
                    .update();
            updateWalletProjection(account, line, now);
        }

        jdbc.sql("""
                INSERT INTO outbox_event
                    (id, aggregate_type, aggregate_id, event_type, event_version, partition_key,
                     payload, occurred_at, publish_attempts)
                VALUES (UUID_TO_BIN(:id, 1), 'JOURNAL', UUID_TO_BIN(:journalId, 1),
                        'wallet.journal-posted.v1', 1, :partitionKey,
                        JSON_OBJECT('journalId', :journalIdText, 'businessReference', :businessReference), :now, 0)
                """)
                .param("id", UUID.randomUUID().toString())
                .param("journalId", journalId.toString())
                .param("journalIdText", journalId.toString())
                .param("partitionKey", journalId.toString())
                .param("businessReference", command.businessReference())
                .param("now", now)
                .update();

        return new JournalView(journalId, command.businessReference(), command.operation(), "POSTED", now);
    }

    private void updateWalletProjection(AccountRow account, LedgerLine line, Instant now) {
        if (account.walletId() == null) {
            return;
        }
        long delta = account.normalSide() == line.side() ? line.amountMinor() : -line.amountMinor();
        int changed = jdbc.sql("""
                UPDATE wallet
                   SET available_minor = available_minor + :delta,
                       version = version + 1,
                       updated_at = :now
                 WHERE id = UUID_TO_BIN(:id, 1)
                   AND status = 'ACTIVE'
                   AND available_minor + :delta >= 0
                """)
                .param("delta", delta)
                .param("now", now)
                .param("id", account.walletId().toString())
                .update();
        if (changed != 1) {
            throw new LedgerPostingException("wallet is inactive or has insufficient available funds");
        }
    }

    private record AccountRow(UUID id, String accountCode, EntrySide normalSide, String currency, UUID walletId) {
    }
}
