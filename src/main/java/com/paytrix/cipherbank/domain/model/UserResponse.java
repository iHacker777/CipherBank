package com.paytrix.cipherbank.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String username;
    private List<String> roles;
}
