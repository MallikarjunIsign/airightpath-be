package com.rightpath.controller;

import java.util.LinkedHashSet;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rightpath.dto.ApiResponse;
import com.rightpath.dto.MessageResponse;
import com.rightpath.dto.RoleAssignmentRequest;
import com.rightpath.dto.UserRbacViewResponse;
import com.rightpath.service.rbac.RbacAdminService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/admin/rbac")
public class RbacAdminController {

    private final RbacAdminService rbacAdminService;

    public RbacAdminController(RbacAdminService rbacAdminService) {
        this.rbacAdminService = rbacAdminService;
    }

    @PostMapping("/assign-role")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<MessageResponse>> assignRole(@Valid @RequestBody RoleAssignmentRequest req) {
        rbacAdminService.assignRole(req.userEmail(), req.role());
        return ResponseEntity.ok(ApiResponse.ok(new MessageResponse("Role assigned")));
    }

    @PostMapping("/remove-role")
    @PreAuthorize("hasAuthority('USER_UPDATE')")
    public ResponseEntity<ApiResponse<MessageResponse>> removeRole(@Valid @RequestBody RoleAssignmentRequest req) {
        rbacAdminService.removeRole(req.userEmail(), req.role());
        return ResponseEntity.ok(ApiResponse.ok(new MessageResponse("Role removed")));
    }

    @GetMapping("/user")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<UserRbacViewResponse>> getUserRbac(
            @RequestParam("email") @NotBlank @Email String email
    ) {
        var authorities = rbacAdminService.listSecurityAuthorities(email);
        var roles = new LinkedHashSet<String>();
        var perms = new LinkedHashSet<String>();
        for (String a : authorities) {
            if (a == null) {
                continue;
            }
            if (a.startsWith("ROLE_")) {
                roles.add(a.substring("ROLE_".length()));
            } else {
                perms.add(a);
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(new UserRbacViewResponse(email, roles, perms)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_READ')")
    public ResponseEntity<ApiResponse<java.util.Set<String>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.ok(rbacAdminService.listRoleNames()));
    }
}
