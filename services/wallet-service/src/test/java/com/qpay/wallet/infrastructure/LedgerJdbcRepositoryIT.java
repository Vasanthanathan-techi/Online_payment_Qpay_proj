package com.qpay.wallet.infrastructure;

import com.qpay.wallet.application.PostJournalCommand;
import com.qpay.wallet.application.WalletService;
import com.qpay.wallet.domain.EntrySide;
import com.qpay.wallet.domain.LedgerLine;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class LedgerJdbcRepositoryIntegrationTest {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    static LedgerJdbcRepository repository;
    static JdbcClient jdbc;
    static TransactionTemplate transactions;

    @BeforeAll
    static void setUpDatabase() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
        repository = new LedgerJdbcRepository(jdbc);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Test
    void postsBalancedPayInAndUpdatesWalletProjectionOnce() {
        UUID ownerId = UUID.randomUUID();
        var wallet = transactions.execute(status ->
                repository.createWallet("MERCHANT", ownerId, Currency.getInstance("INR")));

        transactions.execute(status -> repository.postJournal(new PostJournalCommand(
                "PAYMENT", "pay-" + UUID.randomUUID(), "PAYMENT_CAPTURE", Currency.getInstance("INR"),
                UUID.randomUUID(), "integration test", List.of(
                new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, 1_000),
                new LedgerLine("WALLET:" + wallet.id(), EntrySide.CREDIT, 980),
                new LedgerLine("FEE_REVENUE", EntrySide.CREDIT, 20)))));

        var updated = repository.findWallet(wallet.id()).orElseThrow();
        assertThat(updated.availableMinor()).isEqualTo(980);
        long debits = jdbc.sql("SELECT SUM(amount_minor) FROM ledger_entry WHERE entry_side='DEBIT'")
                .query(Long.class).single();
        long credits = jdbc.sql("SELECT SUM(amount_minor) FROM ledger_entry WHERE entry_side='CREDIT'")
                .query(Long.class).single();
        assertThat(debits).isEqualTo(credits);
    }

    @Test
    void duplicateBusinessOperationIsRejectedByDatabase() {
        String reference = "duplicate-" + UUID.randomUUID();
        var command = new PostJournalCommand("ADJUSTMENT", reference, "TEST", Currency.getInstance("INR"),
                UUID.randomUUID(), null, List.of(
                new LedgerLine("PROVIDER_CASH", EntrySide.DEBIT, 10),
                new LedgerLine("SUSPENSE", EntrySide.CREDIT, 10)));
        transactions.execute(status -> repository.postJournal(command));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> transactions.execute(status -> repository.postJournal(command)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void replayedPaymentEventIsClaimedAndPostedOnlyOnce() {
        var wallet = transactions.execute(status -> repository.createWallet(
                "MERCHANT", UUID.randomUUID(), Currency.getInstance("INR")));
        var service = new WalletService(repository);
        UUID eventId = UUID.randomUUID(), paymentId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> service.postSuccessfulPayment(
                eventId, paymentId, wallet.id(), "order-replay", 1_000, 20, Currency.getInstance("INR")));
        transactions.executeWithoutResult(status -> service.postSuccessfulPayment(
                eventId, paymentId, wallet.id(), "order-replay", 1_000, 20, Currency.getInstance("INR")));

        assertThat(repository.findWallet(wallet.id()).orElseThrow().availableMinor()).isEqualTo(980);
        Integer journals = jdbc.sql("SELECT COUNT(*) FROM journal WHERE business_reference=:reference AND operation='PAYMENT_CAPTURE'")
                .param("reference", paymentId.toString()).query(Integer.class).single();
        Integer inbox = jdbc.sql("SELECT COUNT(*) FROM inbox_event WHERE event_id=UUID_TO_BIN(:eventId,1)")
                .param("eventId", eventId.toString()).query(Integer.class).single();
        assertThat(journals).isEqualTo(1);
        assertThat(inbox).isEqualTo(1);
    }
}
