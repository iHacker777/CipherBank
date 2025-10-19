package com.paytrix.paymentgateway.application.port.out;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;

import java.util.Optional;

public interface UserRepositoryPort {

    Optional<User> findByUsername(String username);
    User save(User user);
    boolean existsByUsername(String username);
}
