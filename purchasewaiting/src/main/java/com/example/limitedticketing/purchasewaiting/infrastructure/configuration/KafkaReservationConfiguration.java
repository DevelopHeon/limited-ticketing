package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.InvalidReservationMessageException;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.ReservationKafkaConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.MicrometerConsumerListener;
import org.springframework.kafka.core.MicrometerProducerListener;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaReservationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaReservationConfiguration.class);
    private final KafkaProperties kafkaProperties;
    private final ObjectProvider<SslBundles> sslBundles;

    public KafkaReservationConfiguration(KafkaProperties kafkaProperties, ObjectProvider<SslBundles> sslBundles) {
        this.kafkaProperties = kafkaProperties;
        this.sslBundles = sslBundles;
    }

    @Bean
    public ProducerFactory<String, String> reservationProducerFactory(MeterRegistry meterRegistry) {
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(kafkaProperties.buildProducerProperties(sslBundles.getIfAvailable()));
        factory.addListener(new MicrometerProducerListener<>(meterRegistry));
        return factory;
    }

    @Bean
    public KafkaTemplate<String, String> reservationKafkaTemplate(ProducerFactory<String, String> reservationProducerFactory) {
        return new KafkaTemplate<>(reservationProducerFactory);
    }

    @Bean
    public KafkaAdmin reservationKafkaAdmin(Environment environment,
        @Value("${limited-ticketing.kafka.provision-topics:true}") boolean provisionTopics) {
        KafkaAdmin admin = new KafkaAdmin(kafkaProperties.buildAdminProperties(sslBundles.getIfAvailable()));
        admin.setAutoCreate(provisionTopics && !environment.acceptsProfiles(Profiles.of("api")));
        return admin;
    }

    @Bean
    public NewTopic reservationTopic(@Value("${limited-ticketing.kafka.reservation-topic:limited-ticketing.purchasewaiting.reservation.v1}") String topic,
        @Value("${limited-ticketing.kafka.partitions}") int partitions,
        @Value("${limited-ticketing.kafka.replication-factor:3}") int replicationFactor,
        @Value("${limited-ticketing.kafka.min-in-sync-replicas:2}") int minInSyncReplicas,
        @Value("${limited-ticketing.kafka.reservation-retention-ms}") long retentionMs) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicationFactor)
            .config("min.insync.replicas", Integer.toString(minInSyncReplicas)).config("retention.ms", Long.toString(retentionMs)).build();
    }

    @Bean
    public NewTopic reservationDeadLetterTopic(@Value("${limited-ticketing.kafka.dead-letter-topic:limited-ticketing.purchasewaiting.reservation.v1-dlt}") String topic,
        @Value("${limited-ticketing.kafka.partitions}") int partitions,
        @Value("${limited-ticketing.kafka.replication-factor:3}") int replicationFactor,
        @Value("${limited-ticketing.kafka.min-in-sync-replicas:2}") int minInSyncReplicas,
        @Value("${limited-ticketing.kafka.dead-letter-retention-ms}") long retentionMs) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(replicationFactor)
            .config("min.insync.replicas", Integer.toString(minInSyncReplicas)).config("retention.ms", Long.toString(retentionMs)).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> reservationKafkaListenerContainerFactory(
        KafkaTemplate<String, String> reservationKafkaTemplate, ObjectProvider<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistry,
        Environment environment, MeterRegistry meterRegistry,
        @Value("${limited-ticketing.kafka.dead-letter-topic:limited-ticketing.purchasewaiting.reservation.v1-dlt}") String deadLetterTopic,
        @Value("${limited-ticketing.kafka.dead-letter-send-timeout-ms}") long deadLetterSendTimeoutMs,
        @Value("${limited-ticketing.kafka.consumer-retry.interval-ms}") long retryIntervalMs,
        @Value("${limited-ticketing.kafka.consumer-retry.max-retries}") long maxRetries) {
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer = new DeadLetterPublishingRecoverer(reservationKafkaTemplate,
            (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        deadLetterPublishingRecoverer.setFailIfSendResultIsError(true);
        deadLetterPublishingRecoverer.setWaitForSendResultTimeout(Duration.ofMillis(deadLetterSendTimeoutMs));
        DefaultErrorHandler defaultErrorHandler = new DefaultErrorHandler((record, exception) -> {
            if (invalidMessage(exception)) {
                try {
                    deadLetterPublishingRecoverer.accept(record, exception);
                    return;
                } catch (RuntimeException failure) {
                    stopConsumer(kafkaListenerEndpointRegistry, failure);
                    throw failure;
                }
            }
            stopConsumer(kafkaListenerEndpointRegistry, exception);
            throw new KafkaException("Reservation recovery requires an available database; offset retained", exception);
        }, new FixedBackOff(retryIntervalMs, maxRetries));
        defaultErrorHandler.addNotRetryableExceptions(InvalidReservationMessageException.class);
        defaultErrorHandler.setCommitRecovered(true);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(kafkaProperties.buildConsumerProperties(sslBundles.getIfAvailable()));
        consumerFactory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(kafkaProperties.getListener().getConcurrency());
        factory.setAutoStartup(kafkaProperties.getListener().isAutoStartup() && !environment.acceptsProfiles(Profiles.of("api")));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(kafkaProperties.getListener().getPollTimeout().toMillis());
        factory.setCommonErrorHandler(defaultErrorHandler);
        return factory;
    }

    private static boolean invalidMessage(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InvalidReservationMessageException) {
                return true;
            }
        }
        return false;
    }

    private static void stopConsumer(ObjectProvider<KafkaListenerEndpointRegistry> registryProvider, Throwable failure) {
        KafkaListenerEndpointRegistry registry = registryProvider.getIfAvailable();
        if (registry != null && registry.getListenerContainer(ReservationKafkaConsumer.LISTENER_ID) != null) {
            registry.getListenerContainer(ReservationKafkaConsumer.LISTENER_ID).stop(() -> log.error("Reservation consumer stopped without acknowledging its failed record", failure));
        }
    }
}
