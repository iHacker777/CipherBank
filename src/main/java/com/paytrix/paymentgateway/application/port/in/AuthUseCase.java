package com.paytrix.paymentgateway.application.port.in;

import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;

import java.util.List;

public interface AuthUseCase {

    String login(String username, String password);
    User register(User user);
    User registerUserWithRoleIds(String username, String rawPassword, List<Long> roleIds);
}
