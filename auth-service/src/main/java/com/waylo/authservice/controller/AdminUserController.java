package com.waylo.authservice.controller;

import com.waylo.authservice.dto.AdminUserResponse;
import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.ChangeUserRoleRequest;
import com.waylo.authservice.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminUserResponse>> findByEmail(
            @RequestParam String email,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        return ResponseEntity.ok(ApiResponse.success("OK", adminUserService.findByEmail(email)));
    }

    @PostMapping("/{id}/role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserRoleRequest req,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        return ResponseEntity.ok(ApiResponse.success("Role updated", adminUserService.changeRole(id, req.role(), userId)));
    }

    private static void requireAdmin(String userRole) {
        if (!"ADMIN".equals(userRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform admin required");
        }
    }
}
