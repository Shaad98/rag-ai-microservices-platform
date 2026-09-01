package com.shaadrag.identity.dto.response;

import java.time.LocalDate;

import com.shaadrag.identity.model.Role;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private String userId;
    private String fullName;
    private String email;
    private LocalDate dateOfBirth;
    private Role role;
}