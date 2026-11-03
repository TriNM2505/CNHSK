# Tasks: Cây năng lực và ngân hàng câu hỏi — Spec #010

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-knowledge.yaml`](contracts/openapi-knowledge.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 13 task**, ≤ 4 giờ/task, một task một nhánh `spec/010-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 010 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau** (Q14).
File dùng chung (spec §0.3) phải nêu trong Shadow Plan từng task.

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `question-bank`: `types.ts` theo OpenAPI, `api/*.ts` (client chung), `catalog.preview.ts` (cây + 12 câu mẫu tự soạn đủ 6 `qtype`), `screens.ts`; thêm 2 route vào `routeAccess.ts` (`CONTENT_EDITOR`, `TEACHER`, layout `admin`) + catalog vào `screenRegistry.ts` (**báo leader trước**) | `web/features/question-bank/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts` | FR-001 (FE), §12 | ✅ 2026-11-04: leader duyệt bổ sung preview registry và hai file tài liệu (questions.md); 12 câu tự soạn, sáu loại, HSK 1–3, cây mẫu sáu miền; screens rỗng theo nền hiện có. 53 test mới; toàn FE 1.412 test pass/125 file, lint 0 lỗi (1 warning có sẵn ở LevelBadge), build pass. Java 17 verify/Testcontainers: 2.017 test, 0 failure/error/skip; JaCoCo line 96,83% (5980/6176). Gate backend chạy riêng: import p95 16.806 ms < 30.000 ms, giữ nguyên test/ngưỡng. Rebase lên origin/main 401882d sau PR #59; không sửa navigation, contract, backend, dependency hay schema. |
| T-02 | SCR-064 Question Bank: bộ lọc (HSK 1–3, loại, trạng thái, nút, tìm), bảng, phân trang 10, giữ lọc trên URL, Loading/Empty/Error | `web/features/question-bank/pages/Scr064QuestionBankPage.tsx` + test | FR-017, FR-018, FR-026 · AT-26 | ⬜ |
| T-03 | SCR-059 Question Edit: xem đủ; `TEACHER` form sửa (khoá trường chấm điểm khi `used`), `CONTENT_EDITOR` chỉ xem + lưu trữ; xác nhận trong trang; chọn nút từ API cây | `web/features/question-bank/pages/Scr059QuestionEditPage.tsx` + test | FR-019…FR-023, FR-027 · AT-27 | ⬜ |
| T-04 | SCR-068: 4 `dataType` mới + nhãn §0.2; hướng dẫn cột đọc từ `GET templates` (**báo leader trước** — feature `content-import`) | `web/features/content-import/types.ts`, `labels.ts` + test | FR-028 · AT-28 | ⬜ |

## Đợt 2 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-05 | `V5__knowledge_tree.sql` (§0.1) + entity `KnowledgePoint`, `QuestionKnowledgePoint` (role), `Question` (code, difficulty, options) trong `learning.knowledge`; test migration Testcontainers; cập nhật `database.md` §4.4–4.6, §11, §13. **Leader chạy V5 lên Neon sau merge** | `src/main/resources/db/migration/V5__knowledge_tree.sql`, `…/learning/knowledge/entity/*`, `…/repository/*`, `docs/reference/database.md` | §0.1 · AT-30 | ⬜ |
| T-06 | Mẫu `KNOWLEDGE_NODE`: `ImportDataType`/`ImportTemplate` thêm 4 giá trị (khai cột cả 4, validator/writer chỉ NODE), validator + writer; sửa enum `dataType` + mã lỗi dòng trong `openapi-import.yaml`; đổi test "7 mẫu" thành 11 | `…/content/importer/template/*`, `…/importer/validate/KnowledgeNodeRowValidator.java`, `…/importer/write/KnowledgeNodeRowWriter.java`, `specs/002-…/contracts/openapi-import.yaml`, test 002 | FR-002…FR-004 · AT-02…AT-04 | ⬜ |
| T-07 | Mẫu `KNOWLEDGE_EDGE` + `PrereqGraph` (kiểm DAG thuần: tự trỏ, cấp, vòng, > 4, bắc cầu thừa; xét DB + dòng phía trên); writer kiểm lại khi ghi, khoá nút `to` | `…/learning/knowledge/PrereqGraph.java`, `…/importer/validate/KnowledgeEdgeRowValidator.java`, `…/importer/write/KnowledgeEdgeRowWriter.java` | FR-005…FR-007 · AT-05…AT-08, AT-29 | ⬜ |
| T-08 | Mẫu `QUESTION`: kiểm theo `qtype`, nhãn, `distractor_errors`; writer tạo `APPROVED`, nhãn trong cùng transaction dòng; cập nhật giữ trạng thái, chặn đổi cách chấm câu đã dùng | `…/importer/validate/QuestionRowValidator.java`, `…/importer/write/QuestionRowWriter.java`, `…/learning/knowledge/QuestionRules.java` (dùng chung với T-12) | FR-008…FR-012 · AT-09…AT-12 | ⬜ |
| T-09 | Mẫu `NODE_LINK`: kiểm đích + miền; writer đặt `knowledge_point_id` | `…/importer/validate/NodeLinkRowValidator.java`, `…/importer/write/NodeLinkRowWriter.java` | FR-013, FR-014 · AT-13, AT-14 | ⬜ |
| T-10 | `KnowledgeTreeService` (FR-024, FR-025) + `GET /api/knowledge-tree`, `/nodes/{kid}`; `SecurityConfig` matcher `/api/knowledge-tree/**`; mã `KNOWLEDGE_NODE_NOT_FOUND`. **Khoá chữ ký `KnowledgeTreeService` khi merge** (011/012 dùng) | `…/learning/knowledge/KnowledgeTreeService.java`, `…/web/KnowledgeTreeController.java`, `…/shared/config/SecurityConfig.java`, `…/shared/exception/ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-001, FR-015, FR-016, FR-024, FR-025 · AT-01, AT-15, AT-16, AT-24, AT-25 | ⬜ |
| T-11 | `GET /api/admin/questions` (lọc, phân trang, nhãn + `used` theo lô) + `GET /{id}`; `SecurityConfig` matcher `/api/admin/questions/**` đặt **trước** `/api/admin/**`; mã `QUESTION_NOT_FOUND` | `…/learning/knowledge/QuestionAdminService.java`, `…/web/QuestionAdminController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-001, FR-017…FR-019 · AT-01, AT-17…AT-19 | ⬜ |
| T-12 | `PUT /{id}` (chỉ `TEACHER`, `QuestionRules`, khoá dòng, audit) + `PATCH /{id}/archive` (chỉ `CONTENT_EDITOR`, idempotent, audit); 4 mã `MALFORMED_QUESTION`, `KNOWLEDGE_NODE_REQUIRED`, `GRADING_CHANGE_NOT_ALLOWED`, `QUESTION_ARCHIVED` | `QuestionAdminService.java`, `QuestionAdminController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-020…FR-023 · AT-20…AT-23 | ⬜ |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-13 | FE SCR-064/059/068 gọi backend thật; sửa lệch contract (không đổi contract — lệch thì dừng báo leader); test hợp đồng `/v3/api-docs` so `openapi-knowledge.yaml` (path + status) | `web/features/question-bank/**`, `t/contract/KnowledgeContractTest.java` | §13 · AT-26…AT-28 chạy với API thật | ⬜ |

## Thứ tự thực thi

```
T-01 → T-02 → T-03 ; T-04 (song song T-02)
T-05 → T-06 → T-07 → T-08 → T-09
T-05 → T-10 → T-11 → T-12
(T-03, T-12) → T-13
```

- T-02, T-03 dùng `catalog.preview.ts`; không chờ backend.
- T-10 là điểm khoá cho spec 011/012: chữ ký `KnowledgeTreeService` không đổi sau merge (đổi phải qua leader).
- Hai nhánh BE (nhập T-06…T-09 và API T-10…T-12) chạy song song được sau T-05; cùng đụng `ErrorCode.java`/`SecurityConfig` ở T-10…T-12 nên merge lần lượt.

**Ba task rủi ro cao:** T-05 (migration đổi bảng "không đụng") · T-07 (kiểm DAG đúng khi xét DB + dòng trong file + kiểm lại lúc ghi) · T-08 (chặn đổi cách chấm câu đã dùng).

**Hiện trạng: 1/13 task xong.**
