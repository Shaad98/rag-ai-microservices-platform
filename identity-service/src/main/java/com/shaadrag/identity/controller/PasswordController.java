package com.shaadrag.identity.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.shaadrag.identity.dto.request.ChangePasswordRequest;
import com.shaadrag.identity.dto.request.ForgotPasswordRequest;
import com.shaadrag.identity.dto.request.ResetPasswordRequest;
import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;
import com.shaadrag.identity.service.EmailService;

import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/password")
@RequiredArgsConstructor
public class PasswordController {

    private final EmailService emailService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${user.frontend-url}")
    private String frontendUrl;

    @PostMapping("/forgot")
    public ResponseEntity<Void> forgotPassword(
            @RequestBody ForgotPasswordRequest request)
            throws MessagingException {

        Optional<User> userOptional =
                userRepository.findByEmail(request.getEmail());

        if (userOptional.isEmpty()) {
            return ResponseEntity.ok().build();
        }

        User user = userOptional.get();

        String resetToken = UUID.randomUUID().toString();

        user.setPasswordResetToken(resetToken);
        user.setPasswordResetTokenExpiry(
                LocalDateTime.now().plusMinutes(15)
        );

        userRepository.save(user);

        String resetLink =
                frontendUrl + "/reset?token=" + resetToken;

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width, initial-scale=1.0">
                    <title>Reset Your Password</title>
                </head>

                <body style="
                    margin:0;
                    padding:0;
                    background-color:#f4f4f4;
                    font-family:Arial,sans-serif;
                ">

                    <table width="100%%"
                           cellpadding="0"
                           cellspacing="0"
                           style="padding:40px 0;">

                        <tr>
                            <td align="center">

                                <table width="600"
                                       cellpadding="0"
                                       cellspacing="0"
                                       style="
                                           background-color:#ffffff;
                                           border-radius:10px;
                                           padding:40px;
                                       ">

                                    <tr>
                                        <td align="center">

                                            <h1 style="
                                                color:#f97316;
                                                margin-bottom:20px;
                                            ">
                                                ShaadRAG
                                            </h1>

                                            <h2 style="color:#333333;">
                                                Reset Your Password
                                            </h2>

                                            <p style="
                                                color:#555555;
                                                font-size:16px;
                                                line-height:1.6;
                                            ">
                                                We received a request to reset
                                                your ShaadRAG password.
                                            </p>

                                            <p style="margin:30px 0;">

                                                <a href="%s"
                                                   style="
                                                       background-color:#f97316;
                                                       color:#ffffff;
                                                       text-decoration:none;
                                                       padding:14px 28px;
                                                       border-radius:6px;
                                                       font-size:16px;
                                                       font-weight:bold;
                                                       display:inline-block;
                                                   ">
                                                    Reset Password
                                                </a>

                                            </p>

                                            <p style="
                                                color:#777777;
                                                font-size:14px;
                                                line-height:1.5;
                                            ">
                                                If you did not request a
                                                password reset, you can safely
                                                ignore this email.
                                            </p>

                                            <p style="
                                                color:#999999;
                                                font-size:12px;
                                                margin-top:30px;
                                            ">
                                                This link will expire in
                                                15 minutes.
                                            </p>

                                        </td>
                                    </tr>

                                </table>

                            </td>
                        </tr>

                    </table>

                </body>
                </html>
                """.formatted(resetLink);

        emailService.sendHTMLInEmail(
                user.getEmail(),
                "Reset Your ShaadRAG Password",
                html
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset")
    public ResponseEntity<Void> resetPassword(
            @RequestBody ResetPasswordRequest request) {

        Optional<User> userOptional =
                userRepository.findByPasswordResetToken(
                        request.getToken()
                );

        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        User user = userOptional.get();

        if (user.getPasswordResetTokenExpiry() == null ||
            user.getPasswordResetTokenExpiry()
                    .isBefore(LocalDateTime.now())) {

            return ResponseEntity.badRequest().build();
        }

        user.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        // Invalidate reset token after successful password reset
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiry(null);

        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/change")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        String email = authentication.getName();

        Optional<User> userOptional =
                userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        User user = userOptional.get();

        // Verify current password against stored BCrypt hash
        if (!passwordEncoder.matches(
                request.getCurrentPassword(),
                user.getPassword())) {

            return ResponseEntity.badRequest().build();
        }

        user.setPassword(
                passwordEncoder.encode(request.getNewPassword())
        );

        userRepository.save(user);

        return ResponseEntity.ok().build();
    }
}