# OpenAPI contract

Golden file for the FlowForge HTTP surface (except SSE, multipart attachment upload, and inbound
`POST /api/webhooks/**`).

## Flag

`app.openapi.enabled` defaults to `false`. When `true`, `/v3/api-docs` and Swagger UI are available
to `TENANT_ADMIN` and `WORKFLOW_ADMIN` only.

## Regenerate the golden YAML

From the repository root, with the test database/profile used by `./mvnw test`:

```bash
./mvnw test -Dtest=OpenApiContractIT -Dopenapi.update=true -Dskip.frontend=true -Dmaven.compiler.release=21
```

Commit `openapi/openapi.yaml` in the same change as any controller/DTO listed by springdoc.
`./mvnw verify` fails if the runtime export differs (LF-normalized).
