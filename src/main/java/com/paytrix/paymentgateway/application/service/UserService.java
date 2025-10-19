package com.paytrix.paymentgateway.application.service;

import com.paytrix.paymentgateway.application.port.in.UserUseCase;
import com.paytrix.paymentgateway.application.port.out.UserRepositoryPort;
import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService implements UserUseCase {

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepositoryPort.findByUsername(username);
    }
}
