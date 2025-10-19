package com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.repository;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaUserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
}
