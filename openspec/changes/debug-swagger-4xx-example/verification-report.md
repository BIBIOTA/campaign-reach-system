# Verification Report: debug-swagger-4xx-example

Date: 2026-06-12
Verifier: claude-opus-4-8 (verification-before-completion session)

> Debugging-only change. There is no formal OpenSpec change (no proposal.md / specs/ /
> tasks.md / progress.md / diagrams/ / designs/) — only `debugging-report.md`. The fix was
> driven by TDD against the existing `OpenApiSpecExportTest`. Spec / progress-log / diagram /
> design stages are therefore **n/a**; code-level verification (Stage 1) is the binding gate.

## Summary
- Code: PASS
- Spec: n/a (debugging-only change; `openspec validate` has no proposal to validate)
- Progress log: n/a (no progress.md; TDD red→green commits are the audit trail)
- Diagrams: n/a (no diagrams/)
- Designs: n/a (no designs/figma.md)

## Code Evidence

Full gate — `./gradlew spotlessCheck checkstyleMain spotbugsMain :app:test --no-daemon`:

```
BUILD SUCCESSFUL in 2m 37s
```

(Spotless format + Checkstyle maxErrors=0 + SpotBugs High/Normal + unit/ArchUnit/Testcontainers
all green. ArchUnit ModuleBoundaryTest passed: reach's 401 uses an empty @Content and does NOT
import campaign's ApiError, so reach ↛ campaign holds.)

Targeted OpenAPI export test — `./gradlew :app:test --tests "*OpenApiSpecExportTest"`:

```
tests=5 failures=0 errors=0 skipped=0
  PASS 取得 OpenAPI spec
  PASS 4xx 錯誤回應使用 ApiError schema 而非成功型別   <-- new regression test
  PASS DTO schema 帶繁中欄位說明
  PASS CI 上匯出 spec 檔
  PASS 端點文件涵蓋成功與錯誤回應
BUILD SUCCESSFUL
```

skipped=0 confirms Docker was up and the Testcontainers-backed test ran for real (not auto-skipped).

Generated spec (`app/build/openapi/openapi.json`) — every 4xx error body now references ApiError;
every 401 carries no body:

```
POST  /internal/campaigns                     400 -> #/components/schemas/ApiError
POST  /internal/campaigns                     401 -> (no content)
PATCH /internal/campaigns/{id}                400 -> #/components/schemas/ApiError
GET   /internal/campaigns/{id}                404 -> #/components/schemas/ApiError
PATCH /internal/campaigns/{id}                404 -> #/components/schemas/ApiError
PATCH /internal/campaigns/{id}                409 -> #/components/schemas/ApiError
POST  /internal/campaigns/{id}/status         404 -> #/components/schemas/ApiError
POST  /internal/campaigns/{id}/status         409 -> #/components/schemas/ApiError
POST  /internal/campaigns/{id}/status         422 -> #/components/schemas/ApiError
GET/PATCH/POST ... all 401                     401 -> (no content)
GET   /internal/reach/.../metrics             401 -> (no content)
GET   /internal/reach/.../recipients/{userId} 401 -> (no content)
```

## TDD Audit Trail (commits)
```
531fb8f test: red  - 4xx OpenAPI responses must use ApiError schema not success type
97a96da feat: green - document 4xx OpenAPI responses with ApiError schema
dbe4f06 docs: add debugging report for swagger 4xx success-example bug
```
Red commit precedes green commit; red failed on assertion `CreateCampaignResponse` ≠ `/ApiError`.

## Diagram Verification
| File | Type | Status | Notes |
|---|---|---|---|
| — | — | n/a | no diagrams/ directory |

## Design Verification
| State | Figma node | Status | Diff |
|---|---|---|---|
| — | — | n/a | no designs/figma.md |

## Next Actions
- All binding (code) checks pass. This is a debugging-only change with no OpenSpec proposal, so
  `openspec archive` does not apply. Suggested next step: open a PR for branch
  `fix/swagger-4xx-error-schema`.
```
