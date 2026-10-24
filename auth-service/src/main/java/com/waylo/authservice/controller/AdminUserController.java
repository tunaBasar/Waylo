package com.waylo.authservice.controller;

import com.waylo.authservice.dto.AdminUserResponse;
import com.waylo.authservice.dto.ApiResponse;
import com.waylo.authservice.dto.ChangeUserRoleRequest;
import com.waylo.authservice.dto.PagedApiResponse;
import org.springframework.data.domain.Page;
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

    @GetMapping(params = "email")
    public ResponseEntity<ApiResponse<AdminUserResponse>> findByEmail(
            @RequestParam String email,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        return ResponseEntity.ok(ApiResponse.success("OK", adminUserService.findByEmail(email)));
    }

    /** Paged list. q matches email, first name, last name or full name by case-insensitive prefix. */
    @GetMapping
    public ResponseEntity<PagedApiResponse<AdminUserResponse>> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String userRole) {
        requireAdmin(userRole);
        Page<AdminUserResponse> result = adminUserService.list(q, page, size);
        return ResponseEntity.ok(PagedApiResponse.of(result.getContent(), result.getTotalElements()));
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
