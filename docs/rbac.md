# RBAC (Roles & Permissions)

This project uses **Option A** RBAC: roles and permissions are stored in the database and enforced via Spring Security authorities.

## Concepts

- **Role**: a named bucket of permissions (e.g. `ADMIN`, `USER`).
- **Permission**: an action-level authority (e.g. `JOB_POST_CREATE`).

At runtime, Spring Security sees **authorities**:
- roles are exposed as `ROLE_<ROLE_NAME>` (example: `ROLE_ADMIN`)
- permissions are exposed as plain strings (example: `JOB_POST_CREATE`)

Method security uses permissions:

- `@PreAuthorize("hasAuthority('JOB_POST_CREATE')")`

## Tables (Option A)

- `roles`
- `permissions`
- `role_permissions` (many-to-many)
- `user_roles` (many-to-many; includes `active` flag)

## Seeding defaults

On application start, `com.rightpath.config.RbacSeedConfig` seeds:
- **all** `PermissionName` values in `permissions`
- baseline roles: `ADMIN` and `USER`

> Note: `SUPER_ADMIN` is supported by code, but legacy databases might not have had enough column length for it. See DB migration below.

## Where authorities come from

`com.rightpath.service.rbac.RbacAuthorityService.resolveAuthorities(email)`:
- loads active roles from `user_roles`
- adds `ROLE_<ROLE_NAME>`
- adds every permission attached to that role

Some older environments may still have legacy authorities stored in `Users.authorities`; the UserDetails service keeps backward compatibility.

## Admin role management endpoints

Controller: `com.rightpath.controller.RbacAdminController`

Base path: `/api/admin/rbac`

- `POST /api/admin/rbac/assign-role`
  - Body: `{ "userEmail": "user@x.com", "role": "ADMIN" }`
- `POST /api/admin/rbac/remove-role`
  - Body: `{ "userEmail": "user@x.com", "role": "ADMIN" }`
- `GET /api/admin/rbac/user?email=user@x.com`
  - Returns roles + permissions for that user
- `GET /api/admin/rbac/roles`
  - Returns supported role names
 
Security:
- assignment/removal requires `USER_UPDATE`
- read requires `USER_READ`

## Debugging: `/api/me`

Endpoint: `GET /api/me`

Returns:
- authenticated email
- roles (without the `ROLE_` prefix)
- permissions

This is the easiest way to confirm RBAC is correctly wired end-to-end.

## JWT filter public endpoints

The JWT authentication filter skips JWT validation only for:
- `/api/login`
- `/api/register`
- `/api/refresh`
- `/api/logout`
- health endpoints (`/api/health`, `/api/healthcheck`)

## DB migration note (important)

If you saw this error on startup:

> `Data truncated for column 'name' at row 1` while inserting into `roles`

It means `roles.name` couldn’t store a longer role name like `SUPER_ADMIN`.

Fix (MySQL):

```sql
ALTER TABLE roles MODIFY name VARCHAR(50) NOT NULL;
```

If you use a migration tool in the future (Flyway/Liquibase), this should become a formal migration.
