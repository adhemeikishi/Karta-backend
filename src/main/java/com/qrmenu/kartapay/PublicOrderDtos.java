package com.qrmenu.kartapay;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Contrat JSON public de Karta Pay : {@code /api/public/restaurants/{code}/orders}.
 *
 * Volontairement sans aucun champ de prix côté client : {@link OrderService} recalcule
 * toujours le total à partir des données serveur, un prix envoyé par le client serait
 * ignoré s'il existait.
 */
public class PublicOrderDtos {

    private PublicOrderDtos() {
    }

    public record CreateOrderRequest(
            @NotNull(message = "fulfillmentType est obligatoire")
            FulfillmentType fulfillmentType,
            String tableNumber,
            @NotBlank(message = "le nom du client est obligatoire")
            @Size(max = 120, message = "le nom du client est trop long")
            String customerName,
            @NotNull(message = "lines est obligatoire")
            @Valid
            List<OrderLineRequest> lines
    ) {
    }

    public record OrderLineRequest(
            @NotNull(message = "itemId est obligatoire")
            UUID itemId,
            @NotNull(message = "quantity est obligatoire")
            @Min(value = 1, message = "quantity doit être >= 1")
            Integer quantity,
            List<UUID> selectedOptionIds
    ) {
    }

    public record CreateOrderResponse(
            String orderNumber,
            int totalCents,
            String currency,
            OrderStatus status
    ) {
    }

    public record PublicOrderStatusResponse(
            String orderNumber,
            OrderStatus status,
            int totalCents,
            String currency,
            OffsetDateTime createdAt
    ) {
    }
}
