package org.example.orderservice.service;

import org.example.orderservice.entity.OutboxEvent;
import org.example.orderservice.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate) {

        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {

        List<OutboxEvent> events =
                outboxEventRepository
                        .findByPublishedFalseOrderByIdAsc();

        for (OutboxEvent event : events) {

            try {

                kafkaTemplate
                        .send(
                                "order-events",
                                event.getAggregateId(),
                                event.getPayload()
                        )
                        .get(10, TimeUnit.SECONDS);

                event.setPublished(true);

                outboxEventRepository.save(event);

                System.out.println(
                        "Published outbox event: " + event.getId()
                );

            } catch (Exception e) {

                System.out.println(
                        "Failed to publish outbox event: " + event.getId()
                );

                e.printStackTrace();
            }
        }
    }
}