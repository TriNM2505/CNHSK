## 14/11/2026 — G2 verified trên b2d3b12; thực hiện G3 Draft theo quyền Leader

- Base chính xác b2d3b1265244072a5b5300233a73779f186150de; giữ đủ19 file. Contract4/F0/E0/S0/native0 (14:22:33–14:24:58+07), targeted11 suites214/F0/E0/S0/native0 (14:25:49–14:27:52+07). Test reference-errors từ main được giữ nguyên.
- Full verify duy nhất: **5177/F4/E0/S0, FAILED/native1**, 332 fresh suites,14:29:10.3927993→15:03:11.6148522+07. Helper-start14:28:50; hard deadline15:28:50 đã công bố trước release. Thiếu/thừa/probe/outside-whitelist0, source không đổi. Không gọi Full PASS.
- Bốn exact failures: LoginAttemptServiceIntegrationTest#FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry:0.953s, expiry1794642525429, expected[1794642525894,1794642526740]ms; #redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings:0.022s, không có p95; ImportConfirmIntegrationTest#FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget:p95=38179ms<30000ms không đạt, testcase169.935s; QuestionImportIntegrationTest#NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries:p95=55390ms<30000ms không đạt, testcase220.678s. NFR_P02 có trong whitelist5 nhưng không fail; không thêm exception.
- JaCoCo report/check riêng trên đúng full exec native0 (15:04:45–15:04:54+07), không chạy lại tests. LINE toàn dự án **13490/13915 =96.9457420050% ≥80%**; execSHA06890B50630D8A7A9BE086FDD2F44F12F44276337EC5F8CBC6135F0BFA744371 giữ nguyên trước/sau, session/class/source khớp, inspection accepted=true.
- Điều kiện G3 mới của Leader được đáp ứng dù Full giữ FAILED/native1: chỉ whitelist, không blocker/source/contract/UNKNOWN và LINE đạt. Stage từng19 file sau final review, conventional commit, push thường, mở DraftPR main; không merge/Ready. URL/commit/native Git metadata lưu trong G2/G3 artifacts, chưa tự nhận MERGED.
- Regression lịch sử2383/F4/E0/S0 vẫn FAILED/native1 (FR02340403ms, NFR_P0459866ms; testcase durations177.029s/214.788s, Redis0.986s/0.043s). Không rerun regression hoặc ordered isolation theo chỉ đạo mới. Đây là giới hạn đã được Leader chọn, không tự tuyên bố đã chứng minh ordered isolation. Giữ toàn bộ G0/G1 evidence, snapshot/autostash và ZIP cũ.
- Self-check: exact19, locked YAML/schema/SecurityConfig/T12/conditional RedeemServiceTest nguyên trạng so với base mới; không dependency/FE/Neon/secrets/BOM; diff-check sạch. ZIP review gọn là evidence sau G3/STOP, không làm gate phụ.

## 14/11/2026 — Leader duyệt G2/G3 trên exact base b2d3b12

- Chỉ đạo mới thay thế gate protocol cũ: rebase đúng `b2d3b1265244072a5b5300233a73779f186150de` → contract → targeted11 → Full verify một lần với deadline60phút → JaCoCo LINE từ đúng full exec ≥80%. Không rerun regression2383 hoặc ordered isolation.
- Đã snapshot19/19 files (gồm11 ADD), đối chiếu nguyên ZIP Round2; rebase bằng autostash, giữ stash32dd6fb và artifacts. Hai conflict được duyệt đã giải quyết: giữ tất cả enums/tests main, thêm PLAN_IN_USE ngay trước INTERNAL_ERROR và giữ test409 T10. AuthApiService giữ bổ sung của main, diff T10 chỉ lockPlan NO KEY UPDATE/comment. Không commit tạm hoặc force-push.
- Whitelist vẫn đúng5 exact methods đã khóa. Native1 luôn FAILED, gồm regression cũ2383/F4/E0/S0; chỉ khi toàn bộ failures đúng whitelist, source/contract hợp lệ và full-exec LINE≥80 mới được stage từng19 file, conventional commit, push thường và mở Draft PR main theo quyền Leader. Không merge/Ready. Nếu lỗi ngoài whitelist/coverage thấp/blocker/UNKNOWN: STOP, không tự sửa/rerun.
- Evidence mới tại `plans/261113-1839-spec013-t10-shadow-plan/g2-g3-20261114/`. Verification sau rebase đang chờ; chưa công bố coverage/PR hoặc kết quả G2. ZIP review gọn được tạo khi hoàn tất/STOP, không dùng ZIP/SHA làm gate. Giữ nguyên lịch sử G0/G1 bên dưới.

## 14/11/2026 — T-10 G1 Fix Round 2 STOP sau regression FAILED/native1

- Trạng thái mới nhất: **G1 STOP**, chưa G1 hoàn tất/G2/G3 APPROVED. Branch `spec/013-t10`, base `de732866cac876e3fea6946f1e101eb259728523`, exact19; Q1 revision #2 và locked YAML giữ nguyên. Main `948a89f` chỉ do Leader báo, chưa fetch/tích hợp. Không commit/push/PR/rebase/merge/pull hoặc SQL Neon.
- Round2 đã sửa explicit JsonNode để bỏ ambiguous overload; tách schema Benefits khỏi runtime map, giữ exact component/ref và integer bounds; typed mock resets và cleanup `assertAll` khôi phục catalog/timestamp/dữ liệu sở hữu. FREE chỉ validate semantics name/description, kiểm JSON types rồi loại các field không editable, gồm price="abc"; PREMIUM giữ validation cũ. Benefits unordered là quyết định đã duyệt, không thêm sorting.

| Gate Round2 | Kết quả thực tế | Native exit |
|---|---|---:|
| 8 test-compile checkpoints sau từng test edit | PASSED; T/F/E/S=N/A, không gọi compile là test PASS | 0 mỗi lượt |
| Dedicated CreditPlanContractTest | 4/F0/E0/S0, đúng1 suite; strict generated/runtime contract PASS | 0 |
| Targeted | 213/F0/E0/S0, đúng11 fresh suites | 0 |
| Regression | **FAILED: 2383/F4/E0/S0**, đúng152 fresh suites, thiếu/thừa0, source manifest không đổi | **1** |

- Regression native `2026-11-14T11:34:08.5787280→11:51:28.1187071+07:00`, helper end `11:51:32.6436158+07:00`. XML/native totals khớp. Cả4 failures đúng whitelist, outside-approved=0: LoginAttempt PEXPIRETIME frame133; LoginAttempt CapturedOutput frame313 chỉ fixture email, không fixture IP/outage literal; ImportConfirm frame398 p95=40403ms so với30000ms; QuestionImport frame304 p95=59866ms so với30000ms. Không phát sinh ngoại lệ thứ5 QuestionAdmin p95. **Whitelist không đổi native1 thành PASS.** Chỉ đạo mới nhất STOP nếu bất kỳ gate FAILED/UNKNOWN thắng classifier `continuationEligible=true`; không chạy tiếp isolation/Full hoặc tự mở vòng fix/rerun.
- Fresh regression family XML: T06 ledger16, T08 redeem56, T09 wallet/history55, T12 consume/refund70, auth lock16 — mỗi nhóm F0/E0/S0; bộ T10 targeted trong regression213/F0/E0/S0. Các nhóm PASS không thay verdict regression FAILED và không chứng minh thứ tự isolation.
- Targeted HTTP19/F0/E0/S0 có đủ19 cleanup JSON đối chiếu1:1 theo display name: users/roles/audit/ledger sở hữu đều0; catalog/global audit trước–sau bằng nhau và hash khớp; JDBC thực tế BigDecimal/PGobject/String đúng kỳ vọng, một PID/container/database identity. Hai fixture failures cũ ở absent PREMIUM và audit rollback create=true nay PASS, giữ nguyên guarded DELETE và assertion affectedRows=1. Đây là bằng chứng cleanup của targeted, **không** phải ordered T08/T09/T12 isolation.
- Ordered25 suites trong cùng Maven/fork, Full verify, full-exec JaCoCo và G2: **NOT RUN**. Không công bố Full deadline hoặc dùng targeted/regression exec làm coverage. ROUND2_STOP ZIP đang được controller đóng gói; chưa có metadata/SHA nghiệm thu tại checkpoint này, không tự đặt hash.
- Evidence: `plans/261113-1839-spec013-t10-shadow-plan/g1-fix-round2-20261114/` — `round2-native-commands.json`, `20261114-112920-560-contract-reconciliation.json`, `20261114-113115-130-targeted-reconciliation.json`, `20261114-113404-494-regression-reconciliation.json`, `20261114-113404-494-regression-family-results.json`, `targeted-cleanup-isolation-report.json`. Giữ toàn bộ baseline/STOP ZIP và các FAILED/UNKNOWN/STOP cũ bên dưới; không ghi đè lịch sử.

## 14/11/2026 — T-10 G1 Fix Round 2 được Leader duyệt, đang xác minh

- Tiếp tục đúng checkpoint STOP; branch/base de732866 và exact19 giữ nguyên. Q1 revision #2: lockPlan dùng FOR NO KEY UPDATE; ADMIN vẫn không khóa users. Không rebase main hoặc sửa locked YAML; PLAN_IN_USE vẫn trước INTERNAL_ERROR.
- Q4 mới nhất: FREE chỉ semantic-validate name/description; bỏ qua durationDays/price/benefits/isActive (kể cả price="abc", unknown benefit keys hoặc giá trị integer ngoài range), nhưng JSON sai kiểu/malformed vẫn422. Bốn field này bị loại trước service/DB/audit after. PREMIUM giữ toàn bộ validation cũ. Benefits unordered đã được Leader duyệt, không thêm sorting.
- Đã sửa explicit JsonNode trong test và cleanup AuditService/Clock bằng biến typed/reset riêng. Ba integration classes khôi phục catalog trước test, chỉ dọn dữ liệu sở hữu; không xóa PREMIUM có user tham chiếu. Compile checkpoints đều có native exit riêng; compile không phải runtime acceptance. Evidence mới trong `plans/261113-1839-spec013-t10-shadow-plan/g1-fix-round2-20261114/`.
- Còn phải xác minh strict generated/runtime contract, toàn bộ targeted11, regression auth/credit/contract T06/T08/T09/T12, isolation cùng một Maven với thứ tự thực tế, rồi Full verify60phút và JaCoCo từ đúng full execution. STOP và ZIP nếu gate FAILED/UNKNOWN; chưa G1 hoàn tất/G2/G3 APPROVED. Mọi evidence/ZIP thất bại cũ được giữ nguyên bên dưới.

## 14/11/2026 — T-10 Leader-approved G1 Fix: STOP tại test-compile đầu tiên

- Đã điều tra đầy đủ F4/E16, đối chiếu source/YAML/V1/FE và bytecode cleanup; báo cáo phân biệt3 nhóm lỗi gốc với2 fixture failures dây chuyền. ZIP pre-fix `T10_G1_FIX_BASELINE_20261114.zip` được tạo trước mọi sửa source/test,1.278.277bytes,SHA256 `107CDD0957247F39BE3867E1AADF2280E052CDF5D9DA14C1ABC7FC8013E54182`,154 entries/19 source files; SHA từng entry đã kiểm.
- Partial Fix chỉ sửa4 code files trong19: Benefits.java, PlanView.java, PlanUpdateRequest.java và PlanUpdateRequestTest.java. Schema dùng nested non-container representation; runtime map/validation/Q1#2 không đổi. Test giữ exact4key/value không áp thứ tự Map.of và thêm kiểm thứ tự JSON không đổi nghĩa. Chưa sửa cleanup/isolation.
- Test-compile Fix **FAILED/native1**, `07:21:53.1139104→07:23:03.0621941+07:00`,14/11/2026. Javac báo `PlanUpdateRequestTest.java:94` ambiguous assertThat(IntPredicate)/assertThat(Predicate), do generic valueToTree trong test mới. Đây là lỗi test mới được thêm, không thuộc whitelist. T/F/E/S=N/A vì chưa chạy test. Helper ended07:23:04.5306070, không UNKNOWN, không chạy trùng/kill process.
- **STOP ngay theo Phase6**, không tự sửa/rerun. Affected/targeted/regression/ordered same-Maven isolation chưa chạy sau Fix. Full verify/JaCoCo vẫn G2 NOT RUN; không đề nghị G2/G3. ZIP STOP bắt buộc và manifests lưu tại `plans/261113-1839-spec013-t10-shadow-plan/g1-fix-20261114/`. Source cleanup cũ vẫn lỗi; schema mới chưa có runtime acceptance. Giữ tất cả failed evidence và baseline ZIP nguyên trạng.

## 14/11/2026 — T-10 Q1 revision #2: G1 STOP sau targeted FAILED

- Branch/base giữ `spec/013-t10` / `de732866cac876e3fea6946f1e101eb259728523`. Exact19 đã viết/sửa; không ngoài allowlist, staging rỗng, conditional `RedeemServiceTest.java` nguyên hash. Không rebase/merge/pull main, không commit/push/PR/G3 hoặc Neon SQL.
- Tổng11 test-compile checkpoints (gồm2 cũ,9 mới; contract có2 lần bổ sung assertion theo review tĩnh), tất cả native0. Chín test files đã được compile riêng sau mỗi lần viết/sửa. Không gọi compile là test PASS. Bảo toàn toàn bộ evidence cũ và recovery760/760.
- Targeted command: `.\mvnw.cmd -B "-Djava.io.tmpdir=<attempt>/maven-tmp" -Dtest=PlanUpdateRequestTest,PlanAdminServiceTest,PlanAdminIntegrationTest,PlanAdminConcurrencyIntegrationTest,CreditPlanContractTest,PlanAdminSecurityConfigIntegrationTest,GlobalExceptionHandlerTest,RedeemIntegrationTest,RedeemServiceTest,RedeemConcurrencyIntegrationTest,AuthPlanApiIntegrationTest test`. Exact temp path/native command trong `g1-resume-q1r2-20261114/20261114-012112-470-targeted/status.json`.
- Targeted **FAILED/native exit1**, start `2026-11-14T01:21:16.1506926+07:00`, end `2026-11-14T01:23:16.9352244+07:00`, **191 tests/F4/E16/S0**, đúng11 suites; XML/Maven totals và testcase counts khớp, thiếu/thừa0, source manifest không đổi. Reconciliation `continuationEligible=false`, **20 occurrences ngoài whitelist**, không có exception môi trường được áp dụng. Không đổi FAILED thành PASS.
- Q1#2 PostgreSQL evidence trong chính targeted: `AuthPlanApiIntegrationTest`16/F0/E0/S0; hai test audit FK hoàn tất khi lockPlan còn giữ và hai lockPlan cùng user tuần tự đều PASS. `PlanAdminConcurrencyIntegrationTest`12/F0/E0/S0, gồm ADMIN+LEARNER exact-cycle, A–F, rollback và renew hết hạn. Đây là measured outcomes của targeted, không thay thế full regression. T08 targeted subset: RedeemService12 + RedeemIntegration12 + RedeemConcurrency4 đều F0/E0/S0.
- Failure mới: CreditPlanContractTest:154 có1F do generated Benefits inline với property `empty`, không có component/ref Benefits theo YAML khóa; PlanUpdateRequestTest:78 có1F vì cùng entry/value nhưng khác thứ tự Map.of; PlanAdminIntegrationTest:85 có16E ClassCastException trong cleanup Mockito.reset/AopTestUtils (AuditService bị cast Clock), và2F ở fixture removeUnreferencedPremium:382 (DELETE0 do còn FK). Cleanup lỗi có thể để lại fixture; chưa chạy lại để khẳng định mọi secondary failure sẽ tự hết.
- **STOP đúng điều kiện Leader; không tự mở vòng fix/rerun.** Regression152 (T06/T08/T09/T12/auth), Full Verify60phút, full-exec JaCoCo đều NOT RUN; không công bố deadline Full vì chưa launch, không dùng targeted exec làm coverage. ZIP G2 NOT CREATED; không G2/G3 APPROVED. Native helper/Surefire đã kết thúc, không kill process.
- Evidence gốc, source manifests, các review và tất cả attempts được giữ tại `plans/261113-1839-spec013-t10-shadow-plan/g1-implementation-20261113-2215/` và `g1-resume-q1r2-20261114/`. Ledger tổng hợp `all-t10-native-commands.json`, targeted reconciliation và `g1-stop-report.md` ghi chi tiết. Cần Leader duyệt vòng sửa trong19 file và quyền rerun trước khi tiếp tục.

## Q1 REVISION #2 — 14/11/2026 — Leader duyệt G1 resume

- Quyết định mới nhất: **lockPlan dùng FOR NO KEY UPDATE; ADMIN vẫn không khoá users**. Chỉ đổi SQL trong `AuthApiService.lockPlan` và thêm comment Leader; giữ `readPlan`, `countActivePlan` và các method/semantics khác. Khóa này vẫn tuần tự hóa hai `lockPlan` trên cùng user, đồng thời tương thích FK key-share từ bảng khác.
- Lý do: review độc lập G1 phát hiện cùng actor ADMIN+LEARNER có thể tạo vòng khóa redeem users→plans và ADMIN plans→audit FK actor_id→users. Đổi khóa user trong lockPlan tránh xung đột với audit FK key-share, giữ audit atomic và actor thật; ADMIN không chủ động khóa users. Finding cũ là source-confirmed, chưa có measured PostgreSQL outcome tại thời điểm ghi quyết định.
- Giữ T-08 users(lockPlan)→atomic claim card→plans. Chỉ SUBSCRIPTION catalog PREMIUM thêm FOR SHARE; CREDIT/ledger/rollback giữ nguyên. ADMIN plans FOR NO KEY UPDATE; countActivePlan SELECT thường; concurrent create INSERT ON CONFLICT(code) DO NOTHING rồi reselect khóa. Mã SUBSCRIPTION đã phát vẫn nạp được khi PREMIUM inactive. Không khôi phục thứ tự card→plans→users đã hủy.
- Allowlist chính thức **19 file**: exact17 G0 + MODIFY `src/main/java/com/cnhsk/auth/service/AuthApiService.java` + MODIFY `src/test/java/com/cnhsk/auth/AuthPlanApiIntegrationTest.java`. `RedeemServiceTest.java` vẫn conditional, tối đa file20, chỉ sửa SQL expectation khi actual failure đúng nguyên nhân FOR SHARE; hiện chưa sửa.
- Regression bắt buộc PostgreSQL thật: giữ lockPlan trong A mà B INSERT audit actor_id cùng user phải hoàn tất trước khi A release; hai lockPlan cùng user phải chờ nhau. Thêm concurrency cùng ADMIN+LEARNER actor giữa subscription redeem và PREMIUM update, deterministic synchronization, independent transactions, bounded lock_timeout; giữ Cases A–F, guard/audit/rollback và Q2–Q5.
- Bảo toàn recovery 760/760 SHA và hai compile native0 cũ. Gate mới sau auth test đang chạy; targeted/regression/full/JaCoCo chưa chạy, chưa G2/G3. Evidence mới: `plans/261113-1839-spec013-t10-shadow-plan/g1-resume-q1r2-20261114/`; compile auth đầu tiên vẫn lưu trong G1 artifact root cũ, không ghi đè attempt.
- Leader báo main tới948a89f và PR151/152 sắp sửa ErrorCode: **không rebase/merge/pull hiện tại**. Giữ PLAN_IN_USE ngay trước INTERNAL_ERROR trên base de732866; chỉ xử lý drift theo chỉ đạo Leader sau code+G2. Không commit/push/PR hoặc chuyển G3.

## Q1 REVISION — 13/11/2026 — T-10 G1 tiếp tục

- Leader hủy card→plans→users; chọn users(lockPlan)→atomic claim card→plans, giữ T-08. Chỉ query catalog PREMIUM trong SUBSCRIPTION thêm FOR SHARE; CREDIT không đổi/phân loại lại.
- ADMIN dùng SELECT catalog FOR NO KEY UPDATE, không FOR UPDATE; không khóa users để tránh vòng khóa với redeem. Khóa này tương thích FOR KEY SHARE từ FK auth.users.plan_code→plans(code), nhưng xung đột FOR SHARE của SUBSCRIPTION để bảo vệ guard E6. Count qua AuthApi SELECT thường, Clock/count sau catalog wait. Đây là protocol phải được chứng minh trong concurrency tests, chưa gọi static reasoning là measured lock evidence.
- Concurrent create: SELECT FOR NO KEY UPDATE; absent INSERT ON CONFLICT(code) DO NOTHING; insert1 PLAN_CREATED; insert0 reselect FOR NO KEY UPDATE rồi update/guard E6. Không advisory lock/schema. Audit cùng transaction.
- Case B đã được Leader làm rõ riêng: “Giữ T-08: mã đã phát vẫn nạp được; sửa kỳ vọng inactive của Case B (khuyến nghị, đúng patch hẹp).” Vì vậy redeem đợi ADMIN rồi đọc catalog mới và vẫn thành công với mã đã phát khi inactive; giữ nguyên inactivePremiumStillHonorsAlreadyIssuedCard, không thêm check is_active hay PLAN_NOT_AVAILABLE vào redeem.
- Q2–Q5 giữ ANSWERED: Q2-A guard-time/no T12 change/no expiry-overlap serialization; Q3 missingFREE422/no defaults; Q4 FREE noneditable values/nulls ignore incl benefitsNULL nhưng sai JSON types422, PREMIUM benefits required; Q5 description omit/null SQLNULL. Price output2 decimals/input3decimals422; audit PLAN_CREATED/PLAN_UPDATED before/after đủ6fields.
- Exact17 giữ nguyên; RedeemServiceTest là file18 chỉ được sửa nếu actual failure chỉ do SQL string, phải lưu failed evidence trước và chỉ đổi SQL expected. Hiện chưa chạm file18. Không rebase/merge/pull/commit/push/PR. Base de732866cac876e3fea6946f1e101eb259728523.
- Production đã được viết trong17; compile checkpoint đầu tiên PlanUpdateRequestTest: native0,22:29:41.1677491→22:30:49.5255510+07. Targeted/regression/full/JaCoCo chưa chạy ở checkpoint này. Mọi kết quả STOP cũ bên dưới là lịch sử được bảo toàn, không xóa hoặc đổi nhãn.

## 2026-11-13 — T-10 G1 APPROVED; Q1–Q5 ANSWERED; STOP ở lock-order preflight

Leader duyệt exact17 (11 ADD+6 MODIFY), branch spec/013-t10, base de732866cac876e3fea6946f1e101eb259728523. Chưa thay production/test. G1 STOP theo chính điều kiện Q1 của Leader vì thứ tự khóa mới không thể đạt bằng patch hẹp đã đề xuất khi giữ CREDIT tuyệt đối nguyên trạng. Không có failure test; Maven/targeted/regression/full/JaCoCo đều NOT RUN, native exit N/A. Không commit/push/PR hoặc rebase/merge/pull main.

| Quyết định | Trạng thái và chỉ đạo cuối của Leader |
|---|---|
| Q1 | ANSWERED: ADMIN catalog FOR UPDATE, chỉ count qua AuthApi. SUBSCRIPTION phải card → plans FOR SHARE → auth.users, trước lockPlan; CREDIT không đổi. Concurrent create dùng SELECT FOR UPDATE → absent INSERT ON CONFLICT(code) DO NOTHING → insert1 create, insert0 reselect/update/guard. Khác proposal G0 user→card→plans. Implementation đang BLOCKED, không tự chọn lại thứ tự. |
| Q2 | ANSWERED — A: guard-time tại ADMIN; giữ T12 và không cam kết serialize consume qua expiry. Không thêm ba file phương án B. |
| Q3 | ANSWERED: thiếu FREE →422 PLAN_NOT_AVAILABLE; không tạo defaults hoặc audit. |
| Q4 | ANSWERED theo bản Leader đã chỉnh: FREE chỉ cập nhật name/description; field không editable bị ignore kể cả null (bao gồm benefits:null), nhưng sai kiểu JSON không-null →422. PREMIUM benefits bắt buộc/non-null, key lạ422 và validate toàn bộ contract. PREMIUM_PLUS không cho PUT. Không áp lại lựa chọn G0 cũ từ chối FREE benefits:null. |
| Q5 | ANSWERED: description omit hoặc null đều ghi SQL NULL, không giữ ngầm giá trị cũ. |

Price theo chỉ đạo cuối: output string đúng2 chữ số; 0→0.00,12.5→12.50; input3 chữ số kể cả trailing0→422; BigDecimal/no rounding. Audit actions theo chỉ đạo cuối là PLAN_CREATED/PLAN_UPDATED, payload before/after đủ name,description,durationDays,price,benefits,isActive, cùng transaction. Đây là yêu cầu đã ghi nhận, chưa có implementation/test evidence. YAML và source production giữ nguyên.

Evidence hiện trạng: RedeemService.java:62 lockPlan →:64 hash →:66–69 atomic UPDATE card RETURNING card_type →:75 nhận Card →:83 mới phân nhánh SUBSCRIPTION →:108 plain SELECT plans. AuthApiService.java:124–129 lockPlan là auth.users FOR UPDATE. RedeemRequest chỉ có code; CardCodes.generate sinh16 ký tự ngẫu nhiên, không có type metadata. V1:456 used_by FK auth.users.

Thêm FOR SHARE tại query108 vẫn là users→card→plans, không đáp ứng Q1 mới. Chuyển toàn bộ UPDATE trước sẽ đổi CREDIT và FK used_by có thể chạm auth.users trước catalog. Muốn phân nhánh trước user lock cần preclassification/routing và card-row-lock protocol khác; đó là thiết kế lại T08 bị Leader cấm tự làm. RedeemServiceTest.java:88–100 bảo vệ CREDIT order; file này ngoài17, subscription fixtures115–177 chỉ có query shapes hiện hữu. Không nới assertion, không sửa file ngoài scope để che khác biệt.

Báo cáo chặn: plans/261113-1839-spec013-t10-shadow-plan/g1-lock-order-preflight-20261113-2202/ (source snapshots/hash, độc lập review, report). Cần Leader điều chỉnh ràng buộc/phạm vi Q1 trước khi tiếp tục; Q1–Q5 không bị mở lại như câu hỏi nghiệp vụ chưa trả lời. L2/main drift chưa kiểm remote ở lượt preflight này; base được giữ nguyên. Toàn bộ lịch sử bên dưới được bảo toàn.

## 2026-11-13 — T-09 G1 Fix: kết quả cuối G2, chờ Leader review

Full Verify chạy đúng một lượt, hoàn tất trước deadline 17:30:33.5174436 (+07). Kết quả **FAILED, native exit 1: 4.305 tests/F4/E0/S0**, đủ 280 suites; XML và Maven summary khớp, không suite thiếu/thừa, không probe chạy lại, không failure ngoài năm ngoại lệ đã duyệt. Regression giữ **UNKNOWN native exit/end/duration**, 1.851/F4/E0/S0; Leader đã chấp nhận UNKNOWN để tiếp tục Full, không chạy lại hoặc suy exit 1 từ BUILD FAILURE.

| Lệnh | Native start (+07) | Native end (+07) | Duration stopwatch | Tests/F/E/S | Native exit | Trạng thái |
|---|---|---|---:|---|---|---|
| test-compile (src/test/java/com/cnhsk/auth/AuthApiServiceTest.java explicit expiry fixtures) | 2026-11-13T10:38:07.6035891+07:00 | 2026-11-13T10:40:00.3813216+07:00 | 112.785 s | N/A (no tests) | 0 | PASSED |
| test-compile (src/test/java/com/cnhsk/shared/config/WalletQuerySecurityConfigIntegrationTest.java empty versus whitespace) | 2026-11-13T10:45:39.4123573+07:00 | 2026-11-13T10:46:51.4170452+07:00 | 72.009 s | N/A (no tests) | 0 | PASSED |
| test-compile (src/test/java/com/cnhsk/contract/CreditWalletContractTest.java pinned generated nullable backlog shape) | 2026-11-13T10:47:55.4352302+07:00 | 2026-11-13T10:48:50.8951806+07:00 | 55.464 s | N/A (no tests) | 0 | PASSED |
| javac probe | 2026-11-13T10:51:09.0057201+07:00 | 2026-11-13T10:51:14.0071715+07:00 | 5.004 s | N/A (no tests) | 0 | PASSED |
| CardBatch local probe | 2026-11-13T10:51:32.2532880+07:00 | 2026-11-13T10:52:27.8385533+07:00 | 55.590 s | 1/0/0/0 | 0 | PASSED |
| Targeted T-09 (9 suites) | 2026-11-13T11:06:47.6398781+07:00 | 2026-11-13T11:09:08.1245108+07:00 | 140.492 s | 96/0/0/0 | 0 | PASSED |
| Regression (116 suites) | 2026-11-13T11:10:50.7441944+07:00 | UNKNOWN; log only 2026-11-13T14:13:16.0000000+07:00 | UNKNOWN | 1851/4/0/0 | UNKNOWN | UNKNOWN |
| Full verify (280 suites) | 2026-11-13T16:31:09.1090699+07:00 | 2026-11-13T17:00:53.5023914+07:00 | 1784.411 s | 4305/4/0/0 | 1 | FAILED |
| JaCoCo report/check | 2026-11-13T17:01:59.4074777+07:00 | 2026-11-13T17:02:08.2177814+07:00 | 8.814 s | N/A (no tests) | 0 | PASSED |

Lệnh đầy đủ, selectors và preflight/native PID nằm trong cumulative-native-command-ledger.json; ba test-compile được chạy ngay sau từng testfile. JDK 17.0.9, Docker Server 27.2.0; không có Maven gate trùng. Regression log báo Finished-at 14:13:16 và 03:02h, chỉ là evidence log, không thay native completion bị mất.

Bốn failed tests từ Surefire Full (giữ nguyên tên method; CapturedOutput là tham số của method thứ hai):

- 1. com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry — frame 133; exact approved environmental exception.
- 2. com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings — frame 313; exact approved environmental exception.
- 3. com.cnhsk.learning.content.importer.ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget — frame 398; Full 36054 ms; regression 41682 ms; limit 30000 ms; exact approved environmental exception.
- 4. com.cnhsk.learning.content.importer.QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries — frame 304; Full 59361 ms; regression 71307 ms; limit 30000 ms; exact approved environmental exception.

LoginAttempt CapturedOutput chỉ trúng fixture email, không trúng outage literal; không mở rộng ngoại lệ sang dữ liệu khác. Ngoại lệ thứ năm QuestionAdminControllerIntegrationTest.NFR_P02_fiveThousandSelfAuthoredQuestionsAreListedByKidWithinOneHundredMillisecondsP95 không phát sinh trong Full này. Tất cả failure đúng java.lang.AssertionError, source frame và assertion đã đối soát.

JaCoCo report/check riêng native exit 0, không chạy tests, dùng đúng maven-tmp/cnhsk-api-jacoco.exec của Full vừa hoàn tất. Project LINE **96,7272% (12029/12436)**; Credit và credit/web LINE **96,7492% (625/646)**. Exec SHA256 E7A0751F5841CEDDAB86AEE69FBDDD423982C448A7519315823EEA9DF922599C; một session nằm trong native interval Full, khớp XML session; source/class hashes không đổi. Hash được ghi sau report và kiểm trước/sau inspection/archive; không tuyên bố có digest trước report. Không dùng exec targeted/regression hoặc lượt bị ngắt.

NFR-P03 ghi đầy đủ các lượt, mỗi lượt warm-up20 +100 mẫu, không chọn lượt đẹp và không hard assertion 100 ms:

| Lượt | Warm-up + mẫu | p50 ms | p95 ms |
|---|---:|---:|---:|
| Original targeted FAILED (preserved) | 20 + 100 | 21.2587 | 28.3492 |
| Fix targeted | 20 + 100 | 21.5407 | 33.8533 |
| Regression UNKNOWN | 20 + 100 | 13.7458 | 17.2356 |
| Full FAILED native 1 | 20 + 100 | 13.1362 | 17.0896 |

Generated OpenAPI: path/method/status/required/numeric types và các schema khác khớp locked YAML. Technical debt duy nhất Transaction.featureCode: exact generated {"type":"null","$ref":"#/components/schemas/PaidFeature"}, khác oneOf(PaidFeature,null) của YAML. Test pin toàn bộ object, shape thay đổi phải FAIL; không customizer/YAML/shared handler. Runtime featureCode:null có key thật; history integration kiểm cả PaidFeature non-null và keySet chính xác 8 trường seq,type,amount,balanceBefore,balanceAfter,featureCode,reason,createdAt. Wallet.plan.code giữ đúng PlanCode.

CardBatch local ADMIN probe duy nhất đã trả 422 VALIDATION_FAILED cho GET /api/admin/card-batches?page=%20 lúc 2026-11-13T10:52:26.799974900+07:00. Không gọi production hoặc commit mã probe. Ba lỗi targeted cũ đều được sửa trong ba test được duyệt; actual targeted 96/96 PASS/native0, giữ nguyên evidence FAILED cũ96/F2/E1/S0/native1.

Scope cuối: 19/19 (10 ADD +9 MODIFY), Fix chỉ ba test + bổ sung questions.md; 15 file khác, gồm8 production và tasks.md, giữ SHA baseline. SecurityConfig/YAML/config/writer/migration/FE không đổi; matcher LEARNER hiện hữu từ T-08 được giữ nguyên. HEAD/base b8e812aae2aa15219c0e9203943a40d699a46cf1, branch spec/013-t09, index rỗng, diff --check exit0. Không commit/push/PR, không tích hợp main2735ac8 do Leader thông báo. tasks.md giữ nội dung lịch sử theo giới hạn Fix, chờ G3 được duyệt để đồng bộ.

Evidence authoritative: plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032/{result-summary.json,final-report.md,cumulative-native-command-ledger.json,20261113-163033-517-full-reconciliation.json,20261113-163033-517-full-coverage-inspection.json}. Dừng G2 chờ review; không tự phê duyệt G2/G3.

## 2026-11-13 — Full Verify đã release một lượt với deadline công bố trước

- Sau quyết định chấp nhận regressionUNKNOWN, helperđộc lập hiddenPID36724 được chuẩn bị và chờ READY_FOR_RELEASE, chưa có Maven. Controller công bố timeout60phút từ helperStart16:30:33.5174436+07, deadline17:30:33.5174436+07 (10:30:33.5174436Z) trước khi ghi releasefile.
- Releasefile ghi lúc16:31:08.239+07; nativeverifyCMDPID35600 bắt đầu2026-11-13T16:31:09.1090699+07:00. Deadline tính cảpreflight/standby, không gia hạn; không ngắt vìtestfailure. Chỉ đúngownedtree được xửlý nếuvượtdeadline.
- Sourcebusiness và3tests vẫn đóng băng; không rebase/merge/pull hoặc rerunregression. Helper/artifact có boundedCIMpoll+nativehandlecapture đểlưu actualexit độc lập phiên;archiveerrors khôngđượcche nativeexit đãthu.
- Evidence: plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032/20261113-163033-517-full/status.json, full-deadline-publication.json,full-release.json,ownership-observations.jsonl. Checkpointnày chưa cóFullresult/JaCoCo,khôngtuyênbốPASS hoặcG2/G3APPROVED.

## 2026-11-13 — Regression UNKNOWN được Leader chấp nhận để tiếp tục Full một lượt

- Sau gián đoạn phiên, regression1851/F4/E0/S0 đã có BUILD FAILURE/Finished-at trong stdout và đủ116fresh suites; cả4failure khớp đúng ngoại lệ đã duyệt, không missing/extra/Probe. Nhưng helper ghi Call cancelled, nativeExitCode=null, không có nativeEnd; session40219 không còn. Không suy native1 từ log, không sửa status gốc hoặc chạy lại regression.
- Native start11:10:50.7441944+07; Maven log báo Finished-at14:13:16+07/Total time03:02h, đây chỉ là mốc log, không phải native completion đã được capture. HelperEnd14:07:14.6548007+07 với trạng tháiUNKNOWN.
- Leader trả lời rõ: “Chấp nhận regression UNKNOWN và cho chạy Full Verify một lượt bằng helper độc lập”. Quyết định này chỉ cho phép bước Full mới; không đổi UNKNOWN thànhPASS/FAILED/native1, không rerun regression.
- Bốn failure: LoginAttempt PEXPIRETIME(frame133),LoginAttempt CapturedOutput chỉfixtureemail(frame313);ImportConfirm FR023p95=41682ms≥30000(frame398);QuestionImport NFR_P04p95=71307ms≥30000(frame304). Không ngoại lệ thứ5QuestionAdmin trong regression. Full phải phân loại riêng từ XML của chính lượtFull.
- Reconciliation posthoc dùng nativeStart đến logFinished-at+1giây để nhận diện XML; ghi rõ không phải native-completion interval. Evidence: plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032/20261113-111046-498-regression-UNKNOWN-posthoc-reconciliation.json; originalstatus/stdout được giữ nguyên.
- Full mới dùng helperđộc lập, timeout60phút/deadline công bố trước Mavenrelease; không ngắt vìtestfailure; coverage chỉ lấyexec củaFullhoànchỉnh. Chưa cóFullresult hoặcJacoco tại checkpoint này.

## 2026-11-13 — T-09 Fix targeted 96/96 PASS (native exit 0)

- Actual9fresh suites,72source testmethods,96JUnitinvocations/F0/E0/S0; đối chiếu với96lượt FAILED cũ: số lượng không đổi, không missing/extra, không tính stale ProbeXML. Hai nhánh expiresAt vẫn ở cùng một testmethod; đổi tên pagination không xóa hoặc thêm invocation.

| Native bắt đầu +07 | Native kết thúc +07 | Duration stopwatch | Tests/F/E/S | Native exit |
|---|---|---:|---|---:|
| 2026-11-13T11:06:47.6398781+07:00 | 2026-11-13T11:09:08.1245108+07:00 | 140.492s | 96/0/0/0 | 0 |

~~~powershell
.\mvnw.cmd -B "-Djava.io.tmpdir=D:\code_huy\Java Language\CNHSK\.worktrees\spec013-t09\plans\261112-0445-spec013-t09-shadow-plan\g1-fix-20261113-1032\20261113-110644-109-targeted\maven-tmp" -Dtest=AuthApiServiceTest,AuthPlanApiIntegrationTest,WalletQueryServiceTest,WalletQueryIntegrationTest,CreditTransactionHistoryIntegrationTest,WalletQuerySecurityConfigIntegrationTest,CreditWalletContractTest,CreditRedeemContractTest,CreditRedeemSecurityConfigIntegrationTest test
~~~

| Lỗi cũ | Fix được duyệt | Actual targeted mới |
|---|---|---|
| AuthApiServiceTest.readPlanMapsAnImmutableSnapshotWithoutLockOrWrite: ERROR Mockito | Explicit nullableTimestamp stub;FREE/null +PREMIUM/exactInstant .123456Z;giữ strictness/assertions | PASS;Authunit16/16,AuthPlanintegration14/14 |
| Pagination blank/whitespace expectation: FAILURE | Test đổi tên paginationAbsentAndEmptyUseDefaultsWhileWhitespaceIsValidationFailed;absent/emptydefault4calls,whitespace422 | PASS;security14/14;CardBatchencodedspaceprobe422độc lập đã ghi |
| CreditWalletContractTest nullable schema: FAILURE | Pin duy nhất Transaction.featureCode exact type:null+$refPaidFeature;mọi shape khác FAIL;so sánh còn lại giữ nghiêm | PASS;contract2/2,mismatch0,nullabledebt1 |

- Generated shape thực tế vẫn {"type":"null","$ref":"#/components/schemas/PaidFeature"}; locked YAML vẫn oneOf(PaidFeature,null). Backlog nay được ghi đúng một entry, không sửa YAML/customizer/production. Runtime JSON featureCode:null có mặt;history integration còn kiểm nonnull TRANSLATE/GRADING và exact8keys.
- NFR-P03 lượt targeted Fix:20warm-up+100mẫu HTTP200 đầy đủ;p50 21,5407ms,p95 33,8533ms,max68,7168ms;đối chiếu100ms,khônghardassert/chọnrun. Giữ100rawsamples và báo riêng với lượt FAILED trước (p50 21,2587,p95 28,3492).
- Source744/class-resource740 hash entries không đổi qua lượt targeted;không có production/test thay đổi sau khi freeze. Các báo cáo fixed-targeted-reconciliation.json,fixed-targeted-nfr-reconciliation.json,fixed-targeted-source-class-hashes.json nằm tại plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032.
- Regression116suites đã được release riêng sau helper targeted kết thúc;full verify/JaCoCo chưa chạy tại checkpoint này. Chưa tuyên bố G2/G3APPROVED vàkhôngcommit/push/PR.

## 2026-11-13 — T-09 Fix: CardBatch local whitespace evidence

- Kiểm chứng đúng một lần bằng MockMvc + PostgreSQL/Redis Testcontainers, user local có role ADMIN trong DB và JWT ADMIN được JwtService thật ký. Không gọi production. Probe source nằm trong plan artifact, class nằm ở ignored target/test-classes; tên Probe không khớp Surefire default nên không chạy lại trong regular targeted/regression/full. Không đưa probe vào source19 hoặc commit.

~~~powershell
.\mvnw.cmd -B "-Djava.io.tmpdir=D:\code_huy\Java Language\CNHSK\.worktrees\spec013-t09\plans\261112-0445-spec013-t09-shadow-plan\g1-fix-20261113-1032\20261113-105128-686-targeted\maven-tmp" -Dtest=T09CardBatchWhitespaceProbe test
~~~

| Native bắt đầu +07 | Native kết thúc +07 | Duration stopwatch | Tests/F/E/S | Native exit |
|---|---|---:|---|---:|
| 2026-11-13T10:51:32.2532880+07:00 | 2026-11-13T10:52:27.8385533+07:00 | 55.590s | 1/0/0/0 | 0 |

- HTTP quan sát lúc 2026-11-13T10:52:26.799974900+07:00: GET /api/admin/card-batches?page=%20; rawQuery page=%20, decodedPage đúng một dấu cách; status422, error_code VALIDATION_FAILED, envelope đúng error_code/message/request_id.
- Evidence JSON: plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032/20261113-105128-686-targeted/maven-tmp/card-batch-whitespace-probe/observed-response.json; reconciliation local-card-batch-probe-reconciliation.json; probejavac source/compiler/argumentfile/native exit trong cùng evidence root.
- Kết quả xác nhận Q5 phân biệt empty với whitespace theo hành vi CardBatch hiện có. Không sửa CardBatchController/WalletController hoặc GlobalExceptionHandler. Các XML cũ ngoài nativeinterval được giữ và loại khỏi số đếm probe.
- Cả3Fix test-compile đã PASS/native0. Regular targeted9 đang được chạy riêng; chưa suy diễn kết quả từ probe.

## 2026-11-13 — T-09 G1 Fix được Leader duyệt (giữ base b8e812a)

- Leader duyệt một lượt Fix cho đúng ba lỗi targeted cũ; giữ exact19 (10ADD+9MOD), branch spec/013-t09, HEAD/base b8e812aae2aa15219c0e9203943a40d699a46cf1. Origin/main2735ac8/PR145 do Leader thông báo là base drift; không fetch/rebase/merge/pull main trong lượt này.
- Chỉ chỉnh AuthApiServiceTest.java, WalletQuerySecurityConfigIntegrationTest.java, CreditWalletContractTest.java và bổ sung questions.md. Không sửa AuthApiService/WalletController/production khác hoặc tasks.md. Các kết quả STOP/FAILED cũ bên dưới và ZIP cũ được giữ nguyên.
- Auth unit: explicit stub getTimestamp(plan_expires_at) cho cả null và có Timestamp; assert Instant chính xác, giữ assertions cũ, không lenient.
- Q5 được Leader giải thích: absent hoặc chuỗi rỗng page=/size= dùng0/20; chuỗi một dấu cách page=%20 trả422 VALIDATION_FAILED. Đổi tên test và phân biệt hai nhóm; không thay binding production.
- Nullable exception chỉ đúng Transaction.featureCode: pin generated schema chính xác {"type":"null","$ref":"#/components/schemas/PaidFeature"}; shape thay đổi phải FAIL. Mọi schema/path/method/status/required/type khác vẫn so nghiêm YAML khóa; runtimeJSONnull vẫn được kiểm. Đây là technical debt được chấp nhận tạm thời, không sửa YAML/customizer/shared error handler.
- Một lần local probe GET /api/admin/card-batches?page=%20 với user ADMIN fixture và JWT hợp lệ; nếu không422 thì STOP, không sửa CardBatchController. Probe ở plan artifact/ignored target, không thuộc19source và không được đưa vào commit.
- Preflight mới xác nhận JDK17.0.9/DockerServer27.2 và chỉ có IDEJava, không Maven khác. Sau mỗi testfile chạy test-compile ngay; rồi probe một lần, targeted9, regression SPEC002/SPEC013/SPEC001/các suite liên quan, full verify và đúng full execJaCoCo nếu gates trước đủ điều kiện.
- Full timeout60phút/deadline phải công bố trước release; không ngắt vì failure, chỉ timeout mới dừng ownedtree có lýdo/PID. Giữ đúng5ngoại lệ môi trường; ngoài whitelist STOP, không tự sửa/rerun để xanh. NFR-P03 warm20+100samples, p50/p95, không hardassert100ms.
- Evidence mới: plans/261112-0445-spec013-t09-shadow-plan/g1-fix-20261113-1032/. Chưa tuyên bố kết quả mới hoặc G2/G3APPROVED; không commit/push/PR.

## 2026-11-13 — T-09 G1 APPROVED, exact 19 file

Leader đã duyệt allowlist G0 Finalization: 10 ADD + 9 MODIFY, bỏ WalletOpenApiConfiguration, không thêm customizer/config/YAML/migration/FE hoặc sửa SecurityConfig/GlobalExceptionHandler/LedgerService writer/EntitlementService. Chưa được commit/push/PR; dừng tại G2 để review.

Trước code: tracked/index sạch, `git log --oneline 474bb74..HEAD` rỗng; fetch native exit 0 và origin/main đúng `b8e812aae2aa15219c0e9203943a40d699a46cf1`. Lệnh checkout được Leader chỉ định chuyển nhánh chưa có commit sang base này; không merge/rebase/pull/reset/clean. G0 artifacts untracked được giữ nguyên. T-08 đã MERGED PR #137, merge `09ea06a`, head `f3f1113`; không đổi các kết quả FAILED lịch sử bên dưới.

| Quyết định | ÁP DỤNG trong T-09 |
|---|---|
| Q1 | `Optional<PlanState> readPlan(long userId)` trong AuthApi; provider SELECT không FOR UPDATE, chỉ đọc, không yêu cầu transaction ghi; không đổi lockPlan/updatePlan. |
| Q2 | Optional.empty được WalletQueryService đổi thành 401 UNAUTHENTICATED cho cả hai GET; không 404 USER_NOT_FOUND hoặc empty-history 200 cho missing user. |
| Q3 | Cả hai query methods `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`; không row lock hoặc REQUIRES_NEW. |
| Q4 | Giữ used thực, remaining=max(0,limit-used); kỳ cũ chỉ project used=0 trong bộ nhớ, không UPDATE free_usage/usage_reset_at. |
| Q5 | Typed page default 0, size default 20; absent/blank dùng default, bounds/nonconvertible trả 422; tests viết trước controller handler, handler chuyển kiểu `@Hidden` cục bộ theo mẫu CardBatchController, không sửa shared handler. |
| Q6 | NFR-P03 warm-up 20 + 100 mẫu, báo p50/p95 và đối chiếu 100 ms, không hard assertion hiệu năng hoặc chọn lượt đẹp. |

- Matcher LEARNER của T-08 đã có: SecurityConfig dòng 167–168 trên base mới (G0 cũ: 164–165); giữ nguyên file và hash evidence. Safe GET cookie/bearer không cần CSRF.
- Wallet.plan.code là PlanCode công khai, được giữ theo YAML. Transaction JSON có tập key chính xác `seq,type,amount,balanceBefore,balanceAfter,featureCode,reason,createdAt`, gồm cả nullable keys; cấm cardId/mã thẻ code/hash/refTransactionId và mọi field nội bộ.
- UC-098/BR-098-3 yêu cầu lý do công khai; BR-098-4 cấm card material. Giữ reason lành tính, trả null nếu chứa chuỗi dạng mã 16 ký tự (kể cả format/alias/whitespace/chuỗi nối), hash 64 hex hoặc mẫu PII/token/secret. Không join card, không truy vấn theo từng item; policy ưu tiên không lộ dữ liệu, có thể ẩn cả reason trông giống mã. Không suppress toàn bộ reason ngoài SUBSCRIPTION.
- AT-12/13, Clock đầu tháng Việt Nam, snapshot read-only/không ghi ledger/Redis, missing-user 401 và pagination/privacy nằm trong test matrix. History có ba SELECT cố định (readPlan/count/page); wallet ba SELECT (readPlan/balance/catalog).
- Hai GET khai báo ApiResponses tường minh 200/401/403; history thêm 422. Contract suite dùng PostgreSQL thật và so hai chiều locked/generated/actual mappings. Chỉ lệch cách biểu diễn nullable Transaction.featureCode trong api-docs được ghi backlog, không STOP; runtime null và PaidFeature vẫn phải đúng. Lệch path/method/status hoặc failure ngoài năm ngoại lệ đã duyệt → STOP.
- Trạng thái hiện tại: **G1 STOP — targeted FAILED, 96 tests/F2/E1/S0, Maven native exit 1**. Không sửa source/tests hoặc rerun sau failure; regression/full verify/JaCoCo NOT RUN. Chưa có G2 APPROVED hoặc quyền G3.

Shadow Plan: `plans/261112-0445-spec013-t09-shadow-plan/`; bổ sung hiện hành `g1-approved-addendum.md`, gate evidence `g1-evidence/`. G0 là lịch sử, không thay thế evidence G1. Mỗi file test có test-compile ngay sau khi viết/chỉnh; compile-only test errors được sửa trong allowlist, mọi attempt được giữ nguyên.

### T-09 — G1 STOP / evidence bàn giao G2 ngày 2026-11-13

Không có commit T-09. HEAD/base b8e812aae2aa15219c0e9203943a40d699a46cf1, branch spec/013-t09; đúng 19 source paths (10 ADD + 9 MODIFY), index sạch. Điểm bắt đầu có timestamp độc lập là base checkout trong reflog: 03:21:58 +07; preflight đọc trước đó không có ledger timestamp riêng. G0 artifacts được giữ nguyên, quyết định mới ở addendum.

**Targeted FAILED/native exit 1: 96 tests, F2/E1/S0, đủ 9 suites.** 13 test-compile checkpoints đều PASS/native exit 0. Không rerun để chọn xanh, không sửa production/tests sau failure. Không có failure thuộc năm ngoại lệ môi trường.

| Phân loại | Exact test | Evidence và xử lý |
|---|---|---|
| ERROR ngoài whitelist → STOP | com.cnhsk.auth.AuthApiServiceTest.readPlanMapsAnImmutableSnapshotWithoutLockOrWrite | Mockito PotentialStubbingProblem: mock chỉ stub usage_reset_at, chưa stub getTimestamp(plan_expires_at); test dòng 179/185/187, provider dòng 117. Đây là lỗi test runtime, không phải lỗi compile được tự sửa. |
| FAILURE ngoài whitelist → STOP | com.cnhsk.shared.config.WalletQuerySecurityConfigIntegrationTest.paginationAbsentEmptyAndBlankUseCardBatchDefaults | Dòng 110: giá trị chỉ có khoảng trắng trả 422 thay vì default/200 theo Q5. Không đổi expectation hoặc tự sửa binding sau failure. |
| FAILURE thực tế; nullable backlog theo Leader | com.cnhsk.contract.CreditWalletContractTest.walletOperationsRegisteredRoutesStatusesSecurityParametersAndSchemasMatchTheLockedContractBidirectionally | Dòng 130: generated featureCode là {"type":"null","$ref":"#/components/schemas/PaidFeature"}, locked là oneOf(PaidFeature,null). Comparator ghi mismatch; backlog array gốc rỗng. Giữ nguyên cả hai và phân loại debt tại checkpoint này; không đổi exit 1 thành PASS. |

Chỉ riêng biểu diễn nullable featureCode được Leader cho ghi backlog. Runtime featureCode null và giá trị TRANSLATE/GRADING đã đạt integration tests. Path/method/status khớp hai chiều với YAML và RequestMappingHandlerMapping: wallet GET 200/401/403; history GET 200/401/403/422; redeem POST 200/400/401/403/422/429. Không customizer/YAML change.

Wallet PostgreSQL suite 13 invocations và history suite 8 đều PASS. Evidence có FREE used/remaining, stale projection không UPDATE, missing TRANSLATE benefit, expired PREMIUM→FREE, Clock tháng/năm/năm nhuận, snapshot khi writer commit giữa reads, owner/25 rows/seq DESC/long offset, missing-user 401 cả hai GET và không ghi ledger/usage/Redis business keys. Lý do công khai lành tính được giữ; 14 fixture reason nhạy cảm được trả null.

JSON transaction thực tế kiểm keySet đúng tám field seq,type,amount,balanceBefore,balanceAfter,featureCode,reason,createdAt, không thiếu hoặc dư; explicit nullable keys vẫn có mặt. Runtime JSON/types/null audit được giữ trong attempt targeted.

**NFR-P03:** một lượt targeted, warm-up 20 + đủ 100 mẫu HTTP GET PostgreSQL thật, tất cả status 200; p50 **21,2587 ms**, p95 **28,3492 ms**, max **29,7762 ms**, đối chiếu mục tiêu 100 ms; không hard assertion hiệu năng. Giữ mọi mẫu, không chọn run. Gate tổng vẫn FAILED.

| # | Lệnh/checkpoint | Native bắt đầu +07 | Native kết thúc +07 | Giây | Tests/F/E/S | Native exit/status |
|---:|---|---|---|---:|---|---|
| 1 | test-compile — AuthApiServiceTest.java | 2026-11-13 03:26:46.9254296 +07 | 2026-11-13 03:27:55.2288876 +07 | 68.305 | N/A — không chạy test | 0 / PASSED |
| 2 | test-compile — AuthPlanApiIntegrationTest.java | 2026-11-13 03:30:17.9940371 +07 | 2026-11-13 03:31:22.5203664 +07 | 64.531 | N/A — không chạy test | 0 / PASSED |
| 3 | test-compile — WalletQueryServiceTest.java | 2026-11-13 03:32:57.4867788 +07 | 2026-11-13 03:34:04.0864134 +07 | 66.604 | N/A — không chạy test | 0 / PASSED |
| 4 | test-compile — WalletQuerySecurityConfigIntegrationTest.java | 2026-11-13 03:35:29.5027741 +07 | 2026-11-13 03:36:25.0198585 +07 | 55.523 | N/A — không chạy test | 0 / PASSED |
| 5 | test-compile — CreditRedeemContractTest.java | 2026-11-13 03:37:54.2933227 +07 | 2026-11-13 03:39:03.6002522 +07 | 69.310 | N/A — không chạy test | 0 / PASSED |
| 6 | test-compile — CreditRedeemSecurityConfigIntegrationTest.java | 2026-11-13 03:39:45.6136901 +07 | 2026-11-13 03:40:50.4232236 +07 | 64.813 | N/A — không chạy test | 0 / PASSED |
| 7 | test-compile — WalletQueryIntegrationTest.java | 2026-11-13 03:44:50.1267316 +07 | 2026-11-13 03:45:49.3364770 +07 | 59.217 | N/A — không chạy test | 0 / PASSED |
| 8 | test-compile — WalletQueryIntegrationTest.java owned Redis scope | 2026-11-13 03:46:25.7835363 +07 | 2026-11-13 03:47:24.8312915 +07 | 59.053 | N/A — không chạy test | 0 / PASSED |
| 9 | test-compile — CreditTransactionHistoryIntegrationTest.java | 2026-11-13 03:49:25.7017031 +07 | 2026-11-13 03:50:23.4102031 +07 | 57.716 | N/A — không chạy test | 0 / PASSED |
| 10 | test-compile — CreditWalletContractTest.java | 2026-11-13 03:52:31.7845166 +07 | 2026-11-13 03:53:31.1052822 +07 | 59.324 | N/A — không chạy test | 0 / PASSED |
| 11 | test-compile — CreditWalletContractTest.java runtime JSON evidence | 2026-11-13 03:54:00.4187947 +07 | 2026-11-13 03:54:58.8836982 +07 | 58.470 | N/A — không chạy test | 0 / PASSED |
| 12 | test-compile — CreditWalletContractTest.java strict semantic schema audit | 2026-11-13 03:56:40.3123360 +07 | 2026-11-13 03:57:49.8698906 +07 | 69.562 | N/A — không chạy test | 0 / PASSED |
| 13 | test-compile — CreditWalletContractTest.java disjoint nullable feature alternatives | 2026-11-13 03:58:19.1922043 +07 | 2026-11-13 03:59:19.6140043 +07 | 60.426 | N/A — không chạy test | 0 / PASSED |
| 14 | Targeted exact 9 suites | 2026-11-13 04:00:21.8211717 +07 | 2026-11-13 04:02:28.3891898 +07 | 126.572 | 96/2/1/0 | 1 / FAILED |
| — | Regression (45 suites đã lập danh sách) | NOT RUN | NOT RUN | N/A | N/A | N/A — STOP |
| — | Full verify (timeout dự kiến 60 phút) | NOT RUN | NOT RUN | N/A | N/A | N/A — STOP |
| — | JaCoCo từ full-run exec | NOT RUN | NOT RUN | N/A | N/A | N/A — chưa có full run |

Giây dùng native stopwatch khi có, nếu không có thì native wall duration đã thu; JSON ledger giữ cả hai trường và full command. Không suy diễn native exit từ helper exit.

Không có full-run exec T-09 để nghiệm thu Project/Credit LINE; không dùng targeted exec hoặc coverage T-08. Các số T-08 4213/F4 và LINE 96,7092% / 96,3668% bên dưới chỉ là lịch sử đã nghiệm thu, không phải kết quả T-09.

Read-only closeout lúc 04:03:49 +07 xác nhận không còn helper/Maven/Surefire thuộc lượt này. Chỉ bàn giao G2 dạng STOP/FAILED để review, không commit/push/PR hoặc tự phê duyệt G2/G3. Việc đóng gói bị gián đoạn do giới hạn phiên và tiếp tục lúc 10:05 +07; không có Maven hoặc sửa production/tests trong khoảng gián đoạn.

Evidence: plans/261112-0445-spec013-t09-shadow-plan/g1-evidence/targeted-stop-report.md, targeted-reconciliation.json, native-command-ledger.json, targeted-nfr-reconciliation.json; raw attempt 20261113-040017-796-targeted được giữ nguyên.

## 2026-11-10 — T-08 G2 APPROVED, G3 housekeeping được Leader duyệt

- Leader duyệt G2 → G3 trên commit `ec2dce2a930cc8ad3b0263979b74ce1dd4014f19`, Draft PR #137. Đây là housekeeping, không phải Fix 3/2. Chỉ bỏ một dòng trống EOF ở đúng bảy file test và cập nhật tasks/questions; giữ production, Clock constructor, logic, assertions, test names và expected values.
- Giữ implementation base `9510643c36633f8b993e3f119bafe6b1772ffde7`; origin/main `f9be7145a4ff59ee82af3c2c5632c087ffeddf73` không được rebase/merge vào nhánh này. Leader xác nhận PR #136 không trùng file và sẽ tự integrated verify trên cây đã gộp. T-12 đã MERGED PR #134 tại main95f401f; SPEC-014/T-14 đã merge PR #135 tại main9510643, giữ nguyên trạng thái.

### Kết quả Fix 2/2 đã được G2 nghiệm thu

- Chẩn đoán Redis/JVM clock skew: 80 mẫu TIME pha đầu giới hạn Redis đi trước JVM1040,638–1183,473ms; 40 replay công thức PEXPIRETIME−JVM trả122 trong khi PTTL không vượt120s. Pha sau offset âm−416,626…−330,922ms trả120. Xác nhận cơ chế overshoot do hai clock độc lập; không tuyên bố tái hiện exact121 của Fix1 vì thiếu timestamp lịch sử.
- Fix2 thay PEXPIRETIME trừ JVM Clock bằng Redis PTTL tương đối: lấy max TTL hợp lệ của counter đang chặn, ceil milliseconds/1000, capping1…3600 giây; giữ bounded retry khi expiry race và fail-open/WARN an toàn khi Redis lỗi/missing/no-TTL. Giữ thresholds5/30, cửa sổ first-hit một giờ,429 không tăng counter và assertion119–120. Ba unit test bổ sung kiểm rounding/cap, không đọc JVM clock và nullTTL.
- Targeted Fix2:115/F0/E0/S0, native exit0. Regression Fix2:728/F2/E0/S0, native exit1/**FAILED**; đúng hai ngoại lệ LoginAttempt, unknown0. Toàn bộ115 cases targeted PASS trong Full Verify; regression chọn suite riêng và không chọn GlobalExceptionHandlerTest, suite này PASS ở targeted/full.
- Full Verify hiện hữu tự kết thúc:269 suites, **4.213 tests/F4/E0/S0, Maven native exit1 — FAILED**. Công bố start10/11/2026 00:45:34 +07, deadline01:45:34 +07; native run00:45:51,1394625–01:17:27,4548073 +07, không kill/restart vì failure. Không đổi thành PASS; cả bốn failure khớp chính xác whitelist, unknown0.

| Failed test đúng tên Surefire | Ngoại lệ Leader duyệt | Đối chiếu |
|---|---|---|
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry` | LoginAttempt PEXPIRETIME | AssertionError, frame dòng133, assertion thời hạn tuyệt đối Redis |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput)` | LoginAttempt CapturedOutput | AssertionError, frame dòng313, fixture trong captured output |
| `com.cnhsk.learning.content.importer.ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` | ImportConfirm FR023, chỉ p95 | AssertionError, frame dòng398;45972ms so với <30000ms |
| `com.cnhsk.learning.content.importer.QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` | QuestionImport NFR_P04, chỉ p95 | AssertionError, frame dòng304;56262ms so với <30000ms |

Ngoại lệ thứ năm `com.cnhsk.learning.knowledge.QuestionAdminControllerIntegrationTest.NFR_P02_fiveThousandSelfAuthoredQuestionsAreListedByKidWithinOneHundredMillisecondsP95` chỉ áp dụng assertion p95 dòng252/<100ms; **không phát sinh** trong lượt Full Verify này. Không miễn trừ assertion chức năng, error hoặc failure khác.

- JaCoCo report/check sau Full Verify: native exit0; Project LINE11814/12216 = **96,7092%**, Credit LINE557/578 = **96,3668%** (learning/credit và web). Dùng đúng exec full1.294.597byte, một session nằm trong native interval; XML/session/hash khớp, source/classes không đổi và không class mismatch. Coverage không thay đổi Full Verify FAILED/exit1 thành PASS.
- Evidence G2 được giữ nguyên tại `C:/WINDOWS/TEMP/cnhsk-spec013-t08-g1-fix2-20261109-233052/`. ZIP G2 SHA256 `5CD676AE6879403410404F8614DC8C30CE6A598555E4212BE849FFD2DA8E5C45`; đủ20file13ADD/7MOD,1251checksum khớp và patch replay20/20; G012/12 giữ nguyên. Các FAILED/UNKNOWN/STOP trước đây bên dưới vẫn là lịch sử, không bị ghi đè.

### NFR-P01 — giữ đầy đủ các lượt đo

Mỗi lượt có20 warm-up +100 successful CREDIT HTTP POST riêng mã với PostgreSQL/Redis thật; p50/p95 nearest-rank (index49/94), giữ đủ100 mẫu nanosecond của từng lượt trong evidence. Không bỏ outlier, không chọn lượt đẹp và không thêm hard assertion300ms. Lượt có lỗi ở test khác vẫn giữ số đo riêng, không dùng để gọi gate PASS.

| Lượt | p50 ms | p95 ms | Số mẫu | Gate gốc |
|---|---:|---:|---:|---|
| Targeted ban đầu | 33,8839 | 53,4196 | 100 | 111/F0/E1/S0, native UNKNOWN |
| Fix1 targeted | 39,2488 | 75,0112 | 100 | 112/F0/E0/S0, native0 |
| Fix1 regression | 36,6767 | 65,6396 | 100 | 725/F3/E0/S0, native1/FAILED |
| Fix2 targeted | 44,5101 | 60,9820 | 100 | 115/F0/E0/S0, native0 |
| Fix2 regression | 31,0701 | 45,2540 | 100 | 728/F2/E0/S0, native1/FAILED |
| Fix2 Full Verify | 18,1248 | 24,2797 | 100 | 4213/F4/E0/S0, native1/FAILED |
| G3 housekeeping targeted | 44,6204 | 62,9911 | 100 | 115/F0/E0/S0, native0/PASS |

### G3 housekeeping — verification rút gọn

Leader chỉ duyệt preflight JDK17/Docker/no-overlap → test-compile → targeted credit115/115 → diff-check → hai commit riêng/push và cập nhật **Draft PR #137**. Không chạy lại regression/full verify/JaCoCo; không tự sửa nghiệp vụ nếu targeted phát sinh lỗi. Native completion đã xác nhận: test-compile PASS/exit0; targeted9suites,115/F0/E0/S0 PASS/exit0. Không có failure để áp dụng ngoại lệ và không rerun để chọn xanh. Evidence riêng: `C:/WINDOWS/TEMP/cnhsk-spec013-t08-g3-20261110-0220/`.


| Lệnh G3 | Native bắt đầu +07 | Native kết thúc +07 | Native elapsed ms | Native exit |
|---|---|---|---:|---:|
| test-compile | 2026-11-10T02:24:23.488+07:00 | 2026-11-10T02:26:18.712+07:00 | 115288 | 0 |
| targeted credit | 2026-11-10T02:27:43.798+07:00 | 2026-11-10T02:30:21.064+07:00 | 157328 | 0 |

G3 targeted NFR-P01 giữ riêng100mẫu: p50=44,6204ms, p95=62,9911ms; không thay các lượt trước và không hard assertion300ms. Chỉ chạy test-compile/targeted theo Leader; không regression/full verify/JaCoCo report-check mới. Tham số `-Djava.io.tmpdir` chỉ cô lập execution data vào TEMP G3 để bảo toàn exec/report G2.

T-08 chưa MERGED. Dừng tại Draft PR để Leader integrated verify và merge; chưa bắt đầu T-09 G1. Chỉ sau Leader xác nhận T-08 MERGED mới chuẩn bị T-09 G0 và gom câu hỏi một lần.

## 2026-11-09 — T-08 G1 Fix Round 2/2: chẩn đoán và sửa có điều kiện

- Checkpoint push sớm 10/11/2026 +07: hai lượt test-compile PASS/native0; review ba file GREEN. Targeted chạy00:00:51.6176943–00:04:15.9260826,115 tests/F0/E0/S0, native Maven exit0. Tăng112→115 do ba unit test bổ sung, không bỏ test cũ. Regression/full/JaCoCo chưa chạy ở checkpoint này; Draft PR phải giữ WIP — G1 FIX 2/2, verification pending.
- NFR-P01 targeted Fix2: warm-up20 +100 successful CREDIT HTTP POST/mã riêng, PostgreSQL/Redis thật; p50=44.5101ms, p95 nearest-rank=60.9820ms. Giữ toàn bộ100 mẫu trong packet, không hard assertion300ms và không thay thế các lượt đo trước bằng số mới.
- Mapping test: retryAfterUsesLatestBlockingCounterAbsoluteExpiry → retryAfterUsesMaximumBlockingCounterRemainingTtl; thresholdsUseMaximumAbsoluteExpiryRoundedUpWithoutIncrementing → thresholdsUseMaximumRemainingTtlRoundedUpWithoutIncrementing. Giữ expected119–120/22; thay oracle cross-clock bằng PTTL tương đối, giữ cửa sổ3599000–3601000ms và assertions first-hit expiry không đổi. Thêm ba unit test cho rounding/cap1…3600, không đọc JVM Clock và nullTTL fail-open/WARN an toàn. Constructor service giữ chữ ký cũ để tương thích caller, không dùng Clock tính Redis TTL.
- Leader duyệt vòng cuối, giữ base `9510643`; origin/main đã tiến tới `f9be714` được ghi riêng, không tự merge/rebase. Quyền mới: chỉ sau compile/targeted xác nhận native exit0 mới commit exact20 file, push nhánh và mở/cập nhật Draft PR WIP — G1 FIX 2/2, verification pending. Không coi push là G3 hay nghiệm thu G1.
- Chẩn đoán không chạy lại regression: probe Java17 độc lập với Redis fixture riêng trên loopback, bracket Redis TIME bằng System.currentTimeMillis và nano RTT; đo PTTL/PEXPIRETIME/counter60s và120s. Pha đầu80 TIME samples ghi Redis đi trước JVM1040.638–1183.473ms, RTT1.533–10.783ms; cả40 replay công thức cũ trả122 dù PTTL IP không vượt120s. Cả hai counter đạt ngưỡng5/30; IP TTL dài hơn nên quyết định max retry. Fixture riêng đã dừng và tự xóa, không can thiệp counter của người khác.
- Pha kiểm chứng tiếp theo3 case ghi offset âm−416.626…−330.922ms, RTT2.737–11.803ms, kết quả120, không tái hiện đúng121. Giữ nguyên cả hai pha. Bằng chứng xác nhận lỗi trừ timestamp tuyệt đối Redis cho Clock JVM độc lập, không khẳng định tái tạo exact121 hoặc biết timestamp của failure cũ. Không suy đoán nguyên nhân hệ thống làm offset đổi dấu.
- Production fix được duyệt có điều kiện chỉ trong RedeemRateLimitService: PTTL tương đối, max TTL hợp lệ của counter đang chặn, ceil milliseconds/1000, kẹp1…3600; missing/no-TTL/expiry race xử lý tường minh theo fail-open/WARN an toàn. Giữ thresholds/cửa sổ,429 không tăng counter,422 ưu tiên và toàn bộ RBAC/transaction/redeem khác. Tests giữ119–120, thêm window/rounding/clock-independence coverage; gates chưa có kết quả ở checkpoint tài liệu này.
- Các gate FAILED/STOP Fix1, native UNKNOWN của helper cũ và mọi bộ NFR dưới đây được giữ nguyên; không dùng coverage cũ. Full Verify tương lai có deadline60 phút công bố trước, không ngắt vì failure; regression vòng2 chỉ chấp nhận đúng hai LoginAttempt exceptions đã xác nhận, failure mới phải STOP, không tự sửa vòng3.

## 2026-11-09 — T-08 G1 Fix Round 1/2 STOP tại G2

- Rebase đã hoàn tất từ `95f401f30221f329974e76943bc9fda122f97802` lên `9510643c36633f8b993e3f119bafe6b1772ffde7`. Exact20 file (13 ADD/7 MODIFY), G0 12/12 hash không đổi, index rỗng. Hai conflict ErrorCode/GlobalExceptionHandlerTest được hợp nhất, giữ đủ code/test T-12/T-14; production không đổi sau rebase. `TRANSLATE_UNAVAILABLE`, `INVALID_CARD_CODE`, `INTERNAL_ERROR` đúng thứ tự.
- Fixture `RedeemIntegrationTest.ledgerAndUpdatePlanFailuresRollBackCardUserAndLedger`: stack cũ56 dòng xác nhận advice MANDATORY chạy lúc setup proxy ngoài transaction. Sửa test-only bằng ultimate Mockito spy targets + doThrow, reset target trong finally/cleanup; giữ cả hai kịch bản và toàn bộ assertions cũ. Bổ sung card UNUSED/used_by và used_at NULL, full plan/quota snapshot, ledger count, counter user/IP không đổi, fixed500 an toàn. Targeted xác nhận method PASS; HTTP vẫn đi qua service/transaction thật.
- test-compile lần đầu bị sandbox từ chối CIM trước Maven (native exit N/A); lần hai Maven compile kết thúc nhưng helper không lấy được native exit (UNKNOWN, không ghi PASS). Sửa helper TEMP giữ process handle, kiểm probe native0/native7; test-compile lần ba PASS/native0. Giữ nguyên evidence của cả ba lần, không sửa source để chọn kết quả xanh.
- Targeted 09/11/2026 06:36:21.2308736–06:38:45.5586027 +07: 112 tests/F0/E0/S0, native Maven exit0. Số112 gồm111 test cũ cộng một handler test mới T-14 từ main, không xóa test để ép111. Contract3/3, concurrency4/4, RedeemIntegrationTest12/12, security9/9 đều PASS trong lượt targeted.
- Regression 09/11/2026 06:41:04.4960167–06:51:04.4449655 +07: 78 suites,725 tests/F3/E0/S0, native Maven exit1, **FAILED**. Hai failure LoginAttemptServiceIntegrationTest nằm trong whitelist được đối chiếu riêng; failure `RedeemRateLimitIntegrationTest.retryAfterUsesLatestBlockingCounterAbsoluteExpiry` dòng75 ngoài whitelist: retryAfterSeconds thực tế121, assertion yêu cầu119–120. Chưa xác nhận root cause của sai lệch; không suy đoán clock/Redis từ triệu chứng.
- STOP sau khi Maven regression tự kết thúc; không ngắt vì failure, không sửa/nới assertion, không rerun. Full Verify và JaCoCo **NOT RUN**; không sử dụng exec targeted/regression làm coverage full. Chưa công bố/chạy deadline full vì gate trước chưa đạt. Không commit/push/PR; chờ ChatGPT review/Leader.
- NFR-P01 targeted Fix1: warm-up20,100 successful CREDIT POST/100 mã riêng/PostgreSQL và Redis thật, p50=39.2488ms, p95 nearest-rank=75.0112ms. Không assertion cứng300ms, không bỏ outlier;100 mẫu được lưu trong G2 packet. Số đo cũ p50=33.8839ms/p95=53.4196ms giữ nguyên bên dưới thuộc lượt targeted cũ có error, không thay thế lịch sử bằng lượt mới.
- NFR-P01 trong regression FAILED cũng được giữ riêng: cùng phương pháp20+100 POST, p50=36.6767ms/p95=65.6396ms,100 mẫu trong packet; không dùng lượt này thay số đo targeted. Bốn suite caller translation T-14 đạt68/68 test; toàn bộ710 source file hash không đổi qua regression.

## 2026-11-09 — T-08 G1 Fix Round 1/2 được Leader duyệt

- Base bắt buộc: origin/main9510643c36633f8b993e3f119bafe6b1772ffde7, SPEC014/T14 PR#135 đã merge và dùng Entitlement API của T-12. T-08 rebase từ95f401f, không feature commit; scoped stash giữ nguyên làm recovery. 20/20file khôi phục,17byte-identical và3sharedfile hợp nhất;12G0artifacts hash giữ nguyên, index0. Conflict chỉ ErrorCode/GlobalExceptionHandlerTest trong allowlist; SecurityConfig tự hợp nhất giữ route translation.
- Giữ toàn bộ quyết định G0/R1–R5/D4–D6/Q-V/Q-P/Q-NFR. ErrorCode theo main mới: TRANSLATE_UNAVAILABLE, INVALID_CARD_CODE, INTERNAL_ERROR; giữ INSUFFICIENT_CREDITS/HTTP402 và TRANSLATE_UNAVAILABLE/HTTP503 cùng mọi tests T12/T14.
- Đã đọc full56dòng stacktrace của lỗi cũ: LedgerService SpringCGLIB proxy bị TransactionInterceptor chặn MANDATORY lúc setup dòng161, trước HTTP redeem; suppressed Mockito matcher/reset là hậu quả. Fix chỉ file RedeemIntegrationTest.java: spy-safe doThrow/doReturn trên ultimate Mockito spy target, không gọi method thật qua proxy trong setup; không sửa production để né lỗi. Giữ mọi scenarios/assertions, bổ sung cardused_by/used_atNULL, plan/ledger/counters bất biến và fixed500 an toàn. Chưa tuyên bố test PASS trước khi có kết quả mới.
- Số targeted cũ111 tăng thành112 do main PR135 bổ sung một test handler503; giữ cả111ca cũ và test mới, không loại test để ép số111.
- Giữ kết quả NFR-P01 cũ: warm-up20 POST,100successfulCREDIT POST/mã riêng/PG+Redis thật; p50=33,8839ms, p95nearest-rank=53,4196ms. 100mẫu gốc ở mục STOP bên dưới. Không thêm hard assertion300ms; kết quả cũ không được thay bằng lượt nhanh hơn. Số đo mới (nếu có) sẽ ghi riêng theo gate.
- Pipeline mới dùng worker nền độc lập, lưu ownerPID/nativePID/start/deadline và nativeexit khi Maven thực sự kết thúc; không bàn giao khi worker cònRUNNING. Full công bố60phút, chỉ timeout mới kill ownedtree có reason/PIDs, không kill khi testfailure. PreflightJDK17/Docker/nooverlap trước từng Maven. Regression/full chưa chạy tại thời điểm ghi duyệt này; giữ nguyên mọi FAILED evidence cũ.
## 2026-11-09 — T-08 G1 STOP tại targeted, giữ nguyên source/tests

- 9 lượt test-compile sau từng file test: native exit0. Targeted có9suites/111tests/F0/E1/S0; error ngoài whitelist ở RedeemIntegrationTest.ledgerAndUpdatePlanFailuresRollBackCardUserAndLedger(CapturedOutput), IllegalTransactionStateException, dòng161. Setup MockitoSpyBean gọi LedgerService.append qua Spring proxy MANDATORY ngoài transaction trước khi gửi redeem; nhánh inject lỗi/rollback này chưa được nghiệm thu. Không tự sửa fixture hoặc rerun.
- Không thu được native exit/end của Maven targeted: lời gọi cũ được dispatch muộn sau khi đổi executor; preflight executor thứ hai chặn đúng Java PID12716/13580, không có test run thứ hai. Không có targeted-result.json; process Maven đã thoát khi thử gắn handle. Giữ stdout/9XML/status gốc và báo native exit chưa xác nhận, không suy exit1 từ error XML. Regression/full/JaCoCo không chạy; không có coverage full-run được nghiệm thu.
- Bằng chứng từng nhóm trong lượt targeted: contract3/3, concurrency4/4, security9/9 PASS; rollback missingPREMIUM và inactivePREMIUM PASS, nhưng test injectledger/updatePlan ERROR. Các kết quả PASS riêng không biến targeted thành PASS.
- NFR-P01: warm-up20 POST,100successfulCREDIT POST riêng mã/PG+Redis thật. p50=33.8839ms, p95=53.4196ms, max=69.1805ms; nearest-rank kiểm đúng index49/94. Không assert ngưỡng300ms, không bỏ outlier. Đây là số đo của lượt targeted có error ở test khác, chưa phải nghiệm thu G1.
- 100 mẫu nanosecond theo thứ tự đo: [44854700, 44640800, 48972100, 50710800, 54056600, 53419600, 56658500, 51573800, 53494800, 28329500, 26450300, 31975700, 33822700, 26671100, 26646700, 25120700, 27211000, 24012700, 24493500, 27918000, 22950600, 22561100, 26605100, 26781500, 25860200, 25568400, 24756300, 24133900, 31291900, 39311100, 41055600, 45179000, 47934500, 37769300, 35766300, 34419900, 37565600, 34262700, 35504900, 34143800, 32080100, 32999700, 33444500, 29327600, 34974800, 34538100, 38295700, 37367100, 44657500, 42787500, 40900600, 37337600, 32107000, 34810600, 32485400, 32067900, 30156500, 39413800, 31258700, 39913300, 69180500, 46917600, 41347300, 38736000, 38787100, 28539000, 30076100, 31220600, 36769400, 27085300, 25806600, 24557800, 28121300, 23931300, 23916100, 26981600, 29939900, 25535200, 32256700, 34671600, 32999200, 34236500, 26524700, 27681500, 26979400, 24893100, 33299400, 31569400, 37137300, 33883900, 41126200, 34774400, 36653600, 39575600, 37736000, 38471100, 37738300, 37705200, 50605400, 53697100].
- Dừng theo Leader: cần phê duyệt sửa fixture/runtime test trước bất kỳ gate tiếp theo. Không commit/push/PR; không thay production/test sau failure.

## 2026-11-08 — T-08 G1 được duyệt, base đã cập nhật

- Leader xác nhận T-12 PR #134 đã merge vào `95f401f30221f329974e76943bc9fda122f97802`. T-08 đã fast-forward từ e6bd160 khi tracked/index sạch; 12 G0 artifact giữ nguyên hash. Không lấy thay đổi từ branch T-12 cũ. G1 chỉ exact 20 file (13 ADD/7 MODIFY), không commit/push/PR trước G2.
- R1/D4: body đúng schema mới kiểm `billing:redeem:fail:user:{id}` ≥5 và `billing:redeem:fail:ip:{getRemoteAddr()}` ≥30 trong một giờ trước normalize. Invalid format/USED/expired/VOIDED/self-created cùng400/cùng message/tăng cả hai;429 không tăng. Retry từ expiryTimeMillis; Redis lỗi fail-open/WARN class-only.
- Q-V: missing/null/non-string/code>40/malformed JSON/extra fields →422 VALIDATION_FAILED, không Redis/DB redeem, ưu tiên hơn429. Parse cục bộ nghiêm ngặt, không coerce number/boolean hoặc nhận trailing JSON. Body hợp lệ: blocked→normalize→lockPlan→UPDATE RETURNING.
- Q-P/R2: SUB vẫn redeem khi PREMIUM.is_active=false; thiếu dòng PREMIUM →fixed500/rollback toàn bộ/cardUNUSED. PREMIUM user còn hiệu lực nối expiry cũ+n ngày, còn lại Clock now+n. Reason `PREMIUM +{n} ngày, giá niêm yết {price}`; BigDecimal hiện tại toPlainString scale2. Giữ D5 user lock trước card/ledger, không reset quota.
- R3/R4/R5: không request logging filter; OutputCaptureExtension mã đúng/sai an toàn. LEARNER matcher `/api/credits/**`, `/api/me/credits`, `/api/me/credit-transactions` trước fallback; ADMIN403,VOIDED400. 10 user/1 mã:1success/9invalid chung. Nợ T-07 WARN chỉ exception class trong CardBatchService và test.
- D6: không tự tạo endpoint/idempotency contract mới; nếu dùng billing:idem thì read trước consumption/fail-open, write afterCommit. Redeem hiện bảo vệ bằng atomic card claim, không tạo receipt Redis mới.
- Q-NFR: warm-up20 POST +100 CREDIT POST thành công/mã riêng/PostgreSQL+Redis thật; p50/p95 nearest-rank và100 mẫu sẽ ghi sau gate. Ngưỡng300ms là mục tiêu báo cáo, KHÔNG assertion cứng; giữ đầy đủ assertions correctness100 lượt. Chưa có kết quả đo tại checkpoint này.
- Gates: preflight JDK17/Docker/không gate trùng trước từng Maven; test-compile sau mỗi file test; targeted→regression→full verify→JaCoCo đúng full exec. Full timeout60phút công bố trước, không kill vì failure; chỉ kill owned tree khi quá timeout với reason/PIDs. Exit1 luôn FAILED, giữ đúng5 ngoại lệ, ngoài whitelist STOP. Không đổi Windows Power Settings.
# Chốt — Spec 013

## 2026-11-08 — T-12, G1 resume và bàn giao G2

- Leader duyệt tiếp tục G1, giữ 13-file allowlist, E1–E8 và Q1–Q2 mới. Quy tắc cố định: được tự sửa testCompile trong tests thuộc allowlist khi chỉ sửa cú pháp/suy luận kiểu, giữ assertions/expected values/kịch bản/nghiệp vụ/API; không tính vào hai vòng G2 fix. Từ các file test tiếp theo phải chạy test-compile ngay sau mỗi file hoàn tất; T-12 sửa xong chạy test-compile toàn bộ trước targeted.
- Resume bắt đầu 09:17:15 +07. Chỉ EntitlementRedisIntegrationTest.java thay năm assertion tại các vị trí cũ 79/92/104/117/156: kết quả TransactionTemplate.execute được gán vào biến Consumption tường minh, sau đó giữ nguyên isEqualTo và giá trị expected. Rà bốn file test còn lại không cần sửa. Phép hoàn nguyên đúng năm tách biến khớp SHA256 file trước sửa; production và các tests khác khớp hash checkpoint STOP.
- test-compile PASS/exit 0, biên dịch toàn bộ 259 test source; không thực thi test. Preflight cho từng Maven invocation đều ghi Java17.0.9/JAVA_HOME phiên, Docker27.2.0, không có gate Java/Maven khác (language server có sẵn được phân loại riêng). Không ép JVM profile, không đọc secret hay truy cập Neon.
- Targeted PASS: 6 suite/85 tests/F0/E0/S0, Maven exit 0. Năm suite mới: API compatibility7, concurrency8, Redis9, service integration16, service unit28; handler17. Monitor tổng hợp gặp lỗi Measure-Object với Hashtable sau suite đầu; Maven vẫn kết thúc BUILD SUCCESS. Sửa helper ngoài repo sang PSCustomObject và tổng hợp lại đúng sáu XML/native status của lượt này, không rerun tests. Evidence lỗi helper và reconciliation được giữ riêng.
- Regression FAILED: exact95/95 suite, 1390 tests/F1/E0/S0, Maven exit1; chỉ ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget, p95=44477ms so với ngưỡng30000ms, assertion dòng398. Unknown failure0, source hash không đổi.
- Full Verify FAILED: 258 suite/4083 tests/F2/E0/S0, Maven exit1, 09:39:39.856–10:08:38.137 +07. Đúng hai ngoại lệ: cùng FR023 p95=37839ms và QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries p95=60390ms, ngưỡng30000ms/assertion dòng304. Unknown failure0; không nới assertion, skip hoặc rerun để chọn PASS. Toàn bộ68 tests T-12 mới PASS trong full.
- AT-18/19/20: FREE quota → CREDIT →402, PREMIUM riêng/thiếu0, boundary tháng Việt Nam, idempotency hai lookup/afterCommit/fail-open và giữ receipt sau refund, refund CREDIT theo dòng gốc, SET NX QUOTA với TTL tới tháng sau, rollback/Redis-down đều PASS. Tám concurrency tests chứng minh khóa user, quota/seq/số dư, hoàn CREDIT/QUOTA một lần và rollback waiter. API compatibility chứng minh provider chạy sau commit/nhả khóa, refund transaction mới,402 không gọi provider;015 không tiêu thụ.
- JaCoCo report/check từ full exec PASS/exit0, không chạy thêm test. Project LINE11578/11972=96,71%; Credit+Web421/440=95,68%. Package learning.api106/106. Đúng một session 09:39:54.561–10:08:34.469 +07 nằm trong full interval; class mismatch0. Exec SHA256958AC2AB33C6BB648D4F4E7D4827AD08A34CD689CA4030FB4AFEFE64D269E49F khớp trước/sau report; XML SHA256AB9DA5AB2E65AC8022148EC05AA4007FA327C0EAFCA125424A79EAFED0485145. Không dùng execution data targeted/regression.
- Read-only review độc lập sau resume không có blocking finding. G1 CONDITIONAL theo các ngoại lệ đã duyệt; không gọi Regression/Full Verify PASS. Giữ đầy đủ FAILED testCompile trước và mọi evidence tại C:/WINDOWS/TEMP/cnhsk-spec013-t12-g1/; không coi sửa cú pháp này là vòng G2 fix.
- Docker đã chạy sẵn, không restart/config changes; các container của lượt chạy đã được Testcontainers tự dọn. Kiểm docker ps -a sau toàn bộ gates chỉ còn container có sẵn, dọn thủ công0; không còn Java/Maven gate, giữ Engine chạy vì phiên này không khởi động Docker.
- Integrity: exact13 file (9 ADD/4 MODIFY), không BOM, index trống; artifact G0 có sẵn giữ nguyên. Không sửa AuthApi/LedgerService/BillingConfig/AuditService/Redis shared helpers/SecurityConfig, schema/migration/config/locked contracts, không commit/push/rebase/PR hoặc bắt đầu T-08. T-12 chưa đánh hoàn tất; dừng tại G2 để ChatGPT review và Leader quyết định checkpoint tiếp theo.

## 2026-11-08 — T-12, G1 được Leader duyệt

- Branch `spec/013-t12`, base `e6bd1608bf5ea444cdb4b86469aff5057c578967`. G0 được duyệt; exact allowlist 13 file (9 ADD/4 MODIFY). Không sửa AuthApi, LedgerService, BillingConfig, AuditService, Redis shared helpers, cấu hình, schema/migration hoặc locked contracts.
- Consumption và chữ ký consume/refund đã khóa: `(source, transactionId, feature, idempotencyKey, periodStart)`; QUOTA transactionId=null, CREDIT giữ DEDUCT ID; kỳ là đầu tháng Việt Nam dạng Instant. Cả hai MANDATORY trong transaction ghi của caller.
- Q1: key không blank, tối đa 128 ký tự, không chuẩn hóa; user/key trùng mà khác feature → IllegalArgumentException với message cố định. Redis → lockPlan → Redis lần hai → quota → credits → 402. Consumption JSON TTL24h, ghi afterCommit, Redis lỗi fail-open/WARN an toàn. Memo trong transaction ngăn trừ lặp trước commit.
- Một key là một consume kể cả sau refund: không xóa/invalidate receipt; caller muốn tính lại phải dùng key mới. SPEC014 consume trong transaction ngắn → commit → gọi API ngoài không giữ lockPlan → lỗi refund bằng transaction mới. SPEC015 không gọi EntitlementApi.
- E3/E7: effective PREMIUM dùng benefits[PaidFeature.name()], thiếu=0, không cộng FREE; FREE/hết hạn dùng free-monthly-limit. Clock/reset kỳ theo Asia/Ho_Chi_Minh; chỉ consume đặt lại kỳ; thông tin đọc không tiêu thụ.
- CREDIT refund giữ E5: lock user → kiểm DEDUCT đúng user/amount âm → kiểm REFUND đã có → append -amount gốc với ref_transaction_id; không bắt UNIQUE để làm idempotency. LedgerService là writer duy nhất.
- Q2 THAY E6 cũ: lockPlan → cùng kỳ và used>0 → SET NX billing:refund:user:key, TTL đến đầu tháng VN kế tiếp → có marker mới giảm used. Đăng ký cleanup rollback ngay sau khi giành marker, trước updateUsage để cả lỗi DB cũng được DEL; commit giữ marker. SET NX lỗi → không refund, WARN an toàn, không throw. DEL lỗi chỉ WARN; không ghi key/payload/exception details.
- E2 vẫn dùng receipt Redis sau commit; không tự thêm distributed/advisory lock hoặc migration. Cửa sổ DB commit tới publish Redis và Redis-loss không được mô tả là strict distributed exactly-once.
- Chỉ thêm INSUFFICIENT_CREDITS sau PLAN_NOT_AVAILABLE trước INTERNAL_ERROR; message 402 cố định: “Đã hết hạn mức và điểm.”. Refund CREDIT sai chủ/không tồn tại/không phải DEDUCT trả FORBIDDEN theo BUS-01; không tiết lộ tồn tại. Không thêm audit action chưa có contract.
- T-07 đã được Leader nghiệm thu/merge PR #133, integrated verify 4014 PASS. Giữ nguyên evidence FAILED lịch sử bên dưới. G1 T-12 bắt đầu 07:07:12 +07; chưa có kết quả gates, chưa đánh hoàn tất.

### G1 STOP — targeted testCompile FAILED

- Thời gian G1: 07:07:12–09:11:57 +07 ngày 2026-11-08, gồm gián đoạn tác vụ phụ do giới hạn phiên. Code/tests được giữ lại; kiểm tra tĩnh tiếp tục trước lượt Maven đầu tiên. Chưa có independent review hoàn chỉnh do tác vụ reviewer cũng bị ngắt.
- Preflight targeted: JAVA_HOME `C:/Program Files/Java/jdk-17`, Maven báo Java 17.0.9; Docker server 27.2.0; không có Java/Maven gate khác (PID Java có sẵn là language server). Không ép profile; dùng thư mục temp riêng để cách ly execution data.
- Targeted selector gồm EntitlementServiceTest, EntitlementServiceIntegrationTest, EntitlementRedisIntegrationTest, EntitlementConcurrencyIntegrationTest, EntitlementApiCompatibilityTest và GlobalExceptionHandlerTest. Lượt monitor 09:09:38.929–09:11:01.825 +07; Maven BUILD FAILURE/exit 1, dừng ở testCompile trước Surefire. Production 379 source files đã compile; testCompile báo 10 diagnostics, chưa chạy test nào; F/E/S không áp dụng, không báo targeted PASS.
- Lỗi duy nhất thuộc EntitlementRedisIntegrationTest.java tại các dòng 79, 92, 104, 117, 156: mỗi vị trí có ambiguous assertThat overload và incompatible inference bounds khi truyền trực tiếp TransactionTemplate.execute vào AssertJ. Đây là compilation failure ngoài năm ngoại lệ môi trường; STOP, không sửa/rerun sau failure, không chạy regression/full verify/JaCoCo.
- Hướng sửa đề xuất để Leader duyệt: gán kết quả execute vào biến Consumption có kiểu tường minh rồi giữ nguyên assertion so sánh; không xóa/nới assertion hoặc đổi production. Chưa áp dụng.
- Evidence ngoài repository: `C:/WINDOWS/TEMP/cnhsk-spec013-t12-g1/`, gồm targeted log, ba dòng preflight, source manifest, native/monitor status. Source hash không đổi trong lượt gate; không commit, push, rebase, PR hoặc bắt đầu T-08. AT-18…20/concurrency/refund/rollback chưa có kết quả thực thi.
- Docker đã chạy sẵn; Maven/Testcontainers chưa khởi chạy test nên không sinh container mới. Kiểm docker ps -a sau gate: chỉ các container có sẵn, dọn 0; giữ Engine chạy vì phiên này không khởi động Docker. Không còn Java/Maven gate hoạt động.

## 2026-11-08 — T-07, G2 được duyệt và bàn giao G3

- Leader xác nhận G2 review hoàn tất, không có blocking finding và duyệt G3: commit đúng 17 file (11 ADD/6 MODIFY), push spec/013-t07, mở Draft PR vào main; không merge/chuyển Ready hoặc bắt đầu T-08.
- Fetch origin xác nhận main vẫn 8b3b2280b05eefd791ec00e990e08231a1317961 (T-06 PR #132); không rebase/rerun gates khi base này không đổi. 15 source/test phải giữ hash G2, chỉ cập nhật hai file tracking cho trạng thái bàn giao. Stage từng đường dẫn, không git add . hoặc -A.
- Regression vẫn FAILED: 773/F1/E0/S0, exit 1, đúng FR023 p95 đã duyệt. Full recovery vẫn FAILED: 4014/F2/E0/S0, exit 1, đúng FR023/NFR-P04 đã duyệt. Contract 5 và targeted 126 PASS; JaCoCo report/check exit 0, Project/Credit+Web LINE 96,75%/97,05%; NFR-P02 riêng [76,85,101,103,254] ms, p95 254 ms <2000 ms. Không gọi Regression/Full Verify PASS.
- Docker: evidence xác nhận lỗi PostgreSQL Testcontainers startup với IOException "The pipe has been ended" và lỗi khởi tạo lớp dây chuyền; root cause bên dưới Docker transport chưa được xác nhận. Không suy đoán Engine off, WSL, OOM hoặc thay đổi cấu hình là nguyên nhân. Recovery thực tế: Leader xác nhận đã bật Engine, kiểm docker version server 27.2.0, chạy lại full trên cùng source/JDK17 và không ép profile; lỗi hạ tầng không tái diễn. Giữ toàn bộ FAILED cũ, tách khỏi hai benchmark exemptions.
- Hai nghĩa vụ chưa nghiệm thu ở T-07 chuyển T-08: ADMIN gọi POST redeem phải 403; nạp mã VOIDED phải trả 400 chung. T-07 chưa đánh hoàn tất trước Leader nghiệm thu; commit/PR identifiers được ghi trong evidence G3 và PR để tránh metadata tự tham chiếu trong commit.

## 2026-11-08 — T-07, Leader duyệt recovery Docker

- Leader xác nhận Docker Engine đã bật và yêu cầu thử lại. Preflight Docker ngoài sandbox trả server 27.2.0; branch/base vẫn spec/013-t07 tại 8b3b228, 15/15 hash source/test khớp manifest sau sửa annotation, locked YAML không đổi. Không sửa code/test, config, dependency hoặc schema.
- Chỉ chạy lại đúng full `mvnw.cmd -B verify`, JAVA_HOME/PATH JDK 17 trong phiên, không ép profile. Lượt FAILED Docker trước được giữ nguyên evidence; đây là recovery sau thay đổi trạng thái hạ tầng do Leader xác nhận, không chọn lượt xanh bằng rerun.
- Evidence recovery riêng `C:/WINDOWS/TEMP/cnhsk-spec013-t07-docker-recovery-20261108-040956/`; full bắt đầu 04:13:55.452 +07. Runner theo dõi XML từng suite và tự dừng đúng cây tiến trình Maven của lượt này nếu có lỗi ngoài năm ngoại lệ, không phụ thuộc phiên theo dõi. PostgreSQL Testcontainers đã khởi động thành công lúc 04:14:32.
- Chưa có kết quả full recovery, JaCoCo hoặc NFR riêng; chỉ tiếp tục hai gate sau nếu full không có lỗi ngoài ngoại lệ được duyệt. Không commit/push/rebase/PR hoặc bắt đầu T-08; dừng G2 để review khi G1 đạt checkpoint.

### Kết quả full recovery và JaCoCo

- Full recovery hoàn tất tự nhiên, không bị ngắt: 04:13:55.452–04:55:42.605 +07, đủ 253 suite, 4014 tests/F2/E0/S0, Maven exit 1/BUILD FAILURE. Monitor phân loại đúng 2 failure đã duyệt, unknown failure=0; không tái diễn lỗi khởi tạo Docker/Testcontainers và không gọi FULL VERIFY PASS.
- FR023: ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget, dòng 398, p95=76079 ms, ngưỡng <30000 ms; 5 mẫu [76079,60856,55743,51913,55852] ms. NFR-P04: QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries, dòng 304, p95=88055 ms, ngưỡng <30000 ms; 5 mẫu [88055,63192,65038,58377,59904] ms. Các failure khác/error/skip đều 0; không nới assertion hoặc rerun để chọn PASS.
- Full-run exec lưu riêng: SHA256 C68BD93C4DCA12557095D8B1D115787EEDC2EEFC919738C0718E9C0766B28AA0, mtime 04:55:38.491 nằm trong full-run interval. Trước/sau report, exec gốc và bản lưu cùng hash; 15/15 source/test vẫn khớp manifest recovery.
- Vì full dừng ở Surefire với hai ngoại lệ, chạy riêng `mvnw.cmd -B jacoco:report@report jacoco:check@check`, JDK 17, không thêm test hoặc ép profile: exit 0/BUILD SUCCESS, mọi coverage check đạt. Project LINE 11414/11797=96,75%; Credit gồm web 263/271=97,05%. Report có đúng một session nằm trong full run; class mismatch warning=0. XML SHA256 CD7E9BF1AEAE200C902D2E4C0B65AEC57154201B1047165C392A1248F0D3CCD4; report và exec đã sao lưu riêng trước NFR.
- CardBatchIntegrationTest trong full PASS 52/F0/E0/S0, gồm HTTP numeric/null và AT-04; benchmark phụ full [33,34,34,35,36] ms, p95=36 ms. NFR-P02 riêng sau JaCoCo đang thực hiện bằng đúng một warm-up rồi năm HTTP POST quantity=100; chưa dùng số đo phụ thay kết quả gate riêng.

### NFR riêng và bàn giao G2

- NFR-P02 riêng chạy sau khi lưu full exec/report: `mvnw.cmd -B -Dtest=CardBatchIntegrationTest#hundredCodeCreationHttpP95IsBelowTwoSeconds test`, JDK 17 trong phiên, không ép profile; 05:03:51.424–05:06:05.223 +07, 1 test/F0/E0/S0, Maven exit 0/BUILD SUCCESS. Một warm-up rồi năm HTTP POST quantity=100, gồm commit và response; năm mẫu sắp tăng [76,85,101,103,254] ms, nearest-rank p95=254 ms <2000 ms. Không chọn mẫu hoặc rerun để lấy PASS.
- Full exec và XML/HTML report đã lưu trước NFR; coverage Project/Credit ở trên chỉ lấy từ session full recovery, không dùng execution data NFR/targeted/regression. NFR chạy sau có thể thay exec mặc định của Maven; bản full lưu riêng và report giữ nguyên để đối chiếu hash.
- G1 CONDITIONAL: contract/negative/HTTP numeric, targeted, JaCoCo và NFR đạt; regression và full vẫn ghi FAILED/exit 1 với đúng các ngoại lệ được duyệt. Mọi failure ngoài ngoại lệ trong full recovery=0, mọi error/skip=0. Không gọi full verify PASS.
- Self-check: đúng 17 file (11 ADD/6 MODIFY); 15/15 source/test giữ hash trước recovery; không đổi locked YAML, schema/migration/dependency/config/SecurityConfig/CardCodes hoặc thêm TODO. Không đọc .env/secret, không truy cập Neon, không commit/push/rebase/PR hoặc bắt đầu T-08. T-07 chưa đánh hoàn tất; dừng tại G2 để ChatGPT review và Leader quyết định checkpoint tiếp theo.

## 2026-11-08 — T-07, audit đầy đủ và sửa annotation một lượt

- Leader mở rộng phạm vi annotation numeric trong đúng 17-file allowlist, yêu cầu audit một lượt trước sửa: chỉ integer↔string của DTO T-07 được sửa; path/status/field/required/nullability/enum/security hoặc loại mismatch khác thì STOP. Không thay Java types, logic, validation, nullability hoặc locked YAML.
- Contract test bổ sung audit độc lập gom từng lỗi type/constraint/required/properties/ref/items trên bảy schema DTO/enum; giữ đủ 226 dòng contract test trước đó và bốn test/comparator cũ. Snapshot generated JSON chỉ xuất khi truyền `cnhsk.contract-audit-output`, là artifact ngoài repo, không phải profile JVM.
- Audit trước sửa: 5 tests/F2/E0/S0, Maven exit 1/BUILD FAILURE, 00:23:36.924–00:25:51.814 +07 ngày 2026-11-08. Hai failure là comparator fail-fast và aggregate audit cùng phản ánh đúng ba type mismatch: BatchPage.page, BatchPage.size, BatchPage.total expected integer, actual string. Không có mismatch schema khác trong aggregate; ba test operation/status/security/negative/parameters còn lại PASS. Snapshot xác nhận total vẫn format int64.
- Theo phân loại Leader duyệt, chỉ thêm `types={integer}` trên ba field BatchPage.page/size/total, giữ type integer, total format int64 và Java int/int/long. BatchSummary sáu field, CreateBatchRequest quantity/value/planDays, VoidResult.voided đã qua audit nên giữ nguyên.
- HTTP integration đã có isIntegralNumber cho page/size/total của page, sáu field numeric của từng summary ở list/detail, cùng kiểm tra null theo CREDIT/SUBSCRIPTION; không cần sửa thêm assertion sau audit.
- Chưa có kết quả gate sau sửa. Source/test được cố định bằng manifest trước contract sau sửa; evidence riêng ngoài repo `C:/WINDOWS/TEMP/cnhsk-spec013-t07-contract-audit-20261108-002250/`. Giữ mọi evidence FAILED trước đó, không chọn lượt xanh bằng rerun, không commit/push/PR.

### Gate sau sửa annotation

- Contract sau sửa: 5 tests/F0/E0/S0, Maven exit 0/BUILD SUCCESS, 00:27:45.406–00:30:23.112 +07 ngày 2026-11-08. Cả negative security OR và aggregate audit PASS, không mismatch. Snapshot trước/sau chỉ khác đúng ba type BatchPage.page/size/total từ string sang integer; total giữ format int64, normalized document còn lại giống nhau.
- Targeted đầy đủ: 126 tests/F0/E0/S0, đủ 6/6 suite, Maven exit 0/BUILD SUCCESS, 00:31:29.383–00:33:24.883 +07. Contract 5, concurrency 3, CardBatchIntegration 52, CardBatchService 38, AuditServiceIntegration 12, GlobalExceptionHandler 16.
- HTTP numeric/null assertions cho list/detail và page envelope PASS. AT-04 kiểm 100 mã có/không gạch và hash không có trong CapturedOutput/audit/GET PASS; hai loại card/ba channel, validation, authorization/CSRF, metadata, pagination/query count, void idempotent giữ USED và rollback khi audit lỗi PASS. Unit retry cuối/cạn giới hạn/memory duplicate/cause che dữ liệu và ba ca PostgreSQL concurrency PASS; D1 null target/transaction/validation cùng compatibility method cũ PASS.
- Benchmark đi kèm targeted: 5 mẫu [53,59,60,64,68] ms, nearest-rank p95=68 ms. Đây chưa thay thế lượt NFR-P02 riêng sau full-run JaCoCo. Regression 59 suite đang thực hiện; full verify, JaCoCo và NFR riêng chưa có kết quả.
- Regression đã hoàn tất 59/59 suite, missing/extra=0: 773 tests/F1/E0/S0, Maven exit 1/BUILD FAILURE, 00:35:48.670–00:48:39.232 +07. Failure duy nhất đúng ngoại lệ môi trường FR023: ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget, dòng 398, p95 50957 ms, yêu cầu <30000 ms; năm mẫu [50957,44738,47352,46121,48689] ms. Các assertion tạo đủ 5000 dòng và failed=0 đã PASS trước assertion thời gian; unknown failure=0. Không nới assertion, skip hoặc rerun. LoginAttempt và các suite còn lại PASS; 15/15 hash source/test trước/sau regression khớp.
- Benchmark HTTP100 đi kèm regression: năm mẫu [25,28,29,32,33] ms, p95=33 ms; chỉ là evidence phụ, lượt NFR riêng sau full-run JaCoCo còn chờ. Full verify được mở sau khi phân loại regression chỉ có đúng ngoại lệ đã duyệt; không gọi regression PASS khi exit 1.

### Full verify — STOP vì hạ tầng ngoài ngoại lệ

- Lệnh đúng `mvnw.cmd -B verify`, JDK 17 JAVA_HOME/PATH trong phiên, không ép JVM profile: 00:51:45.257–01:10:24.353 +07 ngày 2026-11-08, Maven exit 1/BUILD FAILURE. XML hoàn tất 253 suite, 3638 tests/F0/E1528/S0; không gọi full verify PASS.
- Lỗi đầu tiên ở AdminUserQueryIntegrationTest.AT75_searchFiltersAndReturnsRequestedPageOrderedByCreationTime: ExceptionInInitializerError khi khởi tạo AbstractIntegrationTest, ContainerLaunchException cho postgres:18-alpine; cause chain kết thúc java.io.IOException: The pipe has been ended. Sau đó nhiều suite lỗi Could not initialize class dây chuyền. Đây là lỗi Docker/Testcontainers, nằm ngoài năm ngoại lệ môi trường được duyệt nên STOP, không sửa source hoặc tự rerun.
- Tác vụ theo dõi bị gián đoạn do giới hạn phiên; full Maven đã chạy tới kết thúc thay vì được ngắt ngay ở lỗi ngoài ngoại lệ. Ghi nhận đây là sai lệch quy trình STOP; không dùng việc chạy hết làm bằng chứng đạt gate. Controller xác nhận lại log/XML và trạng thái sau khi tiếp tục.
- Source/test trước và sau full giữ nguyên 15/15 hash; class manifest có 681 class. Full-run exec đã được lưu riêng làm evidence với SHA256 F46CEBE4EB4488AF0463ABAE86C52F5E8271B5C0C43CDB88AD6818DF6F67DA72; không chạy JaCoCo report/check hoặc lấy coverage Project/Credit để nghiệm thu từ lượt lỗi hạ tầng này. Không chạy NFR-P02 riêng sau full; các số đo targeted/regression ở trên chỉ là evidence phụ.
- Chỉ cập nhật tracking trạng thái STOP; manifest vẫn đúng 17 file (11 ADD/6 MODIFY), locked YAML giữ SHA256 4B3025DDA60DECFC13B59FA7A8F1CB590593EE36F068FCE051883003003DC6C5. Không commit/push/rebase/PR hoặc bắt đầu T-08. Chờ Leader duyệt recovery hạ tầng và gate tiếp theo, chưa đủ điều kiện nghiệm thu G2.

## 2026-11-08 — T-07, tiếp tục vòng sửa 1/2

- Leader duyệt sửa comparator security OR và annotation sáu numeric fields của BatchSummary; giữ Java types/nullability. Một negative test giữ từng requirement nguyên vẹn, phát hiện thiếu/thừa scheme, sai scopes và OR→AND. Không nới các assertion contract khác.
- Leader duyệt bổ sung CardBatchIntegrationTest: chỉ thêm isIntegralNumber cho total/unused/used/voided/value/planDays ở GET list/detail, giữ toàn bộ kiểm tra giá trị hiện có; CREDIT planDays=null, SUBSCRIPTION value=null và planDays là integer. Có thêm kiểm tra kiểu total/page/size của page. Giữ nguyên hai file đã sửa từ vòng trước.
- Quy tắc STOP mới: được thêm/sửa test trong đúng 17-file allowlist để đáp ứng yêu cầu đã duyệt; chỉ STOP khi cần file ngoài allowlist, sửa production ngoài phạm vi, generated contract lệch YAML, nới/xóa assertion hoặc failure ngoài năm ngoại lệ môi trường. Không tự sửa record production khác nếu runtime schema lệch.
- Gate theo thứ tự: negative test security OR → targeted đầy đủ → regression AuditService/exception/Swagger/OpenAPI/auth/credit/callers → full verify không ép JVM profile → JaCoCo đúng full-run exec → NFR-P02 warm-up 1 và 5 HTTP POST quantity=100, nearest-rank p95 <2 giây. JDK 17 JAVA_HOME/PATH trong phiên. Không commit/push/rebase/PR; dừng G2 khi G1 đạt.
- Chưa có kết quả gate của lượt tiếp tục này. Evidence riêng ngoài repo tại `C:/WINDOWS/TEMP/cnhsk-spec013-t07-round1-continue-20261108-000737/`; giữ nguyên evidence các lượt trước.

### Kết quả vòng sửa 1/2 — STOP tại generated BatchPage

- Đã bổ sung đúng 30 dòng kiểm tra kiểu HTTP; kiểm tra subsequence xác nhận giữ đủ 515 dòng integration test trước sửa. Hai file comparator/BatchSummary giữ nguyên hash của vòng sửa trước; review độc lập không có finding. Tổng diff kỹ thuật vòng sửa gồm đúng ba file; chỉ tracking được cập nhật thêm trong allowlist.
- Negative gate: `CreditCardBatchContractTest#securityComparisonRejectsMissingExtraSchemesAndWrongScopes`, 1 test/F0/E0/S0, Maven exit 0/BUILD SUCCESS, 00:08:56.470–00:11:12.934 +07 ngày 2026-11-08. Reordered OR được chấp nhận; thiếu scheme, thừa scheme, sai scopes và OR→AND đều bị comparator phát hiện.
- Targeted ngoài sandbox, JDK 17, không ép profile: 00:12:21.009–00:14:20.092 +07. `CreditCardBatchContractTest` 4/F1/E0/S0; `CardBatchConcurrencyIntegrationTest` 3/F0/E0/S0. Tổng các XML hoàn tất: 7/F1/E0/S0. Ctrl-C dừng Maven, tool trả exit 1; không có BUILD SUCCESS hoặc tổng kết đầy đủ toàn lượt.
- Failure duy nhất: `integerNullableRecordsAndResponseShapesMatchTheLockedSchemas`, `[Types BatchPage.page] expected [integer], actual [string]`. Các schema đứng trước BatchPage trong vòng so sánh, gồm BatchSummary, đã qua; ba test contract còn lại (operation/status/security, negative security, parameters) PASS. Không sửa BatchPage hoặc record production khác sau failure.
- HTTP numeric assertions đã compile nhưng `CardBatchIntegrationTest` vừa bắt đầu khi dừng, chưa có XML hoàn tất nên chưa tuyên bố PASS. CardBatchServiceTest, AuditServiceIntegrationTest và GlobalExceptionHandlerTest chưa chạy ở lượt targeted này. Regression/full verify/JaCoCo Project-Credit từ full-run exec/NFR-P02 chưa chạy; không có năm mẫu hoặc p95.
- Dừng theo điều kiện generated OpenAPI lệch locked contract. Cần Leader duyệt phạm vi production BatchPage: kiểm page/size/total, giữ total int64 và runtime types. Không tự thay YAML, nới assertion, rerun để chọn PASS, commit/push/rebase/PR hoặc bắt đầu T-08.
- Evidence: `negative.log`, `negative-status.json`, `negative-surefire/`; `targeted.log`, `targeted-status.json`, `targeted-surefire/` trong thư mục lượt tiếp tục. Manifests và diff vòng sửa giữ riêng; 15 source/test được đối chiếu hash với manifest đầu gate trước khi cập nhật tracking.

## 2026-11-07 — T-07

- Leader duyệt G1 trên `spec/013-t07`, base `8b3b228` (T-06 PR #132 đã merge), đúng 17 file: 11 ADD và 6 MODIFY. Chỉ FR-001/005–008; dừng ở G2, không push/rebase/PR hoặc bắt đầu T-08.
- **Q1:** batchId sai pattern hoặc không tồn tại ở GET detail/PATCH void cùng trả 404 `CARD_BATCH_NOT_FOUND`, không thêm 422.
- **Q2:** không advisory lock. BatchId kiểm EXISTS, một ứng viên ban đầu và tối đa năm lần sinh lại. Mã dùng Set trong bộ nhớ, INSERT nhiều dòng `ON CONFLICT (code_hash) DO NOTHING RETURNING code_hash`; giữ mã đã insert và chỉ bù phần thiếu, một vòng ban đầu và tối đa năm vòng retry. Cạn giới hạn hoặc lỗi persistence trả 500 `INTERNAL_ERROR`, message cố định, không cause chứa mã/hash. Seam Supplier nội bộ chỉ phục vụ ép collision, không đổi API public `CardCodes`.
- **Giới hạn được giữ theo quyết định Q2:** EXISTS không bảo đảm loại race khi hai transaction đồng thời sinh cùng batchId trước khi một transaction commit; V1 không có UNIQUE cho batchId. Không tự thêm khóa, migration hoặc cơ chế thay thế. Unique code_hash vẫn được PostgreSQL bảo vệ.
- **Q3:** SUBSCRIPTION lưu DB value=planDays; response/audit value=null, planDays=n, totalValue=null. CREDIT planDays=null, totalValue=quantity×value. PREMIUM phải tồn tại/active; đọc FOR SHARE trong transaction phát hành.
- **Q4:** không sửa locked YAML; generated OpenAPI phải giữ integer|null và bốn path/method/status/operationId, required/enum/constraints. Lệch → STOP.
- **Q5:** NFR-P02 một HTTP POST warm-up rồi năm POST quantity=100, p95 nearest-rank dưới 2 giây, đo cả commit và response.
- **Q6:** 401 và 403 LEARNER/ACCOUNTANT trên cả bốn endpoint, 403 cookie POST/PATCH thiếu CSRF. Void giữ USED, chỉ UNUSED→VOIDED, gọi lại voided=0. Hai assertion nạp mã của AT-01/AT-07 chỉ nghiệm thu ở T-08; không thêm redeem trong T-07.
- **Pagination/D1/AT-04:** list/detail readOnly REPEATABLE_READ, count và GROUP BY cùng snapshot; resolve tên theo một AuthApi.displayNames, test số truy vấn. Shared AuditService thêm overload bốn tham số ghi target_id=NULL; hai method cũ và positive-ID validation giữ nguyên. Test OutputCaptureExtension quét 100 mã có/không gạch và hash trong log/audit; CREATE/VOID chỉ audit metadata an toàn.
- Gate dự kiến: JDK 17 JAVA_HOME/PATH trong phiên → targeted → regression audit/exception/OpenAPI/auth/credit/callers → full `mvnw -B verify` không ép JVM profile → JaCoCo đúng full-run exec. Chưa có kết quả gate; không đánh hoàn tất T-07.

### G1 STOP tại gate contract

- Self-review độc lập trước gate không có finding source cụ thể; whitespace check sạch, đúng 17-file allowlist (11 ADD/6 MODIFY). Java source được giữ nguyên từ manifest trước gate; không sửa code hoặc YAML sau failure.
- Lượt sandbox targeted exit 1 trước compilation/test: Maven Central parent POM bị `Permission denied`. Lưu log/status riêng và chạy lại đúng selector ngoài sandbox theo yêu cầu xử lý sandbox; không thay source hoặc ép profile.
- Lượt ngoài sandbox dùng Java 17.0.9, production compile và testCompile thành công. `CreditCardBatchContractTest`: 3 tests, 2 failures, 0 errors, 0 skipped. Q4 STOP, không sửa/rerun. Maven đã vào lớp concurrency trước khi monitor nhận summary; yêu cầu dừng lượt đang chạy và giữ evidence, không mở gate tiếp.
- `fourRegisteredOperationsMatchLockedPathsMethodsIdsAndStatuses`: test so security OR theo thứ tự; expected cookieAuth rồi bearerAuth, generated bearerAuth rồi cookieAuth. Hai lựa chọn OR tương đương về ngữ nghĩa; comparator hiện nhạy thứ tự, chưa sửa.
- `integerNullableRecordsAndResponseShapesMatchTheLockedSchemas`: `BatchSummary.total` expected type integer, generated type string. Đây là generated schema drift thật; nullable integer/constraints còn lại chưa được chứng minh toàn bộ vì assertion dừng tại đây.
- Regression/full verify/JaCoCo từ full-run exec và NFR-P02 chưa có kết quả; không dùng coverage task khác hoặc targeted. Chưa commit/push/rebase/PR, chưa T-08. Chờ Leader duyệt vòng sửa contract annotation và comparator OR; không tự đổi locked YAML hoặc nới yêu cầu contract.
- Evidence ngoài repo: `C:/WINDOWS/TEMP/cnhsk-spec013-t07-20261107-233917/` chứa manifests, log/status sandbox và lượt ngoài sandbox, cùng Surefire XML. Locked YAML SHA256 `4B3025DDA60DECFC13B59FA7A8F1CB590593EE36F068FCE051883003003DC6C5`.
- Lượt targeted ngoài sandbox đã dừng bằng Ctrl-C, tool session trả exit 1; không có Maven BUILD SUCCESS hoặc tổng kết toàn lượt. Chỉ suite contract hoàn tất (3/F2/E0/S0), lớp concurrency đã bắt đầu nhưng không có XML hoàn tất; không tính các test chưa chạy thành skipped. Regression/full verify/NFR-P02 chưa chạy; không có JaCoCo Project/Credit từ full-run exec. `manifest-final.json` liệt kê đúng 17 file; 15/15 source/test giữ hash trước gate, tracking được cập nhật riêng. Chưa tạo commit.

## 2026-11-07 — T-06

- Base `5b4c478` đã merge T-05 qua PR #131. Leader duyệt G1 và đúng 11 file (5 ADD, 6 MODIFY), bao gồm thay đổi module 001; chỉ FR-003/004, AT-03 và sáu API §0.3. Dừng ở G2, chưa rebase/push/PR.
- **Q1 — khóa và sổ cái:** `LedgerService.append` dùng `MANDATORY`, tự gọi `AuthApi.lockPlan(userId)` trước mọi truy vấn ledger. Caller vẫn khóa trước khi đọc/sửa PlanState; `updatePlan`/`updateUsage` có tiền điều kiện này trong hợp đồng. Không API thứ bảy, không transaction-proof registry. Sổ cái dùng JDBC SELECT dòng cuối rồi INSERT RETURNING, không JPA save.
- **Q2 — quyền ghi cột gói:** chỉ thêm `updatable = false` cho `User.plan_code` và `plan_expires_at`, giữ insert/setter và mọi annotation khác. Không `@DynamicUpdate`. Test `staleUserFlushDoesNotOverwriteJdbcPlanUpdate`: load User → lock/update bằng JDBC → sửa displayName → save/flush → kiểm mã gói và hạn mới vẫn còn.
- **Q3 — lỗi:** user không tồn tại dùng `USER_NOT_FOUND`/404, message hiện có “Không tìm thấy tài khoản.”; tham số không hợp lệ dùng `IllegalArgumentException`, message cố định “Tham số không hợp lệ.” Không thêm ErrorCode, không đổi hành vi bốn API cũ.
- **Q4 — đếm gói:** PREMIUM chỉ khi mã PREMIUM và expiry > now; FREE khi mã FREE hoặc expiry <= now; mã khác trả 0. Không lọc role/status. Test gồm expiry = now và tài khoản banned.
- **D5/D8/D10:** khóa user trước sổ cái; mọi thời điểm ghi qua `Clock` hoặc tham số `Instant` của API; không endpoint/schema/migration/contract/dependency/config mới. PlanState sao chép map thành snapshot bất biến; email qua `Masking.email`.

### Q2 RED → GREEN và recovery

- Lượt đầu dừng trước Maven vì JAVA_HOME cũ không hợp lệ, exit 1, chưa có test. Leader duyệt recovery chỉ đặt JAVA_HOME/Path trong phiên Maven; xác nhận Java 17.0.9, Maven 3.9.16, không sửa biến hệ thống hoặc repository config.
- Lượt sandbox kế tiếp dừng trước compilation vì quyền mạng Maven Central, exit 1, chưa có test. Lượt ngoài sandbox được phê duyệt giữ nguyên selector và source.
- RED hợp lệ: compilation/context thành công; `AuthPlanApiIntegrationTest.staleUserFlushDoesNotOverwriteJdbcPlanUpdate` FAIL đúng assertion expected PREMIUM / actual FREE; Tests run 1, Failures 1, Errors 0, Skipped 0, Maven exit 1. User.java lúc RED khớp base, chưa sửa annotation.
- Sau RED chỉ sửa hai annotation đã duyệt. GREEN cùng selector: Tests run 1, Failures 0, Errors 0, Skipped 0, Maven exit 0, BUILD SUCCESS; cả mã gói và hạn mới được giữ khi flush entity cũ.
- Full logs/XML/metadata các lượt FAILED và GREEN được giữ riêng ngoài repository; chưa tuyên bố G1 hoàn thành.

### Gate và đối chiếu FR/AT

- Targeted: Tests run 50, Failures 0, Errors 0, Skipped 0, Maven exit 0 / BUILD SUCCESS, đủ 6 suite: AuthApiServiceTest 14, AuthPlanApiIntegrationTest 10, AuthEntitiesTest 5, AuthRepositoriesIntegrationTest 5, LedgerArchitectureTest 3, LedgerServiceIntegrationTest 13.
- FR-003/004: `firstAppendStartsAtOneWithZeroBalanceAndStoredTimestamp`, `sequentialAppendUsesTheLastBalanceAndPersistsMetadata`, `overdraftIsRejectedByTheDatabaseWithoutAnInsertedRow`, `arithmeticOverflowCannotWrapTheBalance` đã PASS.
- AT-03: `sameUserConcurrentAppendsWaitForTheLockAndKeepContinuousSequence` quan sát transaction thứ hai chờ khóa trong PostgreSQL, rồi seq 1/2 và số dư 0→40→33; `failedAppendRollsBackPlanUsageAndEarlierAppends` và `caughtAppendFailureStillMarksTheCallerTransactionRollbackOnly` PASS; ba test architecture bảo vệ append-only, writer duy nhất và MANDATORY PASS.
- §0.3/Q4: snapshot bất biến, writable transaction, lỗi 404/tham số cố định, cập nhật/rollback, masking, downgrade và FREE/PREMIUM/expiry-boundary đều PASS. Bốn API cũ giữ hành vi, hai suite entity/repository giữ PASS.
- Regression: Tests run 693, Failures 0, Errors 0, Skipped 0, Maven exit 0 / BUILD SUCCESS, đủ 48 suite: toàn bộ 29 suite auth, 18 suite mock/spy AuthApi và ModuleBoundaryTest (9/9). Không xuất hiện ngoại lệ LoginAttempt; không sửa mock/stub cũ ngoài constructor wiring AuthApiServiceTest.
- Self-review trước full gate: transaction/khóa trước đọc sổ cái, rollback, tràn số nguyên, append-only và compatibility đều có test PASS; source review độc lập không có finding. 9/9 file Java giữ hash của targeted, đúng 11-file allowlist, whitespace/BOM/secret-marker check sạch.
- Full verify: Tests run 3911, Failures 2, Errors 0, Skipped 0, đủ 249/249 suite, Maven exit 1 / BUILD FAILURE. Phân loại XML xác nhận đúng hai ngoại lệ môi trường: `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` (dòng 398, p95 47800 ms > ngưỡng 30000 ms) và `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` (dòng 304, p95 69357 ms > ngưỡng 30000 ms). Unknown failure 0; không nới assertion, skip hoặc rerun để chọn PASS.
- Full run không ép profile: 09:15:07.950Z–12:19:06.727Z ngày 2026-11-07. Nhóm credit PASS đầy đủ: BillingConfig 5, CardCodes 11, LedgerArchitecture 3, LedgerServiceIntegration 13; AuthPlanApiIntegration 10 và hai suite entity/repository cũng PASS trong full run.
- JaCoCo từ chính full-run exec: Project LINE 11222/11600 = 96,74%; credit LINE 83/86 = 96,51%; report/check Maven exit 0 / BUILD SUCCESS, “All coverage checks have been met”. Vì verify dừng ở Surefire, chạy riêng `jacoco:report@report jacoco:check@check`, không chạy thêm test. Exec trước/sau report khớp bản full đã lưu; một session 09:15:24.433Z–12:19:03.737Z nằm trong full-run interval, không class mismatch. Không dùng execution data của targeted/regression hoặc task trước.
- **G2 CONDITIONAL:** source review độc lập không có finding, targeted/regression đạt; full verify vẫn exit 1 chỉ do hai ngoại lệ đã duyệt. 9/9 file Java giữ hash qua mọi gate; đúng 11 file (5 ADD, 6 MODIFY), không config/dependency/schema/contract thay đổi. Chờ ChatGPT review G2 và Leader nghiệm thu trước G3; chưa rebase/push/PR, chưa bắt đầu T-07.

## 2026-11-07 — T-05

- Leader duyệt G1 trên nhánh `spec/013-t05`, base `52ea878d609670031cdd9239a3b555060ba0a052`, đúng 9 file (4 ADD, 5 MODIFY). Chỉ làm FR-002 / AT-02; dừng tại checkpoint G2 để ChatGPT review. Chưa được rebase, push, mở PR hoặc bắt đầu T-06.
- **Q-T05-1 — API strict:** `format` và `hash` chỉ nhận 16 ký tự canonical Crockford; null/noncanonical ném `IllegalArgumentException` với message cố định, không kèm input/cause. Chỉ `normalize` là API nhận dữ liệu nhập chưa chuẩn. Javadoc `hash` nêu người gọi phải truyền kết quả của `normalize()`.
- **Q-T05-2 — ASCII:** kiểm và từ chối non-ASCII trước casing; chỉ bỏ ASCII space/tab/CR/LF và `-`; uppercase `Locale.ROOT`, đổi O→0 và I/L→1, rồi kiểm đúng 16 ký tự canonical. NBSP, dotless i, sharp s, dash Unicode, full-width và ký tự Unicode khác bị từ chối. Không bỏ VT/FF hoặc các control khác. Đây là quyết định G1 thay đề xuất whitespace Unicode trong Shadow Plan.
- **D3:** pepper bắt buộc ≥32 byte UTF-8, không trim/decode base64. Constraint trên boolean kiểm độ dài; diagnostic cố định chỉ nêu thuộc tính và luật; `toString()` che pepper. Main không có fallback, test profile có fixture giả; không dùng hoặc đọc giá trị môi trường riêng.
- **D8:** dùng bean `Clock` có sẵn, ngày batch chuyển tường minh sang `Asia/Ho_Chi_Minh`. **D10:** không endpoint hoặc contract change; giữ nguyên OpenAPI đã khoá. Không thêm migration, cron, dependency hoặc shared configuration.
- `CardCodes` dùng một `SecureRandom` tái sử dụng, không lưu mã/hash/batch đã sinh; mỗi lần hash tạo `Mac` cục bộ với key UTF-8 nguyên dạng, không chia sẻ `Mac` giữa threads. Không log mã, hash hoặc pepper.
- Tiến độ: source/config và test đã triển khai; đã STOP ở lượt regression đầu và được leader duyệt recovery bỏ ép profile JVM. Recovery hoàn tất targeted/regression và JaCoCo PASS; full verify FAILED chỉ với hai ngoại lệ benchmark đã duyệt. Source/config/test giữ nguyên; bàn giao checkpoint G2 chờ ChatGPT review, không rebase, push hoặc PR.

### Gate G1 và điểm STOP

| Gate | Kết quả thực tế | Maven exit |
|---|---|---:|
| Targeted lần đầu trong sandbox | Maven Central bị Permission denied trước khi test chạy; 0 test, giữ riêng evidence | 1 |
| Targeted network recovery ngoài sandbox | 16 tests (BillingConfigTest 5 + CardCodesTest 11), 0 failure/error/skip, BUILD SUCCESS | 0 |
| Regression config/startup/contract | Đủ 9 suite, 88 tests: 87 pass, 1 failure, 0 error/skip, BUILD FAILURE | 1 |
| Full verify | CHƯA CHẠY do STOP | — |
| JaCoCo current full run | CHƯA CHẠY; không dùng coverage của targeted/regression | — |

Failure: `AiConfigTest.FR006_AT06_missingKeyAllowsStartupAndFailsOnlyOnCompletion`, assertion tại `AiConfigTest.java:44` yêu cầu `AnthropicClaudeClient` nhưng nhận `StubClaudeClient`. Không thuộc FR023 / QuestionImport NFR_P04 / LoginAttempt PEXPIRETIME / CapturedOutput / QuestionAdmin NFR_P02.

Đối chiếu runner/source: regression truyền `-Dspring.profiles.active=test` toàn JVM. `StubClaudeClient` có `@Profile("test")`, `AnthropicClaudeClient` có `@Profile("!test")`; ca kiểm AiConfig nói trên dùng ApplicationContextRunner và cố ý kiểm đường production không có API key. Ca tiếp theo mới bật profile test để kiểm stub. Không sửa AiConfigTest hoặc client ngoài allowlist.

Tại thời điểm STOP đã đề xuất chờ leader duyệt: bỏ ép profile test ở mức JVM cho regression/full, giữ profile test sẵn có ở các test ứng dụng/integration và pepper giả; các ApplicationContextRunner tự kiểm profile riêng. Nếu bắt buộc ép profile JVM toàn cục, cần leader mở allowlist để test AiConfig cô lập profile ở đúng ca kiểm; không nới assertion. Không tự áp dụng khi chưa được duyệt.

Source/test/config 7/7 file vẫn khớp frozen manifest sau targeted và regression; không có source drift. Evidence giữ ngoài repo; không ghi giá trị pepper, mã/hash hoặc log riêng vào tracking.

### G1 recovery — Leader duyệt

- Leader duyệt bỏ `-Dspring.profiles.active=test` trong runner, không sửa test cũ hoặc file ngoài allowlist. `BillingConfig` đã đúng `@Component` + `@Validated` + `@ConfigurationProperties` như `AiConfig`, nên không chỉnh source.
- Lý do cờ cũ: diễn giải yêu cầu dùng test profile thành ép profile toàn JVM. Đây là lỗi điều phối runner, không phải biện pháp xử lý một failure thiếu pepper đã quan sát. Lượt targeted cũ PASS 16/16; failure regression duy nhất là sai loại client tại AiConfigTest.java:44; không có test thiếu pepper thực tế buộc phải thêm cờ.
- Recovery dùng run ID và runner riêng, giữ nguyên các log/result/command FAILED cũ. Không ép profile bằng Maven/JVM. Regression/full không bù pepper toàn process; dùng cấu hình giả sẵn có của test profile, các test quản lý profile riêng. Nếu context dev/prod/no-profile thiếu pepper ngoài ca kiểm âm có chủ đích thì STOP, không tự sửa.
- Targeted recovery: 16/16 tests PASS, 0 failure/error/skip, Maven exit0/BUILD SUCCESS, đủ 2 suite. Các config runner mục tiêu cô lập environment và system properties. Source/config 7/7 file giữ nguyên hash.

### Kết quả recovery và checkpoint G2

| Gate recovery | Kết quả thực tế | Exit / BUILD |
|---|---|---|
| Targeted BillingConfigTest + CardCodesTest | 16 tests, 0 failure/error/skip, đủ 2 suite | 0 / SUCCESS |
| Regression config/startup/contract | 88 tests, 0 failure/error/skip, đủ 9 suite; AiConfigTest 9/9 PASS | 0 / SUCCESS |
| Full verify | 3881 tests, 2 failures, 0 error/skip, đủ 246/246 suite, không thiếu/thừa class | 1 / FAILURE |
| JaCoCo từ đúng full-run exec | Project LINE 11120/11496 = 96,73%; credit LINE 53/56 = 94,64%; All coverage checks have been met; class mismatch 0 | 0 / SUCCESS |

**FULL VERIFY FAILED — chỉ hai ngoại lệ môi trường đã duyệt:**

- `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`, assertion dòng 398: p95 33405 ms, yêu cầu <30000 ms.
- `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries`, assertion dòng 304: p95 46708 ms, yêu cầu <30000 ms.

Phân loại từ XML kiểm đúng class/method, AssertionError và stack file:line; unknown failure 0. Không sửa assertion hoặc ngưỡng, không skip, không chạy lại để chọn PASS. Không có failure thiếu pepper dev/prod/no-profile ngoài các ca kiểm âm có chủ đích. Lượt full duy nhất của recovery chạy 01:56:02.603Z–03:31:29.147Z ngày 2026-11-07; không dùng kết quả full của spec trước.

JaCoCo dùng exec SHA256 `CB0531C8B93C930819643E8D817CF492FE6AD966E799782AFB03CB6FB831C9AE`; XML SHA256 `1327DCED0659A2E1B89BEDFE1C36A93CBB9F5CC14C378C5FD24D4D8914A1EEC2`. Session 01:56:16.103Z–03:31:27.018Z nằm trong full-run interval. Report/check chạy trên cùng exec/classes, không chạy thêm test; threshold LINE80% giữ nguyên.

Source/test/config 7/7 khớp manifest SHA256 `7D0F79D01215F73337B7D1E51528F118223D917EEAC4A66A5B30C052D5DA15E0`; manifest toàn src + pom + .env.example của các lượt gate đều `08CB3C70A86322854E7A284F7963CD53B7C86A12CFDAA27227615E17DDCA588A`. Không thay source giữa lượt FAILED ban đầu và recovery. Independent source review không có finding; targeted thực tế chứng minh toàn cause chain/stack/analyzer/action/captured log và toString không chứa fixture pepper.

Evidence ngoài repo giữ riêng mọi lượt FAILED, recovery result JSON, Surefire XML/private logs, manifests và current exec/report. Chỉ số đo an toàn vào tracking. Checkpoint local chỉ gồm đúng 9 file allowlist; không commit plan, log, môi trường riêng hoặc dữ liệu thật. T-05 vẫn chờ ChatGPT review G2; chưa được G3 hoặc T-06. Self-check source/scope PASS: không ngoài allowlist, BOM/TODO mới, dependency/schema/migration/endpoint/contract/SharedConfiguration change hoặc log mã/hash/pepper.

## 2026-11-06 — Leader duyệt T-04: SCR-074 và SCR-075

Người làm: Tường. Base `origin/main` = `a3dc0da`, đã có T-03 (#113). Phạm vi FR-027, FR-028; AT-25, AT-26. Leader duyệt đúng 12 file, một conventional commit và Draft PR; cho phép đánh ✅ riêng T-04 sau khi gate FE đạt. Không sửa API/types/contract, routeAccess, screenRegistry, navigation, ESLint hoặc shared. Backend T-05…T-13 do NgoQuocHuyy làm; không chạy `mvnw verify` trong task FE này.

### Q-T04-01…03

| Mã | Leader chốt và cách áp dụng |
|---|---|
| Q-T04-01 | Giữ contract/FR-017: `from`, `to` là ngày `YYYY-MM-DD` theo giờ Việt Nam. Yêu cầu ban đầu về gửi UTC timestamp đã được đính chính. FE gửi nguyên ngày được chọn; backend đổi biên ngày sang UTC. API T-01 giữ nguyên. |
| Q-T04-02 | `to` bao gồm ngày cuối; kiểm `0 ≤ to − from ≤ 92` ngày. Hai ngày cách đúng 92 ngày hợp lệ, bao phủ 93 ngày lịch khi tính cả hai đầu. |
| Q-T04-03 | Khi gói hiện có thiếu khoá trong `benefits`, hiển thị khoá đó bằng 0. Khi hạn mức không thay đổi, gửi nguyên snapshot map đã nhận, không tự thêm khoá 0. Chỉ dựng đủ bốn khoá khi có thay đổi giá trị hạn mức hoặc tạo PREMIUM. Giá niêm yết là chuỗi thập phân, chuẩn hoá bằng thao tác chuỗi, không tính tiền bằng số thực. |

### Cách hiểu chung FE–BE

- **Backend T-11 — ngày lọc:** nhận `from/to` là ngày Việt Nam, `to` bao gồm ngày cuối. Dùng phạm vi nửa mở `[00:00 Asia/Ho_Chi_Minh của from, 00:00 Asia/Ho_Chi_Minh của ngày sau to)`, rồi đổi hai biên sang UTC khi truy vấn timestamp. Kiểm hiệu hai ngày trong khoảng 0…92, không cộng thêm 1 khi kiểm giới hạn. Cùng quy tắc cho danh sách và xuất CSV. FE không gửi UTC timestamp thay cho tham số `date`.
- **Backend T-10 — so sánh hạn mức:** nên so sánh `benefits` theo giá trị của bốn tính năng, thiếu khoá coi là 0, để không trả `PLAN_IN_USE` nhầm. FE giữ map nhận được khi chỉ sửa tên/mô tả/giá/số ngày/cờ đang bán; việc hiển thị khoá thiếu là 0 không tự biến thành thay đổi cấu hình. Có test snapshot PREMIUM thiếu `ASSISTANT`, sửa giá vẫn không thêm khoá; sửa giá trị hạn mức thì gửi đủ bốn khoá.
- **Đối chiếu:** FR-019 và API không nhận bộ lọc; kết quả luôn là toàn sổ. UI ghi rõ phạm vi toàn sổ, không suy luận lỗi chuỗi từ trang dữ liệu hoặc bộ lọc loại. Không tự điều chỉnh giao dịch. Phần legacy trong `screen-fields.md` về tra cứu tài khoản/điều chỉnh nằm ngoài phạm vi đã khoá của T-04.
- **CSV:** server tạo CSV; FE giữ nguyên Blob và dùng tên server trả, thiếu tên thì `credit-ledger.csv`. Không dựng lại CSV từ trang đang xem, không tự che/mở che email. `EXPORT_TOO_LARGE` hướng dẫn thu hẹp ngày/loại, không tải file và không xoá bảng/bộ lọc.

### Thiết kế và ghi chú kỹ thuật T-04

- `Scr074PlanManagementPage` gọi `listPlans(signal)` và `updatePlan(code, body)` từ `api/plans.ts`; kiểm ba mã gói đúng contract. View có form FREE/PREMIUM, PREMIUM_PLUS chỉ đọc, số người đang dùng từ server, xác nhận lưu ngay trong trang. `PLAN_IN_USE` gắn lỗi và giải thích vào các ô hạn mức, giữ draft. Kết quả lưu thành công vẫn hiện khi tải lại danh sách lỗi.
- `Scr075CreditLedgerPage` dùng `listLedger({from, to, type?, page, size: 20}, signal)`, `exportLedger(filter, signal)`, `reconcileLedger(signal)` từ `api/ledger.ts`. Ban đầu chưa có ngày thì không gọi danh sách; chỉ truy vấn sau khi áp dụng bộ lọc hợp lệ. Tổng dùng `totals` của toàn bộ bộ lọc từ server, mang nhãn "Tổng điểm"; email hiển thị nguyên `maskedEmail`. Các thời điểm giao dịch dùng `Intl.DateTimeFormat` với `Asia/Ho_Chi_Minh`.
- Export gắn với bộ lọc đã áp dụng; sửa draft/áp dụng phạm vi mới hoặc unmount sẽ huỷ request và bỏ qua Blob muộn, không tự tải. Chốt chống gửi lặp và thế hệ request ngăn phản hồi cũ xoá pending của request mới. Đối chiếu có trạng thái/lỗi/thử lại riêng và danh sách ba loại lỗi theo contract.
- Page/View không import preview hoặc test fixture. `test-fixtures.ts` chỉ được các file test import; dữ liệu mới tạo bằng `structuredClone` trong từng ca. Hai preview render View và callback demo trên bản sao, giữ fixture gốc, năm preview ID và ba preview T-01…T-03. Chỉ preview mô phỏng file CSV, Page thật luôn nhận file từ API.
- Không `confirm()`/dialog portal, không spy hàm timer toàn cục. Cleanup dùng cờ vòng đời và huỷ `AbortController`; đã bỏ hai thao tác tăng bộ đếm dư gây warning ref trong cleanup SCR-075, không tắt rule hay sửa ESLint. Sau sửa đã chạy lại test mục tiêu, ba seed, toàn bộ Vitest, lint và build.

### Gate T-04 trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; 333 package theo lockfile |
| `npm run typecheck` | PASS |
| Hai Page T-04 + `credits/routes.test.tsx`, 2 worker | 137 pass, 0 fail, 0 skip; SCR-074: 40, SCR-075: 36, routes/preview: 61; 11,94 giây |
| Shuffle ba file, seed `2611064`, `2611065`, `2611066`, 2 worker | Cả ba: 137 pass, 0 fail, 0 skip; lần lượt 11,82 / 12,42 / 11,76 giây |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 154 file pass; 2.174 test pass, 0 fail, 0 skip; 209,73 giây |
| `npm run lint` | PASS; 0 error, chỉ 1 warning có sẵn `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.212 module; Vite build 4,71 giây |

Lượt toàn bộ cuối không có fail ngoài T-04 nên không cần chạy riêng file test chập chờn. T-14 còn phần tích hợp backend thật; không mở lại menu tài chính trong T-04.

## 2026-11-06 — Leader duyệt T-03: SCR-073 Card Batch Management

Người làm: Tường. Phạm vi FR-026, AT-24; validation/payload theo FR-005…FR-008 và quyền FE theo FR-001. Base `origin/main` = `a0ca1fd`, đã có T-01 (#59), PR ẩn menu #61 và T-02 (#110). Leader duyệt đúng 9 file, một conventional commit và Draft PR; cho phép đánh ✅ riêng T-03 sau khi gate FE đạt. Backend T-05…T-13 thuộc NgoQuocHuyy; T-03 không sửa backend hay chạy `mvnw verify` theo chốt leader.

### Q-T03-01…04

| Mã | Leader chốt và cách áp dụng |
|---|---|
| Q-T03-01 | Chỉ bảng lô, phân trang 20 và số liệu từng lô từ `BatchSummary`. `screen-fields.md` còn nhắc bộ lọc, trạng thái từng mã và tổng hợp theo chiến dịch, nhưng contract không có API tương ứng; nếu cần leader mở task riêng. |
| Q-T03-02 | Xác nhận ngay trong trang cho cả phát hành và khoá lô, không dùng `confirm()` hoặc dialog portal. Xác nhận phát hành hiện đúng kênh, loại, số lượng, giá trị/số ngày, metadata sẽ gửi và hạn dùng theo giờ Việt Nam; huỷ giữ form và trả focus. Khoá chỉ tác động mã chưa dùng; số thực tế lấy từ server, chấp nhận `voided=0`. |
| Q-T03-03 | CSV tạo tại client: UTF-8 không BOM, xuống dòng `\r\n`, header `batchId,code`, một mã mỗi dòng; tên `card-batch-<batchId>.csv`, không có dữ liệu người nhận. Test kiểm byte BOM, UTF-8, CRLF, nội dung/tên file và không có ô bắt đầu bằng `=`, `+`, `-`, `@`. Dùng helper `downloadBlob` có sẵn, không sửa shared. |
| Q-T03-04 | Mã ở bộ nhớ màn đến khi rời/tải lại màn hoặc tạo thành công lô kế tiếp. Tải CSV không xoá mã. Khi mã cũ còn hiện, xác nhận cảnh báo "Mã lô trước sẽ mất khi phát hành thành công lô mới." Tạo lô mới thất bại giữ mã cũ. Khi lỗi mạng/kết quả POST không xác định: không tự gửi lại, hiện "Không rõ lô đã được tạo chưa — kiểm tra bảng lô trước khi tạo lại" và tải lại danh sách. |

### Thiết kế và ghi chú kỹ thuật T-03

- `Scr073CardBatchPage` dùng `listCardBatches({page, size: 20}, signal)`, `createCardBatch(body)`, `voidCardBatch(batchId)` từ `api/cardBatches.ts` T-01. API/types khớp contract và giữ nguyên. Page kiểm `ADMIN`, nội dung được tạo mới khi đổi tài khoản; đọc dùng `useApiQuery`, mutation có chốt chống gửi lặp và bỏ qua kết quả khi unmount.
- `CardBatchView` chỉ nhận props dữ liệu/trạng thái/callback. Form không ngầm chọn loại/kênh; đổi loại/kênh bỏ dữ liệu điều kiện cũ. Kiểm giới hạn số nguyên và độ dài theo contract; `SUBSCRIPTION` chỉ PREMIUM. Ô hạn dùng ghi rõ giờ Việt Nam, chuyển UTC khi gửi; hiển thị qua `Intl.DateTimeFormat` với `Asia/Ho_Chi_Minh`.
- Sau phát hành hiện ngay `CreatedBatch`, rồi tải lại trang đầu của danh sách. Lỗi tải danh sách có vùng lỗi/thử lại riêng, vẫn giữ mã và tải CSV. Có nút tải lại bảng để kiểm tra trạng thái mà không phải rời màn. Khoá lô chỉ thay số liệu sau khi server trả kết quả và tải lại; không sửa/xoá mã đã dùng.
- Preview `credits-batch-samples` render View và callback demo trên bản sao fixture T-01; giữ năm ID, fixture gốc và bốn preview còn lại. Mã demo tự soạn, có `U` ngoài bảng Crockford nên không dùng được. Test Page mock API, fixture chỉ lấy từ `test-fixtures.ts`; file này chỉ được test import. Không import fixture vào Page/View/screens.
- Không ghi mã vào storage/URL/log. Bảng đọc từ `BatchSummary`, không có mã thô/hash. Giữ guard/redirect và kiểm menu tài chính đang ẩn; route `/admin/card-batches` và shared registry/navigation không sửa.
- Giữ bài học timer của T-02: không spy hàm timer toàn cục. T-03 không tạo timer riêng. Có test phản hồi cũ khi chuyển nhanh trang 1 → 2 → 1, huỷ GET khi unmount, bỏ qua mutation sau unmount/đổi tài khoản, cùng kiểm fixture không bị sửa.
- Lượt build đầu phát hiện 6 lỗi `TS2769` trong hai file test T-03 do thêm `exact` vào `getByRole`. Đã bỏ tuỳ chọn không thuộc API này; `name` dạng chuỗi vẫn khớp chính xác, không đổi assertion AT-24. `npm run typecheck` đạt, sau đó chạy lại toàn bộ chuỗi gate dưới đây trên bản cuối.

### Gate T-03 trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; 333 package theo lockfile |
| SCR-073 + `credits/routes.test.tsx`, 2 worker | 110 pass, 0 fail, 0 skip; Page 49, routes/preview 61; 11,31 giây |
| Shuffle hai file, seed `2611061`, `2611062`, `2611063`, 2 worker | Cả ba: 110 pass, 0 fail, 0 skip; lần lượt 11,07 / 11,12 / 10,84 giây |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 152 file pass; 2.098 test pass, 0 fail, 0 skip; 210,34 giây |
| `npm run lint` | PASS; 0 error, chỉ 1 warning có sẵn `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.206 module; Vite build 4,81 giây |

Lượt toàn bộ cuối không có fail ở file ngoài T-03, không cần chạy riêng lại theo quy tắc test chập chờn của leader. Tích hợp với backend thật thuộc T-14; không mở lại menu tài chính trong task này.

## 2026-11-05 — Leader duyệt T-02 và thiết kế bổ sung Page + View + API

Phạm vi: SCR-030, SCR-031; FR-024, FR-025; AT-22, AT-23. Đúng 12 file trong allowlist bổ sung của leader; một commit và Draft PR. Leader cho phép đánh ✅ T-02 sau khi gate đạt. Không mở lại menu tài chính; không sửa route, cấu hình ESLint, contract, types hay API T-01.

### Thiết kế đã chốt

- `Scr030PlanCreditsPage` dùng `getMyWallet(signal)` và `redeemCard({code})` từ `api/wallet.ts`; hiển thị ngay `RedeemResult`, rồi tải lại ví. Lỗi tải lại ví có vùng lỗi riêng, không đổi kết quả nạp thành thất bại.
- `Scr031TransactionHistoryPage` dùng `listMyTransactions({page, size: 20}, signal)`; trang API bắt đầu từ 0. `useApiQuery` bỏ qua phản hồi cũ và huỷ signal khi đổi trang/unmount; có test chuyển nhanh 1 → 2 → 1.
- `PlanCreditsView`, `TransactionHistoryView` nhận props dữ liệu/trạng thái/callback; giữ logic nhập mã, xác nhận nội tuyến, countdown, thời gian Việt Nam và điều khiển phân trang. Page/View không import fixture.
- `catalog.preview.ts` render hai View bằng fixture T-01, giữ ID và ba preview quản trị. Chỉ preview giữ trạng thái/bộ xử lý demo; không gọi API thật.
- `test-fixtures.ts` chỉ được các file test import; test mock module API và tạo dữ liệu mới bằng `structuredClone` trong `beforeEach`. Không có dữ liệu mẫu trong luồng Page gọi API.

### Q-T02-01…03

| Mã | Leader chốt |
|---|---|
| Q-T02-01 | FE bỏ khoảng trắng và `-`, đổi chữ ASCII thành hoa, nhóm 4 ký tự, không gạch cuối; chỉ kiểm đủ 16 ký tự `[A-Z0-9]`. Lượt nhập/dán có ký tự lạ hoặc quá dài bị từ chối nguyên lượt, hiện gợi ý định dạng. Crockford base32 và quy đổi O/I/L thuộc server T-08. Preview chỉ nhận đúng mã mẫu đã chỉ định; mã khác lỗi chung. |
| Q-T02-02 (cập nhật, thay chốt cũ) | Trạng thái và xử lý demo chỉ nằm trong `catalog.preview.ts`, trên bản sao. Fixture T-01 có ví và kết quả nạp cùng `balance=121`, trong khi `amount=100`: không dùng chúng nguyên trạng như snapshot trước/sau một lần nạp. Preview tính kết quả nhất quán với ví đầu vào, giữ nguyên fixture gốc; T-14 lưu ý khi đối chiếu dữ liệu thật. Page dùng kết quả server và tải lại ví. |
| Q-T02-03 | Không làm bộ lọc lịch sử. `screen-fields.md` có nhắc bộ lọc nhưng FR-025, AT-23 và contract chỉ có bảng + phân trang 20. Nếu cần, leader mở task riêng. |

### Ghi chú kỹ thuật

- Không spy hàm timer toàn cục khi dùng vi.useFakeTimers trên Vitest 2.1.9: restoreAllMocks gắn lại hàm giả.
- Chẩn đoán timer: trước 429 = 0; sau 429 = 2; sau tiến 1 giây = 1; sau unmount = 0. Page chỉ tạo một interval 250 ms; timer còn lại là `setImmediate` của jsdom cho sự kiện `select` sau `setSelectionRange`.
- Test cleanup dùng hành vi: số timer tăng sau 429, countdown giảm, unmount + chạy timer đang chờ trở về mức ban đầu; tiến thêm 10 giây không nạp lại/không `console.error`. Có ca kiểm đầu test tiếp theo dùng timer thật và `setTimeout` không có `.clock`. Không còn spy trên hàm timer.
- Helper nhập mã dùng `getByRole('textbox', { name: 'Mã thẻ' })` để tránh nhãn trùng. Kỳ vọng AT-22/AT-23 giữ nguyên ý nghĩa.
- `retryAfterSeconds` thiếu, sai kiểu, NaN, âm hoặc vô hạn dùng thông báo chung, không tạo countdown. Có test kết quả nạp thành công vẫn hiện khi tải lại ví lỗi, cùng kết quả muộn sau unmount/đổi tài khoản.

### Gate trên bản cuối

| Lệnh/kiểm tra | Kết quả |
|---|---|
| `npm ci` | PASS; cài 333 package theo lockfile |
| Hai file test page, `--minWorkers=2 --maxWorkers=2` | 52 pass, 0 fail, 0 skip (SCR-030: 41; SCR-031: 11) |
| Shuffle hai file, seed `2611051`, `2611052`, `2611053` | Cả 3 lượt: 52 pass, 0 fail, 0 skip |
| `npm run test -- --minWorkers=2 --maxWorkers=2` | 151 file pass; 2.048 test pass, 0 fail, 0 skip; 205,35 giây |
| `npm run lint` | PASS; 0 error, 1 warning có sẵn tại `shared/ui/LevelBadge.tsx:6` (`react-refresh/only-export-components`) |
| `npm run build` | PASS; TypeScript + Vite 5.4.21, 2.203 module; Vite build 4,68 giây |

Lượt toàn bộ cuối không có test chập chờn thất bại, không cần chạy riêng lại file ngoài T-02. API T-01 khớp contract và giữ nguyên; kiểm thử tích hợp với backend thật và đối chiếu `/v3/api-docs` vẫn thuộc T-14.

## 2026-11-03 — Leader duyệt Shadow Plan 013/T-01

- Spec §0.4 và T-01 ghi nhầm `shared/auth/navigation.ts` (chỉ xử lý redirect); menu thực tế ở `shared/layout/navigation.ts`.
- T-01 thêm ba mục tài chính vào `adminNavigation`, mỗi mục chỉ có `role: 'ADMIN'`. Không sửa `learnerNavigation`, `LearnerLayout`, `PublicLayout`; menu học viên chờ PR điều hướng chung do leader giao.
- `screens.ts = {} satisfies ScreenCatalog`, theo tiền lệ 009/T-15 và 014/T-01. `ScreenRoute` tự hiển thị màn tạm; T-02–T-04 đăng ký page thật.
- `catalog.preview.ts` export fixture tự soạn và đăng ký vào `app/preview/registry.ts` ngay ở T-01. Mã mẫu không dùng được; không dùng email hay dữ liệu tài chính thật.
- `Benefits` dùng `Partial<Record<PaidFeature, number>>`: mô tả OpenAPI và FR-016 chỉ cho bốn khoá `PaidFeature`, không bắt buộc đủ bốn. Không sửa contract.
- Base là `origin/main` mới nhất đã có #56 và #57. Nếu 012/T-01 merge trước, rebase lên main mới trước khi xin review vì cùng ba file registry/route.
- Leader cho phép đánh ✅ T-01 trong cùng PR; đúng 15 file, một conventional commit, PR Draft, không tự merge. Nháp mô tả PR để ngoài repo.

## 2026-11-03 — Shadow Plan spec (leader duyệt)

Nguồn đã đọc (origin/main `5439672`): `AGENTS.md` §12, constitution v1.4.1 (§Bảy actor, §Mô hình kinh doanh, HR-05, AC-03/07/09, BUS-06, BUS-12…15), migration V1 (`credit_cards`, `credit_transactions`, `plans`, `auth.users`, `user_roles`), `database.md` (`feature_code`), UC-093…UC-101 (`use-cases-07`), `screen-fields.md` SCR-030/031/073/074/075, `CONTEXT.md` (Lượt), `AuthApi`, `CurrentUser`, `AuditService`, `RateLimiter`, `SecurityConfig` (`/api/me/**`, `/api/admin/**`), plan `261102-2104` phase 5, 6, 7. Đếm `ACCOUNTANT`: 224 lần trong 51 file.

| # | Câu hỏi | Leader chốt |
|---|---|---|
| Q1 | Ai sinh/khoá lô thẻ | Leader: *"đang tính bỏ ACCOUNTANT cho vào ADMIN vì đây là hệ thống phễu, không quản lý doanh thu"* → sau phân tích chọn **gộp nghiệp vụ vào ADMIN, giữ mã role ACCOUNTANT** (không migration); thêm luật **không nạp mã do mình tạo** |
| Q2 | Dùng điểm mua gói | **Không, theo UC** |
| Q3 | Mã SUBSCRIPTION kích hoạt gói nào | **Không migration, chỉ PREMIUM** |
| Q4 | Giá trị gói trả phí | **Quản trị nhập qua SCR-074** |
| Q5 | Dịch vụ trừ lượt/điểm | **Làm luôn** (`EntitlementApi`) |
| Q6 | Sổ cái SCR-075 | **Xem + xuất file + đối chiếu** |
| Q7 | Bắt buộc email đã xác minh khi nạp | **Không** |
| D | Mặc định | **Duyệt**: mã 16 ký tự base32 + HMAC (`CARD_CODE_PEPPER` trong `.env`); chặn 5 lần sai/giờ/người + 30/giờ/IP; lô ≤ 100, mã thô trả một lần; chỉ khoá mã chưa dùng; gia hạn cộng dồn, job đêm hạ về FREE; cấm nạp mã do mình tạo; không migration; FE trước; chỉ file trên máy |

### Xung đột tài liệu đã ghi nhận

| # | Xung đột | Xử lý |
|---|---|---|
| W1 | Hiến pháp §Bảy actor, `business.md` §1, BR-099-2, BR-100-2, BR-101-2, SCR-073…075: chỉ `ACCOUNTANT`; "ADMIN không sinh mã, không đụng sổ cái" | Q1 — **RFC, điều kiện APPROVED** |
| W2 | Plan phase 5 "dùng điểm mua gói" | Q2; sửa plan |
| W3 | AC-09 JSONB chỉ đọc ↔ `users.free_usage` ghi mỗi lần tiêu thụ (V1) | Giữ V1, ghi ngoại lệ trong RFC |
| W4 | `credit_cards` không có cột gói | Q3 |
| W5 | UC-099/CONTEXT đề xuất 1 000 mã/lô ↔ SCR-073 chốt 100 | 100 |
| W6 | UC07 khoảng trống #19: chống lặp tiêu thụ cần lưu bền | Redis 24 giờ, không thêm cột; rủi ro §11 |
| W7 | UC-094 400 chung ↔ UC-136 404/409/410 | 400 chung |
| W8 | Red-team #6 (plan) "ACCOUNTANT và ADMIN" | Thay bằng Q1 |
| W9 | 015 trợ lý "hỏi thoải mái" ↔ `ASSISTANT` là một trong 4 tính năng tốn phí | 015 không gọi `EntitlementApi`; ghi lại khi viết 015 |

### Còn mở

- RFC Q1 (W1, W3) cần cả nhóm đồng thuận.
- Plan phase 5 cần sửa theo Q1, Q2, Q5, Q6 (chờ leader cho phép như phase 3, 4).
