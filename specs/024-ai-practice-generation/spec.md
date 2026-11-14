# Feature Specification: AI sinh câu luyện theo điểm yếu + hàng chờ duyệt

**Feature:** `024-ai-practice-generation`
**Feature tree:** 3.3 AI tự sinh bài luyện theo điểm yếu · **Module:** `learning` (package `learning.aigen`)
**Use case:** UC-048, UC-049, UC-050 (use-cases-04) · UC-108, UC-109 (use-cases-08) · **Actor:** job hệ thống, `TEACHER`, `CONTENT_EDITOR`, `ADMIN`
**Màn hình:** SCR-058 AI Review Queue (+ SCR-059 Question Edit của 010 dùng chung)
**Scope:** V2 · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.0.0 |
| **Status** | APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` |
| **Phụ thuộc** | 010 (cây KID, `question_knowledge_points`, `/api/admin/questions`, SCR-059) · 011 (`MasteryReader`) · 015 (`ClaudeClient`, `TokenBudget`, `StubClaudeClient`) · 023 (`SettingsApi`, ⚿K7) · 001 (`AuthApi`, `AuditService`) |
| **Quyết định chung** | Leader validate 2026-11-13: hệ thống tự sinh từ câu sai + dữ liệu cây tri thức, không học viên bấm, không thu phí; phải duyệt và gắn KID trước khi tới học viên · leader chốt 2026-11-14 (§9) |
| **Người code** | Xuân Huy |

> **Người code đọc:** `AGENTS.md` → spec 010 §0 (cây KID, `options.error_kid`) → spec 015 §0.3 (`ClaudeClient`, ngân sách) → file này → `contracts/openapi-ai-practice.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Job định kỳ: gom câu sai → chọn KID yếu, ít câu → gọi Claude sinh lô câu trắc nghiệm → kiểm → lưu `PENDING_REVIEW` (UC-048, UC-049) | Học viên bấm "sinh bài cho tôi"; thu phí `PaidFeature.AI_GENERATE` |
| Hàng chờ duyệt SCR-058 cho `TEACHER`/`CONTENT_EDITOR`; duyệt, duyệt hàng loạt câu đã xem, từ chối có lý do (UC-108) | Dạng câu khác trắc nghiệm 4 phương án; câu có audio/hình |
| Sửa câu AI chờ duyệt qua SCR-059 (UC-109) | Đánh giá chất lượng prompt tự động |
| `ADMIN` xem lịch sử lô + token, chạy thử một lần | Đưa PDF Cây Năng Lực hay dữ liệu đề HSK vào repo/prompt |
| Câu đã duyệt tự vào kho chung (UC-050) | Sửa engine 012/019/011 |

**Không migration, không bảng mới.** Dùng `learning.service_requests` (`kind='AI_GENERATE'`, `uq_ai_running`), `learning.questions` (`source='AI'`, mặc định `PENDING_REVIEW`, `ck_q_ai_review`, `ck_q_reject`, `ix_q_review`), `question_knowledge_points` (`uq_qkp_primary`), `knowledge_points` (`code`, `name_zh`, `can_do`, `hsk_level`, `prereq_ids`), `lexemes.knowledge_point_id`.

### 0.1 · Khoá cấu hình (thêm vào `SettingKey` của 023, nhóm "AI sinh câu"; leader chốt 2026-11-14)

| Khoá | Mặc định | Khoảng | Ý nghĩa |
|---|---|---|---|
| `ai-generate.system-user-id` | **0** | 0–2 147 483 647 | Tài khoản `ADMIN` đứng tên lô; **0 = job tắt** (Q1) |
| `ai-generate.window-days` | 14 | 1–90 | Xét câu sai trong N ngày |
| `ai-generate.min-wrong` | 20 | 1–1 000 | Số lượt sai tối thiểu của một KID |
| `ai-generate.min-bank` | 10 | 1–200 | Chỉ sinh khi KID có **ít hơn** N câu `APPROVED` |
| `ai-generate.batch-size` | 5 | 1–10 | Số câu mỗi lô |
| `ai-generate.max-batches-per-run` | 3 | 0–20 | Số lô tối đa mỗi lần chạy |

Khoá kỹ thuật ở yaml (không ở 078): `cnhsk.ai-generate.cron` = `0 0 2 * * *` (giờ VN), `cnhsk.ai.generate-model` = `claude-sonnet-5-5`, `cnhsk.ai-generate.timeout` = `60s`. Nếu 023/T-01 chưa merge, đọc các khoá §0.1 từ yaml `cnhsk.ai-generate.*` với cùng mặc định; 023/T-04 chuyển sau.

### 0.2 · Vòng đời lô và câu

```
Lô (service_requests AI_GENERATE):  RUNNING ──► COMPLETED (≥ 1 câu hợp lệ)
                                            └─► FAILED (lỗi Claude/JSON/ngân sách/0 câu hợp lệ; error_message)
Câu (questions source='AI'):  PENDING_REVIEW ──approve──► APPROVED ──► kho chung (UC-050)
                                     └──reject (lý do)──► REJECTED
```

`payload` = `{kid, knowledgePointId, model, wrongCount, bankCount}`; `result` = `{questionIds, dropped:[{index, reason}], inputTokens, outputTokens}`.

### 0.3 · Prompt và đầu ra

- **Không có dữ liệu người học trong prompt** — chỉ số đếm tổng hợp nằm ở `payload`, không gửi Claude. Không cần `PiiMasker`.
- Khối hệ thống 1 (cố định, `cache=true`): vai trò "giáo viên tiếng Trung soạn câu luyện cho người Việt"; câu hỏi bằng tiếng Trung giản thể, lời giải tiếng Việt; chỉ dùng từ/chữ trong danh sách cho phép; không chép đề thi có bản quyền; chỉ trả JSON đúng lược đồ.
- Khối hệ thống 2 (theo nút, `cache=true`): `code`, `title`, `name_zh`, `can_do`, cấp HSK, tiêu đề các nút tiên quyết, danh sách từ/chữ gắn nút (`lexemes.knowledge_point_id`, ≤ 80 mục: `text`, `pinyin`, nghĩa), tối đa 3 câu `APPROVED` của nút làm ví dụ (stem + phương án + lời giải), các `error_kid`/`error_note` đã gặp ở nút.
- Tin nhắn người dùng: "Soạn {batch-size} câu trắc nghiệm 4 phương án cho nút {code}, mỗi phương án nhiễu kèm error_kid nếu phù hợp".
- Đầu ra bắt buộc:

```json
{ "questions": [ {
    "stem": "他昨天___北京。",
    "options": [ {"key":"A","text":"去了","isCorrect":true},
                 {"key":"B","text":"去过了","isCorrect":false,"errorKid":"NP2-007","errorNote":"Nhầm 了 với 过"},
                 {"key":"C","text":"去着","isCorrect":false},
                 {"key":"D","text":"在去","isCorrect":false} ],
    "explanation": "Hành động đã xong ở quá khứ dùng 了 sau động từ.",
    "difficulty": 2 } ] }
```

(Ví dụ tự soạn, không lấy từ đề HSK.)

---

## 1 · Context & Goal

| Mã | Chỉ số | Mốc |
|---|---|---|
| **SC-001** | Với dữ liệu demo đủ câu sai, một lần chạy tạo ≥ 1 lô, câu hiện ở SCR-058 | ✔ |
| **SC-002** | Câu `PENDING_REVIEW`/`REJECTED` không bao giờ tới người học (luyện theo nút, ôn, quiz, thi) | 0 |
| **SC-003** | Không sinh lô mới cho KID đang có câu AI chờ duyệt | 0 lô trùng |
| **SC-004** | Job tắt khi `system-user-id = 0`; không gọi Claude | 0 lời gọi |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | Là hệ thống, mỗi đêm tôi tìm nút KID nhiều người làm sai mà kho câu còn mỏng, soạn thêm câu bằng AI từ dữ liệu của chính nút đó và đưa vào hàng chờ duyệt |
| US-2 | Là `TEACHER`/`CONTENT_EDITOR`, tôi xem hàng chờ câu AI theo KID/cấp/ngày, mở từng câu (SCR-059) để sửa nội dung, đáp án, lời giải, KID, rồi duyệt hoặc từ chối có lý do; duyệt hàng loạt các câu tôi đã mở |
| US-3 | Là người học, tôi tự nhận câu mới đã duyệt trong luyện theo nút, ôn và quiz mà không cần thao tác gì |
| US-4 | Là `ADMIN`, tôi bật job bằng cách chọn tài khoản đứng tên ở 078, xem lịch sử lô (thành công/lỗi, token) và chạy thử một lần |

### Edge cases

| # | Tình huống | Hành vi |
|---|---|---|
| E1 | Hai người duyệt cùng một câu | Người sau → **409** `QUESTION_NOT_PENDING` (BR-108-6) |
| E2 | Claude trả JSON hỏng/thiếu trường | Lô `FAILED`, `error_message='INVALID_OUTPUT'`, không lưu câu nào |
| E3 | Hết ngân sách token tháng (015) giữa lần chạy | Lô hiện tại `FAILED` `BUDGET_EXCEEDED`, dừng các lô còn lại |
| E4 | Nút có `can_do` trống hoặc không có từ gắn nút | Bỏ qua nút, log `INFO` |
| E5 | Một câu trong lô trùng câu có sẵn hoặc dùng chữ vượt cấp | Bỏ câu đó (`dropped`), giữ các câu còn lại |
| E6 | `system-user-id` trỏ tới tài khoản không phải `ADMIN`/bị khoá | Job không chạy, log `WARN`; nút "Chạy thử" → **409** `AI_GENERATION_DISABLED` |
| E7 | Server tắt giữa chừng, lô kẹt `RUNNING` | Lần chạy sau đánh `FAILED` (`STALE`) các lô `RUNNING` quá 10 phút trước khi chọn lô mới (giải phóng `uq_ai_running`) |

---

## 3 · Functional Requirements

### Nhóm 1 — Chọn KID (FR-001…FR-004) · UC-048

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `AiGenerationJob` (cron `cnhsk.ai-generate.cron`) SHALL chạy dưới khoá tư vấn `ai-generate:job`; `system-user-id = 0` → thoát ngay không gọi gì (SC-004); tài khoản không tồn tại/không `ADMIN`/bị khoá (`AuthApi`) → thoát + `WARN` (E6) |
| **FR-002** | Đếm lượt sai trong `window-days` theo KID: (a) KID `PRIMARY` của câu ở `attempt_items` có `correct=false`; (b) `error_kid` của phương án người học đã chọn (đọc `options` trong Java, 010 AC-09). Mỗi lượt sai cộng 1 cho (a) và 1 cho (b) nếu có. Một truy vấn gom + xử lý Java, không N+1 |
| **FR-003** | Ứng viên = KID có `wrongCount ≥ min-wrong` **và** số câu `APPROVED` có KID đó là `PRIMARY` < `min-bank` **và** không có câu `source='AI'` `PENDING_REVIEW` gắn KID đó (SC-003) **và** không có lô `RUNNING` cho KID đó; bỏ nút thiếu `can_do`/không có từ gắn nút (E4). Sắp `wrongCount` giảm, lấy tối đa `max-batches-per-run`. Các nút yếu từ `MasteryReader` (011) được cộng thêm `min-wrong` để ưu tiên khi số lượt sai bằng nhau |
| **FR-004** | Trước khi chọn, đánh `FAILED` (`error_message='STALE'`) mọi lô `AI_GENERATE` `RUNNING` có `created_at < now − 10 phút` (E7) |

### Nhóm 2 — Sinh và kiểm (FR-005…FR-010) · UC-049

| Mã | Yêu cầu |
|---|---|
| **FR-005** | Với từng ứng viên, tuần tự: chèn `service_requests` `AI_GENERATE` `RUNNING`, `user_id = system-user-id`, `payload` §0.2 (transaction ngắn); dựng prompt §0.3 từ DB; gọi `ClaudeClient.complete(generate-model, …, maxTokens 4 000, timeout)` qua `TokenBudget.execute` **ngoài transaction** |
| **FR-006** | Phân tích JSON đầu ra; hỏng → lô `FAILED` `INVALID_OUTPUT` (E2); ngân sách → `FAILED` `BUDGET_EXCEEDED` và dừng lần chạy (E3); timeout/lỗi mạng → `FAILED` `PROVIDER_ERROR`, `retry_count+1` (lần chạy sau có thể chọn lại KID) |
| **FR-007** | `AiQuestionValidator` SHALL bỏ câu (ghi `dropped{index, reason}`) khi: `stem` không 1–500 ký tự; không đúng 4 phương án khoá `A`–`D` khác nhau, `text` 1–200 khác nhau; không đúng 1 `isCorrect`; `explanation` không 1–1 000; `difficulty` ngoài 1–3; `errorKid` có nhưng không tồn tại trong `knowledge_points`; `stem` (chuẩn hoá khoảng trắng) trùng câu có sẵn cùng KID hoặc trùng câu khác trong lô; có chữ Hán trong `stem`/phương án mà không có mục `CHAR` nào cấp ≤ cấp nút (E5) |
| **FR-008** | Trong **một transaction**: chèn câu hợp lệ `questions` (`qtype='MULTIPLE_CHOICE'`, `source='AI'`, `status='PENDING_REVIEW'`, `hsk_level` = cấp nút, `options` = `[{key, text, is_correct, error_kid?, error_note?}]`, `correct_key`, `explanation`, `difficulty`, `reusable=true`, `created_by_request_id` = id lô, `code = null`) + `question_knowledge_points` `PRIMARY` = KID; lô `COMPLETED`, `result` §0.2, `completed_at`. 0 câu hợp lệ → `FAILED` `NO_VALID_QUESTION` |
| **FR-009** | Log mỗi lần chạy `INFO {candidates, batches, completed, failed, questions, inputTokens, outputTokens}`; không log prompt, nội dung câu |
| **FR-010** | Test SHALL chỉ dùng `StubClaudeClient` + `Clock` cố định; không gọi Claude thật trong `verify` |

### Nhóm 3 — Duyệt (FR-011…FR-017) · UC-108, UC-109 · SCR-058

| Mã | Yêu cầu |
|---|---|
| **FR-011** | `GET /api/admin/ai-questions?kid&hsk&from&to&page&size` (`TEACHER`, `CONTENT_EDITOR`) SHALL trả câu `source='AI' AND status='PENDING_REVIEW'` sắp `created_at` tăng (`ix_q_review`): `{id, stemSummary (80 ký tự), hskLevel, labels:[{kid, role}], generatedAt, runId, viewedByMe}` |
| **FR-012** | Mở chi tiết `GET /api/admin/questions/{id}` (010) cho câu AI chờ duyệt SHALL ghi dấu "đã xem" `ai-review:viewed:{userId}:{questionId}` (Redis, 24 giờ) — phục vụ BR-108-7; Redis lỗi → không ghi, chỉ ảnh hưởng duyệt hàng loạt |
| **FR-013** | Sửa câu AI chờ duyệt dùng `PUT /api/admin/questions/{id}` (010) — mở thêm cho `CONTENT_EDITOR` **chỉ** khi câu `source='AI' AND status='PENDING_REVIEW'` (BR-109-1); câu khác giữ quyền như 010 (`TEACHER`). Luật kiểm/audit của 010 giữ nguyên |
| **FR-014** | `POST /api/admin/ai-questions/{id}/approve` SHALL kiểm (BR-108-8): có `stem`, `explanation`, đúng 1 KID `PRIMARY`, `MULTIPLE_CHOICE` có đúng 1 phương án đúng khớp `correct_key` → sai → **422** `MALFORMED_QUESTION` `details`; rồi `UPDATE … SET status='APPROVED', reviewed_by, reviewed_at=now() WHERE id=? AND source='AI' AND status='PENDING_REVIEW'` (0 dòng → **409** `QUESTION_NOT_PENDING`); `audit_logs` `AI_QUESTION_APPROVE`; trả **200** |
| **FR-015** | `POST /api/admin/ai-questions/{id}/reject` `{reason* (1–1 000)}` SHALL `UPDATE … SET status='REJECTED', review_reason, reviewed_by, reviewed_at WHERE … status='PENDING_REVIEW'` (0 dòng → 409); `AI_QUESTION_REJECT`; **200** |
| **FR-016** | `POST /api/admin/ai-questions/bulk-approve` `{ids (1–50)}` SHALL xử lý từng id riêng (mỗi id một transaction): chưa có dấu "đã xem" của người gọi → `NOT_VIEWED` (BR-108-7); còn lại như FR-014 → `APPROVED` / `NOT_PENDING` / `MALFORMED`; trả **200** `{results:[{id, outcome}]}` (kết quả trộn, SCR-058) |
| **FR-017** | Câu `PENDING_REVIEW`/`REJECTED` SHALL không được phát ở bất kỳ engine người học nào (BR-108-3): AT kiểm luyện theo nút (012), ôn đến hạn (011), quiz (019), thi thử (012) đều lọc `status='APPROVED'`. Không đổi engine; nếu engine nào chưa lọc → dừng, báo leader |

### Nhóm 4 — Quản trị lô (FR-018…FR-020)

| Mã | Yêu cầu |
|---|---|
| **FR-018** | `GET /api/admin/ai-generation-runs?status&page&size` (`ADMIN`) SHALL trả lô `AI_GENERATE` mới nhất trước `{id, kid, status, createdAt, completedAt, questionCount, droppedCount, inputTokens, outputTokens, error}`; kèm `enabled` (system user hợp lệ) và tổng token tháng của job |
| **FR-019** | `POST /api/admin/ai-generation-runs/trigger` (`ADMIN`) SHALL chạy một lần FR-001…FR-008 bất đồng bộ → **202** `{started: true}`; job đang chạy (khoá tư vấn bận) → **409** `AI_GENERATION_BUSY`; tắt/tài khoản không hợp lệ → **409** `AI_GENERATION_DISABLED` |
| **FR-020** | SCR-058 `/content/ai-review` (`['TEACHER','CONTENT_EDITOR']`, layout `admin`): bảng câu chờ duyệt (tóm tắt, cấp, KID, thời gian chờ, nguồn AI, đã xem), lọc KID/cấp/ngày, mở câu sang SCR-059 (nút Duyệt/Từ chối có lý do trong SCR-059 khi câu là AI chờ duyệt), ô chọn chỉ bật cho câu đã xem, "Duyệt các câu đã chọn" hiện kết quả từng câu; 409 → tải lại. Mục "Lô AI" trong 078 hoặc trang quản trị `ADMIN` hiển thị FR-018 + nút "Chạy thử" |

---

## 4 · Data Model

Không migration. Ví dụ một lô:

```json
{ "id": 310, "kind": "AI_GENERATE", "status": "COMPLETED", "user_id": 1,
  "payload": { "kid": "NP2-004", "knowledgePointId": 88, "model": "claude-sonnet-5-5", "wrongCount": 37, "bankCount": 6 },
  "result": { "questionIds": [9001, 9002, 9003, 9004], "dropped": [ {"index": 2, "reason": "HANZI_ABOVE_LEVEL"} ],
              "inputTokens": 3120, "outputTokens": 1480 } }
```

---

## 5 · Error Matrix

| Mã lỗi | HTTP | Khi | FR |
|---|---|---|---|
| `QUESTION_NOT_PENDING` *(mới)* | 409 | Duyệt/từ chối câu không còn chờ duyệt hoặc không phải câu AI | FR-014, FR-015 |
| `MALFORMED_QUESTION` *(mới)* | 422 | Câu thiếu điều kiện duyệt (`details`) | FR-014 |
| `QUESTION_NOT_FOUND` *(010)* | 404 | Câu không có | FR-014, FR-015 |
| `AI_GENERATION_DISABLED` *(mới)* | 409 | Chạy thử khi job tắt/tài khoản không hợp lệ | FR-019 |
| `AI_GENERATION_BUSY` *(mới)* | 409 | Chạy thử khi đang chạy | FR-019 |
| `VALIDATION_FAILED` | 422 | Lý do/danh sách id/tham số sai | FR-011, FR-015, FR-016 |
| `FORBIDDEN` | 403 | Sai role (vd `ADMIN` duyệt câu — BR-108-1) | mọi endpoint |

Lỗi gọi Claude chỉ ghi `service_requests.error_message`, không trả cho người dùng. Mã mới ngay trước `INTERNAL_ERROR` theo thứ tự trên `main` lúc làm.

---

## 6 · Architectural Constraints

| Ràng buộc | Tuân thủ |
|---|---|
| Bản quyền | Prompt chỉ dựng từ dữ liệu trong DB (đã nhập qua 010); không PDF/đề HSK trong repo |
| Chi phí | Không `EntitlementApi`; `TokenBudget` (015) + `max-batches-per-run` + job tắt mặc định |
| Quyền | `SecurityConfig`: `/api/admin/ai-questions/**` → `TEACHER`, `CONTENT_EDITOR`; `/api/admin/ai-generation-runs/**` → `ADMIN`; `PUT /api/admin/questions/{id}` mở thêm `CONTENT_EDITOR` + kiểm ở service (FR-013) — **báo người giữ 010** |
| Gọi ngoài | Gọi Claude ngoài transaction; ghi DB trước/sau bằng transaction ngắn |

| Lớp | Trách nhiệm |
|---|---|
| `learning.aigen.WeakKnowledgeSelector` | FR-002…FR-004 |
| `learning.aigen.AiPromptBuilder` | §0.3 |
| `learning.aigen.AiQuestionValidator` | FR-007 |
| `learning.aigen.AiGenerationJob` / `AiGenerationService` | FR-001, FR-005, FR-006, FR-008, FR-009, FR-019 |
| `learning.aigen.AiReviewService` + `AiReviewController` | FR-011…FR-016 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Chọn KID trên 100 000 `attempt_items` trong cửa sổ | < 3 s |
| `NFR-P02` | Hàng chờ 500 câu | < 300 ms p95 |
| `NFR-C01` | Token mỗi lô | ≤ 4 000 đầu ra; tổng chung ngân sách tháng 015 |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | Không |
| Dữ liệu người học gửi ra ngoài | Không (§0.3) |
| Câu chưa duyệt tới người học | Không (FR-017, SC-002) |
| Gọi Claude thật trong test | Không (FR-010) |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Tài khoản đứng tên lô = khoá `ai-generate.system-user-id` (078), mặc định 0 = job tắt; phải là `ADMIN` hợp lệ | ✅ Leader chốt 2026-11-14 |
| Q2 | Ngưỡng: 02:00 hằng ngày, 14 ngày, ≥ 20 lượt sai, < 10 câu `APPROVED`, lô 5 câu trắc nghiệm 4 phương án, ≤ 3 lô/lần, model Sonnet | ✅ Leader chốt 2026-11-14 |
| Q3 | Người duyệt/sửa: `TEACHER` hoặc `CONTENT_EDITOR`; `ADMIN` không (BR-108-1) | ✅ Theo UC-108/109 |
| Q4 | Duyệt hàng loạt chỉ câu người đó đã mở (dấu Redis 24 giờ) | ✅ Theo BR-108-7 |
| Q5 | Chỉ sinh trắc nghiệm chữ; không audio/hình | ✅ Chốt |
| Q6 | Không thu phí, không `EntitlementApi` | ✅ Leader validate 2026-11-13 |

---

## 10 · Móc nối

- 010: kho câu, SCR-059, `/api/admin/questions` (FR-012, FR-013).
- 011: `MasteryReader` cho ưu tiên (FR-003); câu `APPROVED` vào ôn và tính mastery bình thường (UC-050).
- 012 `TARGETED_PRACTICE`, 019 quiz: chỉ lấy câu `APPROVED` — câu AI đã duyệt tự xuất hiện.
- 015: `ClaudeClient`, `TokenBudget`; 023: `SettingKey` nhóm "AI sinh câu".

---

## 11 · Rủi ro đã chấp nhận

| Rủi ro | Vì sao chấp nhận |
|---|---|
| Chất lượng câu AI thấp | Validator + duyệt người bắt buộc; tỷ lệ bỏ câu/từ chối xem ở FR-018 |
| Job dùng hết ngân sách token của trợ lý | Tắt mặc định; ≤ 3 lô/lần; `ADMIN` chỉnh ở 078 |
| Dữ liệu demo ít câu sai → không có ứng viên | `ADMIN` hạ `min-wrong` ở 078 để demo |
| Dấu "đã xem" mất khi Redis tắt | Chỉ chặn duyệt hàng loạt; duyệt từng câu vẫn được |

---

## 12 · Màn hình

| SCR | Route | Guard · layout | API |
|---|---|---|---|
| SCR-058 | `/content/ai-review` | `['TEACHER','CONTENT_EDITOR']` · `admin` | FR-011, FR-016 |
| SCR-059 (010) | có sẵn | thêm `CONTENT_EDITOR` cho câu AI chờ duyệt | `/api/admin/questions/{id}`, FR-014, FR-015 |
| Lô AI | `/admin/ai-runs` | `['ADMIN']` · `admin` | FR-018, FR-019 |

---

## 13 · API Contract

[`contracts/openapi-ai-practice.yaml`](contracts/openapi-ai-practice.yaml) (chỉ quản trị; người học dùng endpoint có sẵn của 011/012/019).

| Method · Path | Thành công | Lỗi |
|---|---|---|
| `GET /api/admin/ai-questions` | 200 `AiQuestionPage` | 401, 403, 422 |
| `POST /api/admin/ai-questions/{id}/approve` | 200 `ReviewResult` | 401, 403, 404, 409, 422 |
| `POST /api/admin/ai-questions/{id}/reject` | 200 `ReviewResult` | 401, 403, 404, 409, 422 |
| `POST /api/admin/ai-questions/bulk-approve` | 200 `BulkResult` | 401, 403, 422 |
| `GET /api/admin/ai-generation-runs` | 200 `RunPage` | 401, 403, 422 |
| `POST /api/admin/ai-generation-runs/trigger` | 202 `TriggerResult` | 401, 403, 409 |

---

## 14 · Acceptance Tests

| # | FR | G / W / T |
|---|---|---|
| AT-01 | FR-001, SC-004 | `system-user-id=0` · W chạy job · T stub không được gọi, không lô nào |
| AT-02 | FR-001, E6 | `system-user-id` là `LEARNER` · T không chạy, `WARN`; trigger · T 409 `AI_GENERATION_DISABLED` |
| AT-03 | FR-002, FR-003 | Dữ liệu: KID A 25 lượt sai + 4 câu, KID B 25 lượt sai + 12 câu, KID C 5 lượt sai, KID D 30 lượt sai nhưng có câu AI chờ duyệt · T chỉ A là ứng viên |
| AT-04 | FR-002 | Lượt sai chọn phương án có `error_kid=X` · T cả KID chính và X được cộng |
| AT-05 | FR-003, E4 | Nút thiếu `can_do` · T bị bỏ, log `INFO` |
| AT-06 | FR-004, E7 | Lô `RUNNING` 11 phút trước · T thành `FAILED` `STALE` trước khi chọn |
| AT-07 | FR-005, FR-008 | Stub trả 5 câu hợp lệ · T 5 câu `source='AI'`, `PENDING_REVIEW`, KID `PRIMARY`, `created_by_request_id`, lô `COMPLETED` có token |
| AT-08 | FR-006, E2, E3 | Stub trả chuỗi không phải JSON · T `FAILED INVALID_OUTPUT`, 0 câu; ngân sách hết · T `FAILED BUDGET_EXCEEDED`, lô sau không chạy |
| AT-09 | FR-007, E5 | Lô có câu 2 đáp án đúng, câu trùng stem có sẵn, câu chữ vượt cấp, `errorKid` không tồn tại · T các câu đó ở `dropped` đúng lý do, câu còn lại lưu |
| AT-10 | §0.3 | Prompt dựng ra không chứa id/tên/email người học; chứa `can_do`, `name_zh`, danh sách từ của nút |
| AT-11 | FR-011 | Hàng chờ chỉ có câu AI `PENDING_REVIEW`, cũ nhất trước; `ADMIN` gọi · T 403 |
| AT-12 | FR-014, E1 | Câu thiếu KID `PRIMARY` · T 422 `MALFORMED_QUESTION`; hợp lệ · T `APPROVED`, `reviewed_by`; hai người duyệt song song · T 200 + 409 |
| AT-13 | FR-015 | Từ chối không lý do · T 422; có lý do · T `REJECTED`, `review_reason` |
| AT-14 | FR-016, FR-012 | 3 câu: 1 đã xem hợp lệ, 1 chưa xem, 1 đã bị người khác duyệt · T `APPROVED`, `NOT_VIEWED`, `NOT_PENDING` |
| AT-15 | FR-013 | `CONTENT_EDITOR` sửa câu AI chờ duyệt · T 200; sửa câu `IMPORT` · T 403 (giữ luật 010) |
| AT-16 | FR-017, SC-002 | Kho có câu AI `PENDING_REVIEW`/`REJECTED` cùng KID · T luyện theo nút, ôn, quiz, thi thử không phát câu đó; sau khi duyệt · T luyện theo nút phát được |
| AT-17 | FR-018, FR-019 | Lịch sử lô đúng thứ tự, có token; trigger khi đang chạy · T 409 `AI_GENERATION_BUSY`; hợp lệ · T 202 |
| AT-18 | FR-020 (FE) | Vitest: ô chọn chỉ bật cho câu đã xem; kết quả trộn hiện từng câu; từ chối bắt buộc lý do; 409 tải lại |
| AT-19 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-ai-practice.yaml` (hai chiều path + method + status) |

---

## 15 · Gói việc

[`tasks.md`](tasks.md) · [`questions.md`](questions.md)
