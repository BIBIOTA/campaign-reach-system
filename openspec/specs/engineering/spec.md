# engineering Specification

## Purpose
The engineering specification captures the non-functional conventions and AI-collaboration rules that govern this repository. It documents the module boundary hard constraints (enforced by ArchUnit), the canonical build and lint commands, the CI quality gate, and the CLAUDE.md / AGENTS.md single-source-of-truth policy. These rules are the authoritative reference for developers and AI assistants working in this codebase.
## Requirements
### Requirement: AI 協作與工程慣例指引（CLAUDE.md / AGENTS.md）

The repository SHALL provide a root `CLAUDE.md` that serves as the single source of truth for AI assistants and developers, documenting the three bounded modules (campaign / reach / shared) and their boundary rule, the standard build/lint commands, and the CI quality gate. The repository SHALL provide `AGENTS.md` as a symlink to `CLAUDE.md` so that both surfaces share one maintained file and never diverge. The guide SHALL be kept in sync whenever module boundaries or lint/CI conventions change. (FR-001)

#### Scenario: 根目錄存在 CLAUDE.md 並描述模組邊界

- **WHEN** 在 repo 根目錄檢視文件
- **THEN** the system 存在 `CLAUDE.md`，描述 campaign / reach / shared 三個 bounded module 的職責與邊界
- **AND** 明示「campaign 與 reach 僅透過 `shared/event` 溝通、禁止彼此直接 import」之約束，與 ArchUnit 守護測試一致

> See: ../../diagrams/04-component-architecture.puml

#### Scenario: CLAUDE.md 為建置與 CI gate 的單一事實來源

- **WHEN** 開發者或 AI agent 讀取 `CLAUDE.md`
- **THEN** the system 提供標準指令 `./gradlew spotlessApply`、`spotlessCheck`、`checkstyleMain`、`spotbugsMain`、`test`
- **AND** 說明 CI gate：上述檢查（含 ArchUnit 與 Testcontainers）全數通過才可合併，任一失敗即擋關

#### Scenario: AGENTS.md 為指向 CLAUDE.md 的 symlink

- **WHEN** 以 `ls -l AGENTS.md` 檢視
- **THEN** the system 顯示 `AGENTS.md -> CLAUDE.md` 的 symlink
- **AND** 兩者內容不分歧、僅維護一份

#### Scenario: 慣例變更時同步更新指引

- **WHEN** 模組邊界或 lint/CI 指令有所變更
- **THEN** the system 同步更新 `CLAUDE.md`
- **AND** 避免文件與 build script 落差

