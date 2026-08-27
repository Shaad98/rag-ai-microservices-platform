package com.shaadrag.identity.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.shaadrag.identity.model.User;

public interface UserRepository extends JpaRepository<User,String>{

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByPasswordResetToken(String token);
    
}
