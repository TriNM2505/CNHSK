# Tasks: Kênh, podcast và sách tham khảo — Spec #018

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-references.yaml`](contracts/openapi-references.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 8 task**, ≤ 4 giờ/task, một task một nhánh `spec/018-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 018 `APPROVED`. **Không phụ thuộc spec mới nào.** Migration: xin số V ở leader trước khi mở PR T-01; leader chạy Neon ngay sau merge.

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | **⚿K2** Migration `reference_items` (DDL §0.1, CHECK, index; quyền ngoài Flyway) + entity/repository + test ràng buộc DB | `src/main/resources/db/migration/V7__reference_items.sql`, `…/learning/reference/{ReferenceItem,ReferenceItemRepository}.java` + test | §0.1 · AT-09 (DB) | MERGED (#148, 7d66a11) |
| T-02 | `ReferenceUrlPolicy` (parse, `https`, chặn user:pass, so host với `cnhsk.reference.allowed-domains`/`thumbnail-domains`) + cấu hình yaml + unit test đủ ca | `…/learning/reference/ReferenceUrlPolicy.java`, `application.yaml` + test | FR-015, §0.2 · AT-05…AT-08 | MERGED (#149, de73286) |
| T-03 | `ReferenceQueryService` + `GET /api/public/references`, `GET /api/public/references/{id}` (chỉ PUBLISHED, lọc HSK/tag, DTO công khai); mã `REFERENCE_NOT_FOUND`; permitAll + rate limit khách | `…/learning/reference/ReferenceQueryService.java`, `…/reference/web/PublicReferenceController.java`, `ErrorCode.java`, `SecurityConfig.java` + test | FR-001, FR-004…FR-006 · AT-01…AT-04 | ✅ 2026-11-13: targeted 39/39; shared gate 28/28; full verify 4.451 F0/E0/S0, JaCoCo LINE 96,7601% |
| T-04 | `AuditService.countByTargets(...)` dùng `ix_audit_target` (**`shared` — báo leader**) + `ReferenceReportService` + `POST /api/references/{id}/report` (LEARNER, 1 lần/ngày/mục) | `…/shared/audit/AuditService.java`, `…/learning/reference/ReferenceReportService.java`, controller + test | FR-002, FR-009, FR-010, FR-016 · AT-12, AT-13 | ✅ 2026-11-14: targeted 56/56 (audit cũ 12/12); boundary 9/9; full verify 4.540 F0/E0/S0, JaCoCo LINE 96,7906% |
| T-05 | `ReferenceAdminService` + 7 endpoint quản trị (tạo DRAFT, sửa, công bố, ẩn, hiện lại về DRAFT, danh sách kèm `reportCount` một truy vấn); audit mọi thao tác; mã `MALFORMED_URL`, `NON_HTTPS_LINK`, `DOMAIN_NOT_ALLOWED`, `PUBLISH_INVALID_RESOURCE`; matcher `CONTENT_EDITOR` | `…/learning/reference/ReferenceAdminService.java`, `…/reference/web/AdminReferenceController.java`, `ErrorCode.java`, `SecurityConfig.java` + test | FR-003, FR-011…FR-014, FR-017, FR-018 · AT-09…AT-11, AT-14…AT-16 | ✅ 2026-11-14: 4.619 test, F0/E0/S0, JaCoCo LINE 96,8361%; chờ leader review |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | Nền FE `references` + SCR-053 (tab YouTube/Podcast), SCR-054, SCR-055: lọc, lưới thẻ, link an toàn, nhãn affiliate, báo lỗi (người học), 4 trạng thái, responsive; route + menu công khai/học viên | `web/features/references/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/shared/layout/navigation.ts` + test | FR-007, FR-008, FR-019…FR-021, FR-023 · AT-17, AT-18 | ✅ 2026-11-14: FE 2.520/2.520, lint/typecheck/build PASS; Java 10/11, ngoại lệ SCR-009 được leader duyệt; chờ review |
| T-07 | SCR-072 quản trị: bảng lọc + cột số lần báo, form tạo/sửa (trường sách theo `kind`), Công bố/Ẩn/Hiện lại có xác nhận trong trang; route `/content/references` + menu biên tập viên | `web/features/references/admin/**` + test | FR-022, FR-023 | ⬜ |
| T-08 | Dữ liệu mẫu tự soạn ≥ 10 mục (SQL seed dev hoặc script gọi API) + test hợp đồng `/v3/api-docs` so `openapi-references.yaml` | `docs/demo-data/09-references.csv`, `t/contract/ReferenceContractTest.java` | AT-19 | ⬜ |

## Thứ tự

```
T-01 ─┬─► T-03 ─┐
T-02 ─┘         ├─► T-05 ─► T-08
        T-04 ───┘
T-06, T-07 song song từ đầu bằng dữ liệu mẫu; nối thật sau T-03/T-05
```
