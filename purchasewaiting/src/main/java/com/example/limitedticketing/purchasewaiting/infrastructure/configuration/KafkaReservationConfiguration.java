package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.InvalidReservationMessageException;
import com.example.limitedticketing.purchasewaiting.infrastructure.adapter.in.messaging.ReservationKafkaConsumer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
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
public class KafkaReservationConfiguration {
    private static final Logger log = LoggerFactory.getLogger(KafkaReservationConfiguration.class);
    private final List<String> bootstrapServers;

    public KafkaReservationConfiguration(@Value("${spring.kafka.bootstrap-servers:localhost:19092,localhost:29092,localhost:39092}") List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean
    public ProducerFactory<String, String> reservationProducerFactory(MeterRegistry meterRegistry) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        properties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 1000);
        properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000);
        properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000L);
        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(properties);
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
        KafkaAdmin admin = new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        admin.setAutoCreate(provisionTopics && !environment.acceptsProfiles(Profiles.of("api")));
        return admin;
    }

    @Bean
    public NewTopic reservationTopic(@Value("${limited-ticketing.kafka.reservation-topic:limited-ticketing.purchasewaiting.reservation.v1}") String topic,
        @Value("${limited-ticketing.kafka.replication-factor:3}") int replicationFactor,
        @Value("${limited-ticketing.kafka.min-in-sync-replicas:2}") int minInSyncReplicas) {
        return TopicBuilder.name(topic).partitions(6).replicas(replicationFactor)
            .config("min.insync.replicas", Integer.toString(minInSyncReplicas)).config("retention.ms", "604800000").build();
    }

    @Bean
    public NewTopic reservationDeadLetterTopic(@Value("${limited-ticketing.kafka.dead-letter-topic:limited-ticketing.purchasewaiting.reservation.v1-dlt}") String topic,
        @Value("${limited-ticketing.kafka.replication-factor:3}") int replicationFactor,
        @Value("${limited-ticketing.kafka.min-in-sync-replicas:2}") int minInSyncReplicas) {
        return TopicBuilder.name(topic).partitions(6).replicas(replicationFactor)
            .config("min.insync.replicas", Integer.toString(minInSyncReplicas)).config("retention.ms", "1209600000").build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> reservationKafkaListenerContainerFactory(
        KafkaTemplate<String, String> reservationKafkaTemplate, ObjectProvider<KafkaListenerEndpointRegistry> kafkaListenerEndpointRegistry,
        Environment environment, MeterRegistry meterRegistry, @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup,
        @Value("${limited-ticketing.kafka.dead-letter-topic:limited-ticketing.purchasewaiting.reservation.v1-dlt}") String deadLetterTopic) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer = new DeadLetterPublishingRecoverer(reservationKafkaTemplate,
            (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        deadLetterPublishingRecoverer.setFailIfSendResultIsError(true);
        deadLetterPublishingRecoverer.setWaitForSendResultTimeout(Duration.ofSeconds(7));
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
        }, new FixedBackOff(1000, 2));
        defaultErrorHandler.addNotRetryableExceptions(InvalidReservationMessageException.class);
        defaultErrorHandler.setCommitRecovered(true);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        DefaultKafkaConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(properties);
        consumerFactory.addListener(new MicrometerConsumerListener<>(meterRegistry));
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.setAutoStartup(autoStartup && !environment.acceptsProfiles(Profiles.of("api")));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.getContainerProperties().setPollTimeout(200);
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
