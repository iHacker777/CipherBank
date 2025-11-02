package com.paytrix.cipherbank.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class UserRegistrationRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private List<Long> roleIds;
}
