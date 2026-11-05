# Tasks: Trợ lý AI hỗ trợ sử dụng hệ thống — Spec #015

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-assistant.yaml`](contracts/openapi-assistant.yaml)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 7 task** (6 code + 1 nội dung), ≤ 4 giờ/task, một task một nhánh `spec/015-<task>` một PR. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
Đường dẫn: `…/` = `src/main/java/com/cnhsk/`; `t/` = `src/test/java/com/cnhsk/`; `web/` = `cnhsk-web/src/`.
**Điều kiện bắt đầu:** spec 015 đã `APPROVED` 2026-11-03 — theo thứ tự luồng trong `workflow-phu-thuoc-task-010-015.md` (plan `261102-2104`). **Thứ tự: FE trước, BE sau.** **Không commit `ANTHROPIC_API_KEY`; test dùng `StubClaudeClient`, CI không gọi Claude thật.**

## Đợt 1 · Frontend bằng dữ liệu mẫu

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Nền FE `assistant`: `types.ts`, `api/ask.ts`, câu trả lời mẫu (trong/ngoài phạm vi); gắn panel vào layout học viên; ẩn ở SCR-021 và trang kiểm tra chủ đề (**báo leader trước** — layout dùng chung) | `web/features/assistant/**`, layout học viên trong `web/app/` | FR-013, FR-014 · AT-13, AT-14 | ✅ |
| T-02 | Panel đủ trạng thái: gửi, đang trả lời, `outOfScope` + 2 nút, 429/403/503, escape câu trả lời, "Cuộc trò chuyện mới" | `web/features/assistant/components/*` + test | FR-015 · AT-15 | ✅ |

## Đợt 2 · Nội dung và backend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-03 | **Nội dung (không code):** viết 12 file hướng dẫn §0.3 theo tính năng đã merge + bộ 20 câu kiểm thử `docs/demo-data/assistant-eval.md`; leader duyệt. Bắt đầu ngay với 001–009, bổ sung khi 010–014 merge | `src/main/resources/help/*.md`, `docs/demo-data/assistant-eval.md` | FR-012 · AT-12 | ⬜ |
| T-04 | `learning.ai`: `AiConfig` (`cnhsk.ai.*`), `ClaudeClient` + `AnthropicClaudeClient` (SDK, timeout, `cache_control`) + `StubClaudeClient`, `AiUnavailableException`, `TokenBudget`; `pom.xml`, `.env.example`. **Không phụ thuộc task nào — 014 T-14 cần task này** | `…/learning/ai/*`, `pom.xml`, `application.yaml`, `.env.example` + test | FR-006, FR-009 · AT-06, AT-09 | ✅ |
| T-05 | `HelpLibrary` (nạp, kiểm 60 000 ký tự), `PiiMasker`, `AssistantPromptBuilder` (system §0.2, tên màn) + test | `…/learning/assistant/{HelpLibrary,PiiMasker,AssistantPromptBuilder}.java` + test | FR-005, FR-007, FR-010 · AT-05, AT-07, AT-10 | ✅ |
| T-06 | `AssistantService` + `POST /api/assistant/ask`: kiểm body, `TestInProgressGuard`, giới hạn ngày, gọi client, đọc JSON, lọc log; 2 mã `ASSISTANT_BLOCKED_DURING_TEST`, `ASSISTANT_UNAVAILABLE`; matcher `LEARNER` | `…/learning/assistant/{AssistantService,TestInProgressGuard}.java`, `…/assistant/web/AssistantController.java`, `SecurityConfig.java`, `ErrorCode.java`, `GlobalExceptionHandlerTest`, `application.yaml` | FR-001…FR-004, FR-008, FR-011 · AT-01…AT-04, AT-08, AT-11 | ⬜ |

## Đợt 3 · Nối thật

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | FE panel gọi backend thật; test hợp đồng `/v3/api-docs` so `openapi-assistant.yaml`; chạy bộ 20 câu với Claude thật trên máy leader (khoá do leader đặt), ghi kết quả | `web/features/assistant/**`, `t/contract/AssistantContractTest.java`, `specs/015-…/questions.md` | §13, SC-002 · AT-12…AT-16 | ⬜ |

## Thứ tự thực thi

```
T-01 → T-02
T-03                            (nội dung, bắt đầu ngay, cập nhật dần)
T-04 → T-05 → T-06
(T-02, T-03, T-06) → T-07
```

**Phụ thuộc spec khác:** T-06 đọc `attempts` với nguồn `MOCK_EXAM`, `PLACEMENT` (012 T-06) — trước khi 012 merge, chặn chỉ có tác dụng với `TOPIC_TEST`. **015 T-04 mở khoá 014 T-14.**

**Ba task rủi ro cao:** T-06 (chặn khi đang thi, không lộ nội dung) · T-04 (khoá, timeout, trần chi phí) · T-03 (tài liệu sai → trả lời sai).

**Hiện trạng: 0/7 task xong.**
