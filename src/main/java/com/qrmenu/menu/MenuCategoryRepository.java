package com.qrmenu.menu;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuCategoryRepository extends JpaRepository<MenuCategory, UUID> {

    List<MenuCategory> findByMenuIdOrderBySortOrderAscNameAsc(UUID menuId);

    long countByMenuId(UUID menuId);
}
