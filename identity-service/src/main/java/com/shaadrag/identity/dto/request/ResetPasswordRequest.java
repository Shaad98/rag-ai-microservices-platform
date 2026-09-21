package com.shaadrag.identity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor

public class ResetPasswordRequest {

    @NotBlank(message = "Verification token required")
    private String token; // Token inside link
    @NotBlank(message = "New password required")
    @Size(min = 8,message = "Password must contain atleast 8 characters")
    private String newPassword;
}