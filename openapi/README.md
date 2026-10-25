# OpenAPI contract

Golden file for the Myrmacta HTTP surface (except SSE, multipart attachment upload, and inbound
`POST /api/webhooks/**`).

## Flag

`app.openapi.enabled` defaults to `false` (and `springdoc.api-docs.enabled` / `swagger-ui.enabled`
are off). Set `app.openapi.enabled=true` to turn docs on; an environment post-processor mirrors
that flag onto springdoc. `/v3/api-docs` and Swagger UI are then available to `TENANT_ADMIN` and
`WORKFLOW_ADMIN` only.

## Regenerate the golden YAML

From the repository root, with the test database/profile used by `./mvnw test`:

```bash
./mvnw test -Dtest=OpenApiContractIT -Dopenapi.update=true -Dskip.frontend=true -Dmaven.compiler.release=21
```

Commit `openapi/openapi.yaml` in the same change as any controller/DTO listed by springdoc.
`./mvnw verify` fails if the runtime export differs (LF-normalized).
