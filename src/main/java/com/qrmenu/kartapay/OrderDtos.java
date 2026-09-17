package com.qrmenu.kartapay;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Contrat JSON des commandes (admin) : {@code /api/admin/restaurants/{id}/orders}. */
public class OrderDtos {

    private OrderDtos() {
    }

    public record OrderLineResponse(
            UUID id,
            UUID itemId,
            String itemNameSnapshot,
            int unitPriceCentsSnapshot,
            int quantity,
            int lineTotalCents,
            String modifiersSnapshot
    ) {
    }

    public record OrderResponse(
            UUID id,
            String orderNumber,
            OrderStatus status,
            FulfillmentType fulfillmentType,
            String tableNumber,
            String customerName,
            int subtotalCents,
            int totalCents,
            String currency,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<OrderLineResponse> lines
    ) {
    }

    public record OrderSummaryResponse(
            UUID id,
            String orderNumber,
            OrderStatus status,
            FulfillmentType fulfillmentType,
            String tableNumber,
            String customerName,
            int totalCents,
            String currency,
            OffsetDateTime createdAt
    ) {
    }

    public record UpdateOrderStatusRequest(
            @NotNull(message = "status est obligatoire")
            OrderStatus status
    ) {
    }
}
