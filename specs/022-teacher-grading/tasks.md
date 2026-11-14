# Tasks: Chấm bài viết thuê — Spec #022

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-grading.yaml`](contracts/openapi-grading.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 9 task**, ≤ 4 giờ/task, một task một nhánh `spec/022-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 022 `APPROVED`. Người code: Đạt. **Không migration.** `EntitlementApi`/`LedgerService` (013) đã có trên `main`. Không chờ 023.

## Đợt 1 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | `GradingConfig` (`cnhsk.grading.*`, thiếu khoá → không khởi động), đếm chữ Hán, `essayHash`, `GradingRepository` (SQL chuyển trạng thái có điều kiện §0.2), mã lỗi §5 | `…/learning/grading/{GradingConfig,EssayRules,GradingRepository}.java`, `application.yaml`, `ErrorCode.java`, `GlobalExceptionHandlerTest` + test | §0.1…§0.3 · AT-01 | ✅ MERGED — PR #175, c5da36b |
| T-02 | Người học: policy, gửi (khoá + trùng + `consume` + chèn trong một transaction), danh sách, chi tiết, huỷ + hoàn; `SecurityConfig` `LEARNER` | `…/learning/grading/GradingRequestService.java`, `…/grading/web/GradingRequestController.java`, `SecurityConfig.java` + test | FR-001…FR-007 · AT-02…AT-05, AT-17 | ✅ MERGED — PR #178 |
| T-03 | Giáo viên: hàng đợi, bài đang giữ, nhận (giới hạn 3, chặn tự chấm), xem, trả lại; `SecurityConfig` `TEACHER` `/api/teacher/**` | `…/learning/grading/TeacherGradingService.java`, `…/grading/web/TeacherGradingController.java`, `SecurityConfig.java` + test | FR-009…FR-013 · AT-06…AT-10 | 🔄 chờ review |
| T-04 | Nộp kết quả: kiểm thang, cộng điểm, chuyển `COMPLETED` + `TEACHER_PAYOUT` một lần | `TeacherGradingService.java` + test | FR-014, FR-015 · AT-11, AT-12 | ⬜ |
| T-05 | `GradingExpiryJob`: hết hạn từng yêu cầu một transaction + hoàn, log sức khoẻ | `…/learning/grading/GradingExpiryJob.java` + test | FR-017…FR-019 · AT-13…AT-16 | ⬜ |

## Đợt 2 · Frontend + hợp đồng

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | Nền FE `grading` (types theo OpenAPI, api, dữ liệu mẫu tự soạn) + SCR-032/033/034; route `learner`; `SCREEN_NAMES` SCR-032/033/034 + `AssistantPromptBuilderTest`; mục menu "Chấm bài" | `web/features/grading/**`, `web/app/routeAccess.ts`, `screenRegistry.ts`, `web/shared/layout/navigation.ts`, `…/learning/assistant/AssistantPromptBuilder.java` + test | FR-008 · AT-18 | ⬜ |
| T-07 | FE giáo viên SCR-056/057 (hàng đợi, bài đang giữ, khung chấm 4 tiêu chí, sửa lỗi, trả lại); route `['TEACHER']` layout `admin`; menu quản trị | `web/features/teacher-grading/**`, `routeAccess.ts`, `screenRegistry.ts`, `web/shared/layout/navigation.ts` + test | FR-016 · AT-18 | ⬜ |
| T-08 | Nối FE với backend thật; sửa lệch hợp đồng (lệch thì dừng báo leader) | `web/features/grading/**`, `web/features/teacher-grading/**` | FR-008, FR-016 | ⬜ |
| T-09 | Test hợp đồng `/v3/api-docs` so `openapi-grading.yaml` (hai chiều path + method + status) | `t/contract/GradingContractTest.java` | §13 · AT-19 | ⬜ |

## Thứ tự

```
T-01 ─► T-02 ─► T-03 ─► T-04 ─► T-05 ─► T-09
T-06, T-07 song song từ đầu bằng dữ liệu mẫu ─► T-08 (sau T-04)
```

**Rủi ro cao:** T-02 (trừ lượt và tạo yêu cầu nhất quán), T-04 (trả giáo viên đúng một lần), T-05 (hoàn đúng một lần, đua với nhận bài).
**Seed demo:** leader tạo một tài khoản `TEACHER` trên Neon (không commit mật khẩu) trước khi chạy thử T-08.
