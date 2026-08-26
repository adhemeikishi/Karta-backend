package com.qrmenu.qrcode;

import com.qrmenu.common.DestinationUrlValidator;
import com.qrmenu.common.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QrCodeService {

    private static final int MAX_CODE_GENERATION_ATTEMPTS = 10;

    private final QrCodeRepository qrCodeRepository;
    private final QrCodeGenerator qrCodeGenerator;
    private final DestinationUrlValidator destinationUrlValidator;

    public QrCodeService(
            QrCodeRepository qrCodeRepository,
            QrCodeGenerator qrCodeGenerator,
            DestinationUrlValidator destinationUrlValidator
    ) {
        this.qrCodeRepository = qrCodeRepository;
        this.qrCodeGenerator = qrCodeGenerator;
        this.destinationUrlValidator = destinationUrlValidator;
    }

    public QrCode create(UUID restaurantId, String name, String destinationUrl) {
        destinationUrlValidator.validate(destinationUrl);
        String uniqueCode = generateUniqueCode();
        QrCode qrCode = new QrCode(restaurantId, name, destinationUrl, uniqueCode);
        return qrCodeRepository.save(qrCode);
    }

    public QrCode getOrThrow(UUID id) {
        return qrCodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("QR code introuvable: " + id));
    }

    public QrCode getByCodeOrThrow(String code) {
        return qrCodeRepository.findByCode(code)
                .orElseThrow(() -> new NotFoundException("QR code introuvable: " + code));
    }

    public List<QrCode> findByRestaurant(UUID restaurantId) {
        return qrCodeRepository.findByRestaurantId(restaurantId);
    }

    public QrCode updateDestination(UUID id, String newDestinationUrl) {
        destinationUrlValidator.validate(newDestinationUrl);
        QrCode qrCode = getOrThrow(id);
        qrCode.updateDestination(newDestinationUrl);
        return qrCodeRepository.save(qrCode);
    }

    public QrCode activate(UUID id) {
        QrCode qrCode = getOrThrow(id);
        qrCode.activate();
        return qrCodeRepository.save(qrCode);
    }

    public QrCode deactivate(UUID id) {
        QrCode qrCode = getOrThrow(id);
        qrCode.deactivate();
        return qrCodeRepository.save(qrCode);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_GENERATION_ATTEMPTS; attempt++) {
            String candidate = qrCodeGenerator.generate();
            if (!qrCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Impossible de générer un code QR unique après " + MAX_CODE_GENERATION_ATTEMPTS + " tentatives.");
    }
}
