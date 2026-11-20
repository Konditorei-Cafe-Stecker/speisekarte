package com.stecker.backend.controller;

import com.stecker.backend.dto.StaffOrderDtos.ConfirmOrderRequest;
import com.stecker.backend.dto.StaffOrderDtos.OrderDetail;
import com.stecker.backend.dto.StaffOrderDtos.OrderSummary;
import com.stecker.backend.security.StaffPrincipal;
import com.stecker.backend.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The waiter's side of orders ({@code /api/staff/**} needs a sign-in). */
@RestController
@RequestMapping("/api/staff/orders")
public class StaffOrderController {

    private final OrderService orderService;

    public StaffOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** {@code ?view=open} (default): waiting orders, oldest first.
     *  {@code ?view=today}: confirmed or cancelled today, newest first. */
    @GetMapping
    public List<OrderSummary> list(@RequestParam(defaultValue = "open") String view) {
        return "today".equals(view) ? orderService.handledToday() : orderService.openOrders();
    }

    @GetMapping("/{reference}")
    public OrderDetail get(@PathVariable UUID reference) {
        return orderService.getForStaff(reference);
    }

    /** An open order by the four-character code the guest reads out. */
    @GetMapping("/code/{code}")
    public OrderDetail byCode(@PathVariable String code) {
        return orderService.findOpenByCode(code);
    }

    /** Entered into the till: confirm, optionally with the final lines. */
    @PostMapping("/{reference}/confirm")
    public OrderDetail confirm(
            @PathVariable UUID reference,
            @Valid @RequestBody(required = false) ConfirmOrderRequest request,
            @AuthenticationPrincipal StaffPrincipal staff
    ) {
        return orderService.confirm(reference, request, staff.id());
    }

    @PostMapping("/{reference}/cancel")
    public OrderDetail cancel(
            @PathVariable UUID reference,
            @AuthenticationPrincipal StaffPrincipal staff
    ) {
        return orderService.cancel(reference, staff.id());
    }
}
