// src/main/java/com/paytrix/cipherbank/api/dto/ChangePasswordRequest.java
package com.paytrix.cipherbank.domain.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangePasswordRequest {

    @NotBlank
    private String currentPassword;

    @NotBlank
    private String newPassword;
}