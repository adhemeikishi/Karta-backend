package com.qrmenu.restaurant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public class RestaurantDtos {

    private RestaurantDtos() {
    }

    public record CreateRestaurantRequest(
            @NotBlank(message = "name is required")
            @Size(max = 255)
            String name,

            @NotNull(message = "offer is required")
            RestaurantOffer offer
    ) {
    }

    public record UpdateRestaurantRequest(
            @NotBlank(message = "name is required")
            @Size(max = 255)
            String name
    ) {
    }

    public record ChangeOfferRequest(
            @NotNull(message = "offer is required")
            RestaurantOffer offer
    ) {
    }

    public record RestaurantResponse(
            UUID id,
            String name,
            RestaurantOffer offer,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static RestaurantResponse from(Restaurant restaurant) {
            return new RestaurantResponse(
                    restaurant.getId(),
                    restaurant.getName(),
                    restaurant.getOffer(),
                    restaurant.getCreatedAt(),
                    restaurant.getUpdatedAt()
            );
        }
    }

    /**
     * Version enrichie utilisée par la liste du back-office (§2 du brief) : évite au
     * frontend de devoir faire un appel par restaurant pour afficher les compteurs.
     */
    public record RestaurantSummaryResponse(
            UUID id,
            String name,
            RestaurantOffer offer,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            long qrCodeCount,
            long activeQrCodeCount,
            long totalScans
    ) {
    }
}
