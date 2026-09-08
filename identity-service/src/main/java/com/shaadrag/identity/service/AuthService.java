package com.shaadrag.identity.service;

import com.shaadrag.identity.dto.request.LoginRequest;
import com.shaadrag.identity.dto.request.RegisterRequest;
import com.shaadrag.identity.dto.response.LoginResponse;
import com.shaadrag.identity.dto.response.RefreshTokenResponse;
import com.shaadrag.identity.dto.response.RegisterResponse;
import com.shaadrag.identity.exception.EmailVerificationRequiredException;
import com.shaadrag.identity.exception.InvalidRefreshTokenException;
import com.shaadrag.identity.exception.UserAlreadyExistsException;
import com.shaadrag.identity.exception.UserDisabledException;
import com.shaadrag.identity.exception.UserNotFoundException;
import com.shaadrag.identity.model.RefreshTokenData;
import com.shaadrag.identity.model.Role;
import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        // =========================================================
        // REGISTER
        // =========================================================

        public RegisterResponse register(
                        RegisterRequest request) {

                String email = request.getEmail()
                                .trim()
                                .toLowerCase();

                User existingUser = userRepository
                                .findByEmail(email)
                                .orElse(null);

                /*
                 * User already registered and verified.
                 */
                if (existingUser != null
                                && existingUser.isEnabled()) {

                        throw new UserAlreadyExistsException(
                                        "An account with this email already exists");
                }

                /*
                 * User exists but email is not verified.
                 */
                if (existingUser != null) {

                        /*
                         * Verification token still has more than
                         * 3 minutes remaining.
                         *
                         * DO NOT SEND ANOTHER EMAIL.
                         */
                        if (!emailVerificationService
                                        .shouldResendVerificationEmail(existingUser)) {

                                throw new EmailVerificationRequiredException(
                                                "Please verify your email before registering again");
                        }

                        /*
                         * Expired or <= 3 minutes remaining.
                         *
                         * Generate a fresh token and send new email.
                         */
                        emailVerificationService
                                        .sendVerificationEmail(existingUser);

                        return new RegisterResponse(
                                        "Your verification link is close to expiry or expired. "
                                                        + "A new verification email has been sent.");
                }

                // =====================================================
                // NEW USER
                // =====================================================

                User user = new User();

                user.setFullName(
                                request.getFullName());

                user.setEmail(email);

                user.setPassword(
                                request.getPassword());

                user.setDateOfBirth(
                                request.getDateOfBirth());

                user.setRole(Role.MEMBER);

                /*
                 * User is disabled until email verification.
                 */
                user.setIsEnabled(false);

                /*
                 * UserService handles password encoding
                 * and database save.
                 */
                User savedUser = userService.saveUser(user);

                /*
                 * Create verification token and
                 * send verification email.
                 */
                emailVerificationService
                                .sendVerificationEmail(savedUser);

                return new RegisterResponse(
                                "Registration successful. Please verify your email.");
        }

        // =========================================================
        // LOGIN
        // =========================================================

        public LoginResponse login(
                        LoginRequest request) {

                try {

                        Authentication authentication = authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        request.getEmail()
                                                                        .trim()
                                                                        .toLowerCase(),
                                                        request.getPassword()));

                        String email = authentication.getName();

                        User user = userRepository
                                        .findByEmail(email)
                                        .orElseThrow(
                                                        () -> new UserNotFoundException(
                                                                        "User not found"));

                        /*
                         * Generate access token.
                         */
                        String accessToken = jwtService.generateAccessToken(user);

                        /*
                         * Generate refresh token in Redis.
                         */
                        String refreshToken = refreshTokenService.createRefreshToken(
                                        user.getUserId());

                        return new LoginResponse(
                                        accessToken,
                                        refreshToken);

                } catch (DisabledException ex) {

                        /*
                         * User is disabled.
                         *
                         * In your design this means
                         * email verification is incomplete.
                         */
                        User user = userRepository
                                        .findByEmail(
                                                        request.getEmail()
                                                                        .trim()
                                                                        .toLowerCase())
                                        .orElseThrow(
                                                        () -> new UserNotFoundException(
                                                                        "User not found"));

                        /*
                         * More than 3 minutes remaining:
                         * DON'T send another email.
                         */
                        if (!emailVerificationService
                                        .shouldResendVerificationEmail(user)) {

                                throw new EmailVerificationRequiredException(
                                                "Please verify your email before logging in");
                        }

                        /*
                         * Expired / <= 3 minutes:
                         * send a new verification email.
                         */
                        emailVerificationService
                                        .sendVerificationEmail(user);

                        throw new EmailVerificationRequiredException(
                                        "Your verification link expired or is close to expiry. "
                                                        + "A new verification email has been sent.");
                }
        }

        // =========================================================
        // REFRESH
        // =========================================================

        public RefreshTokenResponse refresh(
                        String refreshTokenCookie) {

                String refreshToken = extractRefreshToken(refreshTokenCookie);

                if (refreshToken == null) {

                        throw new InvalidRefreshTokenException(
                                        "Refresh token is missing");
                }

                /*
                 * Check Redis.
                 */
                RefreshTokenData tokenData = refreshTokenService
                                .validateRefreshToken(refreshToken);

                if (tokenData == null) {

                        throw new InvalidRefreshTokenException(
                                        "Refresh token is invalid or expired");
                }

                String userId = tokenData.getUserId();

                /*
                 * Check user still exists.
                 */
                User user = userRepository
                                .findById(userId)
                                .orElseThrow(
                                                () -> new UserNotFoundException(
                                                                "User not found"));

                /*
                 * User could have been disabled after login.
                 */
                if (!user.isEnabled()) {

                        refreshTokenService
                                        .deleteRefreshToken(refreshToken);

                        throw new UserDisabledException(
                                        "User account is disabled");
                }

                /*
                 * Generate a new access token.
                 */
                String accessToken = jwtService.generateAccessToken(user);

                return new RefreshTokenResponse(
                                accessToken);
        }

        // =========================================================
        // LOGOUT
        // =========================================================

        public void logout(
                        String refreshTokenCookie) {

                String refreshToken = extractRefreshToken(refreshTokenCookie);

                /*
                 * Logout is idempotent.
                 *
                 * Missing refresh token is NOT an error.
                 */
                if (refreshToken == null) {
                        return;
                }

                refreshTokenService
                                .deleteRefreshToken(refreshToken);
        }

        // =========================================================
        // HELPER
        // =========================================================

        private String extractRefreshToken(
                        String cookieHeader) {

                if (cookieHeader == null
                                || cookieHeader.isBlank()) {

                        return null;
                }

                String[] cookies = cookieHeader.split(";");

                for (String cookie : cookies) {

                        String[] parts = cookie.trim()
                                        .split("=", 2);

                        if (parts.length == 2
                                        && "refresh_token"
                                                        .equals(parts[0])) {

                                return parts[1];
                        }
                }

                return null;
        }
}