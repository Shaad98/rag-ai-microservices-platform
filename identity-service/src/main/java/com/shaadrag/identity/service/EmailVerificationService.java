package com.shaadrag.identity.service;

import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final UserRepository userRepository;
    private final EmailService emailService;

    @Value("${user.frontend-url}")
    private String frontendUrl;

    public void sendVerificationEmail(User user) throws MessagingException {

        String verificationToken = UUID.randomUUID().toString();

        user.setEmailVerificationToken(verificationToken);
        user.setEmailVerificationTokenExpiry(
                LocalDateTime.now().plusMinutes(15)
        );

        userRepository.save(user);

        String verificationLink =
                frontendUrl + "/verify-email?token=" + verificationToken;

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="
                    margin:0;
                    padding:0;
                    background-color:#f4f4f4;
                    font-family:Arial,sans-serif;
                ">
                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="padding:40px 0;">
                        <tr>
                            <td align="center">
                                <table width="600" cellpadding="0" cellspacing="0"
                                       style="
                                           background-color:#ffffff;
                                           border-radius:10px;
                                           padding:40px;
                                       ">
                                    <tr>
                                        <td align="center">

                                            <h1 style="color:#f97316;">
                                                ShaadRAG
                                            </h1>

                                            <h2 style="color:#333333;">
                                                Verify Your Email
                                            </h2>

                                            <p style="
                                                color:#555555;
                                                font-size:16px;
                                                line-height:1.6;
                                            ">
                                                Thanks for creating your
                                                ShaadRAG account. Please verify
                                                your email address to activate
                                                your account.
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
                                                    Verify Email
                                                </a>
                                            </p>

                                            <p style="
                                                color:#777777;
                                                font-size:14px;
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
                """.formatted(verificationLink);

        emailService.sendHTMLInEmail(
                user.getEmail(),
                "Verify Your ShaadRAG Email",
                html
        );
    }

    public boolean verifyEmail(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        User user = userRepository
                .findByEmailVerificationToken(token)
                .orElse(null);

        if (user == null) {
            return false;
        }

        if (user.getEmailVerificationTokenExpiry() == null ||
                user.getEmailVerificationTokenExpiry()
                        .isBefore(LocalDateTime.now())) {
            return false;
        }

        user.setIsEnabled(true);
        user.setEmailVerificationToken(null);
        user.setEmailVerificationTokenExpiry(null);

        userRepository.save(user);

        return true;
    }
}