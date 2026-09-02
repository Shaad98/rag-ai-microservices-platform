package com.shaadrag.identity.service;

import com.shaadrag.identity.dto.request.LoginRequest;
import com.shaadrag.identity.dto.request.RegisterRequest;
import com.shaadrag.identity.dto.response.LoginResponse;
import com.shaadrag.identity.dto.response.RefreshTokenResponse;
import com.shaadrag.identity.dto.response.RegisterResponse;
// import com.shaadrag.identity.exception.EmailSendingException;
import com.shaadrag.identity.exception.EmailVerificationRequiredException;
import com.shaadrag.identity.exception.UserAlreadyExistsException;
import com.shaadrag.identity.exception.UserNotFoundException;
import com.shaadrag.identity.model.RefreshTokenData;
import com.shaadrag.identity.model.Role;
import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;
// import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
// import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

        private final UserRepository userRepository;
        private final AuthenticationManager authenticationManager;
        private final JwtService jwtService;
        private final RefreshTokenService refreshTokenService;
        private final UserService userService;
        private final EmailVerificationService emailVerificationService;
        // private final PasswordEncoder passwordEncoder;
        // =========================================================
        // 1. REGISTER
        // =========================================================

        public RegisterResponse register(RegisterRequest request) {

                User existingUser = userRepository
                                .findByEmail(request.getEmail())
                                .orElse(null);

                // Already registered and verified
                if (existingUser != null && existingUser.isEnabled()) {

                        throw new UserAlreadyExistsException(
                                        "An account with this email already exists");
                }

                // Already registered but not verified
                if (existingUser != null) {

                        LocalDateTime expiry = existingUser.getEmailVerificationTokenExpiry();

                        LocalDateTime now = LocalDateTime.now();

                        // Existing verification token is still valid
                        if (expiry != null &&
                                        expiry.isAfter(now.plusMinutes(3))) {

                                throw new EmailVerificationRequiredException(
                                                "Please verify your email before registering again");
                        }

                        // Token expired or missing
                        // Generate new token and send email asynchronously
                        emailVerificationService.sendVerificationEmail(
                                        existingUser);

                        return new RegisterResponse(
                                        "Your verification link expired. "
                                                        + "A new verification email has been sent.");
                }

                // New user
                User user = new User();

                user.setFullName(request.getFullName());
                user.setEmail(request.getEmail());
                user.setPassword(request.getPassword());
                user.setDateOfBirth(request.getDateOfBirth());
                user.setRole(Role.MEMBER);

                // User must verify email
                user.setIsEnabled(false);

                /*
                 * UserService:
                 * - encodes password
                 * - saves user
                 */
                User savedUser = userService.saveUser(user);

                // Send verification email asynchronously
                emailVerificationService.sendVerificationEmail(
                                savedUser);

                return new RegisterResponse(
                                "Registration successful. Please verify your email.");
        }
        // =========================================================
        // 2. LOGIN
        // =========================================================

        public LoginResponse login(LoginRequest request) {

                try {

                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        request.getEmail(),
                                                        request.getPassword()));

                        String email = authentication.getName();

                        User user = userRepository
                                        .findByEmail(email)
                                        .orElseThrow(() -> new UserNotFoundException(
                                                        "User not found"));

                        String accessToken = jwtService.generateAccessToken(user);

                        String refreshToken = refreshTokenService.createRefreshToken(
                                        user.getUserId());

                        return new LoginResponse(
                                        accessToken,
                                        refreshToken);

                } catch (DisabledException e) {

                        User user = userRepository
                                        .findByEmail(request.getEmail())
                                        .orElseThrow(() -> new UserNotFoundException(
                                                        "User not found"));

                        LocalDateTime expiry = user.getEmailVerificationTokenExpiry();

                        LocalDateTime now = LocalDateTime.now();

                        // Verification link is still valid
                        if (expiry != null &&
                                        expiry.isAfter(now.plusMinutes(3))) {

                                throw new EmailVerificationRequiredException(
                                                "Please verify your email before logging in");
                        }

                        // Token expired or missing
                        // Generate a new token and send email asynchronously
                        emailVerificationService.sendVerificationEmail(user);

                        throw new EmailVerificationRequiredException(
                                        "Your verification link expired. "
                                                        + "A new verification email has been sent.");
                }
        }
        // =========================================================
        // 3. REFRESH
        // =========================================================

        public RefreshTokenResponse refresh(
                        String refreshTokenCookie) {

                String refreshToken = extractRefreshToken(refreshTokenCookie);

                if (refreshToken == null) {

                        throw new IllegalArgumentException(
                                        "Refresh token missing");
                }

                // Check refresh token in Redis
                RefreshTokenData tokenData = refreshTokenService.validateRefreshToken(
                                refreshToken);

                if (tokenData == null) {

                        throw new IllegalArgumentException(
                                        "Invalid or expired refresh token");
                }

                // Get userId from Redis
                String userId = tokenData.getUserId();

                // Get user from database
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new UserNotFoundException(
                                                "User not found"));

                /*
                 * User could have been disabled after login.
                 * Don't allow refresh in that case.
                 */
                if (!user.isEnabled()) {

                        refreshTokenService.deleteRefreshToken(
                                        refreshToken);

                        throw new IllegalStateException(
                                        "User account is disabled");
                }

                // Generate a new access token
                String accessToken = jwtService.generateAccessToken(user);

                return new RefreshTokenResponse(
                                accessToken);
        }

        // =========================================================
        // 4. LOGOUT
        // =========================================================

        public void logout(String refreshTokenCookie) {

                String refreshToken = extractRefreshToken(refreshTokenCookie);

                if (refreshToken == null) {
                        return;
                }

                // Remove refresh token from Redis
                refreshTokenService.deleteRefreshToken(
                                refreshToken);
        }

        // =========================================================
        // HELPER
        // =========================================================

        private String extractRefreshToken(
                        String cookieHeader) {

                if (cookieHeader == null ||
                                cookieHeader.isBlank()) {

                        return null;
                }

                String[] cookies = cookieHeader.split(";");

                for (String cookie : cookies) {

                        String[] parts = cookie.trim().split("=", 2);

                        if (parts.length == 2 &&
                                        "refresh_token".equals(parts[0])) {

                                return parts[1];
                        }
                }

                return null;
        }
}