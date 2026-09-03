package com.qrmenu.kartaai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MenuDraftRepository extends JpaRepository<MenuDraft, UUID> {

    Optional<MenuDraft> findByRestaurantId(UUID restaurantId);

    void deleteByRestaurantId(UUID restaurantId);
}
