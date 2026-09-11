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
    public Page<AdminUserResponse> getAllUsers(Pageable pageable) {

        return userRepository.findAll(pageable)
                .map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchByEmail(
            String email,
            Pageable pageable) {

        return userRepository
                .findByEmailContainingIgnoreCase(email, pageable)
                .map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchByName(
            String name,
            Pageable pageable) {

        return userRepository
                .findByFullNameContainingIgnoreCase(name, pageable)
                .map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchByDateOfBirth(
            LocalDate dateOfBirth,
            Pageable pageable) {

        return userRepository
                .findByDateOfBirth(dateOfBirth, pageable)
                .map(this::toAdminUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserDetails(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return toAdminUserResponse(user);
    }

    public AdminUserResponse activateUser(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        user.setIsEnabled(true);

        User updatedUser = userRepository.save(user);

        return toAdminUserResponse(updatedUser);
    }

    public AdminUserResponse deactivateUser(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        user.setIsEnabled(false);

        User updatedUser = userRepository.save(user);

        return toAdminUserResponse(updatedUser);
    }

    public AdminUserResponse changeRole(
            String userId,
            Role role) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        user.setRole(role);

        User updatedUser = userRepository.save(user);

        return toAdminUserResponse(updatedUser);
    }

    private AdminUserResponse toAdminUserResponse(User user) {

        return new AdminUserResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getDateOfBirth(),
                user.getRole(),
                user.getIsEnabled()
        );
    }
}