---
phase: 2
title: "Bo luat cho Codex"
status: pending
priority: P1
effort: "1 ngay"
dependencies: [1]
---

# Phase 2: Bộ luật cho Codex

## Overview

Codex đọc `AGENTS.md` ở gốc repo làm chỉ dẫn. Bổ sung những gì còn thiếu để Codex sinh code **đúng cấu trúc, đúng quy ước, tự kiểm được**,
và dựng lưới an toàn tự động (CI) bắt lỗi lệch luật.

## Requirements

- Không viết lại luật đã có (stack §3, cấm §5, quy ước §6, API First §7, DoD §8) — chỉ bổ sung.
- Mỗi luật mới phải **kiểm được** (bằng test, lint hoặc CI), không chỉ là lời khuyên.

## Bổ sung vào `AGENTS.md`

| Mục mới | Nội dung |
|---|---|
| **§13 Quy trình Codex** | Codex CLI chạy cục bộ trong repo (V8): **mỗi task một nhánh `agent/<spec-id>-<task-id>` + một PR**; chỉ code từ spec `Status: APPROVED`; đọc `spec.md` → `plan.md` → `tasks.md`; làm **một task một lần**; trước task viết shadow plan (§9) vào mô tả PR; gặp chỗ spec không nói → dừng, ghi câu hỏi vào `specs/<id>/questions.md`, **không tự giả định**; mỗi task một commit `feat(<module>): <FR-ID> …` |
| **§14 Cấu trúc package backend** | `com.cnhsk.<module>.{api, controller, dto, entity, repository, service}`; `api` là cổng duy nhất module khác được gọi; DTO là `record`; mapper viết tay (không MapStruct trừ khi duyệt); lỗi qua `ApiException(errorCode, httpStatus)` + `@RestControllerAdvice` trả `{error_code, message, request_id}` |
| **§15 Cấu trúc frontend** | `cnhsk-web/src/{app, routes, features/<feature>/{pages, components, api, types}, shared/{ui, api, auth, hooks}}`; một trang = một `SCR-xxx` (tên file ghi mã màn); gọi API qua `shared/api/client.ts`; guard role qua `shared/auth/RequireRole.tsx`; chỉ dùng token màu trong `tailwind.config`. **`cnhsk-game`** cùng stack, `src/{app, games/<game-code>/, shared/}`; mỗi game một thư mục, chung khung `GameShell` (đếm giờ, nộp lượt); không engine |
| **§16 Test bắt buộc** | BE: unit cho service, `@WebMvcTest` cho controller, integration với Testcontainers PostgreSQL cho repository có SQL riêng; mỗi FR ≥ 1 test có tên chứa FR-ID. FE: không bắt buộc test UI ở MVP, bắt buộc `tsc --noEmit` + lint |
| **§17 Cổng ghi kết quả học** | Mọi chế độ học/game ghi kết quả **chỉ** qua `learning.api.LearningResultRecorder`; cấm tính mastery/mở khoá/lịch ôn trong code tính năng |
| **§9 sửa** | "Phân công AI": Codex sinh code theo task; Claude Code review + viết spec; người duyệt PR |

## Lưới an toàn tự động

| Kiểm | Công cụ | Học từ |
|---|---|---|
| Build + test + coverage BE | `.github/workflows/ci.yml`: `./mvnw verify`, JaCoCo ≥ 80% (ES-05) | MoveHome `ci.yml` |
| Lint + type FE | cùng CI: `npm ci && npm run lint && npx tsc --noEmit` | |
| Luật hiến pháp | `constitution-check.yml`: grep cấm `ddl-auto: update`, `FLOAT` trong migration, `ENUM` PG, chuỗi SQL nối input, `*` CORS, `TODO` | MoveHome `constitution-check.yml` |
| Đồng bộ tài liệu–code | `consistency-gate.yml`: mọi endpoint trong code có trong `openapi.yaml`; mọi FR-ID trong spec APPROVED có test | MoveHome `consistency-gate.yml` |
| Ranh giới module | `ModuleBoundaryTest` (đã có) + luật §17 | |
| PR | `.github/pull_request_template.md`: spec/FR-ID, shadow plan, self-check hiến pháp | |

## Related Code Files

- Modify: `AGENTS.md`, `CLAUDE.md` (trỏ tới §13–17), `.specify/memory/constraints/global.md`
- Create: `.github/workflows/ci.yml`, `constitution-check.yml`, `consistency-gate.yml`, `.github/pull_request_template.md`
- Tham khảo: `C:\Users\Admin\Move_home\.github\` (chép rồi sửa cho Java 17 + cấu trúc CNHSK)

## Implementation Steps

1. Viết §13–17 vào `AGENTS.md`, sửa §9.
2. Chép 3 workflow + PR template từ MoveHome, thay đường dẫn, Java version, lệnh FE.
3. Chạy thử workflow trên nhánh nháp (sẽ đỏ vì chưa có base — chấp nhận, xanh sau phase 3).
4. "Agent pass test" (sách bước 1.6): đưa `AGENTS.md` cho Codex, hỏi 5 câu (đặt DTO ở đâu, lỗi trả thế nào, ghi kết quả học thế nào, khi spec thiếu làm gì, commit thế nào) — trả lời sai câu nào thì sửa luật câu đó.

## Success Criteria

- [ ] Codex trả lời đúng 5/5 câu agent pass test
- [ ] 3 workflow tồn tại, chạy được (đỏ do thiếu code là chấp nhận ở bước này)
- [ ] Mỗi luật §13–17 có cách kiểm ghi rõ

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| `AGENTS.md` quá dài, Codex bỏ sót | Giữ `AGENTS.md` ≤ 300 dòng; chi tiết để ở `constraints/`, `AGENTS.md` trỏ tới |
| consistency-gate báo sai nhiều | Bắt đầu ở chế độ cảnh báo, bật chặn sau spec 001 |
