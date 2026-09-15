package org.example.orderservice.service;

import org.example.orderservice.dto.OrderRequest;
import org.example.orderservice.dto.ProductResponse;
import org.example.orderservice.entity.Order;
import org.example.orderservice.event.OrderEvent;
import org.example.orderservice.producer.OrderEventProducer;
import org.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestClient restClient;
    private final OrderEventProducer orderEventProducer;

    public OrderService(OrderRepository orderRepository,
                        RestClient restClient,
                        OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.restClient = restClient;
        this.orderEventProducer = orderEventProducer;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        return orderRepository.findById(id);
    }

    public Order createOrder(OrderRequest request) {

        System.out.println("PRODUCT ID = " + request.getProductId());
        System.out.println("QUANTITY = " + request.getQuantity());

       /* ProductResponse product = restClient.get()
                .uri("http://product-service:8080/products/" + request.getProductId())
                .retrieve()
                .body(ProductResponse.class);
*/
        ProductResponse product = restClient.get()
                .uri("http://localhost:8080/products/" + request.getProductId())
                .retrieve()
                .body(ProductResponse.class);
        double totalPrice = product.getPrice() * request.getQuantity();

        Order order = new Order();
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setTotalPrice(totalPrice);

        Order savedOrder = orderRepository.save(order);

        OrderEvent event = new OrderEvent(
                savedOrder.getId(),
                savedOrder.getProductId(),
                savedOrder.getQuantity(),
                savedOrder.getTotalPrice(),
                "ORDER_CREATED"
        );

        orderEventProducer.sendOrderCreatedEvent(event);

        return savedOrder;
    }
}