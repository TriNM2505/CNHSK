---
title: "Codebase co ban - luat du an, nen tang va spec chi tiet cho Codex (auth, che do hoc, game)"
description: >-
  Chot quyet dinh ky thuat, bo luat cho Codex, spec nen tang va spec chi tiet
  cho auth, cac che do hoc tap va game; khoa spec roi giao Codex sinh code.
  Cay tri thuc / mastery lam sau, cam vao qua mot cong ghi ket qua hoc.
status: pending
priority: P1
branch: "main"
tags:
  - spec
  - sdd
  - backend
  - frontend
  - codex
blockedBy: [261018-2240-doi-ten-actor]
blocks: []
created: "2026-10-18T18:40:58.610Z"
createdBy: "ck:plan"
source: skill
---

# Codebase cơ bản — luật dự án, nền tảng và spec chi tiết cho Codex

## Overview

Mục tiêu: **Codex sinh được code đúng ngay lần đầu** cho auth, các chế độ học tập và game.
Muốn vậy cần ba thứ, theo đúng thứ tự: **luật rõ** → **nền tảng (base) chạy được** → **spec chi tiết đã khoá**.
Plan này không viết code sản phẩm; nó tạo ra những thứ Codex đọc để viết code.

| Trong phạm vi | Ngoài phạm vi (làm sau) |
|---|---|
| Auth, tài khoản, quản trị người dùng/role, đăng nhập chung trang game (SCR-001→008, 076, 077, 079) | Cây tri thức, mastery, mở khoá chủ đề, lịch ôn (plan `261017-1654-cay-nang-luc-han-ngu`, chờ data Học Bá) |
| **Nhập dữ liệu học** (SCR-068, 069 · spec 002) — đường duy nhất đưa data Học Bá vào hệ thống | Dashboard/tiến độ (010, 019), ôn đến hạn (013), luyện phần yếu (023) |
| Chế độ học: từ vựng theo chủ đề, nhận diện chữ, viết chữ, phát âm 8 tầng, ngữ pháp (SCR-011, 012, 014→018) | Mã thẻ, gói, sổ cái (FR-076→082b tách khỏi spec 001 → spec gói dịch vụ, plan sau) |
| Game: **4 game**, app riêng **`cnhsk-game`** (React + CSS), Game Hub, Game Session (SCR-035, 036) | Bảng xếp hạng, cuộc thi, cộng đồng (V2) |
| Nền tảng BE + `cnhsk-web` + hạ tầng dev + CI | Từ điển, thư viện, đề thi, quản trị đề/ngân hàng câu, AI sinh câu (plan sau) |

**Dữ liệu:** không có seed tự soạn. Test dùng fixture trong code test; demo chế độ học **chờ data Học Bá**, nạp qua spec 002.

### Cổng để làm cây tri thức sau mà không sửa lại

<!-- Updated 2026-10-19: thay LearningResultRecorder bằng bộ máy lượt luyện + sự kiện (spec 003 §0) -->
Mọi chế độ học và game chạy qua **một bộ máy lượt luyện** (`learning.practice`, spec 003 §0): lượt = `attempts`, câu = `attempt_items`
(có `skill`, `item_type`, `meta`). Mỗi câu chấm xong phát `ItemGradedEvent`, mỗi lượt xong phát `SessionFinishedEvent` — **đồng bộ trong transaction**.

- **Bây giờ:** không có listener → không mastery.
- **Sau này:** plan cây tri thức thêm `@EventListener` cập nhật mastery cùng transaction (`AC-10`), và/hoặc tính lại từ `attempt_items` đã lưu. Chế độ học và game không phải sửa.
## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Quyet dinh ky thuat va ADR](./phase-01-quyet-dinh-ky-thuat-va-adr.md) | Pending (D1, D2 chốt trong spec 001; D3 = spec 006 D-08; D4 Redis chốt spec 001) |
| 2 | [Bo luat cho Codex](./phase-02-bo-luat-cho-codex.md) | Pending |
| 3 | [Spec 000 nen tang](./phase-03-spec-000-nen-tang.md) | Cancelled — gộp vào spec 001 §0 |
| 4 | [Spec 001 auth cap nhat va khoa](./phase-04-spec-001-auth-cap-nhat-va-khoa.md) | Spec DRAFT 1.1.0 — chờ nhóm duyệt |
| 5 | [Spec 002-006 che do hoc tap](./phase-05-spec-002-005-che-do-hoc-tap.md) | Spec DRAFT 1.0.0 (002→006) — chờ nhóm duyệt |
| 6 | [Spec 007-008 game](./phase-06-spec-006-game.md) | Spec DRAFT 1.0.0 (007, 008) — chờ nhóm duyệt |
| 7 | [Khoa spec va ban giao Codex](./phase-07-khoa-spec-va-ban-giao-codex.md) | Pending |

**Đánh số lại (2026-10-19, "mỗi tính năng một spec, bắt đầu từ 001"):**

| Spec | Thư mục | Tên cũ trong phase file |
|---|---|---|
| 001 | `specs/001-auth-rbac` (+ nền tảng) | 000 + 001 |
| 002 | `specs/002-learning-data-import` | 008 |
| 003 | `specs/003-vocab-topics` (+ bộ máy lượt luyện) | 002 |
| 004 | `specs/004-grammar` | 005 |
| 005 | `specs/005-pronunciation-stages` | 004 |
| 006 | `specs/006-hanzi-recognition-writing` | 003 |
| 007 | `specs/007-game-platform` | 006a |
| 008 | `specs/008-vocab-games` | 006b |

Thứ tự khoá + giao Codex: **001 → 002 → 003 → (004, 005, 006 song song) → 007 → 008**. Quyết định chung: `plans/reports/brainstorm-261019-0551-spec-002-008-quyet-dinh-chung-report.md`.
## Chuẩn "spec chi tiết" (áp cho mọi spec)

Lấy cấu trúc `specs/001-auth-rbac/spec.md` (11 mục) làm khung, **bổ sung 4 mục** để Codex không phải đoán:

| # | Mục | Nội dung bắt buộc |
|---|---|---|
| 1–11 | Như spec 001 | Context, User stories, FR dạng EARS, Data model, Error matrix, AC mapping, NFR, Constitution check, Open questions, Frontend note, Rủi ro |
| 12 | **Màn hình** | Mỗi SCR: route, actor/guard, layout area, từng field (nguồn dữ liệu, kiểu, validate, message), từng action (API gọi, trạng thái loading/empty/error/success), màn kế tiếp — trích từ `screen-fields.md`, ghi dòng nguồn |
| 13 | **API contract** | Đoạn OpenAPI 3.1 đầy đủ cho mọi endpoint của spec (request, response, mã lỗi) → gộp vào `docs/api/openapi.yaml` |
| 14 | **Acceptance tests** | Given/When/Then cho **mọi** FR, mỗi test ghi FR-ID; gồm ca biên và ca lỗi trong Error matrix |
| 15 | **Gói việc cho Codex** | `tasks.md`: task ≤ 4 giờ, mỗi task ghi file tạo/sửa, FR/test phải pass, lệnh kiểm |

**Spec chưa khoá thì không giao Codex.** Không có "spec gần xong".

## Acceptance criteria (toàn plan)

- [ ] 4 quyết định chặn (JWT, TTL token, BUS-16, Redis) đã chốt và ghi ADR
- [ ] `AGENTS.md` có mục "Quy trình Codex" + quy ước BE/FE đủ để sinh code không hỏi lại
- [ ] Spec 001 §0 khoá; Codex dựng base: `./mvnw test` xanh, `cnhsk-web` chạy, CI xanh, Flyway chạy migration
- [ ] Spec 001→008 đạt chuẩn 15 mục, `APPROVED` (cả nhóm duyệt), có tag `spec/<id>/v1.0.0`
- [ ] Nhập được một file data mẫu đúng định dạng qua SCR-068 và thấy báo cáo lỗi ở SCR-069
- [ ] Mọi chế độ học và game ghi kết quả **chỉ** qua bộ máy `learning.practice` / `learning.api.PracticeApi` (ArchUnit kiểm)
- [ ] Không spec nào chứa logic mastery/mở khoá/lịch ôn

## Dependencies

- **Bị chặn bởi** [`261018-2240-doi-ten-actor`](../261018-2240-doi-ten-actor/plan.md): spec phải dùng tên actor mới; phase 3–5 của plan đó cần xong trước phase 4 ở đây.
- **Thay phase 5** của [`261015-2036-79-man-html-sang-react`](../261015-2036-79-man-html-sang-react/plan.md): React dựng theo từng spec, lấy HTML `C:\CLHSK\cnhsk-mockup-79` làm mẫu giao diện. Phase 1–4 (HTML, duyệt luồng) của plan đó giữ nguyên.
- **Cung cấp cổng** cho [`261017-1654-cay-nang-luc-han-ngu`](../261017-1654-cay-nang-luc-han-ngu/plan.md): sự kiện `ItemGradedEvent`/`SessionFinishedEvent` và `attempt_items`.
- **Nguồn:** `AGENTS.md`, Hiến pháp, `database.md`, `use-cases-01/02/06`, `screen-fields.md`, `design.md`, `kien-truc.md`.

## Unresolved questions

Gom từ §9 các spec — cả nhóm chốt ở buổi duyệt:

1. **RFC `AC-03`/`AC-04`** (4 schema, FK cross-schema) — spec 001 Q6.
2. **Đích đến khi đa role** (màn "Khu vực của bạn") — spec 001 Q7.
3. **Định dạng file Học Bá** — spec 002 dùng mẫu v0 CSV; xác nhận khi có file.
4. **Thư viện mới:** `commons-csv 1.11.0` (002), `hanzi-writer 3.7.3` + dữ liệu nét qua jsDelivr (006).
5. **Migration mới:** `V3__content_import.sql` (002), `V4__practice_engine.sql` (003).
6. **Luật 4 game** (spec 008 §3) — đề xuất, cần nhóm duyệt.
## Validation Log

### Session 1 — 19/10/2026

**Verification Results**
- Tier: Full (7 phase) · Claims checked: 28 · Verified: 25 · Failed: 3 · Unverified: 0
- 3 "Failed" là thư viện **chưa có trong `pom.xml`**, đúng như plan dự kiến thêm: springdoc, Testcontainers (đã ở D5), **JaCoCo (thiếu trong D5 → đã bổ sung)**

**Quyết định**

| # | Câu hỏi | Chốt | Ảnh hưởng |
|---|---|---|---|
| V1 | Game chạy ở đâu | **App riêng `cnhsk-game`**, giữ kiến trúc 3 client | Phase 1 D7, phase 2 §15, phase 4 (giữ UC-005), phase 6 |
| V2 | Số game | **4 game** như use case | Phase 6 tách 006a/006b |
| V3 | Mã thẻ/gói trong spec 001 | **Tách sang spec 007** (plan sau) | Phase 4 |
| V4 | Dữ liệu chạy thử | **Chờ data Học Bá**, không seed tự soạn | Phase 3 bỏ `V999__seed_dev`; phase 5 test dùng fixture |
| V5 | Đưa data Học Bá vào thế nào | **Thêm spec 008 nhập dữ liệu** (SCR-068, 069) | Phase 5, phase 7 thứ tự |
| V6 | Thư viện game | **React + CSS**, chưa dùng engine | Phase 1 D5/D7, phase 6 |
| V7 | Ai khoá spec | **Cả nhóm duyệt** | Phase 7 |
| V8 | Codex chạy thế nào | **Codex CLI cục bộ**, mỗi task một nhánh + PR | Phase 2 §13, phase 7 |

### Whole-Plan Consistency Sweep
- Đã sửa: thứ tự spec (thêm 008, 006a/006b), seed dev (bỏ ở phase 3, 5), D7 (app riêng), spec 001 (giữ UC-005 đăng nhập chung)
- Mâu thuẫn còn lại: 0

### Session 2 — 19/10/2026 (viết spec 002→008)

- Người dùng: "mỗi tính năng một spec, bắt đầu từ 001", "làm hết 002–008, không hỏi lại".
- Spec 000 gộp vào 001 §0; đánh số lại (bảng ở mục Phases).
- `LearningResultRecorder` thay bằng bộ máy lượt luyện + sự kiện (D-03, D-04).
- Phát hiện 5 lỗi tài liệu (L1–L5 trong báo cáo brainstorm), đã xử lý trong spec; L5 sửa spec 001 FR-090.
- 7 hợp đồng OpenAPI lint hợp lệ (Redocly 1.25.0).