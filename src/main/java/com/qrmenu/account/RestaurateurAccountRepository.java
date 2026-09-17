package com.qrmenu.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RestaurateurAccountRepository extends JpaRepository<RestaurateurAccount, UUID> {

    Optional<RestaurateurAccount> findByEmail(String email);

    boolean existsByEmail(String email);
}
