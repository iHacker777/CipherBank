package com.paytrix.paymentgateway.application.service;

import com.paytrix.paymentgateway.application.port.in.AuthUseCase;
import com.paytrix.paymentgateway.application.port.out.RoleRepositoryPort;
import com.paytrix.paymentgateway.application.port.out.UserRepositoryPort;
import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.Role;
import com.paytrix.paymentgateway.infrastructure.adapter.out.persistence.entity.User;
import com.paytrix.paymentgateway.infrastructure.security.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthService implements AuthUseCase {

    @Autowired
    private UserRepositoryPort userRepositoryPort;

    @Autowired
    private RoleRepositoryPort roleRepositoryPort;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    public String login(String username, String password) {

        var opt = userRepositoryPort.findByUsername(username);

        if (opt.isEmpty()) {
            throw new BadCredentialsException("Invalid credentials");
        }

        User user = opt.get();

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        return jwtTokenUtil.generateTokenFromUser(user);
    }

    @Override
    public User register(User user) {
        return registerUserWithRoleIds(user.getUsername(), user.getPassword(), null);
    }

    @Override
    public User registerUserWithRoleIds(String username, String rawPassword, List<Long> roleIds) {

        if (userRepositoryPort.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));

        Set<Role> roles = new HashSet<>();

        if (roleIds != null && !roleIds.isEmpty()) {
            // Fetch roles by IDs
            for (Long id : roleIds) {
                roleRepositoryPort.findById(id).ifPresent(roles::add);
            }
            if (roles.isEmpty()) {
                throw new IllegalArgumentException("Invalid role IDs provided");
            }
        } else {
            // Default to ROLE_USER
            Role roleUser = roleRepositoryPort.findByName("ROLE_USER")
                    .orElseGet(() -> roleRepositoryPort.save(new Role(null, "ROLE_USER")));
            roles.add(roleUser);
        }

        user.setRoles(roles);

        return userRepositoryPort.save(user);
    }
}
