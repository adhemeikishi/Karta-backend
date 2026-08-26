package com.qrmenu.qrcode;

import com.qrmenu.qrscan.QrScanService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

public class QrCodeDtos {

    private QrCodeDtos() {
    }

    public record CreateQrCodeRequest(
            @NotBlank(message = "name is required")
            @Size(max = 100)
            String name,

            @NotBlank(message = "destinationUrl is required")
            String destinationUrl
    ) {
    }

    public record UpdateQrCodeRequest(
            @NotBlank(message = "destinationUrl is required")
            String destinationUrl
    ) {
    }

    public record QrCodeResponse(
            UUID id,
            UUID restaurantId,
            String name,
            String destinationUrl,
            String code,
            String redirectUrl,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
        public static QrCodeResponse from(QrCode qrCode, String redirectUrl) {
            return new QrCodeResponse(
                    qrCode.getId(),
                    qrCode.getRestaurantId(),
                    qrCode.getName(),
                    qrCode.getDestinationUrl(),
                    qrCode.getCode(),
                    redirectUrl,
                    qrCode.isActive(),
                    qrCode.getCreatedAt(),
                    qrCode.getUpdatedAt()
            );
        }
    }

    public record QrCodeStatsResponse(
            UUID qrCodeId,
            long today,
            long thisWeek,
            long thisMonth,
            long total
    ) {
        public static QrCodeStatsResponse from(UUID qrCodeId, QrScanService.QrScanStats stats) {
            return new QrCodeStatsResponse(
                    qrCodeId, stats.today(), stats.thisWeek(), stats.thisMonth(), stats.total());
        }
    }
}
