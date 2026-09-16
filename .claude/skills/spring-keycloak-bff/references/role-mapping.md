# Role Mapping

## Flow

```
Keycloak roles (realm and/or client roles)
        v
Spring GrantedAuthority
        v
backend authorization (hasRole / hasAuthority on endpoints)
```

## Guidance

- Check whether the existing realm uses realm roles, client roles, or
  both, before adding a new role — stay consistent with what's already
  there rather than introducing a second convention.
- Mapping from Keycloak role claims to `GrantedAuthority` must be explicit
  and consistent (e.g. always prefixed `ROLE_` if using `hasRole`, or
  consistently unprefixed if using `hasAuthority` directly) — check the
  existing mapping code before assuming which convention this project
  uses.
- Endpoint authorization must reference the mapped authority
  (`hasRole("ADMIN")`/`hasAuthority(...)`), never `authenticated()` alone,
  for anything that's actually role-gated.

## Invariant

**React may use roles to control what it renders. React role checks are
never security enforcement.** A hidden "ADMIN" button is a UX decision; it
does nothing to stop a direct, authenticated request to the admin endpoint
unless that endpoint independently enforces the role server-side. When
adding or changing role-gated UI, verify the backend enforcement exists —
don't assume hiding the control is sufficient.
