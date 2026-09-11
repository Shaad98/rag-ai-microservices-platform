package com.shaadrag.identity.repository;

import com.shaadrag.identity.dto.response.UserResponse;
import com.shaadrag.identity.model.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

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


     Page<User> findByEmailContainingIgnoreCase(
            String email,
            Pageable pageable
    );

    Page<User> findByFullNameContainingIgnoreCase(
            String fullName,
            Pageable pageable
    );

    Page<User> findByDateOfBirth(
            LocalDate dateOfBirth,
            Pageable pageable
    );
}