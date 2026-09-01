package com.shaadrag.identity.controller;

import com.shaadrag.identity.service.EmailVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email-verification")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    @GetMapping("/verify")
    public ResponseEntity<Void> verifyEmail(
            @RequestParam String token) {

        boolean verified =
                emailVerificationService.verifyEmail(token);

        if (!verified) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok().build();
    }
}