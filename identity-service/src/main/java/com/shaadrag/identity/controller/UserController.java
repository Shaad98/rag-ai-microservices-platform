package com.shaadrag.identity.controller;

import com.shaadrag.identity.dto.request.UpdateUserRequest;
import com.shaadrag.identity.dto.response.UserResponse;
import com.shaadrag.identity.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // =========================================================
    // GET CURRENT USER
    // =========================================================

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(
            Authentication authentication) {

        /*
         * In your security setup, getName()
         * returns the authenticated user's email.
         */
        String email = authentication.getName();

        UserResponse user =
                userService.getUserByEmail(email);

        return ResponseEntity.ok(user);
    }


    // =========================================================
    // UPDATE CURRENT USER
    // =========================================================

    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateCurrentUser(
            @RequestBody UpdateUserRequest request,
            Authentication authentication) {

        /*
         * Get authenticated user's email.
         */
        String email = authentication.getName();

        UserResponse updatedUser =
                userService.updateUserByEmail(
                        email,
                        request.getFullName(),
                        request.getDateOfBirth()
                );

        return ResponseEntity.ok(updatedUser);
    }
}