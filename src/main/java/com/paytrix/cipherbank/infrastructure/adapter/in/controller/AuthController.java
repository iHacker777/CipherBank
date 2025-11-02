package com.paytrix.cipherbank.infrastructure.adapter.in.controller;

import com.paytrix.cipherbank.application.port.in.AuthUseCase;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.Role;
import com.paytrix.cipherbank.infrastructure.adapter.out.persistence.entity.User;
import com.paytrix.cipherbank.domain.model.UserRegistrationRequest;
import com.paytrix.cipherbank.domain.model.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

record AuthRequest(String username, String password) {}
record AuthResponse(String token) {}

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthUseCase authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {

        String token = authService.login(request.username(), request.password());

        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@RequestBody @Valid UserRegistrationRequest request) {

        User user = authService.registerUserWithRoleIds(request.getUsername(), request.getPassword(), request.getRoleIds());

        UserResponse response = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toList())
        );

        return ResponseEntity.ok(response);
    }
}
