# Tasks: Mastery, biên học tập và lịch ôn mục từ — Spec #011

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-mastery.yaml`](contracts/openapi-mastery.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 15 task**, ≤ 4 giờ/task, một task một nhánh `spec/011-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 011 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau.**
File dùng chung (spec §0.3) phải nêu trong Shadow Plan từng task.

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `mastery`: `types.ts` và `api/mastery.ts`, `api/reviews.ts` theo OpenAPI; `catalog.preview.ts` có cây ~30 nút tự soạn đủ 6 trạng thái + biên, 5 gợi ý, 12 mục ôn và đăng ký `previews` trong preview registry; `screens.ts`; 3 route §12 (`LEARNER`, layout `learner`) vào `routeAccess.ts` + `screenRegistry.ts`; chỉ thêm `DUE_REVIEW` vào cuối `PracticeSource`, đặt type `FlashcardRating` ở mastery (báo leader trước) | `web/features/mastery/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/app/preview/registry.ts`, `web/features/practice/types.ts` | FR-001 (FE), §12 | ✅ |
| T-02 | SCR-019 Learning Progress: lưới 6 miền × HSK 1–3, màu + nhãn chữ, viền biên, bảng chi tiết KID, người mới | `web/features/mastery/pages/Scr019LearningProgressPage.tsx`, `components/*` + test | FR-024 · AT-24 | ⬜ |
| T-03 | SCR-010 Learning Dashboard: khối "Gợi ý hôm nay" + khối "Ôn tập" | `web/features/mastery/pages/Scr010LearningDashboardPage.tsx` + test | FR-025 · AT-25 | ⬜ |
| T-04 | SCR-013 Due Review: dùng lại `usePracticeSession`; câu chọn + thẻ 4 mức, đếm còn lại, tạm dừng, rỗng + "Ôn trước" | `web/features/mastery/pages/Scr013DueReviewPage.tsx` + test | FR-026 · AT-26 | ⬜ |

## Đợt 2 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-05 | `V6__mastery_progress.sql` (§0.1) + entity `KnowledgeProgress`, `LexemeSchedule` + repository; test migration Testcontainers (tên CHECK, `ck_prog_mp`, entity 005 `validate`); `database.md` §4.9, §11, §13. **Leader chạy V6 lên Neon sau merge** | `src/main/resources/db/migration/V6__mastery_progress.sql`, `…/learning/mastery/KnowledgeProgress.java`, `…/learning/review/LexemeSchedule.java`, repository, `docs/reference/database.md` | FR-003 · AT-03 | ✅ |
| T-06 | `MasteryConfig` (`cnhsk.mastery.*`, kiểm khi khởi động) + `MasteryCalculator` + `MasteryStateMachine` (thuần) + test ví dụ Quốc Bảo, cửa sổ, vùng đệm. **Không phụ thuộc task nào** | `…/learning/mastery/{MasteryConfig,MasteryCalculator,MasteryStateMachine}.java`, `application.yaml`, `t/learning/mastery/*Test.java` | FR-009, FR-010 · AT-09, AT-10 | ✅ 2026-11-05: H1–H7 theo leader; 21/21 test mục tiêu pass; full verify 3.124 test, 0 failure/error/skip; JaCoCo LINE 96,94% (9.089/9.376), CLASS 505, mọi coverage check đạt. |
| T-07 | `EvidenceAllocator` (bối cảnh, KID, trọng số, hợp lệ, luật 7 ngày) + `MasteryUpdater` (upsert, khoá dòng) + `MasteryListener` (`SessionFinishedEvent`) | `…/learning/mastery/{EvidenceAllocator,MasteryUpdater,MasteryListener}.java` + test | FR-004…FR-008, FR-012 · AT-04…AT-08, AT-12 | ✅ 2026-11-06: targeted 28/28; full verify 3.342 test, 0 failure/error/skip, exit 0; JaCoCo LINE 96,88% (9.673/9.985). AT-12, E8 và NFR-P01 (12/12 truy vấn đọc) PASS. |
| T-08 | `MasteryRebuilder` + test so tăng dần ↔ phát lại (40 lượt trộn nguồn) | `…/learning/mastery/MasteryRebuilder.java` + test | FR-013 · AT-13 | ⬜ |
| T-09 | `MasteryQueryService` (đọc lại theo `today`, `inferred`) + `FrontierService` + `MasteryReader` (**khoá chữ ký khi merge** — 012 T-16 dùng) + `GET /api/mastery`, `/nodes/{kid}`; matcher | `…/learning/mastery/{MasteryQueryService,FrontierService,MasteryReader}.java`, `…/mastery/web/MasteryController.java`, `…/shared/config/SecurityConfig.java` | FR-001, FR-011, FR-014, FR-016…FR-018 · AT-01, AT-11, AT-14, AT-16…AT-18 | ⬜ |
| T-10 | `RecommendationService` + `GET /api/mastery/recommendations`; matcher | `…/learning/mastery/RecommendationService.java`, `MasteryController.java`, `SecurityConfig.java` | FR-015 · AT-15 | ⬜ |
| T-11 | `FsrsConfig` (`cnhsk.fsrs.*`, 19 trọng số, kiểm khi khởi động) + `FsrsScheduler` (thuần) + test; dùng nguyên 19 số của spec §0.5 (OQ-2 đã chốt). **Không phụ thuộc task nào** | `…/learning/review/{FsrsConfig,FsrsScheduler}.java`, `application.yaml`, `t/learning/review/FsrsSchedulerTest.java` | FR-019 · AT-19 | ✅ 2026-11-05: 73 test mục tiêu PASS; full verify 3.328 test, 0 failure/error/skip; JaCoCo LINE 96,99% (9.423/9.715). API first/review, Rating/Memory/Result đã duyệt, khóa khi merge; bàn giao bundle từ máy mượn theo questions.md. |
| T-12 | Engine §0.2: `PracticeSource.DUE_REVIEW`, `ItemType.FLASHCARD_RATING`, `PracticeApi`, contract 003. **Full verify 003–008, 012** (**báo leader trước**) | `…/learning/practice/{PracticeSource,ItemType}.java`, `…/learning/api/PracticeApi.java`, `specs/003-vocab-topics/contracts/openapi-practice-vocab.yaml` + test | FR-002 · AT-02 | ⬜ |
| T-13 | `ReviewListener`: vào lịch ôn và cập nhật FSRS từ lượt đã nộp (gồm `TOPIC_STUDY`) | `…/learning/review/ReviewListener.java` + test | FR-020 · AT-20 | ⬜ |
| T-14 | `GET /api/reviews/summary`, `POST /api/reviews/sessions`, `ReviewHandler` (rating thẻ, xử lý từng câu `DUE_REVIEW`: FSRS + minh chứng); matcher | `…/learning/review/{ReviewSessionService,ReviewHandler}.java`, `…/review/web/ReviewController.java`, `SecurityConfig.java` | FR-021…FR-023 · AT-21…AT-23 | ⬜ |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-15 | FE SCR-010/013/019 gọi backend thật; sửa lệch contract (lệch thì dừng báo leader); test hợp đồng `/v3/api-docs` so `openapi-mastery.yaml` (path + status) | `web/features/mastery/**`, `t/contract/MasteryContractTest.java` | §13 · AT-24…AT-27 chạy với API thật | ⬜ |

## Thứ tự thực thi

```
T-01 → T-02 → T-03 → T-04
T-06, T-11                         (thuần, làm ngay, không chờ ai)
T-05 → T-07 → T-08
T-07 → T-09 → T-10
T-12 → T-14
T-05 + T-11 → T-13 → T-14         (T-14 cần cả T-07)
(T-02, T-03, T-04, T-10, T-14) → T-15
```

**Phụ thuộc spec khác (merge trước):**

| Task 011 | Cần | Vì sao |
|---|---|---|
| T-05 | 010 T-05 (V5) | V6 chạy sau V5 |
| T-07, T-09, T-10 | 010 T-10 | `kidsOfQuestions`, `kidOfLexeme`, `kidOfTopic`, `nodes`, `approvedQuestionIds` |
| T-07 | 012 T-06 | `attempt_items.question_id`, 3 nguồn `MOCK_EXAM`/`PLACEMENT`/`TARGETED_PRACTICE` |
| T-12 | 012 T-06 | Cùng file `PracticeSource`, `ItemType`, `PracticeApi` |
| T-14 | 003 `ChoiceItemFactory` (đã có) | Câu chọn nghĩa cho mục ôn |

| 011 mở khoá cho | Task |
|---|---|
| 012 T-16 (điểm yếu theo mastery) | T-09 (`MasteryReader`) |

- T-02…T-04 dùng `catalog.preview.ts`; không chờ backend.
- T-09, T-10, T-14 cùng đụng `SecurityConfig` nên merge lần lượt.
- T-07 là điểm vào của mọi minh chứng: chạy full verify toàn repo (003–008, 012 phải xanh).

**Bốn task rủi ro cao:** T-07 (phân bổ đúng + cùng transaction `finish`) · T-06 (công thức phải ra 53,3/61,1) · T-11 (FSRS-5 đúng công thức) · T-12 (sửa engine dùng chung).

**Hiện trạng: 5/15 task xong.**
