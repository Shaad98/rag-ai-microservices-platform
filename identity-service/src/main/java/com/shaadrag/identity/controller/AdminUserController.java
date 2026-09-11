package com.shaadrag.identity.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shaadrag.identity.dto.response.AdminUserResponse;
import com.shaadrag.identity.model.Role;
import com.shaadrag.identity.service.AdminUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Page<AdminUserResponse> getAllUsers(
            @PageableDefault(
                    size = 10,
                    sort = "fullName",
                    direction = Sort.Direction.ASC
            )
            Pageable pageable) {

        return adminUserService.getAllUsers(pageable);
    }

    @GetMapping("/search/email")
    public Page<AdminUserResponse> searchByEmail(
            @RequestParam String email,
            @PageableDefault(size = 10)
            Pageable pageable) {

        return adminUserService.searchByEmail(
                email,
                pageable
        );
    }

    @GetMapping("/search/name")
    public Page<AdminUserResponse> searchByName(
            @RequestParam String name,
            @PageableDefault(size = 10)
            Pageable pageable) {

        return adminUserService.searchByName(
                name,
                pageable
        );
    }

    @GetMapping("/search/dob")
    public Page<AdminUserResponse> searchByDateOfBirth(
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate dateOfBirth,
            @PageableDefault(size = 10)
            Pageable pageable) {

        return adminUserService.searchByDateOfBirth(
                dateOfBirth,
                pageable
        );
    }

    @GetMapping("/{userId}")
    public AdminUserResponse getUserDetails(
            @PathVariable String userId) {

        return adminUserService.getUserDetails(userId);
    }

    @PatchMapping("/{userId}/activate")
    public AdminUserResponse activateUser(
            @PathVariable String userId) {

        return adminUserService.activateUser(userId);
    }

    @PatchMapping("/{userId}/deactivate")
    public AdminUserResponse deactivateUser(
            @PathVariable String userId) {

        return adminUserService.deactivateUser(userId);
    }

    @PatchMapping("/{userId}/role")
    public AdminUserResponse changeRole(
            @PathVariable String userId,
            @RequestParam Role role) {

        return adminUserService.changeRole(
                userId,
                role
        );
    }
}