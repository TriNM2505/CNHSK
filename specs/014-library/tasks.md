# Tasks: Thư viện tra cứu, sổ tay, flashcard và dịch câu — Spec #014

> Nguồn: [`spec.md`](spec.md) v1.1.0 · [`contracts/openapi-library.yaml`](contracts/openapi-library.yaml) · phụ lục [`contracts/openapi-library-v1-1.yaml`](contracts/openapi-library-v1-1.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 18 task** (15 bản 1.0 + 3 phụ lục v1.1), ≤ 4 giờ/task, một task một nhánh `spec/014-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 014 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau.** **Không commit `ANTHROPIC_API_KEY`; test dịch dùng stub, không gọi Claude thật.**
File dùng chung (spec §0.4) phải nêu trong Shadow Plan từng task.

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `library`: `types.ts` theo OpenAPI, `api/*.ts`, `catalog.preview.ts` (≈ 30 mục từ tự soạn đủ 3 loại, ghi chú, 2 bộ, kết quả dịch mẫu), `screens.ts`; 6 route §12 (2 public, 4 `LEARNER`) vào `routeAccess.ts` + `screenRegistry.ts` + mục điều hướng (**báo leader trước**) | `web/features/library/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/shared/auth/navigation.ts` | FR-001 (FE), §12 | ✅ (leader duyệt; menu tách PR riêng — xem `questions.md`) |
| T-02 | SCR-024 Dictionary Search + SCR-025 Dictionary Detail (dùng lại thành phần nét của 006) | `web/features/library/pages/Scr024DictionarySearchPage.tsx`, `Scr025DictionaryDetailPage.tsx` + test | FR-023, FR-024 · AT-23, AT-24 | ✅ (leader duyệt; xem `questions.md`) |
| T-03 | SCR-026 Translation (đoạn bấm được, popup tra từ + lưu) | `web/features/library/pages/Scr026TranslationPage.tsx` + test | FR-025 · AT-25 | ✅ — leader duyệt 12 file; library 345/345; full Vitest 2.263/2.263, 0 failure/error/skip; lint 0 error (chỉ warning LevelBadge có sẵn), build PASS; preview giữ 12 mục; nghiệm thu mock, nối thật ở T-15 |
| T-04 | SCR-027 Personal Notes (tìm, thẻ, thùng rác, đoạn trích escape) | `web/features/library/pages/Scr027PersonalNotesPage.tsx` + test | FR-026 · AT-26 | ✅ — leader duyệt 12 file; Vitest 1.825/1.825, lint chỉ warning LevelBadge có sẵn, build PASS; nối thật ở T-15 |
| T-05 | SCR-028 Deck List + SCR-029 Deck Detail (thẻ, ôn 4 nút) | `web/features/library/pages/Scr028DeckListPage.tsx`, `Scr029DeckDetailPage.tsx` + test | FR-027, FR-028 · AT-27, AT-28 | ✅ — leader duyệt 15 file; Vitest 1.996/1.996, lint chỉ warning LevelBadge có sẵn, build PASS; nối thật ở T-15 |

## Đợt 2 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | Tra cứu: `LibraryConfig` (`cnhsk.library.*`), `LibraryRateLimiter`, tìm chữ Hán/pinyin/nghĩa; matcher `permitAll` `/api/public/dictionary/**` | `…/learning/library/{LibraryConfig,LibraryRateLimiter,DictionaryService,PinyinQuery}.java`, `…/library/web/DictionaryController.java`, `SecurityConfig.java`, `application.yaml` | FR-001, FR-002, FR-003…FR-005 · AT-01…AT-05 | ✅ — leader full verify PASS tại 82a6b3b |
| T-07 | Bộ thủ + chi tiết mục từ; mã `LEXEME_NOT_FOUND` | `…/library/{RadicalService,LexemeDetailService}.java`, `DictionaryController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-006, FR-007 · AT-06, AT-07 | ✅ — full verify 2.729 test, 0 failure/error/skip; NFR-P02 p95 5,886 ms |
| T-08 | Sổ tay: sổ mặc định, tạo/đọc/sửa, danh sách + tìm; mã `COLLECTION_NOT_OWNED`, `LIBRARY_LIMIT_REACHED`, `NOTE_DUPLICATE`; matcher `LEARNER` `/api/notes/**` | `…/library/NotebookService.java`, `…/library/web/NoteController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-008…FR-011 · AT-08…AT-10 | ✅ — leader duyệt hướng B; full verify 2.913 test, 0 failure/error/skip; NFR-P04 p95 48,296 ms |
| T-09 | Xoá mềm, thùng rác, khôi phục; mã `NOTE_NOT_RESTORABLE` | `NotebookService.java`, `NotebookRepository.java`, `NoteController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` + test vòng đời/đồng thời/quyền | FR-012 · AT-11 | ✅ — chọn lọc 74 test PASS; full verify 2.994 test, 0 failure/error/skip; JaCoCo LINE 97,20% |
| T-10 | Bộ flashcard: danh sách, tạo (trống/từ chủ đề), chi tiết, sửa, xoá; matcher `LEARNER` `/api/flashcard-decks/**` | `…/library/DeckService.java`, `…/library/web/DeckController.java`, `SecurityConfig.java` | FR-013, FR-014 · AT-12, AT-13 | ✅ — leader duyệt 14 file; chọn lọc 46/46; full verify 3.002 test, 0 failure/error/skip; JaCoCo LINE 97,2542%; SQL danh sách 1 SELECT, chi tiết 2 SELECT |
| T-11 | Thẻ: thêm/sửa/xoá; mã `CARD_DUPLICATE` | `…/library/CardService.java`, `DeckController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-015, FR-016 · AT-14, AT-15 | ✅ — leader duyệt 11 file; chọn lọc 134/134; full verify 3.179 test, 0 failure/error/skip trên V6; JaCoCo LINE 97,0723%; tranh 499/500, trùng và guard unique PostgreSQL PASS |
| T-12 | Ôn thẻ: `GET …/due`, `POST /api/flashcards/{cardId}/review` dùng `FsrsScheduler`; matcher `/api/flashcards/**` | `…/library/FlashcardReviewService.java`, `…/library/web/FlashcardReviewController.java`, `SecurityConfig.java` | FR-017, FR-018 · AT-16, AT-17 | ✅ — leader duyệt tối đa 20 file; chọn lọc 373/373; full verify 3.799 test, 0 failure/error/skip; JaCoCo LINE 96,7286%; due 2 SELECT, FSRS 42/42, stability 36500 ghi DB PASS |
| T-13 | `Segmenter` (dài nhất trước, 1 truy vấn) — lớp gần thuần, test bằng từ điển fixture | `…/library/Segmenter.java` + test | FR-021 · AT-20 | ✅ — leader duyệt 6 file; library 322/322; full verify 3.833 test, 0 failure/error/skip; JaCoCo LINE 96,7506%; 2.000 code point: 1 SELECT, đầu/p50/p95 118,642/54,492/105,246 ms trong full verify; lượt chọn lọc chậm hơn ghi đủ tại questions.md; không-CJK 0 SELECT |
| T-14 | Dịch: `ClaudeTranslator` (dùng `ClaudeClient` của 015, system prompt §0.3, timeout 10 s, test bằng `StubClaudeClient`), `TranslationService` (cache, tiêu thụ, hoàn), `POST /api/translate`, không log nội dung; mã `TRANSLATE_UNAVAILABLE`; đọc `cnhsk.ai.translate-model` | `…/library/{ClaudeTranslator,TranslationService}.java`, `…/library/web/{TranslationController,TranslateRequest,TranslateResult}.java`, `SecurityConfig.java`, `ErrorCode.java`, test | FR-019, FR-020, FR-022 · AT-18, AT-19, AT-21, AT-22 | ✅ — leader duyệt G3, 14 file; targeted sau Fix 2 86/86 (delta callCount), regression 616/616; full 4.154 test có 1 ngoại lệ NFR_P04 QUESTION (32.693/30.000 ms), 0 error/skip; JaCoCo LINE 96,6882%, check PASS; NFR-P03 stub p95 9,147 ms; nối thật ở T-15 |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-15 | FE SCR-024…029 gọi backend thật; sửa lệch contract (lệch thì dừng báo leader); test hợp đồng `/v3/api-docs` so `openapi-library.yaml` (path + status) | `web/features/library/**`, `t/contract/LibraryContractTest.java` | §13 · AT-23…AT-29 chạy với API thật | ✅ — AT-23…28 đạt trên Chrome với Vite/API thật + Testcontainers trong 28,06 phút; AT-29 đủ 15 path/22 operation/status; contract 41/41, targeted 437/437, verify 4.161/4.161, JaCoCo LINE 96,6882%; Vitest 387/387 và full 2.387/2.387, lint/build PASS; 20 file, Fix 1 launcher; process/container và listener 8080/5173 đã dọn; Draft PR #136, chờ leader merge |

## Đợt 4 · Phụ lục v1.1 — bộ thẻ công khai, sao chép, menu Thư viện (leader thêm 2026-11-14, spec §16)

Người code: Xuân Huy. Không migration. Báo cáo bộ thẻ ở phía 009 (T-25/T-26, Quốc Huy) cần T-16 đã merge.

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-16 | BE: bật/tắt công khai, xoá bộ tắt công khai, danh sách + chi tiết công khai, sao chép; `learning.api.PublicDeckApi`; mã `DECK_TOO_FEW_CARDS`, `PUBLIC_DECK_NOT_FOUND` (trước `INTERNAL_ERROR`); matcher `permitAll` `GET /api/public/flashcard-decks/**`; gộp `openapi-library-v1-1.yaml` vào file gốc (DeckSummary thêm `isPublic`, `sharedAt`), xoá file phụ lục, nâng số path/operation trong `LibraryContractTest` | `…/learning/library/{DeckSharingService,PublicDeckQueryService}.java`, `…/library/web/*`, `…/learning/api/PublicDeckApi.java`, `ErrorCode.java`, `SecurityConfig.java`, `specs/014-library/contracts/*` + test | FR-029…FR-032, FR-034 · AT-30…AT-38 | ⬜ |
| T-17 | FE: công tắc chia sẻ + hộp xác nhận ở SCR-028; SCR-052 `/library/public-decks`; SCR-029 chế độ công khai `/library/public-decks/:id` (sao chép, báo cáo); route `by-role`; `SCREEN_NAMES` SCR-052 + `AssistantPromptBuilderTest` | `web/features/library/**`, `web/app/routeAccess.ts`, `screenRegistry.ts`, `…/learning/assistant/AssistantPromptBuilder.java` + test | FR-033, FR-035, FR-036 · AT-39 | ⬜ |
| T-18 | Menu Thư viện 5 mục (desktop nhóm thanh bên; mobile trong "Thêm" của 019/T-06) | `web/shared/layout/{navigation.ts,LearnerLayout.tsx}` + test | FR-037 · AT-40 | ⬜ |

**Thứ tự đợt 4:** T-16 → T-17; T-18 sau khi 019/T-06 (menu "Thêm") đã merge.
## Thứ tự thực thi

```
T-01 → T-02 → T-03 ; T-04 ; T-05       (T-03…T-05 song song sau T-01)
T-06 → T-07
T-08 → T-09
T-10 → T-11 → T-12
T-13 → T-14
(T-02…T-05, T-07, T-09, T-12, T-14) → T-15
```

**Phụ thuộc spec khác (merge trước):**

| Task 014 | Cần | Vì sao |
|---|---|---|
| T-12 | 011 T-11 | `FsrsScheduler` |
| T-14 | 013 T-12 | `EntitlementApi`, `INSUFFICIENT_CREDITS` |
| T-14 | 015 T-04 | `ClaudeClient`, `TokenBudget`, khối cấu hình `cnhsk.ai` |
| T-02 | FE 006 (đã có) | Thành phần hoạt hình nét |

- Tra cứu (T-06, T-07), sổ tay (T-08, T-09), bộ thẻ (T-10, T-11) **không phụ thuộc spec nào khác** → bắt đầu ngay.
- T-07, T-08, T-09, T-11, T-14 cùng đụng `ErrorCode.java`; T-06, T-08, T-10, T-12, T-14 cùng đụng `SecurityConfig` → merge lần lượt.

**Bốn task rủi ro cao:** T-14 (trừ/hoàn lượt đúng, không lộ nội dung, chống prompt injection) · T-06 (giới hạn khách theo IP, tra pinyin đúng) · T-08 (403 dữ liệu riêng, giới hạn) · T-12 (đúng FSRS, không đổi mastery).

**Hiện trạng: 15/15 task bản 1.0 xong; phụ lục v1.1 T-16…T-18 chưa làm.**
