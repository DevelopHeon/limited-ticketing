package com.example.limitedticketing.purchasewaiting.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

@DisplayName("예약 Kafka 설정 적용")
class KafkaReservationConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(KafkaReservationConfiguration.class)
        .withBean(SimpleMeterRegistry.class, SimpleMeterRegistry::new)
        .withPropertyValues("limited-ticketing.kafka.provision-topics=false", "spring.kafka.listener.auto-startup=false",
            "spring.kafka.listener.concurrency=3", "spring.kafka.listener.poll-timeout=200ms",
            "limited-ticketing.kafka.partitions=6", "limited-ticketing.kafka.reservation-retention-ms=604800000",
            "limited-ticketing.kafka.dead-letter-retention-ms=1209600000", "limited-ticketing.kafka.dead-letter-send-timeout-ms=7000",
            "limited-ticketing.kafka.consumer-retry.interval-ms=1000", "limited-ticketing.kafka.consumer-retry.max-retries=2");

    @Test
    @DisplayName("Consumer 동시성과 poll 설정을 변경하면 실제 예약 수신 컨테이너에 적용된다")
    @SuppressWarnings("unchecked")
    void appliesConsumerCapacityAndPollSettings() {
        contextRunner.withPropertyValues("spring.kafka.listener.concurrency=2", "spring.kafka.listener.poll-timeout=350ms",
            "spring.kafka.consumer.max-poll-records=7").run(context -> {
                ConcurrentKafkaListenerContainerFactory<String, String> factory = context.getBean(
                    "reservationKafkaListenerContainerFactory", ConcurrentKafkaListenerContainerFactory.class);
                ConcurrentMessageListenerContainer<String, String> container = factory.createContainer("reservation-test");

                assertThat(container.getConcurrency()).isEqualTo(2);
                assertThat(container.getContainerProperties().getPollTimeout()).isEqualTo(350);
                assertThat(factory.getConsumerFactory().getConfigurationProperties()).containsEntry(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 7);
                assertThat(container.getContainerProperties().getAckMode()).isEqualTo(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
                assertThat(container.isAutoStartup()).isFalse();
            });
    }

    @Test
    @DisplayName("Producer 연결별 발행 상한과 전송 제한 시간을 변경하면 실제 발행 Factory에 적용된다")
    @SuppressWarnings("unchecked")
    void appliesProducerLimitsAndTimeouts() {
        contextRunner.withPropertyValues("spring.kafka.producer.properties.max.in.flight.requests.per.connection=3",
            "spring.kafka.producer.properties.request.timeout.ms=2000", "spring.kafka.producer.properties.delivery.timeout.ms=9000",
            "spring.kafka.producer.properties.max.block.ms=4000").run(context -> {
                Map<String, Object> properties = context.getBean("reservationProducerFactory", DefaultKafkaProducerFactory.class).getConfigurationProperties();

                assertThat(properties).containsEntry(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "3")
                    .containsEntry(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000")
                    .containsEntry(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "9000")
                    .containsEntry(ProducerConfig.MAX_BLOCK_MS_CONFIG, "4000");
            });
    }

    @Test
    @DisplayName("예약과 DLT는 동일한 partition 수를 적용하고 각각의 보존 기간을 사용한다")
    void appliesTopicPartitionsAndRetention() {
        contextRunner.withPropertyValues("limited-ticketing.kafka.partitions=4", "limited-ticketing.kafka.reservation-retention-ms=86400000",
            "limited-ticketing.kafka.dead-letter-retention-ms=172800000").run(context -> {
                NewTopic reservationTopic = context.getBean("reservationTopic", NewTopic.class);
                NewTopic deadLetterTopic = context.getBean("reservationDeadLetterTopic", NewTopic.class);

                assertThat(reservationTopic.numPartitions()).isEqualTo(4);
                assertThat(deadLetterTopic.numPartitions()).isEqualTo(4);
                assertThat(reservationTopic.configs()).containsEntry("retention.ms", "86400000");
                assertThat(deadLetterTopic.configs()).containsEntry("retention.ms", "172800000");
            });
    }
}
