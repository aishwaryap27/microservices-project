package org.example.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.entity.Order;
import org.example.orderservice.entity.OutboxEvent;
import org.example.orderservice.repository.OrderRepository;
import org.example.orderservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public OrderService(
            OrderRepository orderRepository,
            OutboxEventRepository outboxEventRepository,
            RestClient restClient,
            ObjectMapper objectMapper) {

        this.orderRepository = orderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    // Get all orders
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    // Get order by ID
    public java.util.Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    // Create order + Outbox event
    @Transactional
    public Order createOrder(OrderRequest request) {

        Long productId = request.getProductId();
        int quantity = request.getQuantity();

        // 1. Get product from Product Service
        ProductResponse product = restClient.get()
                .uri("http://localhost:8080/products/" + productId)
                .retrieve()
                .body(ProductResponse.class);

        if (product == null) {
            throw new RuntimeException("Product not found");
        }

        // 2. Calculate total price
        double totalPrice = product.price() * quantity;

        // 3. Create Order
        Order order = new Order();

        order.setProductId(productId);
        order.setQuantity(quantity);
        order.setTotalPrice(totalPrice);

        // 4. Save Order
        Order savedOrder = orderRepository.save(order);

        // 5. Create OrderCreated event
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getProductId(),
                savedOrder.getQuantity(),
                savedOrder.getTotalPrice()
        );

        try {

            // Convert event to JSON
            String payload = objectMapper.writeValueAsString(event);

            // 6. Save event to Outbox table
            OutboxEvent outboxEvent = new OutboxEvent();

            outboxEvent.setEventType("OrderCreated");

            outboxEvent.setAggregateId(
                    savedOrder.getId().toString()
            );

            outboxEvent.setPayload(payload);

            outboxEvent.setPublished(false);

            outboxEvent.setCreatedAt(
                    LocalDateTime.now()
            );

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {

            throw new RuntimeException(
                    "Failed to create outbox event",
                    e
            );
        }

        /*
         * IMPORTANT:
         * Kafka is NOT called directly here.
         *
         * Order + OutboxEvent are saved in the
         * same database transaction.
         *
         * OutboxPublisher will later publish
         * the event to Kafka.
         */

        return savedOrder;
    }

    // Response received from Product Service
    public record ProductResponse(
            Long id,
            String name,
            double price
    ) {
    }

    // Kafka event stored inside Outbox
    public record OrderCreatedEvent(
            Long orderId,
            Long productId,
            int quantity,
            double totalPrice
    ) {
    }
}