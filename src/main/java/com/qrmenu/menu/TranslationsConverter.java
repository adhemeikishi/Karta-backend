package com.qrmenu.menu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

/**
 * Colonne {@code translations} (TEXT JSON) ⇄ {@code Map<code langue, Translation>}.
 *
 * Un document par ligne plutôt qu'une table : il vit et meurt avec la catégorie ou le
 * plat, sans jointure ni nettoyage. Vide en Java = NULL en base.
 */
@Converter
public class TranslationsConverter implements AttributeConverter<Map<String, Translation>, String> {

    // Tolérant à la lecture : un champ ajouté un jour au JSON ne doit pas effacer les traductions.
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final TypeReference<Map<String, Translation>> TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(Map<String, Translation> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Traductions non sérialisables", e);
        }
    }

    @Override
    public Map<String, Translation> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            // Une colonne corrompue ne doit pas rendre la carte illisible : on affiche le français.
            return Map.of();
        }
    }
}
