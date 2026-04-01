# DB cleanup: drop legacy `user_authorities`

Date: 2026-01-06

## Context

We moved to DB-backed RBAC as the single source of truth:

- `user_roles` + `roles` + `role_permissions` + `permissions` are authoritative.
- Spring Security authorities are derived at runtime (`ROLE_*` + permission names).
- The legacy `user_authorities` table (email -> string set) is no longer read or written by the backend.

## Safe rollout plan

1) Deploy backend changes first.
2) Verify:
   - new registrations still work
   - login works
   - `GET /api/me` returns roles + permissions
3) Only after verification, drop the legacy table.

## SQL

```sql
DROP TABLE IF EXISTS user_authorities;
```
