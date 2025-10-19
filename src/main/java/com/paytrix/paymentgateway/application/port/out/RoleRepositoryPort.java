package com.paytrix.paymentgateway.application.port.out;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.Role;

import java.util.Optional;

public interface RoleRepositoryPort {

    Optional<Role> findByName(String name);
    Optional<Role> findById(Long id);
    Role save(Role role);
}
