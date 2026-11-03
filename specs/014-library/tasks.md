# Tasks: Thư viện tra cứu, sổ tay, flashcard và dịch câu — Spec #014

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-library.yaml`](contracts/openapi-library.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 15 task**, ≤ 4 giờ/task, một task một nhánh `spec/014-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 014 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau.** **Không commit `ANTHROPIC_API_KEY`; test dịch dùng stub, không gọi Claude thật.**
File dùng chung (spec §0.4) phải nêu trong Shadow Plan từng task.

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `library`: `types.ts` theo OpenAPI, `api/*.ts`, `catalog.preview.ts` (≈ 30 mục từ tự soạn đủ 3 loại, ghi chú, 2 bộ, kết quả dịch mẫu), `screens.ts`; 6 route §12 (2 public, 4 `LEARNER`) vào `routeAccess.ts` + `screenRegistry.ts` + mục điều hướng (**báo leader trước**) | `web/features/library/**`, `web/app/routeAccess.ts`, `web/app/screenRegistry.ts`, `web/shared/auth/navigation.ts` | FR-001 (FE), §12 | ⬜ |
| T-02 | SCR-024 Dictionary Search + SCR-025 Dictionary Detail (dùng lại thành phần nét của 006) | `web/features/library/pages/Scr024DictionarySearchPage.tsx`, `Scr025DictionaryDetailPage.tsx` + test | FR-023, FR-024 · AT-23, AT-24 | ⬜ |
| T-03 | SCR-026 Translation (đoạn bấm được, popup tra từ + lưu) | `web/features/library/pages/Scr026TranslationPage.tsx` + test | FR-025 · AT-25 | ⬜ |
| T-04 | SCR-027 Personal Notes (tìm, thẻ, thùng rác, đoạn trích escape) | `web/features/library/pages/Scr027PersonalNotesPage.tsx` + test | FR-026 · AT-26 | ⬜ |
| T-05 | SCR-028 Deck List + SCR-029 Deck Detail (thẻ, ôn 4 nút) | `web/features/library/pages/Scr028DeckListPage.tsx`, `Scr029DeckDetailPage.tsx` + test | FR-027, FR-028 · AT-27, AT-28 | ⬜ |

## Đợt 2 · Backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-06 | Tra cứu: `LibraryConfig` (`cnhsk.library.*`), `LibraryRateLimiter`, tìm chữ Hán/pinyin/nghĩa; matcher `permitAll` `/api/public/dictionary/**` | `…/learning/library/{LibraryConfig,LibraryRateLimiter,DictionaryService,PinyinQuery}.java`, `…/library/web/DictionaryController.java`, `SecurityConfig.java`, `application.yaml` | FR-001, FR-002, FR-003…FR-005 · AT-01…AT-05 | ⬜ |
| T-07 | Bộ thủ + chi tiết mục từ; mã `LEXEME_NOT_FOUND` | `…/library/{RadicalService,LexemeDetailService}.java`, `DictionaryController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-006, FR-007 · AT-06, AT-07 | ⬜ |
| T-08 | Sổ tay: sổ mặc định, tạo/đọc/sửa, danh sách + tìm; mã `COLLECTION_NOT_OWNED`, `LIBRARY_LIMIT_REACHED`; matcher `LEARNER` `/api/notes/**` | `…/library/NotebookService.java`, `…/library/web/NoteController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-008…FR-011 · AT-08…AT-10 | ⬜ |
| T-09 | Xoá mềm, thùng rác, khôi phục; mã `NOTE_NOT_RESTORABLE` | `NotebookService.java`, `NoteController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-012 · AT-11 | ⬜ |
| T-10 | Bộ flashcard: danh sách, tạo (trống/từ chủ đề), chi tiết, sửa, xoá; matcher `LEARNER` `/api/flashcard-decks/**` | `…/library/DeckService.java`, `…/library/web/DeckController.java`, `SecurityConfig.java` | FR-013, FR-014 · AT-12, AT-13 | ⬜ |
| T-11 | Thẻ: thêm/sửa/xoá; mã `CARD_DUPLICATE` | `…/library/CardService.java`, `DeckController.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest` | FR-015, FR-016 · AT-14, AT-15 | ⬜ |
| T-12 | Ôn thẻ: `GET …/due`, `POST /api/flashcards/{cardId}/review` dùng `FsrsScheduler`; matcher `/api/flashcards/**` | `…/library/FlashcardReviewService.java`, `…/library/web/FlashcardReviewController.java`, `SecurityConfig.java` | FR-017, FR-018 · AT-16, AT-17 | ⬜ |
| T-13 | `Segmenter` (dài nhất trước, 1 truy vấn) — lớp gần thuần, test bằng từ điển fixture | `…/library/Segmenter.java` + test | FR-021 · AT-20 | ⬜ |
| T-14 | Dịch: `ClaudeTranslator` (dùng `ClaudeClient` của 015, system prompt §0.3, timeout 10 s, test bằng `StubClaudeClient`), `TranslationService` (cache, tiêu thụ, hoàn), `POST /api/translate`, lọc log; mã `TRANSLATE_UNAVAILABLE`; `cnhsk.ai.translate-model` | `…/library/{ClaudeTranslator,TranslationService}.java`, `…/library/web/TranslationController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest`, `application.yaml` | FR-019, FR-020, FR-022 · AT-18, AT-19, AT-21, AT-22 | ⬜ |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-15 | FE SCR-024…029 gọi backend thật; sửa lệch contract (lệch thì dừng báo leader); test hợp đồng `/v3/api-docs` so `openapi-library.yaml` (path + status) | `web/features/library/**`, `t/contract/LibraryContractTest.java` | §13 · AT-23…AT-29 chạy với API thật | ⬜ |

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

**Hiện trạng: 0/15 task xong.**
