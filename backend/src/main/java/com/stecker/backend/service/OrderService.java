package com.stecker.backend.service;

import com.stecker.backend.dto.CreateOrderRequest;
import com.stecker.backend.model.CustomerOrder;
import com.stecker.backend.model.MenuItem;
import com.stecker.backend.model.OrderItem;
import com.stecker.backend.repository.CustomerOrderRepository;
import com.stecker.backend.repository.MenuRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private final CustomerOrderRepository orderRepository;
    private final MenuRepository menuRepository;

    public OrderService(
            CustomerOrderRepository orderRepository,
            MenuRepository menuRepository
    ) {
        this.orderRepository = orderRepository;
        this.menuRepository = menuRepository;
    }

    @Transactional
    public CustomerOrder createOrder(CreateOrderRequest request) {

        if (request == null
                || request.items() == null
                || request.items().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An order must contain at least one item."
            );
        }

        CustomerOrder order = new CustomerOrder(BigDecimal.ZERO);
        BigDecimal total = BigDecimal.ZERO;

        List<OrderItem> orderItems = new ArrayList<>();

        for (CreateOrderRequest.OrderItemRequest requestedItem
                : request.items()) {

            if (requestedItem == null
                    || requestedItem.menuItemId() == null
                    || requestedItem.quantity() <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid product or quantity."
                );
            }

            MenuItem menuItem = menuRepository
                    .findById(requestedItem.menuItemId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Menu item not found: "
                                    + requestedItem.menuItemId()
                    ));

            if (!menuItem.isAvailable()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Product is currently unavailable: "
                                + menuItem.getName()
                );
            }

            OrderItem orderItem = new OrderItem(
                    order,
                    menuItem,
                    requestedItem.quantity()
            );

            orderItems.add(orderItem);
            total = total.add(orderItem.getLineTotal());
        }

        for (OrderItem item : orderItems) {
            order.addItem(item);
        }

        order.setTotalPrice(total);

        return orderRepository.save(order);
    }
}
