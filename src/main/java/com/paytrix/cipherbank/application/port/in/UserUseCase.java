package com.paytrix.cipherbank.application.port.in;

import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.User;

import java.util.Optional;

public interface UserUseCase {

    Optional<User> findByUsername(String username);
}
