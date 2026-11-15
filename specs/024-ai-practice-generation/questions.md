# Câu hỏi và quyết định — Spec #024

## 2026-11-15 · Leader duyệt T-01 G1 đến Draft PR, Q1-A

- Base `0fab09a0bd0905b654d80376366c2b45646b76e7`, branch `spec/024-t01`, exact13 (3 ADD/10 MODIFY) theo `plans/261115-0800-spec024-t01-shadow-plan/allowlist.md`. Không rebase hoặc sửa ngoài allowlist; không migration/job/scheduler/Claude call thật/API key.
- **§5 ghi MALFORMED_QUESTION là mã mới nhưng thực tế đã có từ 010; 024 dùng lại, cùng 422.** Q1-A giữ nguyên vị trí mã cũ; chỉ thêm `QUESTION_NOT_PENDING`, `AI_GENERATION_DISABLED`, `AI_GENERATION_BUSY` theo thứ tự ngay trước `INTERNAL_ERROR`, giữ mọi mã hiện có. Không sửa spec.md; conflict với #191/#195 để Leader xử lý.
- Sáu key §0.1 qua Settings hiện có, auditId19–24/nhóm "AI sinh câu", defaults/min/max đúng spec; system-user-id0 hợp lệ và mặc định tắt. Model `cnhsk.ai.generate-model=claude-sonnet-5-5`, cron02:00VN, timeout60s; test cron `"-"`. Tests Settings chỉ mở rộng khóa mới; concrete SettingsService nếu mock, không `@MockitoBean SettingsApi`.
- Gate được duyệt: bỏ DEBUG trong đúng PowerShell chạy Maven/child → test-compile → targeted → regression → một Full verify/alltests → LINE≥80% từ đúng Full exec. Full chỉ chấp nhận exact5 environmental methods đã khóa; native1 vẫn FAILED, lỗi khác/UNKNOWN/coverage không xác minh thì STOP+ZIP source/evidence, không tự fix/rerun chọn PASS.
- G3 nếu đạt: kiểm exact13/diff/BOM/secrets, stage từng path, conventional commit không nhắc AI/task/FR/plan, regular branch push/Draft PR; không Ready/merge/main push. Tại thời điểm ghi nhận phê duyệt: mọi gate NOT RUN.

## 2026-11-15 · T-01 đã triển khai, G2 đạt theo whitelist được duyệt

- Phạm vi exact13/Q1-A: sáu key §0.1 qua Settings hiện có, auditId19–24; cấu hình model/cron/timeout được kiểm tra khi khởi động. Giữ nguyên `MALFORMED_QUESTION` của 010 và HTTP 422, chỉ thêm ba mã lỗi đã duyệt. Không thêm migration, dependency, job/scheduler, endpoint hoặc lời gọi Claude thật.
- Compile: BUILD SUCCESS/native0. Targeted7: **220 test/F0/E0/S0/native0**. Regression13: **109 test/F0/E0/S0/native0**; đã đối chiếu đủ suite/case và source freeze.
- Full duy nhất `20261115-094353-555-full`: **BUILD FAILURE/FAILED/native1**, **6161 test/F2/E0/S0**, đủ **391/391 suite** mới; không có lỗi ngoài whitelist. Hai ngoại lệ timing đúng method/assertion đã duyệt: `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`, dòng 398, p95 **38501 ms > 30000 ms**; `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`, dòng 304, p95 **53398 ms > 30000 ms**. Không sửa test, bỏ qua test hoặc chạy lại để chọn PASS; native1 vẫn là FAILED.
- Coverage BUNDLE LINE từ đúng Full exec: **15030 covered/455 missed = 97.061672586%**, vượt ngưỡng 80%. Exec gốc được giữ nguyên trước/sau report/check, SHA-256 `82858D7F566BBB1E93371052F5439684BA82F79E249AECF6EAF1C94453183231`; một session exec khớp XML, đối chiếu hash **968 file nguồn/972 file class**. JaCoCo report/check riêng BUILD SUCCESS/native0, inspection accepted=true/issues0; không chạy thêm test sau Full.
- G2 đáp ứng điều kiện theo whitelist được Leader duyệt; **không có tuyên bố toàn bộ test PASS**. T-01 chờ Draft PR và Leader review; chưa Ready/merge/main push. T-02…T-09 và các AT của toàn feature 024 chưa được triển khai trong phạm vi này.

## 2026-11-13 · Leader validate plan

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| V1 | Ai kích hoạt sinh câu | Hệ thống tự đánh giá câu sai, sinh theo dữ liệu cây tri thức; không học viên bấm, không thu phí | ✅ Chốt |
| V2 | Câu AI tới học viên khi nào | Phải duyệt và gắn KID trước | ✅ Chốt |

## 2026-11-14 · Leader chốt khi viết spec

| # | Câu hỏi | Quyết định | Trạng thái |
|---|---|---|---|
| Q1 | `service_requests.user_id` bắt buộc mà job không có người yêu cầu (câu mở #6 của plan) | Khoá `ai-generate.system-user-id` ở 078, mặc định 0 = job tắt; phải là `ADMIN` hợp lệ | ✅ Chốt |
| Q2 | Ngưỡng, nhịp, dạng câu | 02:00 hằng ngày; cửa sổ 14 ngày; ≥ 20 lượt sai; < 10 câu `APPROVED`; lô 5 câu trắc nghiệm 4 phương án chỉ chữ; ≤ 3 lô/lần; model Sonnet riêng (`cnhsk.ai.generate-model`) | ✅ Chốt |
| Q3 | Ai duyệt/sửa | `TEACHER` hoặc `CONTENT_EDITOR` (BR-108-1, BR-109-1); `ADMIN` không duyệt | ✅ Theo UC |
| Q4 | Duyệt hàng loạt | Chỉ câu người đó đã mở (dấu Redis 24 giờ, BR-108-7) | ✅ Chốt |

## Dữ kiện đã kiểm trên main `f02fc0a`

- `questions`: `source` `IMPORT/AI/MANUAL`, `status` mặc định `PENDING_REVIEW`, `ck_q_ai_review`, `ck_q_reject`, `ix_q_review`, `created_by_request_id`, `code` (bắt buộc chỉ với `IMPORT`), `difficulty`.
- `question_knowledge_points.role` + `uq_qkp_primary`; `options` có `error_kid`, `error_note` (010 Q7, chỉ đọc trong Java).
- `service_requests` `kind='AI_GENERATE'`; `uq_ai_running` một lô `PENDING/RUNNING` mỗi người.
- `ClaudeClient.complete(model, systemBlocks, messages, maxTokens, timeout)` trả token vào/ra; `TokenBudget.execute`; `StubClaudeClient`.
- `/api/admin/questions`: `GET` cho `CONTENT_EDITOR`/`TEACHER`, `PUT` chỉ `TEACHER`, `PATCH …/archive` cho `CONTENT_EDITOR`.
