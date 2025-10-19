package com.paytrix.paymentgateway.application.port.in;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;

import java.util.Optional;

public interface UserUseCase {

    Optional<User> findByUsername(String username);
}
