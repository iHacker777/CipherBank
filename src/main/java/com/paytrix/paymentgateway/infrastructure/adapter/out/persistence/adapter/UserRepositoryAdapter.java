package com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.adapter;

import com.paytrix.paymentgateway.application.port.out.UserRepositoryPort;
import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.repository.JpaUserRepository;
import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserRepositoryAdapter implements UserRepositoryPort {

    @Autowired
    private JpaUserRepository jpa;

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username);
    }

    @Override
    public User save(User user) {
        return jpa.save(user);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }
}
