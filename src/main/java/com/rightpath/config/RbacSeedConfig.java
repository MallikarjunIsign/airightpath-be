package com.rightpath.config;

import java.util.EnumSet;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rightpath.entity.Permission;
import com.rightpath.entity.Role;
import com.rightpath.rbac.PermissionName;
import com.rightpath.rbac.RoleName;
import com.rightpath.repository.PermissionRepository;
import com.rightpath.repository.RoleRepository;

@Configuration
public class RbacSeedConfig {

    /**
     * Seeds roles + permissions if they don't exist.
     *
     * With spring.jpa.hibernate.ddl-auto=update, this is the simplest way to get a baseline.
     * Later we can move to Flyway/Liquibase for proper migrations.
     */
    @Bean
    CommandLineRunner rbacSeed(PermissionRepository permissions, RoleRepository roles) {
        return args -> {
            // 1) permissions
            for (PermissionName pn : PermissionName.values()) {
                permissions.findByName(pn).orElseGet(() -> {
                    Permission p = new Permission();
                    p.setName(pn);
                    return permissions.save(p);
                });
            }

            // Helper: fetch Permission entities
            java.util.function.Function<PermissionName, Permission> get = pn -> permissions.findByName(pn).orElseThrow();

            // 2) roles
            // NOTE: Some existing deployments have `roles.name` defined too small (or as a restricted enum)
            // which can truncate SUPER_ADMIN and fail application startup.
            // We create ADMIN/USER safely, and only create SUPER_ADMIN if it already exists.
            Role superAdmin = roles.findByName(RoleName.SUPER_ADMIN).orElse(null);
            Role admin = roles.findByName(RoleName.ADMIN).orElseGet(() -> {
                Role r = new Role();
                r.setName(RoleName.ADMIN);
                return roles.save(r);
            });
            Role user = roles.findByName(RoleName.USER).orElseGet(() -> {
                Role r = new Role();
                r.setName(RoleName.USER);
                return roles.save(r);
            });

            // 3) role -> perms defaults
            // Super Admin gets everything
            if (superAdmin != null) {
                superAdmin.getPermissions().clear();
                for (PermissionName pn : PermissionName.values()) {
                    superAdmin.getPermissions().add(get.apply(pn));
                }
                roles.save(superAdmin);
            }

            // Admin gets almost everything except some user/role management if you want (adjust as you wish)
            EnumSet<PermissionName> adminPerms = EnumSet.allOf(PermissionName.class);
            adminPerms.remove(PermissionName.USER_LIST);
            adminPerms.remove(PermissionName.USER_ACTIVATE);
            adminPerms.remove(PermissionName.USER_DEACTIVATE);
            admin.getPermissions().clear();
            for (PermissionName pn : adminPerms) {
                admin.getPermissions().add(get.apply(pn));
            }
            roles.save(admin);

            // User gets limited set
            EnumSet<PermissionName> userPerms = EnumSet.of(
                    PermissionName.USER_READ,
                    PermissionName.USER_UPDATE,
                    PermissionName.RESUME_UPLOAD,
                    PermissionName.RESUME_UPDATE,
                    PermissionName.RESUME_VIEW,
                    PermissionName.ASSESSMENT_SUBMIT,
                    PermissionName.ASSESSMENT_RESULT_SUBMIT,
                    PermissionName.JOB_POST_READ,
                    PermissionName.JOB_APPLY,
                    PermissionName.INTERVIEW_START,
                    PermissionName.INTERVIEW_ANSWER,
                    PermissionName.COMPILER_RUN,
                    PermissionName.COMPILER_RESULTS_READ
            );
            user.getPermissions().clear();
            for (PermissionName pn : userPerms) {
                user.getPermissions().add(get.apply(pn));
            }
            roles.save(user);
        };
    }
}
