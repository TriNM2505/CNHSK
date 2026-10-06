# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]

**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]

**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]

**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]

**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]

**Project Type**: [e.g., library/cli/web-service/mobile-app/compiler/desktop-app or NEEDS CLARIFICATION]

**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]

**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]

**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Đối chiếu với `.specify/memory/constitution.md`. Đánh dấu từng mục có liên quan tới feature này.

**Lớp 1 — Hard Rules (vi phạm = DỪNG, không được sang Phase 0):**

- [ ] HR-01 Mật khẩu hash bcrypt ≥12 hoặc argon2id
- [ ] HR-02 Không secret trong code/config/log
- [ ] HR-03 Cookie đủ 5 thuộc tính (`domain`, `httpOnly`, `secure`, `sameSite`, `maxAge`)
- [ ] HR-04 CORS ghi rõ từng tên miền, không `*`
- [ ] HR-05 CSRF token cho thao tác ghi dính tiền
- [ ] HR-06 Chấm điểm ở server, kiểm trần điểm + thời gian + tần suất
- [ ] HR-07 Validate mọi input, không nối chuỗi SQL
- [ ] HR-08 Không commit dữ liệu đề thi của giảng viên
- [ ] HR-09 Không lộ stack trace, format `{error_code, message, request_id}`

**Lớp 2 — Architectural Constraints (vi phạm cần RFC, ghi vào Complexity Tracking):**

- [ ] AC-01 OpenAPI viết TRƯỚC khi code endpoint, đã có người duyệt
- [ ] AC-02 Nằm trong 4 module `auth`/`learning`/`community`/`shared`
- [ ] AC-03 Module gọi nhau qua lớp `api`, không đọc thẳng bảng module khác
- [ ] AC-04 Dùng chung `cnhsk_db`, đúng schema của module
- [ ] AC-05 Schema đổi qua Flyway, `ddl-auto: validate`, không sửa migration cũ
- [ ] AC-06 Enum dùng `VARCHAR` + `CHECK`, không dùng `ENUM` type
- [ ] AC-07 Tiền `NUMERIC(12,2)`, điểm `BIGINT`, không `FLOAT`
- [ ] AC-08 Thời gian `TIMESTAMPTZ`, tác vụ định kỳ ghi rõ zone
- [ ] AC-09 `JSONB` chỉ cho dữ liệu chỉ đọc
- [ ] AC-10 Mastery và điểm game trong cùng transaction

**Lớp 3 — Engineering Standards (override được, ghi lý do trong PR):**

- [ ] ES-01 Đúng stack đã chốt, không thêm phiên bản mới
- [ ] ES-02 Thư viện FE nằm trong danh sách đã duyệt
- [ ] ES-03 Đúng quy ước đặt tên
- [ ] ES-05 Mọi endpoint có integration test
- [ ] ES-07 Nhánh đúng quy ước, PR có reviewer

**Mục không liên quan tới feature này → ghi `N/A`. Mục chưa rõ áp dụng thế nào → ghi
`NEEDS CLARIFICATION`, không bỏ trống.**

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
