package com.qpay.notification.config;

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
class KafkaNotificationConfiguration {
    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumers, KafkaTemplate<String, String> kafka,
            MeterRegistry metrics,
            @Value("${qpay.notification.retry.interval-ms:2000}") long interval,
            @Value("${qpay.notification.retry.max-attempts:5}") long attempts) {
        var publisher = new DeadLetterPublishingRecoverer(
                kafka, (record, error) -> new TopicPartition(record.topic() + ".notification.dlq", record.partition()));
        var handler = new DefaultErrorHandler((record, error) -> {
            publisher.accept(record, error);
            metrics.counter("qpay.notification.dlq.published", "topic", record.topic()).increment();
        }, new FixedBackOff(interval, Math.max(0, attempts - 1)));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setAckAfterHandle(true);
        handler.setRetryListeners((record, error, attempt) ->
                metrics.counter("qpay.notification.delivery.retry", "topic", record.topic()).increment());
        var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
        factory.setConsumerFactory(consumers);
        factory.setCommonErrorHandler(handler);
        return factory;
    }

    @Bean
    org.apache.kafka.clients.admin.NewTopic notificationDlqTopic() {
        return org.springframework.kafka.config.TopicBuilder.name("qpay.payment.succeeded.v1.notification.dlq")
                .partitions(3).replicas(1)
                .config(org.apache.kafka.common.config.TopicConfig.RETENTION_MS_CONFIG, "1209600000").build();
    }
}
