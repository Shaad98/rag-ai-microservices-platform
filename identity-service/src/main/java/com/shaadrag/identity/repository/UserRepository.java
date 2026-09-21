package com.shaadrag.identity.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shaadrag.identity.dto.response.UserResponse;
import com.shaadrag.identity.model.User;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPasswordResetToken(String token);

    Optional<User> findByEmailVerificationToken(String token);

    void deleteByEmail(String email);

    @Query("""
        SELECT new com.shaadrag.identity.dto.response.UserResponse(
            u.userId,
            u.fullName,
            u.email,
            u.dateOfBirth,
            u.role
        )
        FROM User u
        WHERE u.email = :email
        """)
    Optional<UserResponse> findUserResponseByEmail(
            @Param("email") String email
    );

    @Query("""
        SELECT new com.shaadrag.identity.dto.response.UserResponse(
            u.userId,
            u.fullName,
            u.email,
            u.dateOfBirth,
            u.role
        )
        FROM User u
        WHERE u.userId = :userId
        """)
    Optional<UserResponse> findUserResponseById(
            @Param("userId") String userId
    );

    Page<User> findByUserIdNot(
            String userId,
            Pageable pageable
    );

    Page<User> findByUserIdNotAndEmailContainingIgnoreCase(
            String userId,
            String email,
            Pageable pageable
    );

    Page<User> findByUserIdNotAndFullNameContainingIgnoreCase(
            String userId,
            String fullName,
            Pageable pageable
    );

    Page<User> findByUserIdNotAndDateOfBirth(
            String userId,
            LocalDate dateOfBirth,
            Pageable pageable
    );
}