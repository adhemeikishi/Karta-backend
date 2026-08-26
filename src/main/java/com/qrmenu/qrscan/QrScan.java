package com.qrmenu.qrscan;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "qr_scans")
public class QrScan {

    @Id
    private UUID id;

    @Column(name = "qr_code_id", nullable = false)
    private UUID qrCodeId;

    @Column(name = "scanned_at", nullable = false)
    private OffsetDateTime scannedAt;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "device_type", length = 20)
    private String deviceType;

    protected QrScan() {
        // JPA
    }

    public QrScan(UUID qrCodeId, String userAgent, DeviceType deviceType) {
        this.id = UUID.randomUUID();
        this.qrCodeId = qrCodeId;
        this.scannedAt = OffsetDateTime.now();
        this.userAgent = userAgent;
        this.deviceType = deviceType.name().toLowerCase();
    }

    public UUID getId() {
        return id;
    }

    public UUID getQrCodeId() {
        return qrCodeId;
    }

    public OffsetDateTime getScannedAt() {
        return scannedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getDeviceType() {
        return deviceType;
    }
}
