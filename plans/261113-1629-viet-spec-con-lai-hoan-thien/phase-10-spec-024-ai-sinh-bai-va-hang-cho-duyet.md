---
phase: 10
title: "Spec 024 AI sinh bài + hàng chờ duyệt"
status: pending
priority: P2
dependencies: [1]
effort: "spec 1,5 · BE 6 · FE 1,5 ngày công"
owner: "Xuân Huy"
---

# Phase 10: Spec 024 — AI sinh bài luyện theo điểm yếu + hàng chờ duyệt

## Overview
Feature 3.3. Màn SCR-058 AI Review Queue (CONTENT_EDITOR). Sinh **tự động** từ phân tích câu sai, không có nút cho học viên. **Không bảng mới.** Mọi phụ thuộc kỹ thuật đã merge: `ClaudeClient` (015/T-04), `MasteryReader` (011), luyện theo nút (012 `TargetedPracticeController`), dữ liệu nút KID (010 V5: `can_do`, `name_zh`, `error_kid`).

## Context links
- UC-048 Yêu cầu AI sinh bài theo điểm yếu, UC-049 AI sinh câu vào hàng đợi duyệt, UC-050 Tái dùng câu đã duyệt (use-cases-04); UC-108 Duyệt câu AI, UC-109 Sửa câu (use-cases-08)
- `service_requests` kind `AI_GENERATE` (+ index hàng đợi), `questions.source`, `status PENDING_REVIEW` (V1: "câu AI luôn vào PENDING_REVIEW"), `question_knowledge_points`
- 010 `QuestionAdminController`, SCR-059 sửa câu; 011 `MasteryReader` (nút yếu); 015 `AssistantPromptBuilder`/`PiiMasker` (mẫu prompt + che PII)
- 010/011/012 spec đều ghi 3.3 "để sau" → 024 là chỗ làm

## Thiết kế đề xuất
<!-- Updated: Validation Session 1 - sinh tự động từ câu sai + dữ liệu cây tri thức, không do học viên bấm -->
- **Leader chốt (validate 13/11): hệ thống tự sinh — không ai bấm, không thu phí học viên.**
- **Bước 1 — Đánh giá câu sai (job định kỳ, cron cấu hình, test đặt `-`):** gom `attempt_items` sai trong N ngày (theo `SettingsApi`) → quy về nút KID: KID chính của câu (`question_knowledge_points` PRIMARY) + `error_kid` của phương án nhiễu học viên đã chọn (010 Q7) + nút yếu từ `MasteryReader` (011). Chọn KID **nhiều lượt sai** và **số câu APPROVED còn ít** (ngưỡng `ai-generate.min-wrong`, `ai-generate.min-bank` trong `SettingsApi`). Chưa có lô đang chờ duyệt cho KID đó → mới sinh (chống sinh trùng).
- **Bước 2 — Sinh theo tài liệu cây tri thức:** prompt dựng từ dữ liệu nút trong DB (đã nạp từ Cây Năng Lực v2.0 qua 010): `code`, `title`, `name_zh`, `can_do`, cấp HSK, nút tiên quyết, từ/chữ gắn nút (`lexemes.knowledge_point_id`), mẫu lỗi thường gặp (`error_note`) + vài câu APPROVED làm ví dụ. **Không đưa PDF/dữ liệu đề thi vào repo** — chỉ dùng dữ liệu đã có trong DB.
- **Bước 3:** tạo `service_requests` `AI_GENERATE` (người yêu cầu = tài khoản hệ thống cấu hình — xem câu hỏi mở) → worker gọi Claude (structured JSON, 5 câu `QUESTION_CHOICE` kèm `error_kid` cho từng phương án nhiễu) → kiểm hợp lệ (4 đáp án, 1 đúng, không trùng câu có sẵn, chữ Hán ≤ cấp nút) → lưu `questions` `source='AI'`, `PENDING_REVIEW`, gắn KID đề xuất → `COMPLETED`. Lỗi/timeout → `FAILED`, thử lại lần sau. Giới hạn bằng ngân sách token tháng (015 `AiConfig`) + số lô tối đa mỗi lần chạy.
- **Không gọi `EntitlementApi`** (chi phí hệ thống chịu). `PaidFeature.AI_GENERATE` để nguyên, không dùng đợt này.
- **Leader chốt 13/11: phải duyệt trước, rồi gắn vào cây tri thức, mới tới học viên.** Câu AI ở `PENDING_REVIEW` **không bao giờ** phát cho học viên. Editor duyệt ở 058: kiểm/sửa nội dung, **xác nhận hoặc đổi nút KID** (`question_knowledge_points` chính/phụ, theo cây 010) → `APPROVED` → từ đó câu vào kho chung, được engine luyện theo nút (012 `TARGETED_PRACTICE`, quiz 019, ôn tập) phát cho mọi học viên và tính mastery bình thường (UC-050).
- Sau khi duyệt, câu tự xuất hiện trong luyện theo nút (012 `TARGETED_PRACTICE`), gợi ý ôn (011) và quiz (019) cho mọi học viên yếu nút đó — học viên không cần thao tác gì.
- **058 (CONTENT_EDITOR):** hàng đợi lọc KID/cấp/ngày; xem câu + giải thích; Duyệt / Sửa (mở SCR-059) / Từ chối có lý do; không ai duyệt câu do chính mình yêu cầu.
- Giới hạn: tối đa 1 yêu cầu đang chạy/người (index `service_requests` đã có), ngân sách token tháng (015 `AiConfig`), che PII trong prompt.

## Requirements
- Functional: job phân tích câu sai → chọn KID → sinh lô câu; hàng chờ duyệt + gắn KID; câu đã duyệt vào luyện tập; ADMIN xem lịch sử lô (thành công/lỗi, token dùng).
- Non-functional: idempotent theo (KID, lô đang chờ); không vượt ngân sách token; test chỉ dùng `StubClaudeClient` + `Clock` cố định; chạy thật cần `ANTHROPIC_API_KEY` (máy leader).

## Implementation Steps (viết spec)
1. §0.3 prompt hệ thống + JSON schema đầu ra (ví dụ tự soạn, không lấy đề HSK).
2. Error matrix: `AI_QUESTION_NOT_FOUND`, `AI_QUESTION_NOT_PENDING`, `KNOWLEDGE_NODE_REQUIRED` (có sẵn) khi duyệt thiếu KID; lỗi gọi Claude chỉ ghi vào `service_requests.result`, không trả cho người dùng.
3. API `contracts/openapi-ai-practice.yaml` (chỉ quản trị): `GET /api/admin/ai-questions`, `PUT /api/admin/ai-questions/{id}` (sửa + KID), `POST …/{id}/approve|reject`, `GET /api/admin/ai-generation-runs` (lịch sử lô). Học viên luyện câu đã duyệt qua endpoint sẵn có của 012.
4. tasks.md: (a) phân tích câu sai → chọn KID, (b) dựng prompt từ dữ liệu nút, (c) worker + validator, (d) màn 058, (e) AT. Không task nào chờ spec mới; khoá cấu hình ngưỡng dùng ⚿K7.

## Success Criteria
- [ ] Không bảng mới; dùng `service_requests` + `questions` đúng thiết kế V1
- [ ] Prompt chỉ dựng từ dữ liệu cây tri thức trong DB; không file PDF/đề thi trong repo
- [ ] Không sinh trùng khi KID đã có lô chờ duyệt (test)
- [ ] Câu chưa duyệt không bao giờ tới học viên (test API + engine); câu duyệt phải có KID chính

## Risk Assessment
- Chất lượng câu AI → validator chặt + duyệt người; tỷ lệ lỗi ghi metric.
- Chi phí API do hệ thống chịu → ngân sách token tháng (015) + số lô tối đa mỗi lần chạy (`SettingsApi`).
- `service_requests.user_id` NOT NULL mà job không có người yêu cầu → câu hỏi mở: dùng tài khoản hệ thống cấu hình (đề xuất: tài khoản bootstrap ADMIN qua `SettingsApi`) — chốt khi viết spec.
- Nút có `can_do` trống → bỏ qua nút đó, ghi log.
