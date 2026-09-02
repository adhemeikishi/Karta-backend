package com.qrmenu.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MenuRepository extends JpaRepository<Menu, UUID> {

    Optional<Menu> findByRestaurantId(UUID restaurantId);
}
