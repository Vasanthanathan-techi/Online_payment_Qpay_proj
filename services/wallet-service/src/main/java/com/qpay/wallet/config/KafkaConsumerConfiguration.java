package com.qpay.wallet.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
class KafkaConsumerConfiguration {
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry metrics,
            @Value("${qpay.kafka.retry.interval-ms:1000}") long retryInterval,
            @Value("${qpay.kafka.retry.max-attempts:3}") long maxAttempts) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".dlq", record.partition()));
        var errorHandler = new DefaultErrorHandler(
                (record, exception) -> {
                    recoverer.accept(record, exception);
                    metrics.counter("qpay.kafka.dlq.published", "topic", record.topic()).increment();
                },
                new FixedBackOff(retryInterval, Math.max(0, maxAttempts - 1)));
        errorHandler.setCommitRecovered(true);
        errorHandler.setAckAfterHandle(true);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) ->
                metrics.counter("qpay.kafka.consumer.retry", "topic", record.topic()).increment());

        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic paymentSucceededTopic(
            @Value("${qpay.kafka.partitions:3}") int partitions) {
        return org.springframework.kafka.config.TopicBuilder.name("qpay.payment.succeeded.v1")
                .partitions(partitions).replicas(1)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .build();
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic paymentSucceededDlqTopic(
            @Value("${qpay.kafka.partitions:3}") int partitions) {
        return org.springframework.kafka.config.TopicBuilder.name("qpay.payment.succeeded.v1.dlq")
                .partitions(partitions).replicas(1)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, "1209600000")
                .build();
    }

    @Bean org.apache.kafka.clients.admin.NewTopic refundSucceededTopic(@Value("${qpay.kafka.partitions:3}") int partitions){return org.springframework.kafka.config.TopicBuilder.name("qpay.refund.succeeded.v1").partitions(partitions).replicas(1).build();}
    @Bean org.apache.kafka.clients.admin.NewTopic refundSucceededDlqTopic(@Value("${qpay.kafka.partitions:3}") int partitions){return org.springframework.kafka.config.TopicBuilder.name("qpay.refund.succeeded.v1.dlq").partitions(partitions).replicas(1).config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG,"1209600000").build();}
}
