package com.rightpath.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rightpath.entity.UserRole;
import com.rightpath.rbac.RoleName;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    List<UserRole> findByUser_EmailAndActiveTrue(String email);

    java.util.Optional<UserRole> findByUser_EmailAndRole_NameAndActiveTrue(
            String email,
            com.rightpath.rbac.RoleName roleName
    );

    /**
     * Whether anyone currently holds a role. Used by the super admin bootstrap to
     * decide whether the database still needs a first administrator, without
     * caring which account it turned out to be.
     */
    boolean existsByRole_NameAndActiveTrue(RoleName roleName);
}
