# Feature Specification: AI sinh câu luyện theo điểm yếu + hàng chờ duyệt

**Feature:** `024-ai-practice-generation`
**Feature tree:** 3.3 AI tự sinh bài luyện theo điểm yếu · **Module:** `learning` (package `learning.aigen`)
**Use case:** UC-048, UC-049, UC-050 (use-cases-04) · UC-108, UC-109 (use-cases-08) · **Actor:** job hệ thống, `TEACHER`, `ADMIN`
**Màn hình:** SCR-058 AI Review Queue (+ SCR-059 Question Edit của 010 dùng chung)
**Scope:** V2 · **Priority:** P2

| Trường | Giá trị |
|---|---|
| **Version** | 1.6.0 |
| **Status** | v1.6.0 APPROVED — leader chốt 2026-11-25: duyệt câu AI hai bước, không đổi DB — `TEACHER` duyệt chuyên môn (giữ `PENDING_REVIEW`, ghi `reviewed_by`), rồi `CONTENT_EDITOR` khác người đó đưa vào hệ thống (`APPROVED`) hoặc từ chối (FR-011, FR-014…FR-016, Q3, AT-15, AT-30; Hiến pháp v1.8.0) · v1.5.0 APPROVED — leader duyệt 2026-11-25: đợt 2 dạng câu theo loại nút (trắc nghiệm + đúng/sai), lọc dạng câu, Luyện lỗi của tôi (FR-024…FR-029, AT-25…AT-29, Q10…Q12) · v1.4.0 APPROVED — leader duyệt 2026-11-25: chạy 2 giờ/lần trên lỗi 24 giờ, trần câu AI mỗi ngày chia theo tỉ lệ lỗi, ngân sách 5 triệu token, chống trùng ngữ nghĩa bằng SBERT nhúng trong BE; SBERT không sẵn sàng → tạm dừng sinh (§0.1, FR-003, FR-003b, FR-007, FR-007a, FR-018, FR-021…FR-023, AT-21…AT-24, Q2, Q7…Q9) · v1.3.0 APPROVED — leader duyệt 2026-11-14, được code theo `tasks.md` · v1.1.0 leader duyệt 2026-11-19: chống câu gần trùng (§0.3 chỉ dẫn không lặp mẫu, FR-007 `NEAR_DUPLICATE_STEM`, AT-09) · v1.2.0 leader chốt 2026-11-20: chỉ `TEACHER` duyệt và sửa câu hỏi (BUS-07, Hiến pháp v1.7.0); `CONTENT_EDITOR` chỉ xem, lưu trữ (FR-011, FR-013, FR-020, Q3, AT-15) · v1.3.0 leader chốt 2026-11-21: tìm nút gốc bằng PageRank cá nhân hoá ngược cạnh tiên quyết (FR-003a, AT-20) |
| **Phụ thuộc** | 010 (cây KID, `question_knowledge_points`, `/api/admin/questions`, SCR-059) · 011 (`MasteryReader`) · 015 (`ClaudeClient`, `TokenBudget`, `StubClaudeClient`) · 023 (`SettingsApi`, ⚿K7) · 001 (`AuthApi`, `AuditService`) |
| **Quyết định chung** | Leader validate 2026-11-13: hệ thống tự sinh từ câu sai + dữ liệu cây tri thức, không học viên bấm, không thu phí; phải duyệt và gắn KID trước khi tới học viên · leader chốt 2026-11-14 (§9) |
| **Người code** | Quốc Huy |

> **Người code đọc:** `AGENTS.md` → spec 010 §0 (cây KID, `options.error_kid`) → spec 015 §0.3 (`ClaudeClient`, ngân sách) → file này → `contracts/openapi-ai-practice.yaml` → `tasks.md`.

---

## 0 · Phạm vi

| Trong phạm vi | Ngoài phạm vi |
|---|---|
| Job định kỳ: gom câu sai → chọn KID yếu, ít câu → gọi Claude sinh lô câu trắc nghiệm → kiểm → lưu `PENDING_REVIEW` (UC-048, UC-049) | Học viên bấm "sinh bài cho tôi"; thu phí `PaidFeature.AI_GENERATE` |
| Hàng chờ duyệt SCR-058 chỉ cho `TEACHER`; duyệt, duyệt hàng loạt câu đã xem, từ chối có lý do (UC-108) | Dạng câu khác trắc nghiệm 4 phương án; câu có audio/hình |
| Sửa câu AI chờ duyệt qua SCR-059 (UC-109) | Đánh giá chất lượng prompt tự động |
| `ADMIN` xem lịch sử lô + token, chạy thử một lần | Đưa PDF Cây Năng Lực hay dữ liệu đề HSK vào repo/prompt |
| Câu đã duyệt tự vào kho chung (UC-050) | Sửa engine 012/019/011 |

**v1.4: một migration `V19__question_embeddings.sql` (bảng `learning.question_embeddings`, §4) — leader chạy trên Neon.** Ngoài bảng đó dùng `learning.service_requests` (`kind='AI_GENERATE'`, `uq_ai_running`), `learning.questions` (`source='AI'`, mặc định `PENDING_REVIEW`, `ck_q_ai_review`, `ck_q_reject`, `ix_q_review`), `question_knowledge_points` (`uq_qkp_primary`), `knowledge_points` (`code`, `name_zh`, `can_do`, `hsk_level`, `prereq_ids`), `lexemes.knowledge_point_id`.

### 0.1 · Khoá cấu hình (thêm vào `SettingKey` của 023, nhóm "AI sinh câu"; leader chốt 2026-11-14)

| Khoá | Mặc định | Khoảng | Ý nghĩa |
|---|---|---|---|
| `ai-generate.system-user-id` | **0** | 0–2 147 483 647 | Tài khoản `ADMIN` đứng tên lô; **0 = job tắt** (Q1) |
| `ai-generate.window-days` | **1** (v1.3: 14) | 1–90 | Xét câu sai trong N ngày (1 = 24 giờ vừa qua) |
| `ai-generate.min-wrong` | **3** (v1.3: 20) | 1–1 000 | Số lượt sai tối thiểu của một KID |
| `ai-generate.min-bank` | **200** (v1.3: 10) | 1–200 | Chỉ sinh khi KID có **ít hơn** N câu `APPROVED` |
| `ai-generate.batch-size` | 5 | 1–10 | Số câu tối đa mỗi lô (mỗi nút) |
| `ai-generate.max-batches-per-run` | **20** (v1.3: 3) | 0–20 | Số lô tối đa mỗi lần chạy |
| `ai-generate.daily-cap` *(v1.4)* | 100 | 0–500 | Số câu AI **đã lưu** tối đa mỗi ngày giờ VN; 0 = không sinh |
| `ai-generate.semantic-threshold` *(v1.4)* | ~~900~~ → 800 *(2026-11-25, đo trên model thật)* | 500–1 000 | Ngưỡng cosine × 1 000 để coi là trùng nghĩa (FR-007a) |
| `ai-generate.max-pending` *(2026-11-25)* | 500 | 0–10 000 | Số câu AI `PENDING_REVIEW` tối đa; đạt ngưỡng thì lần chạy không sinh thêm (hạn mức còn lại = min(trần ngày, chỗ trống hàng chờ)); 0 = không giới hạn |
| `ai.monthly-token-budget` *(015, đổi mặc định v1.4)* | **5 000 000** (cũ 2 000 000) | giữ | Ngân sách chung trợ lý + dịch + sinh câu |

Khoá kỹ thuật ở yaml (không ở 078): `cnhsk.ai-generate.cron` = **`0 0 */2 * * *`** (v1.4, cũ `0 0 2 * * *`; giờ VN); `cnhsk.embedding.*` (v1.4: `cache-dir` mặc định `${user.home}/.cnhsk/models`, `model-url`, `tokenizer-url`, `model-sha256`, `tokenizer-sha256`, `max-tokens` 128); `cnhsk.ai.generate-model` = `deepseek-chat` (2026-11-19, provider DeepSeek theo 015 v1.1; trước là `claude-sonnet-5-5`), `cnhsk.ai-generate.timeout` = `60s`. Nếu 023/T-01 chưa merge, đọc các khoá §0.1 từ yaml `cnhsk.ai-generate.*` với cùng mặc định; 023/T-04 chuyển sau.

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
- Khối hệ thống 2 (theo nút, `cache=true`): `code`, `title`, `name_zh`, `can_do`, cấp HSK, tiêu đề các nút tiên quyết, danh sách từ/chữ gắn nút (`lexemes.knowledge_point_id`, ≤ 80 mục: `text`, `pinyin`, nghĩa), tối đa 3 câu `APPROVED` của nút làm ví dụ (stem + phương án + lời giải), các `error_kid`/`error_note` đã gặp ở nút. Khối 1 có thêm chỉ dẫn (v1.1): không lặp/viết lại gần giống câu trong `samples`; các câu trong lô khác ngữ cảnh và khía cạnh kiểm tra. Không gửi thêm câu nào ngoài 3 mẫu — chặn trùng với toàn bộ ngân hàng do FR-007 đảm nhiệm.
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
| **SC-005** *(v1.4)* | Số câu AI lưu trong một ngày VN không vượt `daily-cap` | ≤ 100 |
| **SC-006** *(v1.4)* | Câu cùng nghĩa, cùng đáp án với câu đã có của nút không vào hàng chờ | 0 câu lọt khi cosine ≥ ngưỡng |

---

## 2 · User Stories

| # | Story |
|---|---|
| US-1 | Là hệ thống, mỗi đêm tôi tìm nút KID nhiều người làm sai mà kho câu còn mỏng, soạn thêm câu bằng AI từ dữ liệu của chính nút đó và đưa vào hàng chờ duyệt |
| US-2 | Là `TEACHER`, tôi xem hàng chờ câu AI theo KID/cấp/ngày, mở từng câu (SCR-059) để sửa nội dung, đáp án, lời giải, KID, rồi duyệt hoặc từ chối có lý do; duyệt hàng loạt các câu tôi đã mở |
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
| E8 *(v1.4)* | Đã đủ `daily-cap` câu trong ngày | Lần chạy không tạo lô, không gọi AI |
| E9 *(v1.4)* | SBERT không nạp được (thiếu mạng, SHA sai, lỗi native) | Lần chạy `PAUSED`, không tạo lô, không gọi AI, log `WARN`; BE vẫn chạy bình thường |
| E10 *(v1.4)* | SBERT lỗi giữa lô (sau khi đã gọi AI) | Lô `FAILED` `EMBEDDING_UNAVAILABLE`, không lưu câu nào, dừng lần chạy |

---

## 3 · Functional Requirements

### Nhóm 1 — Chọn KID (FR-001…FR-004) · UC-048

| Mã | Yêu cầu |
|---|---|
| **FR-001** | `AiGenerationJob` (cron `cnhsk.ai-generate.cron`) SHALL chạy dưới khoá tư vấn `ai-generate:job`; `system-user-id = 0` → thoát ngay không gọi gì (SC-004); tài khoản không tồn tại/không `ADMIN`/bị khoá (`AuthApi`) → thoát + `WARN` (E6) |
| **FR-002** | Đếm lượt sai trong `window-days` theo KID: (a) KID `PRIMARY` của câu ở `attempt_items` có `correct=false`; *(2026-11-25)* mục chấm theo từ/chữ/ngữ pháp (Từ vựng, Ngữ pháp, Chữ Hán, Ôn tập: `attempt_items.lexeme_id`, không có câu) sai thì cộng 1 cho nút của từ đó qua `lexemes.knowledge_point_id`, như cách 011 phân bổ bằng chứng; mục không về được nút (Phát âm, Nghe) không tính; (b) `error_kid` của phương án người học đã chọn (đọc `options` trong Java, 010 AC-09). Mỗi lượt sai cộng 1 cho (a) và 1 cho (b) nếu có. Một truy vấn gom + xử lý Java, không N+1 |
| **FR-003** | Ứng viên = KID có `wrongCount ≥ min-wrong` **và** số câu `APPROVED` có KID đó là `PRIMARY` < `min-bank` **và** không có câu `source='AI'` `PENDING_REVIEW` gắn KID đó (SC-003) **và** không có lô `RUNNING` cho KID đó; bỏ nút thiếu `can_do`/không có từ gắn nút (E4). Sắp `wrongCount` giảm, lấy tối đa `max-batches-per-run`. Các nút yếu từ `MasteryReader` (011) được cộng thêm `min-wrong` để ưu tiên khi số lượt sai bằng nhau |
| **FR-003a** | (v1.3) **Nút gốc.** Đọc toàn bộ cạnh `prereq_ids` (trọng số 1) và `support_ids` (trọng số 0,3) **một truy vấn**. Chạy PageRank cá nhân hoá trên đồ thị ngược (nút phụ thuộc → nút nó cần): hạt giống = `wrongCount` từng KID, hệ số 0,85, tối đa 50 vòng hoặc chênh lệch < 1e-6; khối lượng của nút không có cạnh đi ra quay về phân bố hạt giống. `rootScore` = điểm × tổng lượt sai, làm tròn. Ứng viên = KID có `wrongCount ≥ min-wrong` **hoặc** `rootScore ≥ min-wrong`, rồi qua đủ bộ lọc FR-003; sắp `rootScore` giảm, rồi `wrongCount` giảm, rồi số người học yếu, rồi KID. Không nút hạt giống nào có cạnh đi ra → `rootScore = wrongCount`, kết quả trùng v1.2 |
| **FR-003b** | (v1.4) **Hạn mức và phân bổ.** `remaining = daily-cap − số câu source='AI' đã lưu từ 00:00 giờ VN hôm nay` (câu bị `dropped` không có dòng nên không tính). `remaining ≤ 0` → không tạo lô (E8). Phân bổ `remaining` cho các ứng viên (đã sắp theo FR-003a, tối đa `max-batches-per-run`) theo tỉ lệ `wrongCount`, phương pháp phần dư lớn nhất: mỗi nút được chọn nhận 1…`batch-size` câu, phần vượt `batch-size` chia lại cho nút còn lại theo tỉ lệ; nếu `remaining` < số nút thì chỉ lấy `remaining` nút đầu, mỗi nút 1 câu. Lời nhắn người dùng §0.3 dùng số câu được phân bổ thay cho `batch-size`. Câu bị bỏ không sinh bù trong cùng lần chạy; lần chạy sau tự bù vì `remaining` chỉ trừ câu đã lưu. AI trả thừa câu so với số được phân bổ → các câu hợp lệ vượt số đó bị bỏ với lý do `OVER_QUOTA` (câu hỏng không chiếm suất). Chạy thử (FR-019) đi qua cùng hạn mức |
| **FR-004** | Trước khi chọn, đánh `FAILED` (`error_message='STALE'`) mọi lô `AI_GENERATE` `RUNNING` có `created_at < now − 10 phút` (E7) |

### Nhóm 2 — Sinh và kiểm (FR-005…FR-010) · UC-049

| Mã | Yêu cầu |
|---|---|
| **FR-005** | Với từng ứng viên, tuần tự: chèn `service_requests` `AI_GENERATE` `RUNNING`, `user_id = system-user-id`, `payload` §0.2 (transaction ngắn); dựng prompt §0.3 từ DB; gọi `ClaudeClient.complete(generate-model, …, maxTokens 4 000, timeout)` qua `TokenBudget.execute` **ngoài transaction** |
| **FR-006** | Phân tích JSON đầu ra; hỏng → lô `FAILED` `INVALID_OUTPUT` (E2); ngân sách → `FAILED` `BUDGET_EXCEEDED` và dừng lần chạy (E3); timeout/lỗi mạng → `FAILED` `PROVIDER_ERROR`, `retry_count+1` (lần chạy sau có thể chọn lại KID) |
| **FR-007** | `AiQuestionValidator` SHALL bỏ câu (ghi `dropped{index, reason}`) khi: `stem` không 1–500 ký tự; không đúng 4 phương án khoá `A`–`D` khác nhau, `text` 1–200 khác nhau; không đúng 1 `isCorrect`; `explanation` không 1–1 000; `difficulty` ngoài 1–3; `errorKid` có nhưng không tồn tại trong `knowledge_points`; `stem` (chuẩn hoá khoảng trắng) trùng câu có sẵn cùng KID hoặc trùng câu khác trong lô (`DUPLICATE_STEM`); `stem` gần trùng (`NEAR_DUPLICATE_STEM`) với câu có sẵn cùng KID (mọi trạng thái) hoặc câu đã nhận trước trong lô — sau khi bỏ dấu câu và khoảng trắng (giữ hoa/thường): bằng nhau; hoặc cùng ≥ 8 code point, cùng văn bản đáp án đúng (đã bỏ dấu câu/khoảng trắng), cùng dãy chữ số và Jaccard bigram code point ≥ 0,8; có chữ Hán trong `stem`/phương án mà không có mục `CHAR` nào cấp ≤ cấp nút (E5) |
| **FR-007a** | (v1.4) **Trùng nghĩa.** Sau mọi luật FR-007, với câu còn lại: tính vector SBERT của `stem`; bỏ câu (`SEMANTIC_DUPLICATE_STEM`) khi có câu so sánh với cosine ≥ `semantic-threshold / 1000` **và** cùng văn bản đáp án đúng (đã bỏ dấu câu/khoảng trắng) **và**, khi phần chữ Hán của một trong hai `stem` có ≤ 4 chữ, cùng dãy chữ Hán (chữ đang hỏi: "chữ 件 thuộc bộ nào?" khác "chữ 今 thuộc bộ nào?"; luật này áp cả cho câu gần giống của FR-007). Câu so sánh = câu cùng KID ở mọi trạng thái có vector của cùng model + câu đã nhận trước trong lô. Vector tính **ngoài** transaction |
| **FR-008** | Trong **một transaction**: chèn câu hợp lệ `questions` (`qtype='MULTIPLE_CHOICE'`, `source='AI'`, `status='PENDING_REVIEW'`, `hsk_level` = cấp nút, `options` = `[{key, text, is_correct, error_kid?, error_note?}]`, `correct_key`, `explanation`, `difficulty`, `reusable=true`, `created_by_request_id` = id lô, `code = null`) + `question_knowledge_points` `PRIMARY` = KID; lô `COMPLETED`, `result` §0.2, `completed_at`. 0 câu hợp lệ → `FAILED` `NO_VALID_QUESTION`. (v1.4) Cùng transaction ghi vector câu đã lưu vào `question_embeddings` |
| **FR-009** | Log mỗi lần chạy `INFO {candidates, remaining, batches, completed, failed, questions, inputTokens, outputTokens}`; không log prompt, nội dung câu |
| **FR-010** | Test SHALL chỉ dùng `StubClaudeClient` + `Clock` cố định; không gọi Claude thật trong `verify` |

### Nhóm 3 — Duyệt (FR-011…FR-017) · UC-108, UC-109 · SCR-058

| Mã | Yêu cầu |
|---|---|
| **FR-011** | `GET /api/admin/ai-questions?kid&hsk&from&to&page&size` (`TEACHER` hoặc `CONTENT_EDITOR`; `ADMIN` một mình → 403) SHALL trả câu `source='AI' AND status='PENDING_REVIEW'` sắp `created_at` tăng (`ix_q_review`), **chỉ ở bước của người gọi** (v1.6): `TEACHER` thấy câu `reviewed_by IS NULL` (bước giáo viên); `CONTENT_EDITOR` thấy câu `reviewed_by IS NOT NULL AND reviewed_by <> người gọi` (bước đưa vào hệ thống); có cả hai role thấy cả hai. Mỗi dòng `{id, stemSummary (80 ký tự), hskLevel, labels:[{kid, role}], generatedAt, runId, viewedByMe, step: TEACHER\|CONTENT}` |
| **FR-012** | Mở chi tiết `GET /api/admin/questions/{id}` (010) cho câu AI chờ duyệt SHALL ghi dấu "đã xem" `ai-review:viewed:{userId}:{questionId}` (Redis, 24 giờ) — phục vụ BR-108-7; Redis lỗi → không ghi, chỉ ảnh hưởng duyệt hàng loạt |
| **FR-013** | Sửa câu AI chờ duyệt dùng `PUT /api/admin/questions/{id}` (010) — **chỉ** `TEACHER` ở mọi trạng thái (BR-109-1); `CONTENT_EDITOR` bị 403 kể cả câu AI chờ duyệt. Luật kiểm/audit của 010 giữ nguyên |
| **FR-014** | `POST /api/admin/ai-questions/{id}/approve` SHALL kiểm (BR-108-8): có `stem`, `explanation`, đúng 1 KID `PRIMARY`, `MULTIPLE_CHOICE` có đúng 1 phương án đúng khớp `correct_key` → sai → **422** `MALFORMED_QUESTION` `details`; rồi theo bước (v1.6, không đổi DB): **bước giáo viên** (`reviewed_by IS NULL`, cần `TEACHER`) `UPDATE … SET reviewed_by, reviewed_at=now()` giữ `status='PENDING_REVIEW'`, audit `AI_QUESTION_TEACHER_APPROVE`, trả `status: TEACHER_APPROVED`; **bước đưa vào hệ thống** (`reviewed_by` đã có, cần `CONTENT_EDITOR` khác người đó) `UPDATE … SET status='APPROVED', reviewed_by, reviewed_at=now()`, audit `AI_QUESTION_APPROVE`, trả `APPROVED`. Cả hai `WHERE id=? AND source='AI' AND status='PENDING_REVIEW' AND reviewed_by IS NOT DISTINCT FROM <giá trị đã khoá>` (0 dòng → **409** `QUESTION_NOT_PENDING`). Sai bước: không có role của bước → **403**; giáo viên gặp câu đã qua bước giáo viên → **409**; người đã duyệt chuyên môn tự đưa vào hệ thống → **403**. Trả **200** |
| **FR-015** | `POST /api/admin/ai-questions/{id}/reject` `{reason* (1–1 000)}` SHALL theo đúng luật bước của FR-014 (giáo viên từ chối ở bước giáo viên, `CONTENT_EDITOR` ở bước đưa vào hệ thống) rồi `UPDATE … SET status='REJECTED', review_reason, reviewed_by, reviewed_at WHERE … status='PENDING_REVIEW'` (0 dòng → 409); `AI_QUESTION_REJECT`; **200** |
| **FR-016** | `POST /api/admin/ai-questions/bulk-approve` `{ids (1–50)}` SHALL xử lý từng id riêng (mỗi id một transaction): chưa có dấu "đã xem" của người gọi → `NOT_VIEWED` (BR-108-7) — trừ bước đưa vào hệ thống: `CONTENT_EDITOR` duyệt câu giáo viên đã duyệt chuyên môn không cần mở trước (leader chốt 2026-11-26); còn lại như FR-014 → `TEACHER_APPROVED` / `APPROVED` / `NOT_PENDING` (gồm câu đang ở bước của role khác) / `MALFORMED`; trả **200** `{results:[{id, outcome}]}` (kết quả trộn, SCR-058) |
| **FR-017** | Câu `PENDING_REVIEW`/`REJECTED` SHALL không được phát ở bất kỳ engine người học nào (BR-108-3): AT kiểm luyện theo nút (012), ôn đến hạn (011), quiz (019), thi thử (012) đều lọc `status='APPROVED'`. Không đổi engine; nếu engine nào chưa lọc → dừng, báo leader |

### Nhóm 4 — Quản trị lô (FR-018…FR-020)

| Mã | Yêu cầu |
|---|---|
| **FR-018** | `GET /api/admin/ai-generation-runs?status&page&size` (`ADMIN`) SHALL trả lô `AI_GENERATE` mới nhất trước `{id, kid, status, createdAt, completedAt, questionCount, droppedCount, inputTokens, outputTokens, error}`; kèm `enabled` (system user hợp lệ), (v1.4) `embeddingReady` (SBERT đã nạp được) và tổng token tháng của job |
| **FR-019** | `POST /api/admin/ai-generation-runs/trigger` (`ADMIN`) SHALL chạy một lần FR-001…FR-008 bất đồng bộ → **202** `{started: true}`; job đang chạy (khoá tư vấn bận) → **409** `AI_GENERATION_BUSY`; tắt/tài khoản không hợp lệ → **409** `AI_GENERATION_DISABLED` |
| **FR-020** | SCR-058 `/content/ai-review` (`['TEACHER']`, layout `admin`): bảng câu chờ duyệt (tóm tắt, cấp, KID, thời gian chờ, nguồn AI, đã xem), lọc KID/cấp/ngày, mở câu sang SCR-059 (nút Duyệt/Từ chối có lý do trong SCR-059 khi câu là AI chờ duyệt), ô chọn chỉ bật cho câu đã xem, "Duyệt các câu đã chọn" hiện kết quả từng câu; 409 → tải lại. Mục "Lô AI" trong 078 hoặc trang quản trị `ADMIN` hiển thị FR-018 + nút "Chạy thử" |

---

### Nhóm 5 — SBERT (FR-021…FR-023) · v1.4

| Mã | Yêu cầu |
|---|---|
| **FR-021** | `EmbeddingEngine` chạy trong BE: model `paraphrase-multilingual-MiniLM-L12-v2` bản ONNX int8 (Apache 2.0) qua ONNX Runtime + tokenizer HuggingFace (DJL); mean pooling theo attention mask, chuẩn hoá L2, vector 384 chiều. Nạp lười lần đầu job cần (không chặn khởi động BE, test module khác không tải model). File thiếu → tải từ URL cấu hình vào `cache-dir`, kiểm SHA-256; sai → xoá file, coi như không sẵn sàng. Thử nạp lại tối đa 1 lần mỗi lần chạy. File model không commit |
| **FR-022** | Đầu lần chạy (sau khoá tư vấn, trước khi tạo lô): engine không sẵn sàng → kết quả `PAUSED`, không tạo lô, không gọi AI (E9); lỗi giữa lô → E10 |
| **FR-023** | Đầu lần chạy, khi engine sẵn sàng: tính bù vector cho tối đa 500 câu chưa có vector của model hiện tại (ưu tiên KID ứng viên), mỗi lô ghi transaction ngắn. Đổi model → câu có vector model khác coi như thiếu |

### Nhóm 6 — Đợt 2: dạng câu theo loại nút, Luyện lỗi của tôi (FR-024…FR-029) · v1.5 APPROVED 2026-11-25

Ràng buộc nền (đã kiểm code 2026-11-25): engine người học (`QuestionItemFactory`, quiz) chỉ phát `MULTIPLE_CHOICE` và `TRUE_FALSE`. Câu `FILL_BLANK`/`SENTENCE_ORDER` nạp được nhưng không tới học viên → **ngoài phạm vi v1.5**, làm thành tính năng riêng sau (leader chốt 2026-11-25).

| Mã | Yêu cầu |
|---|---|
| **FR-024** | Prompt §0.3 thêm khối "dạng câu theo loại nút" chọn theo tiền tố KID: `NP` chọn từ/cấu trúc đúng điền vào chỗ `___` (4 phương án) · `TV` chọn nghĩa tiếng Việt hoặc từ đúng ngữ cảnh · `CH` chọn chữ đúng theo âm/nghĩa, hoặc câu đúng/sai về chữ · `NA` chọn pinyin/thanh điệu đúng (chỉ chữ, không audio) · `NL` đọc đoạn hội thoại ≤ 4 lượt rồi chọn đáp án. Tiền tố khác → như v1.4 |
| **FR-025** | Đầu ra cho phép thêm trường `"type": "MULTIPLE_CHOICE" \| "TRUE_FALSE"` (thiếu = `MULTIPLE_CHOICE`). `TRUE_FALSE`: đúng 2 phương án khoá `A` (Đúng) và `B` (Sai), đúng 1 `isCorrect`, không `errorKid` ở phương án đúng; mọi luật FR-007/FR-007a khác giữ nguyên. Sai cấu trúc → `INVALID_OPTIONS`. Câu lưu với `qtype` tương ứng; duyệt FR-014 chấp nhận cả `TRUE_FALSE` với đúng 1 phương án đúng khớp `correct_key` |
| **FR-026** | Mỗi lô ≤ 1/3 số câu là `TRUE_FALSE` (làm tròn xuống); câu `TRUE_FALSE` vượt tỉ lệ → `dropped` `TYPE_QUOTA` |
| **FR-027** | `GET /api/admin/ai-questions` thêm tham số `qtype` (`MULTIPLE_CHOICE`/`TRUE_FALSE`); mỗi dòng trả thêm `qtype`. SCR-058 thêm ô lọc "Dạng câu" |
| **FR-028** | `GET /api/mistake-practice` → `{kids:[{kid, nameVi, wrongCount}]}` (≤ 20 nút) và `POST /api/mistake-practice/sessions` (chỉ `LEARNER`; path riêng để không đụng hợp đồng 012 đã khoá) SHALL mở phiên **Luyện lỗi của tôi**: lấy các KID mà chính người gọi làm sai trong 7 ngày (giờ VN) — KID `PRIMARY` của câu, hoặc *(2026-11-25)* nút của từ/chữ/ngữ pháp làm sai ở các mục học theo cùng luật FR-002 — sắp theo số lượt sai giảm; chọn tối đa 10 câu `APPROVED` phát được (`QuestionItemFactory.isUsable`) của các KID đó, **ưu tiên câu người gọi chưa từng làm**, rồi câu AI duyệt gần nhất, chia đều giữa các KID; dùng nguồn `TARGETED_PRACTICE` (cùng lượt miễn phí/gói và cùng cách tính mức thành thạo như Luyện theo nút); `meta = {mode: "MISTAKES", kids: [...]}`. Không có lỗi trong 7 ngày hoặc không có câu phát được → **200** `{session: null, reason: "NO_RECENT_MISTAKES" \| "NO_QUESTIONS"}` |
| **FR-029** | Trang học tập (SCR-010) thêm mục "Luyện lỗi của tôi": hiện số nút sai trong 7 ngày; bấm → mở phiên FR-028 trong màn luyện có sẵn; rỗng → thông báo "Bạn chưa có lỗi nào trong 7 ngày qua" |

AT bổ sung: **AT-25** (FR-024) prompt nút `NP…` chứa chỉ dẫn điền `___`, nút `CH…` chứa chỉ dẫn chữ; không chứa dữ liệu người học · **AT-26** (FR-025, FR-026) lô 3 câu: 1 `TRUE_FALSE` hợp lệ lưu `qtype='TRUE_FALSE'`, 1 `TRUE_FALSE` có 3 phương án → `INVALID_OPTIONS`; lô 3 câu có 2 `TRUE_FALSE` hợp lệ → câu thứ 2 `TYPE_QUOTA`; câu `TRUE_FALSE` đã duyệt phát được ở Luyện theo nút · **AT-27** (FR-027) lọc `qtype=TRUE_FALSE` chỉ trả câu đúng/sai · **AT-28** (FR-028) học viên sai nút A (3 lượt), B (1 lượt) trong 7 ngày và nút C 10 ngày trước · T phiên chỉ có câu của A, B, câu chưa làm đứng trước; học viên khác không ảnh hưởng; không lỗi · T `NO_RECENT_MISTAKES`; phiên trừ lượt như Luyện theo nút · **AT-29** (FR-029, FE) mục hiện số nút, bấm mở phiên, rỗng hiện thông báo.

## 4 · Data Model

v1.4 thêm một bảng (migration `V19__question_embeddings.sql`, leader chạy trên Neon):

```sql
CREATE TABLE learning.question_embeddings (
    question_id bigint PRIMARY KEY REFERENCES learning.questions(id) ON DELETE CASCADE,
    model       varchar(80) NOT NULL,
    vector      real[]      NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
);
```

Không dùng pgvector: so sánh trong Java theo KID (vài trăm câu). Ví dụ một lô:

```json
{ "id": 310, "kind": "AI_GENERATE", "status": "COMPLETED", "user_id": 1,
  "payload": { "kid": "NP2-004", "knowledgePointId": 88, "model": "deepseek-chat", "wrongCount": 37, "bankCount": 6 },
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
| Chi phí | Không `EntitlementApi`; `TokenBudget` (015) + `daily-cap` + `max-batches-per-run` + job tắt mặc định; SBERT chạy tại chỗ, không tốn token |
| Bộ nhớ | SBERT +~300–500 MB RAM cho BE; file model ~120 MB ngoài repo |
| Quyền | `SecurityConfig`: `/api/admin/ai-questions/**` → `TEACHER`; `/api/admin/ai-generation-runs/**` → `ADMIN`; `PUT /api/admin/questions/{id}` → `TEACHER` + kiểm ở service (FR-013, v1.2.0) |
| Gọi ngoài | Gọi Claude ngoài transaction; ghi DB trước/sau bằng transaction ngắn |

| Lớp | Trách nhiệm |
|---|---|
| `learning.aigen.WeakKnowledgeSelector` | FR-002…FR-004 |
| `learning.aigen.AiPromptBuilder` | §0.3 |
| `learning.aigen.AiQuestionValidator` | FR-007 |
| `learning.aigen.AiGenerationJob` / `AiGenerationService` | FR-001, FR-005, FR-006, FR-008, FR-009, FR-019 |
| `learning.aigen.AiReviewService` + `AiReviewController` | FR-011…FR-016 |
| `learning.aigen.AiGenerationQuota` *(v1.4)* | FR-003b |
| `learning.aigen.embedding.*` *(v1.4)* | FR-007a, FR-021…FR-023 |

---

## 7 · Non-Functional Requirements

| Mã | Yêu cầu | Mốc |
|---|---|---|
| `NFR-P01` | Chọn KID trên 100 000 `attempt_items` trong cửa sổ | < 3 s |
| `NFR-P02` | Hàng chờ 500 câu | < 300 ms p95 |
| `NFR-C01` | Token mỗi lô | ≤ 4 000 đầu ra; tổng chung ngân sách tháng 015 |
| `NFR-P03` *(v1.4)* | SBERT tính vector 100 câu (CPU) | < 5 s |

---

## 8 · Constitution Check

| Kiểm | Kết quả |
|---|---|
| Đổi schema | v1.4: thêm bảng `question_embeddings` (V19, leader chạy Neon) |
| Dữ liệu người học gửi ra ngoài | Không (§0.3) |
| Câu chưa duyệt tới người học | Không (FR-017, SC-002) |
| Gọi Claude thật trong test | Không (FR-010) |

---

## 9 · Quyết định

| # | Nội dung | Trạng thái |
|---|---|---|
| Q1 | Tài khoản đứng tên lô = khoá `ai-generate.system-user-id` (078), mặc định 0 = job tắt; phải là `ADMIN` hợp lệ | ✅ Leader chốt 2026-11-14 |
| Q2 | Ngưỡng: ~~02:00 hằng ngày, 14 ngày, ≥ 20 lượt sai, < 10 câu `APPROVED`, ≤ 3 lô/lần~~ → v1.4: 2 giờ/lần, 24 giờ, ≥ 3 lượt sai, < 200 câu `APPROVED`, ≤ 20 lô/lần, ≤ 100 câu/ngày; lô ≤ 5 câu trắc nghiệm 4 phương án; model theo `CNHSK_AI_GENERATEMODEL` (chung cổng với trợ lý) | ✅ Leader chốt 2026-11-14 · v1.4 2026-11-25 |
| Q7 | Chống trùng: giữ luật chữ + thêm SBERT nhúng BE (không dịch vụ Python riêng, không prompt dài hơn) | ✅ Leader chốt 2026-11-25 |
| Q8 | SBERT hỏng → tạm dừng sinh (không chạy chỉ với luật chữ) | ✅ Leader chốt 2026-11-25 |
| Q9 | Trần ngày đếm câu đã lưu; chia theo tỉ lệ lỗi thực tế; ngân sách chung 5 triệu token | ✅ Leader chốt 2026-11-25 |
| Q10 | Đợt 2 chỉ trắc nghiệm + đúng/sai đa dạng theo loại nút; điền trống/sắp xếp câu cho học viên làm tính năng riêng sau | ✅ Leader chốt 2026-11-25 |
| Q11 | "Luyện lỗi của tôi" tính lượt như Luyện theo nút (nguồn `TARGETED_PRACTICE`) | ✅ Leader chốt 2026-11-25 |
| Q12 | Lỗi ở mục Từ vựng, Ngữ pháp, Chữ Hán, Ôn tập cũng kích hoạt AI và "Luyện lỗi của tôi", truy về nút qua cây (`lexemes.knowledge_point_id`); Phát âm/Nghe tạm chưa tính; ngưỡng trùng nghĩa 0,80 | ✅ Leader chốt 2026-11-25 |
| Q13 | Câu AI đã duyệt hiện nhãn nhỏ **"Câu mới"** cho học viên (Luyện lỗi của tôi, Đọc, Đố vui): phiên luyện ghi `meta.newItems` = số thứ tự các câu `source='AI'`; "Luyện lỗi của tôi" vẫn giữ nút đủ 7 ngày dù học viên đã làm lại đúng; chữ hiển thị cho học viên dùng "phần" thay cho "nút" | ✅ Leader chốt 2026-11-25 |
| Q14 | Phát âm/Nghe phân biệt cũng tính lỗi theo nút NA mà phiên đã gắn (`served_items.items[].targetKid`, như 011); trần hàng chờ duyệt `max-pending` = 500; FR-018 trả thêm `droppedReasons` (số câu bị loại theo lý do) cho trang Chạy AI; dạng Điền từ/Sắp xếp câu tách spec riêng | ✅ Leader chốt 2026-11-25 |
| Q12 | Tỉ lệ `TRUE_FALSE` ≤ 1/3 mỗi lô; cửa sổ lỗi cá nhân 7 ngày; 10 câu/phiên | ✅ Leader chốt 2026-11-25 |
| Q3 | Duyệt hai bước: `TEACHER` duyệt chuyên môn và là người duy nhất sửa nội dung; `CONTENT_EDITOR` (khác giáo viên đã duyệt) đưa vào hệ thống hoặc từ chối; `ADMIN` một mình không duyệt. Không thêm bảng hay cột, dùng `reviewed_by` của câu `PENDING_REVIEW` làm dấu "giáo viên đã duyệt"; ai duyệt chuyên môn còn trong `audit_logs` | ✅ Leader chốt lại 2026-11-25 (v1.6.0), thay bản 2026-11-20 |
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
| Job dùng hết ngân sách token của trợ lý | Tắt mặc định; v1.4 ≤ 100 câu/ngày (~3 triệu token/tháng) trên ngân sách chung 5 triệu; `ADMIN` chỉnh ở 078 |
| 100 câu/ngày quá tải giáo viên *(v1.4)* | Câu chờ duyệt chặn nút sinh tiếp (SC-003); `ADMIN` hạ `daily-cap` |
| SBERT coi câu mẫu ngắn là giống nhau *(v1.4)* | Bắt buộc cùng đáp án đúng; ngưỡng chỉnh ở 078 không cần deploy |
| BE nặng thêm, tải model lần đầu *(v1.4)* | Nạp lười; hỏng thì chỉ tạm dừng sinh |
| Dữ liệu demo ít câu sai → không có ứng viên | `ADMIN` hạ `min-wrong` ở 078 để demo |
| Dấu "đã xem" mất khi Redis tắt | Chỉ chặn duyệt hàng loạt; duyệt từng câu vẫn được |

---

## 12 · Màn hình

| SCR | Route | Guard · layout | API |
|---|---|---|---|
| SCR-058 | `/content/ai-review` | `['TEACHER', 'CONTENT_EDITOR']` · `admin` | FR-011, FR-016; mỗi dòng hiện bước "Chờ giáo viên duyệt" / "GV đã duyệt · Chờ đưa vào hệ thống" |
| SCR-059 (010) | có sẵn | sửa chỉ `TEACHER`; nút duyệt/từ chối theo bước mở từ SCR-058 (`reviewStep`): bước giáo viên cho `TEACHER`, bước đưa vào hệ thống cho `CONTENT_EDITOR`; mở từ kho câu hỏi chỉ có bước giáo viên | `/api/admin/questions/{id}`, FR-014, FR-015 |
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
| AT-09 | FR-007, E5 | Lô có câu 2 đáp án đúng, câu trùng stem có sẵn, câu chữ vượt cấp, `errorKid` không tồn tại · T các câu đó ở `dropped` đúng lý do, câu còn lại lưu · v1.1: câu chỉ khác dấu câu/khoảng trắng, câu gần giống ≥ 0,8 cùng đáp án đúng · T `NEAR_DUPLICATE_STEM`; cùng mức giống nhưng khác đáp án, khác dãy số hoặc < 8 code point · T giữ; vẫn đúng một lượt đọc nội dung khi không có `errorKid`/chữ Hán |
| AT-10 | §0.3 | Prompt dựng ra không chứa id/tên/email người học; chứa `can_do`, `name_zh`, danh sách từ của nút |
| AT-11 | FR-011 | Hàng chờ chỉ có câu AI `PENDING_REVIEW`, cũ nhất trước; `ADMIN` gọi · T 403 |
| AT-12 | FR-014, E1 | Câu thiếu KID `PRIMARY` · T 422 `MALFORMED_QUESTION`; hợp lệ · T `APPROVED`, `reviewed_by`; hai người duyệt song song · T 200 + 409 |
| AT-13 | FR-015 | Từ chối không lý do · T 422; có lý do · T `REJECTED`, `review_reason` |
| AT-14 | FR-016, FR-012 | 3 câu: 1 đã xem hợp lệ, 1 chưa xem, 1 đã bị người khác duyệt · T `APPROVED`, `NOT_VIEWED`, `NOT_PENDING` |
| AT-15 | FR-013 | `TEACHER` sửa câu AI chờ duyệt · T 200; `CONTENT_EDITOR` sửa câu AI chờ duyệt hoặc câu `IMPORT` · T 403; `CONTENT_EDITOR` duyệt câu chưa qua giáo viên · T 403 |
| AT-16 | FR-017, SC-002 | Kho có câu AI `PENDING_REVIEW`/`REJECTED` cùng KID · T luyện theo nút, ôn, quiz, thi thử không phát câu đó; sau khi duyệt · T luyện theo nút phát được |
| AT-17 | FR-018, FR-019 | Lịch sử lô đúng thứ tự, có token; trigger khi đang chạy · T 409 `AI_GENERATION_BUSY`; hợp lệ · T 202 |
| AT-18 | FR-020 (FE) | Vitest: ô chọn chỉ bật cho câu đã xem; kết quả trộn hiện từng câu; từ chối bắt buộc lý do; 409 tải lại |
| AT-19 | §13 | Test hợp đồng `/v3/api-docs` so `openapi-ai-practice.yaml` (hai chiều path + method + status) |
| AT-20 | FR-003a | `NP3-901` 30 lượt sai và `NP3-902` 25 lượt sai cùng cần `NP2-901` (5 lượt sai), kho cả ba < `min-bank` · T `NP2-901` là ứng viên đầu tiên dù < `min-wrong`, hai nút kia vẫn là ứng viên; xoá cạnh · T thứ tự và tập ứng viên như v1.2 |
| AT-21 | FR-003b, E8 | `daily-cap=100`, hôm nay đã lưu 98 câu AI · T lần chạy phân bổ tổng 2 câu; đã lưu 100 · T không tạo lô, stub không được gọi; câu tạo hôm qua không tính; chạy thử cũng tôn trọng trần |
| AT-22 | FR-003b | `remaining=6`, `batch-size=5`, lỗi {A:30, B:10, C:5} · T {A:4, B:1, C:1}; `remaining=20` · T {A:5, B:5, C:5}; `remaining=2` · T {A:1, B:1} |
| AT-23 | FR-007a | Engine giả: câu mới cosine 0,95 với câu có sẵn cùng KID, cùng đáp án · T `SEMANTIC_DUPLICATE_STEM`; cosine 0,95 khác đáp án · T giữ; cosine 0,85 · T giữ; hai câu trong lô cosine 0,95 cùng đáp án · T câu sau bị bỏ; câu lưu có vector |
| AT-30 | FR-011, FR-014, Q3 · v1.6 | Câu AI mới · W `CONTENT_EDITOR` xem hàng chờ · T trống; `TEACHER` duyệt · T `TEACHER_APPROVED`, vẫn `PENDING_REVIEW`, học viên chưa thấy, hàng chờ GV trống; cùng người đó với role `CONTENT_EDITOR` duyệt tiếp · T 403; `CONTENT_EDITOR` khác thấy câu với `step=CONTENT`, duyệt · T `APPROVED`, `reviewed_by` = CE, audit `AI_QUESTION_APPROVE`; chuông của CE hiện `AI_PUBLISH` |
| AT-24 | FR-021, FR-022, E9, E10 | Engine không sẵn sàng · T `PAUSED`, 0 lô, stub không được gọi, `embeddingReady=false`; SHA sai · T file bị xoá, không sẵn sàng, BE vẫn chạy; lỗi giữa lô · T `FAILED EMBEDDING_UNAVAILABLE`, 0 câu; câu import chưa có vector · T được tính bù |

---

## 15 · Gói việc

[`tasks.md`](tasks.md) · [`questions.md`](questions.md)
