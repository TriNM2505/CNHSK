# Câu hỏi và quyết định — Spec #024

## 2026-11-16 · Kết quả Full duy nhất của duyệt câu chờ

- Full `20261116-095421541-full`: đủ445 suite, **6978/F3/E0/S0, BUILD FAILURE/FAILED/native1**, kết thúc17:42:40.668 trước deadline17:54:36.821 giờ Việt Nam. Reconciliation XML/native/case counts khớp, source/classes giữ nguyên, thiếu/thừa suite0 và lỗi ngoài exact5 whitelist0. Không chạy lại Full, không ghi toàn bộ test PASS.
- Ba failure đúng assertion đã duyệt: LoginAttemptServiceIntegrationTest dòng133 Redis PEXPIRETIME `1794824101533` thấp hơn cận dưới `1794824101653` đúng120ms (cận trên1794824102501); cùng lớp dòng313 captured output chứa email fixture tổng hợp, không chứa IP/outage message; QuestionImportIntegrationTest dòng304 p95 **47734ms >30000ms**. Hai vị trí whitelist còn lại không fail ở lần này.
- Đúng exec Full1358261bytes, SHA256 `06A35A3B288BEBC2D4D00DD70C57E1338CE7EA701221A9AF16A92BCC3377F20A`, một session trong khoảng native và source/classes gốc đã bảo toàn. Report/check riêng `20261116-144737202-coverage` **BUILD SUCCESS/native0**, LINE BUNDLE **16787 covered/492 missed =97.1526129984% ≥80%**. Reconciliation xác nhận exec nguyên vẹn, source/classes khớp, continuationEligible=true; không chạy test sau Full. G2 đạt điều kiện ngoại lệ Leader duyệt, Full vẫn FAILED/native1.

## 2026-11-16 · Leader duyệt hàng chờ câu sinh tự động, Q1-A

- Base cố định `8c6e2269a4996c6fa4eb782412e37b1333c40d72`, branch `spec/024-t05`; exact14 (9 ADD/5 MODIFY), gồm phụ lục `contracts/openapi-ai-practice-v1-1.yaml` và tài liệu này. Không sửa tasks.md, ErrorCode, GlobalExceptionHandler, OpenAPI locked, migration hoặc engine. Leader xử lý conflict spec khi merge, không rebase theo main mới.
- Leader xác nhận đã thông báo Tường. Phạm vi010: controller chỉ truyền caller xuống service, không đổi generated OpenAPI; service mở PUT cho CONTENT_EDITOR duy nhất AI PENDING_REVIEW, câu khác403; TEACHER giữ hành vi cũ. Giữ nguyên toàn bộ assertions/fixtures cũ trong QuestionAdminServiceTest, @ValueSource cũ và locked tests010; chỉ thêm test. Hai mã MALFORMED_QUESTION422/QUESTION_NOT_PENDING409 đã có, tái sử dụng.
- Q1-A: bulk có bốn outcome APPROVED/NOT_VIEWED/NOT_PENDING/MALFORMED; mỗi ID một transaction độc lập. Lỗi DB/audit bất ngờ rollback riêng ID lỗi, trả500 và dừng; các ID trước đã APPROVED giữ nguyên với đúng một audit mỗi câu. Không thêm outcome FAILED hoặc đổi lỗi hạ tầng thành MALFORMED.
- Redis viewed theo user/question TTL24h, chỉ GET detail AIpending ghi marker; Redis lỗi GET vẫn200/response không đổi. Danh sách không ghi marker; bulk mất marker trả NOT_VIEWED. Quyền luôn kiểm ở service bên cạnh filter; ADMIN không có quyền duyệt.
- MC AIpending sau chỉnh sửa lưu correct_key từ phương án đúng duy nhất sau validation010; không đổi QuestionRules, IMPORT/MANUAL hoặc luật câu đã dùng. Approve/reject giữ row lock, conditional UPDATE và audit cùng transaction.
- Giữ precedence lỗi của entry long-ID cũ khi missing/archive để không sửa assertion no-auth-interaction đã khóa. Mọi HTTP PUT đi qua overload raw-ID/caller kiểm quyền trước target/body/archive; entry cũ kiểm role trước body/persistence khi đã có câu hợp lệ. Đây là quyết định tương thích đã duyệt, không phải bỏ kiểm quyền ghi.
- Phụ lục `contracts/openapi-ai-practice-v1-1.yaml` đã được thành viên khác duyệt trước khi code endpoint; mô tả đúng delta GET/PUT010 và500 bulk, không định nghĩa lại schema/parameters/status010. Kiểm marker trước lookup bulk: thiếu marker → NOT_VIEWED; ID đã xem nhưng câu không còn tồn tại → NOT_PENDING. Implementation và source review đã xong; chưa khẳng định G2 PASS.
- Gates được duyệt: bỏ DEBUG trong đúng PowerShell Maven → targeted (gồm compile/test-compile) → regression aigen+learning.knowledge+contract+ModuleBoundaryTest → Full verify một lần → đúng Full exec JaCoCo LINE≥80%. Chỉ exact5 environmental whitelist; native1 vẫn FAILED. Focused đã recovery và regression đã đạt theo evidence bên dưới; Full đang chạy, coverage chờ đúng exec Full. Không dùng lịch sử T-01…T-04 bên dưới để tuyên bố gate T-05 đã đạt.

## 2026-11-16 · T-05 compile đạt, targeted có lỗi fixture được giữ nguyên evidence

- Compile **BUILD SUCCESS/native0**. Targeted đầu đủ5 suite: **128/F0/E3/S0, BUILD FAILURE/FAILED/native1**. Cả3 error thuộc fixture mới của `AiReviewKnowledgeIntegrationTest`, vi phạm constraint `ck_q_ai_review`/`ck_q_reject` khi dựng dữ liệu trước HTTP; không phải assertion HTTP đã chạy rồi fail.
- Sửa đúng5 dòng thêm mới trong fixture của suite bridge; không đổi assertion, production hoặc test/fixture cũ. Giữ lại4 suite PASS/110 case của attempt đầu, không chạy lại để chọn PASS. Recovery riêng18 case bridge đã bắt đầu với run `20261116-094404507-targeted-launch`; tại snapshot này **chưa có kết quả**. Không tuyên bố fresh128PASS hoặc cộng một recovery chưa kết thúc vào tổng PASS.
- Benchmark queue500 câu của suite controller đã PASS về chức năng. Raw samples (ms): `49.6755,52.7398,52.7472,40.2571,35.1464,33.2594,32.7119,34.2161,31.5673,34.6553`; **p95=52.7472ms**, dưới mục tiêu300ms. Đây là số đo/log, không có assertion latency; không thay thế kết quả gate targeted.
- **Regression, Full và coverage chưa chạy** tại snapshot này; chưa đủ điều kiện kết luận G2. Native1 và3 fixture error của attempt đầu được giữ nguyên lịch sử.

## 2026-11-16 · T-05 recovery và regression đạt, Full duy nhất đang chạy

- Recovery `20261116-094404507-targeted-launch`: **18/F0/E0/S0, BUILD SUCCESS/native0**. Cùng4 suite/110 case PASS giữ lại từ attempt đầu, xác minh đủ **128 case logic qua2 attempt,146 lượt thực thi native**; không phải fresh128PASS. Attempt128/F0/E3/S0 FAILED/native1 ở trên giữ nguyên lịch sử.
- Regression **41 suite,494/F0/E0/S0, BUILD SUCCESS/native0**; native totals, XML/case inventories và source/classes đã đối chiếu khớp, không thiếu/thừa suite. Không chạy lại các gate đã đạt.
- Full duy nhất `20261116-095421541-full` đang chạy: PID30576, helper38040; bắt đầu thực tế **16:54:36.821 giờ Việt Nam**, deadline cố định **17:54:36.821**. Expected445 suite; **chưa có kết quả/native exit cuối**, không tuyên bố Full PASS hoặc G2 đạt.
- Coverage chờ exec gốc `tmp/cnhsk-api-jacoco.exec` của đúng attempt Full và snapshot `full-original.exec` sau khi kết thúc. Chưa có hash/session/LINE counters cuối để xác minh; không lấy exec targeted/regression hoặc coverage lịch sử thay thế.

## 2026-11-16 · Leader yêu cầu sample chỉ dạng 4 phương án

- Sample chỉ lấy APPROVED + PRIMARY có đúng4 options và đúng1 is_correct=true. FILL_BLANK options=[], câu3 options hoặc2 đáp án đúng không làm sample; options hợp lệ của các câu đó vẫn góp error_patterns như cũ. Ba sample là ba câu hợp lệ đầu tiên sau lọc, không N+1; malformed options vẫn skip riêng câu/WARN chỉ question ID.
- Fix tiếp nối PR#226 trong exact9; chỉ test-compile → targeted T-03 → regression aigen, không chạy lại Full/JaCoCo. Kết quả Full FAILED/native1 và coverage cũ bên dưới là lịch sử commit225eb8b, không phải xác minh commit Fix. Leader sẽ integrated verify trên main834a4cd.
- Gate Fix ngày2026-11-16: test-compile BUILD SUCCESS/native0; targeted đầu45/F0/E1/S0/native1 được giữ nguyên (unit fixture mapping chỉ2options, bị lọc sample nên NPE). Chỉ sửa fixture thêmC/Dfalse, giữ mọi assertion mapping/privacy; recovery riêng AiPromptBuilderTest4/F0/E0/S0/native0 cùng3suite/41case PASS giữ lại xác minh đủ45case T-03 qua hai attempt, không tuyên bố fresh45PASS. Regression aigen4suite config/selector **70/F0/E0/S0, BUILD SUCCESS/native0**. Không rerun compile/targeted suites đãPASS, Full/JaCoCo hoặc benchmark. Full exec gốc/copy1338905bytes giữ SHA256 `8657E95ED8017371D83B0CB372F2D7AF613C57C1F59E2DEE4FF80D3D6A93675E`; coverage Fix không đo, chờ integrated verify của Leader.

## 2026-11-15 · T-03 Leader duyệt exact9/Q1-A

- Prompt giữ `code,title,name_zh,can_do,hsk_level`, prerequisite titles, tối đa80 lexemes và3 mẫu APPROVED PRIMARY hợp lệ cùng error patterns distinct; đúng hai system blocks cache=true. Không learner data/số đếm hoặc prompt/nội dung câu trong log.
- Legacy options hỏng: skip riêng câu cho cả sample/error patterns, WARN chỉ question ID; không làm mất các câu hợp lệ còn lại hoặc sửa selector T-02.
- Contract nội bộ: `AiPromptBuilder.build(knowledgePointId,batchSize)` trả immutable Prompt; `AiQuestionValidator.validate(rawJson,Prompt)` trả accepted drafts + dropped zero-based index/reason. Whole JSON/cấu trúc lô hỏng → exception an toàn INVALID_OUTPUT không parser cause; lỗi từng câu → drop riêng. Correct key suy ra từ duy nhất một isCorrect, options được ánh xạ sang QuestionOption snake_case hiện hữu.
- Validator gom đọc references/CHAR theo cấp và stem mọi status/source/role cùng KID; NFC/Unicode whitespace, code-point lengths; không DB writes/provider/schema/config thay đổi. T-04 sở hữu claim/recheck/persistence, race stem không được tuyên bố đã loại bỏ bằng DB unique.
- G1/gates/Draft PR đã được Leader duyệt; G2 xác minh ngày2026-11-16: compile BUILD SUCCESS/native0; targeted4 suite **44/F0/E0/S0/native0**; regression8 suite **118/F0/E0/S0/native0**. Không rerun gate PASS; source/build tooling giữ nguyên freeze từ compile tới coverage.
- Full duy nhất `20261115-201631154-full`, không exclude/skip: **404/404 fresh suite,6349/F2/E0/S0, BUILD FAILURE/FAILED/native1**; native03:16:39.571–03:59:14.332 Asia/Ho_Chi_Minh, trước deadline04:16:39.571. Native/XML/case inventory khớp, ngoài exact5 whitelist0;44test T-03 trong Full F0/E0/S0. Hai failure đều đúng assertion đã duyệt: `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`:398, p95 **38099ms>30000ms**; `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`:304, p95 **48382ms>30000ms**. Ba whitelist còn lại không fail. Không tuyên bố toàn bộ test PASS.
- JaCoCo LINE BUNDLE từ đúng Full exec: **15619 covered/457 missed=97.157253048%≥80%**, report/check-only BUILD SUCCESS/native0;917 analyzed classes. Exec gốc1338905bytes, SHA256 `8657E95ED8017371D83B0CB372F2D7AF613C57C1F59E2DEE4FF80D3D6A93675E`, original/preserved bytes trước/sau coverage không đổi; session khớp Full, source/classes khớp. Không Maven test sau Full. Evidence cục bộ trong `plans/261115-2035-spec024-t03-shadow-plan/reports/`, không commit.
- G2 đạt điều kiện ngoại lệ Leader duyệt; G3 Draft PR được phép, không Ready/merge/main push. Giữ nguyên toàn bộ lịch sử T-01/T-02 phía dưới.

## 2026-11-15 · T-02 đã triển khai cap100, G2 đạt theo whitelist được Leader duyệt

- Phạm vi exact7/Q1-A: selector và query repository cùng ba test, hai tài liệu SPEC. Cap cuối **100 user/lần chạy**, chỉ phá hòa tại boundary; vẫn xét toàn bộ tiedKids, xếp user theo số item sai duy nhất giảm dần rồi userId tăng dần, đếm review chỉ cho user thực sự đóng góp vào từng KID. Raw wrongCount, eligibility và thứ tự ưu tiên không đổi; không sửa module011, schema, config, dependency, endpoint hoặc production exception translation.
- Compile BUILD SUCCESS/native0 được giữ lại. Targeted gốc **50/F1/E0/S0 FAILED/native1** và ZIP STOP còn nguyên. Sau test-only fix kiểm exception/cause/root cause, R1 recovery **51/F0/E0/S0/native0** ở cap200; mẫu200user3526.8998ms kích hoạt giảm cap100 đã được Leader duyệt trước. Không rerun targeted đã PASS hoặc tuyên bố R1 đã kiểm cap100. R2 regression đủ8 suite **52/F0/E0/S0/native0**; Full sau đó kiểm cả51 test T-02 ở cap100, **F0/E0/S0**.
- Full duy nhất `20261115-145757-297-full`, không loại test: **6305/F4/E0/S0, đủ400 suite, BUILD FAILURE/FAILED/native1**. Bốn failure đều khớp exact5 whitelist; ngoài whitelist0. `QuestionAdminControllerIntegrationTest` dòng252 PASS. Không có tuyên bố toàn bộ test PASS, không sửa hoặc chạy lại Full để chọn PASS.

| Failure Full được duyệt | Kết quả thực tế |
|---|---|
| `LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry`, dòng133 | Redis expiry1794730694279, dưới cận1794730694567 của khoảng [1794730694567,1794730695315] đúng288ms |
| `LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings`, dòng313 | Log chứa email fixture tổng hợp; IP=false, outage message=false |
| `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`, dòng398 | p95=50368ms >30000ms |
| `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`, dòng304 | p95=66503ms >30000ms |

- Helper Full gặp lỗi chia sẻ file khi SaveStatus lúc15:14; trạng thái **UNKNOWN gốc được bảo toàn**. Observer read-only giữ handle/identity của đúng CMD/Maven/Surefire gốc xác nhận CMD/Maven native1, Surefire0, kết thúc15:48:06.1528899+07 trước deadline15:57:57.2978908+07; không restart/retry/kill hay suy exit code từ log.400 XML mới, case counts và native summary khớp;982 source/987 class không đổi. Xem `plans/261115-1258-spec024-t02-shadow-plan/reports/resume-20261115-1439/R2/20261115-145757-297-full-recovered-reconciliation.json` (evidence cục bộ, không commit) và `plans/261115-1258-spec024-t02-shadow-plan/reports/resume-20261115-1439/R2/original-full-recovered-provenance-proof.json` (evidence cục bộ, không commit). Lỗi observer V1 cũng còn trong lịch sử.
- NFR tại Full/cap100: fixture100000 item sai/20user có samples365.7825/332.4343/334.3447ms, **p95=max365.7825ms** (3 samples, thống kê hạn chế),20 mastery API/3 own reads/64 Hibernate SQL. Fixture200user/8000 item sai: warmup1806.5692ms, **một mẫu measured1450.7693ms**,100 API/3 own reads/304 Hibernate SQL; đây là elapsed đơn, không phải p95. Fixture100user1198.383ms. Đo toàn selector với mastery thật, gồm Settings/cleanup/reads/mastery; Settings cache thật đã warm, JDBC Settings và tổng DB rows chưa được đếm. Các fixture không chứng minh worst-case cho mọi phân bố dữ liệu.
- Coverage BUNDLE LINE từ **đúng Full exec gốc**: **15386 covered/452 missed =97.1461043061% ≥80%**. Exec1336884bytes giữ nguyên trước/sau, SHA-256 `95AC47DCB859B0636623301B70BAFFB54A5D5891C646EE3002CE40ED631B816B`; session/XML/time interval và982 source/987 class đã đối chiếu. Một lần Maven trực tiếp JaCoCo report/check **BUILD SUCCESS/native0**, không chạy test sau Full; bản helper coverage đầu lỗi metadata trước Maven được giữ lại, chỉ helper mới thực hiện Maven. `plans/261115-1258-spec024-t02-shadow-plan/reports/resume-20261115-1439/R2/coverage-original-full-20261115-v2/status-639303436385667768-dee61103ae2a49fd8f5e3afa35d88c81.json` (evidence cục bộ, không commit) accepted=true, archive bytes khớp.
- **T-02 đã triển khai; G2 được chấp nhận theo ngoại lệ Leader duyệt, chờ Draft PR/Leader review.** Full vẫn FAILED/native1, helper UNKNOWN vẫn là evidence lịch sử; chưa Ready/merge/main push. T-01 và T-03…T-09 không thay đổi bởi cập nhật này.

## 2026-11-15 · T-02 quyết định cap100 sau benchmark200 được Leader duyệt trước

- R1 targeted recovery `20261115-144541-962-targeted`: **51/F0/E0/S0, BUILD SUCCESS/native0**, đủ3 suite, source982 trước/sau không đổi; hai assertion exception/cause và các assertion DB rollback/persistence đã chạy PASS. Compile native0 cũ và targeted FAILED50/F1/native1 cũ được giữ nguyên, không ghi đè.
- Benchmark200 user hòa tại boundary, MasteryReader thật: warmup3973.2008ms (không dùng làm mẫu quyết định), **mẫu measured=true3526.8998ms >3000ms**;200 API,3 repository reads,604 Hibernate SQL. Một mẫu chỉ là elapsed, không phải p95. Theo quyền Leader đã cấp, **giảm cap xuống100 user/lần chạy** và cập nhật test kỳ vọng, giữ nguyên raw wrongCount/eligibility/ordering và toàn bộ nhóm tiedKids. Không sửa module011 hoặc production exception handling.
- Bảo toàn R1 cap200 PASS; không rerun targeted đã PASS. R2 freeze/review ghi rõ source cap100 là revision được duyệt; regression và Full/all tests sẽ kiểm chứng các test cap100 đã cập nhật. Không tuyên bố R1 đã test cap100.
- R1 benchmark100k/20user: samples282.3252/282.7653/283.2081ms, p95=max283.2081ms,20 API/3 reads/64 Hibernate SQL;100user growth1190.3218ms,100 API/304 Hibernate SQL. Settings cache thật đã warm; thời gian gồm Settings calls, counter không tính JDBC Settings hoặc tổng DB rows.
## 2026-11-15 · Leader cho T-02 resume sau account switch

- Giữ base `da5e1bc`, exact7 và toàn bộ evidence trước: compile BUILD SUCCESS/native0, targeted cũ **50/F1/E0/S0 FAILED/native1**, ZIP review không ghi đè. Không rerun compile đã PASS, không reset/rebase/kill process hoặc xóa evidence.
- Test-only fix: assertion cleanup/read failure phải nhận `InvalidDataAccessApiUsageException`, kiểm cause/root cause `IllegalStateException` đúng message; giữ nguyên các assertion rollback/persistence phía sau. Không sửa production để đổi exception translation.
- Thêm benchmark **200 user hòa tại boundary**, dùng MasteryReader thật và đo toàn selector, chỉ log thời gian. **Nếu số đo >3000ms, Leader cho phép giảm cap 200 xuống 100 trong source và chỉnh tests tương ứng, không cần hỏi lại.** Trước số đo, cap vẫn 200; kết quả và cap cuối sẽ ghi sau.
- Quy tắc STOP mới: test mới thuộc task sai assertion được tự sửa trong exact7, bảo toàn evidence và không nới assertion bảo mật/nghiệp vụ. Test ngoài phạm vi lỗi hoặc Full ngoài exact5 whitelist thì STOP. Rerun chỉ khi cần xác minh thay đổi đã được duyệt; không chọn PASS bằng cách lặp lại source không đổi.
- Thứ tự còn lại: targeted → regression → Full/all tests → JaCoCo cùng Full exec LINE≥80%; DEBUG phải bỏ trong đúng PowerShell trước Maven và child kế thừa vắng mặt. Native1 luôn giữ FAILED. Nếu đủ điều kiện: stage từng exact path, conventional commit không nhắc AI/task/FR/plan, push thường và mở Draft PR; không merge/Ready/push main.
## 2026-11-15 · Leader duyệt T-02 G1 đến Draft PR, Q1-A

- Base cố định `da5e1bc1a811ba36d68ccb833719fe817560386b`, branch `spec/024-t02`, exact7 (5 ADD/2 MODIFY); không rebase. T-01 đã CLOSED/PR #201 merged; kết quả integrated 6254/F0/E0/S0 do Leader xác nhận là lịch sử T-01, không phải gate T-02.
- Q1-A thay thế đề xuất G0: mastery **chỉ phá hòa tại ranh giới cắt** `max-batches-per-run`. Chỉ có boundary tie nếu số ứng viên lớn hơn giới hạn và hai ứng viên ngay hai bên ranh giới có cùng wrongCount; xét toàn bộ nhóm KID có wrongCount đó, không cắt nhóm trước khi chấm hòa. Không có boundary tie hoặc max=0 thì không gọi MasteryReader.
- Tối đa **200 user/lần chạy**, chỉ user có lượt sai đóng góp vào các KID đang hòa; ưu tiên số item sai đóng góp vào nhóm hòa giảm dần, rồi userId tăng dần. Một item được tính một lần khi xếp user dù PRIMARY và error_kid cùng thuộc nhóm; hai đóng góp vẫn độc lập khi tính wrongCount của KID. Mỗi user được chọn gọi đúng một `valuesOf(userId, tiedKids)`; không sửa module 011.
- `weakUsers` của KID đếm user trong tập được chọn có lượt sai đóng góp cho KID đó trong window và trạng thái mastery `review`; missing/notyet/insufficient hoặc chỉ review KID khác không được cộng. Thứ tự `wrongCount DESC → weakUsers DESC → kid.code ASC`; ngoài nhóm boundary tie weakUsers không cần đánh giá. Không dùng mastery để vượt min-wrong hoặc thay đổi eligibility.
- Cách hiện thực “cộng min-wrong để ưu tiên khi bằng nhau”: trong nhóm raw wrongCount bằng nhau, mỗi weak user tương đương một trọng số min-wrong; vì hệ số này dương và giống nhau, so sánh weakUsers trực tiếp cho cùng thứ tự mà không sửa raw wrongCount, bankCount hoặc số đo thống kê. Giới hạn 200 user là quyết định Leader, không phải toàn bộ quần thể mastery.
- FR-002 chỉ một SQL gom item sai; đọc options và error_kid bằng Java, +1 PRIMARY và +1 option đã chọn, kể cả cùng KID thì +2. FR-004 cleanup RUNNING quá 10 phút toàn hệ thống trước selection; transaction ngắn cleanup commit riêng, read snapshot sau đó; T-04 chịu trách nhiệm claim/recheck.
- Gate được duyệt: DEBUG phải bỏ trong đúng PowerShell/child chạy Maven → test-compile → targeted → regression → Full verify → cùng Full exec LINE≥80%. Chỉ exact5 environmental whitelist được duyệt cho Full, native1 giữ FAILED; lỗi khác/UNKNOWN/ngoài exact7 thì STOP và đóng ZIP source/evidence. Tại thời điểm ghi quyết định: mọi gate T-02 NOT RUN.
- G3 nếu đủ điều kiện: kiểm diff/BOM/secrets/exact7, stage từng path, conventional commit không nhắc AI/task/FR/plan, push thường và Draft PR; không merge/Ready/push main. Không migration, config change, Claude thật hoặc secret.

## 2026-11-15 · T-02 STOP sau targeted, chưa đủ điều kiện G3

- Compile BUILD SUCCESS/native0. Targeted duy nhất `20261115-132441-366-targeted`: **50/F1/E0/S0, BUILD FAILURE/FAILED/native1**. Ba suite: integration14/F1, performance2/F0, unit34/F0; không có test skip.
- Failure ngoài whitelist tại `WeakKnowledgeSelectorIntegrationTest.cleanupFailureRollsBackEveryChangedRowButLaterReadFailureCannotUndoCleanup`, dòng263: expected `IllegalStateException`, actual `InvalidDataAccessApiUsageException: cleanup fixture failure` do Spring dịch exception của repository spy. Các assertion rollback/persistence sau dòng263 chưa thực hiện, không được ghi PASS. Giữ source đã freeze; chưa sửa assertion hoặc rerun.
- NFR-P01 trên fixture100000 item sai/20 user, có boundary tie và MasteryReader thật: samples317.3804/316.2116/320.816601ms, p50=317.3804ms, p95=max=320.816601ms (3 samples, thống kê hạn chế). Mỗi lần selector3 repository reads,64 Hibernate SQL,20 MasteryReader API; returned rows100 wrong groups/5 nodes/0 runs. Thời gian gồm Settings/cleanup/reads/mastery; Settings dùng cache thật đã warm, JDBC Settings và tổng DB rows không nằm trong counter. Fixture100 user/4000 item:1943.578499ms,3 reads/304 Hibernate SQL/100 API. Chỉ đo/log, không assert latency; không thay thế kết quả gate.
- **Regression/Full/JaCoCo NOT RUN; chưa commit/push/PR.** STOP theo chỉ đạo Leader, không kill Maven (lần hiện tại đã kết thúc bình thường), không tự fix hoặc chạy lại. Source7/evidence được đóng ZIP để review; cần Leader duyệt bước tiếp theo.
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
