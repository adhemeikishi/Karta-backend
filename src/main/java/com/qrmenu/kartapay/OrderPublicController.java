package com.qrmenu.kartapay;

import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderRequest;
import com.qrmenu.kartapay.PublicOrderDtos.CreateOrderResponse;
import com.qrmenu.kartapay.PublicOrderDtos.PublicOrderStatusResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Commande publique Karta Pay, résolue par le code QR du client comme
 * {@code PublicMenuService.findPublic} : {@code /api/public/restaurants/{code}/orders}.
 */
@RestController
@RequestMapping("/api/public/restaurants/{code}/orders")
public class OrderPublicController {

    private final OrderService orderService;

    public OrderPublicController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> create(
            @PathVariable String code,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        UUID restaurantId = orderService.resolveRestaurantId(code);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.createOrder(restaurantId, request));
    }

    @GetMapping("/{orderNumber}")
    public PublicOrderStatusResponse findByOrderNumber(@PathVariable String code, @PathVariable String orderNumber) {
        return orderService.findByOrderNumberPublic(code, orderNumber);
    }
}
