## Version policy

- Use the latest stable production version of each framework/runtime.
- Where an LTS concept exists, prefer the latest compatible LTS version.
- Do not use preview, milestone, RC, or snapshot versions.
- Verify versions from official sources before introducing or upgrading dependencies.
- Keep versions mutually compatible.

Current project baseline:
- Java: latest compatible LTS, currently Java 25 LTS
- Spring Boot: latest stable Spring Boot 4.x
- Spring Security: use the version managed by the Spring Boot BOM unless explicitly justified
- React: latest stable compatible version
- TypeScript: latest stable compatible version
- Vite: latest stable compatible version
- Node.js: latest active LTS compatible with the frontend toolchain
- Keycloak: latest stable production release
- PostgreSQL: latest stable major compatible with Keycloak

Do not downgrade to Spring Boot 3.x or Java 21 merely because examples or training data use them.

Before adding or upgrading a dependency, verify compatibility with the actual project.