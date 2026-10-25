---
phase: testing
title: Testing Strategy
description: Define testing approach, test cases, and quality assurance
---

# Testing Strategy

## Test Coverage Goals
**What level of testing do we aim for?**

- Backend: Jacoco ≥ project threshold on new/changed code (`./mvnw test` / `verify`).
- Frontend unit: Vitest (`cd frontend && npm run test` / `test:coverage`).
- **Frontend E2E: Playwright is mandatory** when the feature touches the SPA or
  an API contract consumed by the SPA (`cd frontend && npm run test:e2e` or
  `npm run test:all`). Do **not** mark Playwright out of scope to skip Phase 8.
- Integration tests for critical paths + error handling (MockMvc / persistence).
- Align with requirements/design acceptance criteria.

## Unit Tests
**What individual components need testing?**

### Component/Module 1
- [ ] Test case 1: [Description] (covers scenario / branch)
- [ ] Test case 2: [Description] (covers edge case / error handling)
- [ ] Additional coverage: [Description]

### Component/Module 2
- [ ] Test case 1: [Description]
- [ ] Test case 2: [Description]
- [ ] Additional coverage: [Description]

## Integration Tests
**How do we test component interactions?**

- [ ] Integration scenario 1
- [ ] Integration scenario 2
- [ ] API endpoint tests
- [ ] Integration scenario 3 (failure mode / rollback)

## End-to-End Tests
**What user flows need validation?**

Myrmacta CI runs Playwright on every PR (`frontend` job). Phase 8 must produce
fresh `npm run test:e2e` evidence for FE-impacting features and keep
`frontend/e2e/**` mocks in sync with API shapes (Vitest mocks alone are not enough).

- [ ] User flow 1: [Description]
- [ ] User flow 2: [Description]
- [ ] Critical path testing
- [ ] Regression of adjacent features / existing E2E specs still green
- [ ] E2E network mocks updated for any new/changed API contracts

## Test Data
**What data do we use for testing?**

- Test fixtures and mocks
- Seed data requirements
- Test database setup
- Playwright `page.route` fixtures (keep aligned with real API envelopes)

## Test Reporting & Coverage
**How do we verify and communicate test results?**

```bash
./mvnw test
cd frontend && npm run test -- --coverage
cd frontend && npm run test:e2e   # or: npm run test:all
```

- Coverage commands and thresholds
- Coverage gaps (files/functions below target and rationale)
- Links to test reports or dashboards
- Manual testing outcomes and sign-off
- **Record Playwright command + pass counts** before marking testing `verified`

## Manual Testing
**What requires human validation?**

- UI/UX testing checklist (include accessibility)
- Browser/device compatibility
- Smoke tests after deployment

## Performance Testing
**How do we validate performance?**

- Load testing scenarios
- Stress testing approach
- Performance benchmarks

## Bug Tracking
**How do we manage issues?**

- Issue tracking process
- Bug severity levels
- Regression testing strategy
