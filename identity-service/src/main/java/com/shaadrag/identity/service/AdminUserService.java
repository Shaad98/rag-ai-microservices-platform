package com.shaadrag.identity.service;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shaadrag.identity.dto.response.AdminUserResponse;
import com.shaadrag.identity.model.Role;
import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminUserService {

        private final UserRepository userRepository;

        @Transactional(readOnly = true)
        public Page<AdminUserResponse> getAllUsers(
                        String currentAdminUserId,
                        Pageable pageable) {

                return userRepository
                                .findByUserIdNot(currentAdminUserId, pageable)
                                .map(this::toAdminUserResponse);
        }

        @Transactional(readOnly = true)
        public Page<AdminUserResponse> searchByEmail(
                        String currentAdminUserId,
                        String email,
                        Pageable pageable) {

                return userRepository
                                .findByUserIdNotAndEmailContainingIgnoreCase(
                                                currentAdminUserId,
                                                email,
                                                pageable)
                                .map(this::toAdminUserResponse);
        }

        @Transactional(readOnly = true)
        public Page<AdminUserResponse> searchByName(
                        String currentAdminUserId,
                        String name,
                        Pageable pageable) {

                return userRepository
                                .findByUserIdNotAndFullNameContainingIgnoreCase(
                                                currentAdminUserId,
                                                name,
                                                pageable)
                                .map(this::toAdminUserResponse);
        }

        @Transactional(readOnly = true)
        public Page<AdminUserResponse> searchByDateOfBirth(
                        String currentAdminUserId,
                        LocalDate dateOfBirth,
                        Pageable pageable) {

                return userRepository
                                .findByUserIdNotAndDateOfBirth(
                                                currentAdminUserId,
                                                dateOfBirth,
                                                pageable)
                                .map(this::toAdminUserResponse);
        }

        @Transactional(readOnly = true)
        public AdminUserResponse getUserDetails(String userId) {

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                return toAdminUserResponse(user);
        }

        public AdminUserResponse activateUser(
                        String currentAdminUserId,
                        String userId) {

                validateNotSelf(currentAdminUserId, userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                user.setIsEnabled(true);

                User updatedUser = userRepository.save(user);

                return toAdminUserResponse(updatedUser);
        }

        public AdminUserResponse deactivateUser(
                        String currentAdminUserId,
                        String userId) {

                validateNotSelf(currentAdminUserId, userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                user.setIsEnabled(false);

                User updatedUser = userRepository.save(user);

                return toAdminUserResponse(updatedUser);
        }

        public AdminUserResponse changeRole(
                        String currentAdminUserId,
                        String userId,
                        Role role) {

                validateNotSelf(currentAdminUserId, userId);

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                user.setRole(role);

                User updatedUser = userRepository.save(user);

                return toAdminUserResponse(updatedUser);
        }

        public void deleteUser(
                        String currentAdminUserId,
                        String userId) {

                validateNotSelf(currentAdminUserId, userId);

                if (!userRepository.existsById(userId)) {
                        throw new RuntimeException("User not found");
                }

                userRepository.deleteById(userId);
        }

        private void validateNotSelf(
                        String currentAdminUserId,
                        String targetUserId) {

                if (currentAdminUserId.equals(targetUserId)) {
                        throw new IllegalStateException(
                                        "Admin cannot modify their own account");
                }
        }

        private AdminUserResponse toAdminUserResponse(User user) {

                return new AdminUserResponse(
                                user.getUserId(),
                                user.getFullName(),
                                user.getEmail(),
                                user.getDateOfBirth(),
                                user.getRole(),
                                user.getIsEnabled());
        }
}