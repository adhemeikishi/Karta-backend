package com.qrmenu.kartapay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ModifierOptionRepository extends JpaRepository<ModifierOption, UUID> {

    List<ModifierOption> findByGroupIdOrderBySortOrderAscNameAsc(UUID groupId);

    List<ModifierOption> findByGroupIdInOrderBySortOrderAscNameAsc(Collection<UUID> groupIds);
}
