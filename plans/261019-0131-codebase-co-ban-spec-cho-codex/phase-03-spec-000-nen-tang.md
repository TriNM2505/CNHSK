---
phase: 3
title: "Spec 000 nen tang"
status: cancelled
priority: P1
effort: "2 ngay viet spec · Codex 2-3 ngay"
dependencies: [2]
---

> **2026-10-19 — gộp vào spec 001** theo yêu cầu "mỗi tính năng một spec, bắt đầu từ 001".
> Nền tảng nằm ở `specs/001-auth-rbac/spec.md` §0, task T-00→T-06 và T-28→T-29.

# Phase 3: Spec 000 — nền tảng

## Overview

Spec cho phần mọi tính năng đều dùng: cấu hình backend, lỗi, bảo mật nền, cổng ghi kết quả học, khung frontend, hạ tầng dev.
Khoá xong giao Codex **trước tiên**; xong spec này thì các spec sau mới có chỗ để đặt code.

## Requirements — spec `specs/000-platform-base/spec.md` phải đặc tả

| Nhóm | Nội dung |
|---|---|
| **Migration baseline** | Viết lại `V1__core_schema.sql`, `V2__community.sql` khớp `database.md` (bỏ `roles`, thêm `plans`, `pron_stages`, `videos`, `dictation_attempts`, `shadowing_attempts`, cột gói/phễu, `seq`), role mới theo plan đổi tên. Chuyển vào `src/main/resources/db/migration` (D6, D8). **Không có seed dữ liệu học** (Validation V4: chờ data Học Bá, nạp qua spec 008); chỉ seed tài khoản quản trị đầu tiên qua biến môi trường. **Không** đụng `knowledge_points`/`question_knowledge_points` (đóng băng chờ cây) |
| **Backend chung** | `shared.exception` (`ApiException`, `ErrorCode`, handler, `request_id` qua MDC filter) · `shared.config` (CORS danh sách cố định gồm origin `cnhsk-web` **và** `cnhsk-game`, cookie đặt ở tên miền cha cho đăng nhập chung, Jackson UTC, `Clock` bean để test) · `shared.security` khung (`SecurityFilterChain`, đọc JWT từ cookie **hoặc** header — logic token ở spec 001) · `shared.audit` (`AuditService` ghi `audit_logs`) · OpenAPI qua springdoc tại `/v3/api-docs` |
| **Cổng ghi kết quả học** | `learning.api.LearningResultRecorder` + `LearningAttempt` (userId, kind, refType, refId, items[{questionId?, lexemeId?, answer, isCorrect, score 0–100, durationMs}]) + cài đặt lưu `attempts`/`attempt_items`, kiểm quyền sở hữu, chống nộp hai lần (`ATTEMPT_ALREADY_SUBMITTED`), chống thời gian bất khả (`IMPOSSIBLE_DURATION`). **Không** mastery |
| **Frontend khung** | `cnhsk-web` React 18.3/Vite 5/TS 5.7/Tailwind 3.4/router 6.28; token màu từ `design.md` §6; layout 3 loại (public, learner, admin) theo `design.md` §5; `shared/api/client.ts` (cookie, xử lý 401 → refresh → thử lại một lần); `RequireRole`; trang SCR-079 Access Denied; trang 404 |
| **Hạ tầng dev** | `docker-compose.yml` (PostgreSQL 16, Redis 7); `.env.example`; profile `dev`/`test`; README chạy trong 5 lệnh |
| **Test nền** | Testcontainers base class; test `LearningResultRecorder` (ghi đủ item, chặn nộp lại, chặn user khác); ArchUnit: chỉ `LearningResultRecorder` được ghi `attempt_items` |

Theo chuẩn 15 mục của plan; mục 12 (màn hình) chỉ có SCR-079 và các layout.

## Related Code Files

- Create: `specs/000-platform-base/{spec.md, plan.md, tasks.md, checklists/requirements.md}`, `docs/api/openapi.yaml` (khung)
- Codex sẽ tạo: `src/main/java/com/cnhsk/shared/**`, `learning/api/**`, `src/main/resources/db/migration/V1..V2`, `cnhsk-web/**` (khung), `docker-compose.yml`. Khung `cnhsk-game` thuộc spec 006a.
<!-- Updated: Validation Session 1 - bỏ seed dev (V4); CORS + cookie cho cnhsk-game (V1) -->

## Implementation Steps

1. `/speckit-specify` từ bảng trên → `spec.md`; bổ sung mục 12–15 theo chuẩn plan.
2. Đặc tả DDL đầy đủ trong mục 4 (Data model) — copy từ `database.md`, đã đổi role.
3. `/speckit-plan` → `plan.md`; `/speckit-tasks` → `tasks.md` (task ≤ 4 giờ).
4. Review chéo theo 3 câu hỏi của sách (bước 1.9) → sửa → sang phase 7 khoá.

## Success Criteria

- [ ] Spec đủ 15 mục; DDL chạy được trên PostgreSQL 16 (thử bằng Testcontainers trước khi khoá)
- [ ] `LearningResultRecorder` có hợp đồng Java đầy đủ (chữ ký, ngoại lệ, giao dịch)
- [ ] Sau khi Codex làm: `./mvnw verify` xanh, `npm run build` xanh, `docker compose up` + app chạy, CI xanh

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Viết lại V1 lệch `database.md` lần nữa | Spec ghi "DDL = database.md mục X"; consistency-gate so tên bảng |
| Cổng ghi kết quả thiếu thông tin cây tri thức cần sau này | `attempt_items` giữ đủ question_id, lexeme_id, đáp án, đúng/sai, điểm từng phần, thời gian — đủ để tính lại minh chứng (đã đối chiếu báo cáo DB cây tri thức) |
