package com.rightpath.service.rbac;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.rightpath.repository.UserRoleRepository;
import com.rightpath.rbac.PermissionName;

@Service
public class RbacAuthorityService {

    private final UserRoleRepository userRoleRepository;

    public RbacAuthorityService(UserRoleRepository userRoleRepository) {
        this.userRoleRepository = userRoleRepository;
    }

    /**
     * Returns security authorities for Spring (ROLE_* + permission names) for the given user.
     */
    public Set<String> resolveAuthorities(String userEmail) {
        Set<String> out = new LinkedHashSet<>();

        var roles = userRoleRepository.findByUser_EmailAndActiveTrue(userEmail);
        for (var ur : roles) {
            var roleName = ur.getRole().getName();
            out.add(roleName.asSecurityRole());

            var perms = ur.getRole().getPermissions();
            if (perms != null) {
                perms.forEach(p -> out.add(p.getName().name()));
            }
        }

        return out;
    }

    /** Convenience to avoid typos in code. */
    public static String perm(PermissionName p) {
        return p.name();
    }
}
