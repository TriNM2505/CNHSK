# Chốt — Spec 010

## 2026-11-04 — Leader duyệt 010/T-03 (luồng B)

- T-02 đã merge qua PR #68. Base T-03 là `origin/main` tại `e39b14f`; tạo nhánh `spec/010-t03` và worktree riêng, Draft PR đặt base `main`. Bỏ đề xuất chồng nhánh lên T-02.
- Allowlist gồm 8 file trong `question-bank/`: `pages/Scr059QuestionEditPage.tsx`, `pages/Scr059QuestionEditPage.test.tsx`, `components/QuestionEditView.tsx`, `questionEditForm.ts`, `questionEditForm.test.ts`, `screens.ts`, `catalog.preview.ts`, `routes.test.tsx`; thêm hai tài liệu `tasks.md`, `questions.md`, tổng 10 file. Leader duyệt đánh ✅ T-03 khi gate xanh.
- `TEACHER` được sửa nội dung và nhãn theo FR-020; `CONTENT_EDITOR` chỉ xem và lưu trữ theo FR-023. Tài khoản có cả hai role nhận từng quyền được cấp tường minh. Câu `ARCHIVED` chỉ đọc và ẩn hành động lưu trữ.
- **Câu `ESSAY` chỉ đọc**: contract có loại này nhưng FR-009 chưa hỗ trợ. Hiện giải thích ngắn trong trang và chặn form sửa của `TEACHER`; `CONTENT_EDITOR` vẫn được lưu trữ. Không đổi contract hoặc thêm cách chấm tự luận.
- **Khi `used=true`, khoá đúng các trường chấm điểm:** khoá phương án (`options[].key`), thêm/xoá phương án, đáp án đúng (`options[].isCorrect`), `correctKey`. Khoá phương án được sinh liên tiếp từ A và không có ô sửa trực tiếp; thao tác thay đổi số phương án bị vô hiệu hoá khi đã dùng. Nội dung phương án, nội dung câu, lời giải, HSK/độ khó, media và nhãn vẫn sửa theo FR-020. Riêng hai nhãn `Đúng`/`Sai` của `TRUE_FALSE` luôn cố định theo FR-009. `qtype`, `code`, `source`, `status` luôn chỉ đọc, không có trong payload cập nhật.
- Các khoá trên chỉ thuộc giao diện. Backend T-12 tiếp tục kiểm FR-021/FR-022 và trả **409 `GRADING_CHANGE_NOT_ALLOWED`** nếu cần; FE hiện thông báo, mã yêu cầu, giữ bản nháp và có nút tải lại trạng thái câu. Lưu nội dung không đổi trạng thái duyệt.
- Nạp cây bằng `getKnowledgeTree` một lần khi mở trang; chỉ nạp lại khi người dùng bấm thử lại vùng cây. Chọn 1 KID chính, tối đa 2 KID phụ, không lặp và không trùng chính. Lưu bị chặn khi cây chưa tải được hoặc chưa có nút. HSK trên form chỉ chọn 1–3 qua `SUPPORTED_HSK_LEVELS`.
- Xác nhận lưu và lưu trữ nằm ngay trong trang, không dùng `window.confirm`; huỷ giữ bản nháp, đang gửi chặn thao tác lặp. Dùng response `QuestionDetail` thành công làm trạng thái đã xác nhận; huỷ request cũ khi đổi câu hoặc phiên/role. Query string từ SCR-064 đi theo link quay lại.
- Page dùng wrapper API hiện có và client chung; test mock wrapper với fixture tự soạn; preview dùng cùng View, không gọi API thật và không đưa dữ liệu `*.preview` vào page. Route/guard đã có từ T-01; chỉ đăng ký lazy SCR-059 trong `question-bank/screens.ts`.
- Gate theo leader cho task chỉ FE: toàn bộ `npm run test`, `npm run lint` 0 lỗi, `npm run build`; không bắt buộc `mvnw verify`. Không đổi backend, schema, dependency, shared, app registry hoặc contract; không truy cập Neon.
- Gate trên base `e39b14f`: **93 test mới** (51 page, 40 form, 2 route/preview). Chạy mặc định ban đầu: 1.628 pass, 3 lỗi timing ở hai file `Scr007ProfilePage.test.tsx` và `Scr014CharacterWritingPage.test.tsx`; các test tìm thấy DOM trước khi effect điền form/đăng ký callback sẵn sàng. Chạy riêng hai file: **50/50 pass**. Chạy toàn bộ FE với `npm run test -- --maxWorkers=2 --minWorkers=2`: **1.631/1.631 test, 135/135 file pass**, không fail/skip, 192,29 giây. Giới hạn số worker để giảm tải song song; không sửa/tắt/skip test, không đổi timeout hoặc cấu hình repo. `npm run lint`: **0 lỗi**, 1 warning Fast Refresh có sẵn tại `shared/ui/LevelBadge.tsx`; `npm run build` TypeScript/Vite pass. Prettier cho 8 file code, `git diff --check`, BOM và TODO/FIXME check đều pass.
- Trước khi mở Draft PR, `main` nhận PR #70 (011/T-01). Đã rebase nhánh T-03 chưa push lên **`origin/main` tại `1dcd176`**, không conflict, diff vẫn đúng 10 file allowlist. Chạy lại gate trên base này: **1.683/1.683 test / 138 file pass**, không fail/skip, 186,89 giây (`npm run test -- --maxWorkers=2 --minWorkers=2`); lint **0 lỗi**, 1 warning LevelBadge có sẵn; build TypeScript/Vite pass. Các thay đổi mới ở app registry/mastery/practice là kế thừa từ PR #70, không thuộc diff T-03.

## 2026-11-04 — Leader duyệt 010/T-02 (luồng B)

- Allowlist gồm 7 file trong `question-bank/` và hai tài liệu `tasks.md`, `questions.md`. Đăng ký lazy `SCR-064` tại `question-bank/screens.ts`; route và guard `CONTENT_EDITOR`/`TEACHER` đã có từ T-01. Không sửa `routeAccess.ts`, `screenRegistry.ts`, registry preview chung, shared, contract hoặc backend.
- `Scr064QuestionBankPage` đọc/ghi URL, gọi `listQuestions` trong `api/questions` và `getKnowledgeTree` để nạp lựa chọn KID. `QuestionBankListView` thuần props. Page và test không import `*.preview`, không thêm `eslint-disable`; test mock API wrapper và dùng fixture tự soạn trong file test.
- `catalog.preview.ts` vẽ cùng View với phản hồi mẫu cố định cho trang đầu, trang hai, rỗng, lỗi và đang tải. Lọc/tìm (gồm KID phụ, không phân biệt hoa/thường), sắp `id` giảm dần và phân trang thuộc backend T-11; FE giữ nguyên dòng/thứ tự phản hồi, không tự lọc/sắp/chia trang. Hiển thị trang từ `response.page`, tổng trang từ `response.totalElements`/`response.size` (contract không có field `totalPages`).
- URL dùng đúng `hskLevel`, `qtype`, `status`, `kid`, `q`, `page`, `size`. `hskLevel` UI chỉ 1–3 theo `SUPPORTED_HSK_LEVELS`; loại câu đủ 7 giá trị và trạng thái đủ 4 giá trị theo contract. `page` bắt đầu từ 0, `size` luôn 10. Tham số cấp/loại/trạng thái/trang sai được chuẩn hoá bằng `replace`, giữ các bộ lọc hợp lệ; KID chưa có trong cây vẫn giữ để API trả rỗng. Query string đi theo khi mở SCR-059 và được khôi phục khi quay lại/tải lại/mở link.
- Ô tìm chỉ gửi khi nhấn nút hoặc Enter, bỏ khoảng trắng hai đầu, từ 2–100 ký tự hoặc bỏ trống để xoá tìm kiếm. Bản nháp sai hiển thị lỗi trong form, giữ query đã áp dụng. Đổi bộ lọc hoặc gửi tìm kiếm về trang 0. Trang vượt cuối có nút “Về trang đầu”, giữ bộ lọc; trạng thái rỗng có “Xoá bộ lọc”.
- Đã đồng bộ worktree T-02 còn sạch bằng fast-forward từ `5a6da76` lên `c3a5fc7` trước khi code để kế thừa hai tài liệu được #66 cập nhật; không có commit riêng hoặc thay đổi nào bị ghi đè. Gate của task chỉ FE theo leader: toàn bộ Vitest, lint 0 lỗi, build pass; không bắt buộc `mvnw verify`. Một conventional commit, đánh ✅ T-02 khi gate xanh, mở Draft PR, leader merge.
- Gate thực tế: **43 test mới** (39 page + 4 preview), targeted **96 test / 4 file** pass; `npm.cmd run test` toàn FE **1.538 test / 133 file**, 0 fail/skip; `npm.cmd run lint` **0 lỗi**, 1 warning có sẵn tại `shared/ui/LevelBadge.tsx` (Fast Refresh); `npm.cmd run build` TypeScript/Vite pass. Test kiểm URL, chuẩn hoá bằng replace, Back/Forward/tải lại, tham số API, không tự lọc/sắp dòng, response page/total, loading/empty/error/retry và route/role. Preview và test không gọi API thật; diff/BOM/TODO check sạch. Không đổi dependency, contract hoặc backend; không chạy lại gate backend theo chỉ thị leader.

## 2026-11-04 — Leader duyệt 010/T-10 (luồng B)

- Base `origin/main` là `5a6da76` (đã có PR #65); nhánh `spec/010-t10`, worktree riêng. Repository mới đặt tại `learning/knowledge/repository/KnowledgeTreeQueryRepository.java`. Contract test dùng chung tên `contract/KnowledgeContractTest.java`; T-10 chỉ kiểm hai operation đọc cây, T-11/T-12/T-13 bổ sung operation quản trị sau.
- Phần chữ ký khoá cho 011/012 là các method của FR-024/FR-025 trong `learning.knowledge.KnowledgeTreeService`:
  - `List<KnowledgeNode> nodes()`
  - `Optional<KnowledgeNode> node(String kid)`
  - `Map<Long, QuestionKids> kidsOfQuestions(Set<Long> questionIds)`
  - `Optional<String> kidOfLexeme(long lexemeId)`
  - `Optional<String> kidOfTopic(long topicId)`
  - `List<Long> approvedQuestionIds(Set<String> kids, Integer hskLevel, int limit)`
- FR-024 hiện liệt kê 5 method, FR-025 thêm 1 method: tổng **6 method** nêu trên. Tin duyệt ghi "7 method"; đã báo lại leader để xác nhận số lượng, không tự thêm method ngoài FR. `KnowledgeNodeDetail detail(String kid)` chỉ phục vụ controller, **không thuộc phần khoá** cho 011/012.
- Các kiểu nội bộ là public record lồng trong service: `KnowledgeNode(long id, String kid, KnowledgeDomain domain, int hskLevel, String nameVi, String nameZh, String canDo, KnowledgeSkillType skillType, List<String> prereqKids, List<String> supportKids)`; `QuestionKids(String primaryKid, List<String> secondaryKids, Map<String, DistractorError> errorsByOption)`; `DistractorError(String errorKid, String errorNote)`. `id` của nút dùng nội bộ, không xuất qua hai API cây; controller trả đúng field đã khoá trong OpenAPI.
- `approvedQuestionCount` đếm **DISTINCT question_id** có trạng thái `APPROVED`, gắn nút ở **mọi vai trò**. `approvedQuestionIds()` chỉ lấy nhãn `PRIMARY`, sắp ID tăng dần, giới hạn 1–200; giới hạn sai ném `IllegalArgumentException`.
- `kidsOfQuestions(Set.of())` trả map rỗng, **không query**. ID không tồn tại hoặc câu chưa có nhãn không xuất hiện trong map; hành vi được ghi trong Javadoc và kiểm thử. Chính/phụ và lỗi theo khoá phương án lấy bằng một query cho cả tập, JSONB chỉ đọc trong Java.
- Bộ lọc cây: `domain` chỉ nhận `NA`, `CH`, `TV`, `NP`, `NL`, `VB`; `hskLevel` **1–7**, không giới hạn theo phạm vi demo HSK 1–3. KID sai dạng/không có trả **404** `KNOWLEDGE_NODE_NOT_FOUND`; mẫu KID theo `ck_kp_kid` của V5: `^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$`.
- Matcher chỉ `GET /api/knowledge-tree/**`, đặt ngay trước matcher tổng `/api/admin/**`, cho `LEARNER`, `CONTENT_EDITOR`, `TEACHER`; không kế thừa quyền `ADMIN`. Mã lỗi thêm ngay trước `INTERNAL_ERROR` theo L2. Nếu main đổi file dùng chung trong lúc làm, báo leader xếp hàng, không tự merge/rebase.
- Duyệt cập nhật ✅ T-10 trong cùng PR sau khi toàn bộ gate xanh; một conventional commit, đúng 12 file allowlist, mở Draft PR, không merge. Không đổi contract, schema, dependency, entity `Topic`/`Lexeme`, không truy cập Neon.
- Gate thực tế: 72 test mới; targeted T-10 + GlobalExceptionHandlerTest **74 test**, contract (010/001/009) + ModuleBoundaryTest **31 test**, đều 0 failure/error/skip. `DEBUG=false`, Java 17, Docker/Testcontainers: `mvnw -B verify` **2.167 test / 130 suite**, 0 failure/error/skip; JaCoCo LINE **96,55% (6376/6604)**, tất cả coverage check pass. Benchmark import 5.000 dòng p95 **17.405 ms < 30.000 ms**. `git diff --check`, kiểm BOM và TODO trong code task đều sạch; Maven không cấu hình Java linter riêng. Fixture tự soạn, câu `IMPORT` có code riêng; không đổi/hạ/ngắt test hoặc ngưỡng coverage.

## 2026-11-04 — Leader duyệt 010/T-05 (luồng B)

- V5 giữ đúng khối SQL §0.1, gồm `skill_type DROP NOT NULL`; không thêm bảng, cột hay ALTER ngoài khối đã khoá. Entity `Topic`/`Lexeme` và migration V1–V4 giữ nguyên.
- `questions.correct_key` theo V1 thực tế: `VARCHAR(50)`. Entity và DDL trong `database.md` dùng 50; không thêm ALTER vào V5.
- `database.md` được đồng bộ thêm §3 (bỏ nhãn "không được đụng" của `question_knowledge_points`) và §4.3 (cột `lexemes.knowledge_point_id`); §13 chỉ thêm dẫn chiếu V5. Chỉ sửa các dòng cần thiết, không format lại file.
- Kiểm AT-30 trên PostgreSQL Testcontainers, mỗi ca vi phạm constraint cách ly giao dịch; gate Java 17 với `DEBUG=false`. Leader chạy V5 trên Neon sau merge.
- Sau khi 012/T-06 merge (#63), leader duyệt bổ sung `PracticeApiIntegrationTest.java` và `PracticeFinishIntegrationTest.java` vào allowlist T-05: V5 (`ck_q_import_code`) buộc fixture `IMPORT` có `code`. Đã sửa đúng hai helper `createQuestion()` để thêm `code` duy nhất mỗi lần gọi (`TEST-` + UUID); giữ nguồn `IMPORT`, assertion và logic khác.

## 2026-11-04 — Leader duyệt 010/T-01 (luồng B)

- Thêm `cnhsk-web/src/app/preview/registry.ts` ngoài cột File của T-01 để đăng ký preview của feature. Đây là quyết định chung cho mọi task T-01 nền frontend.
- Theo OQ-1, tạm hiển thị mã miền `NA`, `CH`, `TV`, `NP`, `NL`, `VB`; chưa tự đặt tên tiếng Việt cho miền.
- Duyệt hai route `/content/questions` (SCR-064), `/content/questions/:id` (SCR-059), guard `CONTENT_EDITOR`/`TEACHER`, layout `admin`; đăng ký catalog `question-bank` với `screens = {} satisfies ScreenCatalog`.
- Không sửa `shared/layout/navigation.ts`; mục menu "Kho câu hỏi" để PR điều hướng chung hoặc T-13.
- Duyệt cập nhật ✅ T-01 trong cùng PR sau khi gate xanh; mô tả PR liệt kê từng file theo tên thật, không dùng wildcard.

## 2026-11-02 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc: `AGENTS.md`, constitution v1.4.1, `constraints/` (business, global, safety), `CONTEXT.md`, spec 002/003 §0/004/005, `specs/001-auth-rbac/tasks.md`, feature-tree 2.1–3.3/6.3/6.4/6.6, UC-109/110/112/137, `screen-fields.md` SCR-059/064, `design.md`, `database.md`, migration V1–V4, `learning.api.PracticeApi`, `auth.api.AuthApi`, Cây Năng Lực v2.0 (qua báo cáo trích của plan `261017-1654`), 3 file `Caytrithuc/*.md`.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Định nghĩa knowledge point/mastery lệch `business.md` §4/§9 | **Làm RFC theo v2.0**: knowledge point = nút KID, mastery = MP-1.0; giữ `BUS-04` cho lịch ôn mục từ, `BUS-05` cho chủ đề. Chờ cả nhóm đồng thuận |
| Q2 | Bảng lưu cây | **0 bảng mới**: mở rộng `knowledge_points` (miền, tên Trung, can-do, mảng tiên quyết/hỗ trợ), `question_knowledge_points` (vai trò), `questions`, `topics`, `lexemes` |
| Q3 | Ai sửa nội dung câu hỏi | **Theo constitution**: chỉ `TEACHER` sửa nội dung + nhãn; `CONTENT_EDITOR` xem + lưu trữ; `TEACHER` xem SCR-064 chỉ đọc |
| Q4 | Trạng thái câu nhập | **Nhập từ file → `APPROVED`; câu AI → `PENDING_REVIEW`** |
| Q5 | Dữ liệu cây phụ lục B (repo public) | **Không commit**, nạp qua SCR-068; test dùng cây tự soạn |
| Q6 | Nối nội dung cũ vào nút | **Cột `knowledge_point_id` trên `topics`/`lexemes` + mẫu `NODE_LINK`** (thay `kid_map` đã duyệt ở plan) |
| Q7 | Mã lỗi | **Làm luôn**, không thêm bảng: `error_kid`/`error_note` trên từng phương án nhiễu; có tài liệu mã lỗi chính thức thì chuyển đổi sau |
| Q8 | `questions.difficulty` | **Có** (1–3, mặc định 2) |
| Q9 | Số migration | **V5 cho 010, V6 cho 011** |
| Q10 | `questions.code` UNIQUE | **Có** |
| Q11 | API cây cho người học | **Thuộc 010** |
| Q12 | Khoá sửa khi có cuộc thi | **Bỏ đợt này** |
| Q13 | Bàn giao spec | **Chỉ file trên máy**, không push |
| Q14 | Thứ tự task | **FE trước như mọi spec** |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| X1 | Constitution/`business.md`/`design.md`: chỉ `TEACHER` sửa câu ↔ UC-109 BR-109-1, SCR-059 §5/§11: cả `CONTENT_EDITOR` | Theo constitution (Q3); cập nhật UC-109/SCR-059 trong RFC |
| X2 | `business.md` §4/§9, `CONTEXT.md` §3.1, feature-tree 3.1: mastery FSRS, knowledge point = 1 từ ↔ v2.0 | RFC Q1 |
| X3 | `database.md` §13 "không đụng" `knowledge_points`/`question_knowledge_points` | Mở rộng cột, không xoá; ghi vào RFC |
| X4 | D-01, spec 002 §0: câu hỏi chờ cây | Spec 010 gỡ |
| X5 | Plan đợt cuối (phase 2, red-team #2) dùng `kid_map` | Thay theo Q6 |
| X6 | Phụ lục B thuộc Học Bá, repo public | Q5 |
| X7 | feature-tree 6.3 nhắc `question_options`; `global.md` §7 cấm FK xuyên schema; ES-07 có `agent/*` | Theo migration V1, constitution AC-03, luật nhóm (nhánh không chứa "agent") |
| X8 | Plan phase 3 định sửa `PracticeService.finish` | Spec 011 dùng `@EventListener` theo 003 §0.9 (D-04), không sửa bộ máy |

### Còn mở

- OQ-1: tên tiếng Việt 6 miền theo v2.0 (leader đối chiếu PDF).
- OQ-2: danh mục mã lỗi chính thức của Học Bá.
