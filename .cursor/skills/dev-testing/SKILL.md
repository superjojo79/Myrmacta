---
name: dev-testing
description: AI DevKit · Testing phase guidance for adding and validating feature test coverage. Use when the user wants to write tests, update testing docs, run coverage, close coverage gaps, or run dev-lifecycle phase 8.
---

# Dev Testing

Run testing work for configured AI docs features. Before changing docs or code, propose the concrete plan for this phase and wait for user approval unless the user already approved the exact phase plan.

## Phase Contract

1. Run `npx ai-devkit@latest lint` before phase work.
2. If working on a named feature, run `npx ai-devkit@latest lint --feature <name>`.
3. Read the testing doc, requirements, design, implementation notes, and current diff before changes.
4. Apply the `verify` skill before making coverage or test-pass claims.
5. If parent `dev-lifecycle` established usable task tracing, emit testing phase, next-action, and evidence events per `task`.

## Write Tests

Use for Phase 8.

1. Run `npx ai-devkit@latest lint --feature <name>` and reference the testing doc path it validates. If manual path resolution is unavoidable, first resolve `.ai-devkit.json` `paths.docs`, falling back to `docs/ai`.
2. Gather context: feature name, changes summary, environment, existing test suites, flaky tests to avoid.
3. Analyze the testing template, success criteria, edge cases, available mocks, and fixtures.
4. Add unit tests for happy paths, edge cases, and error handling for each module. Highlight missing branches.
5. Add integration tests for critical cross-component flows, setup/teardown, and boundary/failure cases.
6. Run coverage tooling, identify gaps, and suggest additional tests if below the target.
7. **Frontend E2E (mandatory when FE or shared API contracts used by FE change):**
   - Do **not** mark Playwright as out of scope for Myrmacta FE features.
   - Update `frontend/e2e/**` mocks/assertions when API shapes or routes change
     (Vitest mocks alone are not sufficient).
   - Run fresh evidence with `cd frontend && npm run test:e2e` (or
     `npm run test:all`). Install browsers if needed:
     `npx playwright install --with-deps chromium`.
   - Record Playwright command, exit code, and pass counts in the testing doc.
   - Phase 8 is incomplete without that evidence when the feature touches FE.
8. If task tracing is available, record evidence for each fresh test/coverage command per `task`.
9. Update the selected testing doc with test file links and results (unit, IT, **and E2E**).

Next: `dev-review`. If tests reveal design flaws, return to `dev-design`.
