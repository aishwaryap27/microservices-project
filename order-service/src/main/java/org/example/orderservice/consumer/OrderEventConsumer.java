package org.example.orderservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class OrderEventConsumer {

    @KafkaListener(
            topics = "order-events",
            groupId = "order-consumer-group"
    )
    public void consume(String message) {
        System.out.println("🔥 RECEIVED ORDER EVENT: " + message);
    }
}