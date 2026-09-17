package com.qrmenu.admin;

import com.qrmenu.kartapay.OrderDtos.OrderResponse;
import com.qrmenu.kartapay.OrderDtos.OrderSummaryResponse;
import com.qrmenu.kartapay.OrderDtos.UpdateOrderStatusRequest;
import com.qrmenu.kartapay.OrderService;
import com.qrmenu.kartapay.OrderStatus;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Commandes Karta Pay d'un client : {@code /api/admin/restaurants/{id}/orders}. */
@RestController
@RequestMapping("/api/admin/restaurants/{restaurantId}/orders")
public class OrderAdminController {

    private final OrderService orderService;

    public OrderAdminController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Par défaut, seules les commandes du jour courant : {@code ?all=true} pour l'historique complet. */
    @GetMapping
    public List<OrderSummaryResponse> findAll(
            @PathVariable UUID restaurantId,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false, defaultValue = "false") boolean all
    ) {
        return orderService.findByRestaurant(restaurantId, status, all);
    }

    @GetMapping("/{orderId}")
    public OrderResponse findById(@PathVariable UUID restaurantId, @PathVariable UUID orderId) {
        return orderService.getOrThrow(restaurantId, orderId);
    }

    @PutMapping("/{orderId}/status")
    public OrderResponse updateStatus(
            @PathVariable UUID restaurantId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        return orderService.updateStatus(restaurantId, orderId, request.status());
    }
}
