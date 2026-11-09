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
