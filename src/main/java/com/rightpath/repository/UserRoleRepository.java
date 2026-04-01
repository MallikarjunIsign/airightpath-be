package com.rightpath.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.UserRole;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUser_EmailAndActiveTrue(String email);

    java.util.Optional<UserRole> findByUser_EmailAndRole_NameAndActiveTrue(
            String email,
            com.rightpath.rbac.RoleName roleName
    );
}
