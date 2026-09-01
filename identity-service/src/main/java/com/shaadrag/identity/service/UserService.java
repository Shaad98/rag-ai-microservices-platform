package com.shaadrag.identity.service;

import com.shaadrag.identity.dto.response.UserResponse;
import com.shaadrag.identity.model.User;
import com.shaadrag.identity.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // @CachePut(value = "users", key = "#result.userId")
    // public UserResponse saveUser(User user) {

    // user.setPassword(
    // passwordEncoder.encode(user.getPassword())
    // );

    // if (user.getIsEnabled() == null) {
    // user.setIsEnabled(true);
    // }

    // User savedUser = userRepository.save(user);

    // return toUserResponse(savedUser);
    // }

    public User saveUser(User user) {

        user.setPassword(
                passwordEncoder.encode(user.getPassword()));

        user.setIsEnabled(false);

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "users", key = "#userId")
    public UserResponse getUser(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return toUserResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByEmail(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return getUser(user.getUserId());
    }

    public UserResponse updateUserByEmail(
            String email,
            String fullName,
            LocalDate dateOfBirth) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return updateUser(
                user.getUserId(),
                fullName,
                dateOfBirth);
    }

    @CachePut(value = "users", key = "#userId")
    public UserResponse updateUser(
            String userId,
            String fullName,
            java.time.LocalDate dateOfBirth) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // null means don't update that field
        if (fullName != null) {
            user.setFullName(fullName);
        }

        if (dateOfBirth != null) {
            user.setDateOfBirth(dateOfBirth);
        }

        User updatedUser = userRepository.save(user);

        return toUserResponse(updatedUser);
    }

    @CacheEvict(value = "users", key = "#userId")
    public void deleteUser(String userId) {

        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }

        userRepository.deleteById(userId);
    }

    private UserResponse toUserResponse(User user) {

        return new UserResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getDateOfBirth(),
                user.getRole());
    }
}