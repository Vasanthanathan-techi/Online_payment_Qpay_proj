package com.qpay.payment.infrastructure;

import com.qpay.payment.application.CreatePaymentCommand;
import com.qpay.payment.domain.PaymentStatus;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Currency;
import java.util.Map;
import java.util.List;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class PaymentLifecycleIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Container static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");
    static PaymentJdbcRepository repository;
    static RefundRepository refunds;
    static TransactionTemplate transactions;
    static JdbcClient jdbc;

    @BeforeAll static void setUp() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        repository = new PaymentJdbcRepository(JdbcClient.create(dataSource));
        refunds = new RefundRepository(JdbcClient.create(dataSource));
        jdbc = JdbcClient.create(dataSource);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Test void identicalIdempotencyKeyReturnsSamePaymentAndChangedPayloadIsRejected() throws Exception {
        UUID merchant = UUID.randomUUID();
        var command = command(merchant, 1_000, "idem-12345678");
        byte[] hash = MessageDigest.getInstance("SHA-256").digest("canonical-one".getBytes(StandardCharsets.UTF_8));
        var first = transactions.execute(s -> repository.reserve(command, hash, 20));
        var replay = transactions.execute(s -> repository.reserve(command, hash, 20));
        assertThat(replay.replay()).isTrue();
        assertThat(replay.payment().id()).isEqualTo(first.payment().id());
        assertThatThrownBy(() -> transactions.execute(s -> repository.reserve(command, new byte[32], 20)))
                .hasMessageContaining("different request");
    }

    @Test void duplicateSuccessfulWebhookCreatesOneSuccessEvent() throws Exception {
        var command = command(UUID.randomUUID(), 2_000, "idem-" + UUID.randomUUID());
        byte[] requestHash = MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes());
        var payment = transactions.execute(s -> repository.reserve(command, requestHash, 40)).payment();
        transactions.executeWithoutResult(s -> repository.markProcessing(payment.id()));
        transactions.execute(s -> repository.providerCreated(payment.id(), "STUB", "provider-" + payment.id(), "https://example.test/pay"));
        byte[] payloadHash = MessageDigest.getInstance("SHA-256").digest("success".getBytes());
        transactions.execute(s -> repository.succeedWebhook("STUB", "event-1-" + payment.id(), "provider-" + payment.id(), payloadHash));
        transactions.execute(s -> repository.succeedWebhook("STUB", "event-1-" + payment.id(), "provider-" + payment.id(), payloadHash));

        assertThat(repository.find(payment.id()).orElseThrow().status()).isEqualTo(PaymentStatus.SUCCEEDED);
        Integer successEvents = jdbc.sql("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id=UUID_TO_BIN(:id,1) AND event_type='payment.succeeded.v1'")
                .param("id", payment.id().toString()).query(Integer.class).single();
        assertThat(successEvents).isEqualTo(1);
    }

    @Test void transactionalOutboxPublishesSuccessEventExactlyOnce() throws Exception {
        jdbc.sql("UPDATE outbox_event SET published_at=CURRENT_TIMESTAMP(6) WHERE published_at IS NULL").update();
        var command = command(UUID.randomUUID(), 3_000, "idem-" + UUID.randomUUID());
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes());
        var payment = transactions.execute(s -> repository.reserve(command, hash, 60)).payment();
        transactions.executeWithoutResult(s -> repository.markProcessing(payment.id()));
        String providerReference = "provider-kafka-" + payment.id();
        transactions.execute(s -> repository.providerCreated(payment.id(), "STUB", providerReference, "https://example.test"));
        transactions.execute(s -> repository.succeedWebhook("STUB", "event-kafka-" + payment.id(), providerReference, hash));

        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
        var publisher = new OutboxPublisher(jdbc, new KafkaTemplate<>(producerFactory));
        try (var consumer = consumer("outbox-test-" + payment.id())) {
            consumer.subscribe(List.of("qpay.payment.succeeded.v1"));
            publisher.publishBatch();
            ConsumerRecords<String, String> first = consumer.poll(Duration.ofSeconds(10));
            assertThat(first.count()).isEqualTo(1);
            assertThat(first.iterator().next().value()).contains(payment.id().toString(), "payment.succeeded.v1");

            publisher.publishBatch();
            assertThat(consumer.poll(Duration.ofSeconds(2))).isEmpty();
        } finally {
            producerFactory.destroy();
        }
    }

    @Test void cumulativePendingRefundsCannotExceedCapturedPayment() throws Exception {
        UUID merchant=UUID.randomUUID();var command=command(merchant,1_000,"idem-"+UUID.randomUUID());byte[] hash=MessageDigest.getInstance("SHA-256").digest(UUID.randomUUID().toString().getBytes());var payment=transactions.execute(s->repository.reserve(command,hash,20)).payment();transactions.executeWithoutResult(s->repository.markProcessing(payment.id()));String providerRef="provider-refund-"+payment.id();transactions.execute(s->repository.providerCreated(payment.id(),"STUB",providerRef,"https://example.test"));transactions.execute(s->repository.succeedWebhook("STUB","event-refund-"+payment.id(),providerRef,hash));
        transactions.execute(s->refunds.reserve(merchant,payment.id(),"refund-one",700));
        assertThatThrownBy(()->transactions.execute(s->refunds.reserve(merchant,payment.id(),"refund-two",400))).hasMessageContaining("exceeds remaining refundable amount");
    }

    private static KafkaConsumer<String, String> consumer(String group) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, group,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private static CreatePaymentCommand command(UUID merchant, long amount, String key) {
        return new CreatePaymentCommand(merchant, UUID.randomUUID(), "order-" + UUID.randomUUID(), amount,
                Currency.getInstance("INR"), "UPI", "https://merchant.test/return", key);
    }
}
