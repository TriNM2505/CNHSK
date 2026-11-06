# Quyết định triển khai Spec-009

## K1-A — Nguồn PostDetail cho MODERATOR trên SCR-040

- Ngày chốt: 2026-10-30.
- Status: CHỐT / APPROVED BY LEADER.
- Vấn đề: nguồn PostDetail cho MODERATOR khi mở SCR-040.
- Decision: SCR-040 dùng public PostDetail cho mọi actor; chỉ bài APPROVED.
- MODERATOR dùng moderation-comments sau khi public detail APPROVED tải thành công.
- Bài ở trạng thái khác được xem và kiểm duyệt tại SCR-061 (T-18).
- FR-063 moderation-comments vẫn hỗ trợ mọi trạng thái bài ở backend; SCR-040 chỉ sử dụng cho bài APPROVED.
- Không thay OpenAPI; không fallback public 404 sang moderation detail.

## T-18 — Triển khai các màn kiểm duyệt

- Ngày chốt: 2026-10-30.
- Status: CHỐT / APPROVED BY LEADER.
- Triển khai SCR-060/061/062/063 theo Shadow Plan v2.
- SCR-061 có moderation-comments cho mọi trạng thái bài theo khả năng backend.
- SCR-063 có panel direct comment moderation mở theo yêu cầu, lấy metadata bằng API moderation-comments hiện có.
- Direct moderation không tự đóng report case, không gọi `decideReportCase` ngầm.
- Không bổ sung route/API ngoài scope; ngoại lệ route chỉ áp dụng khi thực sự thiếu route.
- Không sửa `screen-fields.md`; không thay locked OpenAPI hoặc spec.
- Sau T-18 delivery: FE Visual/Functional QA tại 360/768/1280 px trước khi Shadow Plan backend.
- PR #20 tiếp tục DRAFT.

## T-18 / SessionStorage lifecycle relaxation — CHỐT 2026-10-30

Status: CHỐT / APPROVED BY LEADER.

1. Leader REJECT thay đổi `RouteScaffolding.tsx` và mọi auth observer ngoài feature.
2. Chỉ lưu văn bản MODERATOR tự gõ, dưới dạng `text` và `savedAt`; không lưu confirmation hoặc kết quả có thể replay mutation.
3. Storage key gồm namespace `cnhsk:spec009:t18:draft:` + userId + screenId + resourceId + field.
4. Chỉ đọc/khôi phục nháp khi component T-18 đang mount sau khi guard xác nhận MODERATOR, userId trùng và tài nguyên hợp lệ.
5. Auth loading/error: không khôi phục.
6. Dọn lười khi mount: xóa keys thuộc namespace T-18 của userId khác mà không đọc value của họ; xóa dữ liệu hỏng, thiếu/sai timestamp hoặc quá 2 giờ. TTL 7.200.000 ms được enforce khi đọc/mount; đúng 2 giờ đã hết hạn. Không có background timer tự xóa.
7. Không dùng `sessionStorage.clear()`; không xóa dữ liệu ngoài namespace T-18.
8. Xóa nháp tương ứng khi gửi quyết định thành công (trước navigation) hoặc bấm Hủy soạn nội dung. Mutation lỗi giữ nháp; đóng confirmation riêng không được coi là hủy toàn bộ nội dung.
9. Chấp nhận nháp có thể còn trong tab sau logout/mất role cho đến khi tab đóng hoặc lần dọn tiếp theo; không được hiển thị cho user khác. Cùng user đăng nhập lại trong cùng tab trước TTL có thể khôi phục nháp hợp lệ. Authorization luôn dựa vào auth hiện tại.
10. Không lưu API responses, nội dung bài/bình luận, danh tính người báo cáo, tokens, credentials hoặc errors. Storage không khả dụng thì form tiếp tục hoạt động bằng state trong memory.

## [009] Leader — Backend T-01 Implementation Authorization

- Ngày chốt: 2026-10-31.
- Status: CHỐT / APPROVED BY LEADER.
- Leader duyệt implementation T-01 (persistence foundation). T-02–T-14 phải có Shadow Plan và approval riêng trước implementation.
- Tạo nhánh backend `spec/009-community-blog-moderation-backend` từ `origin/main` mới nhất sau fetch; tạo Draft PR mới vào `main`, không dùng nhánh FE cũ hoặc PR #20.
- Không merge/rebase/pull `main` vào nhánh backend; chỉ fetch để quan sát và báo Leader khi có thay đổi ảnh hưởng thực tế.
- Allowlist đúng 21 source/test files theo Shadow Plan T-01 đã review; `questions.md` là decision log được phép cập nhật kèm theo. Không sửa locked contracts, shared/auth/learning, migrations hoặc schema.
- AC-09: không tạo repository query/filter condition trên cột JSONB. T-01 chỉ có nền `JpaRepository`, không triển khai nghiệp vụ task sau.
- Testcontainers PostgreSQL 18, Redis 7 và Mailpit qua `AbstractIntegrationTest`; áp dụng toàn bộ Flyway migrations hiện hành, Hibernate `ddl-auto=validate`. Không dùng H2 hoặc Neon thay thế.
- Quality gate: full Maven verify exit 0, zero failures/errors/skipped, JaCoCo LINE/BUNDLE ≥80%, tổng tests không thấp hơn 1.537; không đổi coverage rules hoặc test configuration.
- Independent source review bắt buộc trước commit. Sau review PASS: một Conventional Commit T-01, push nhánh backend và tạo Draft PR; không merge. Chưa được sửa checkbox `tasks.md` khi chưa có authorization riêng.

### Backlog FE completeness audit — chưa sửa trong T-01

Các findings dưới đây giữ trạng thái P2 / OPEN, xử lý bằng PR FE riêng trước nghiệm thu tích hợp.

| ID | Màn hình / khu vực | Trạng thái |
|---|---|---|
| FE009-AUD-01 | SCR-041 — stale mutation navigate sau unmount | P2 / OPEN |
| FE009-AUD-02 | SCR-041 — mất unsaved draft sau verify-email roundtrip | P2 / OPEN |
| FE009-AUD-03 | SCR-041 — thiếu cancel/dirty-confirmation | P2 / OPEN |
| FE009-AUD-04 | SCR-040 — edit-window timer dùng thời điểm cũ khi GET chậm | P2 / OPEN |
| FE009-AUD-05 | SCR-040 — đồng thời hai ReportForm gây duplicate DOM IDs | P2 / OPEN |
| FE009-AUD-06 | SCR-040, SCR-061, SCR-063 — inline CommentForm/ReportForm/CommentModerationForm có thể khóa nút Đóng khi mất eligibility | P2 / OPEN |
| FE009-AUD-07 | Auth navigation → SCR-060–063 — MODERATOR entry còn hiển thị under construction/403 | P2 / OPEN |

### Visual QA — trạng thái thực tế và thứ tự mới do Leader chốt

- FE T-15–T-18 đã delivery code và tests.
- Đã xem năm trạng thái Preview của SCR-039 qua ảnh do người dùng cung cấp.
- Chưa hoàn tất Visual QA đầy đủ tại 360/768/1280 px.
- Chưa kiểm đủ SCR-040, SCR-041, SCR-060–063 bằng screenshot/browser QA.
- Chưa có E2E với backend Community thật.
- Leader ưu tiên backend trước; visual refinement giống Hanzii được defer đến sau khi có backend. Quyết định này thay thứ tự Visual QA trước backend đã ghi ngày 2026-10-30, không thay các quyết định nghiệp vụ K1-A hoặc T-18.
- **T-18 Visual QA: NOT COMPLETED / DEFERRED.** Không ghi nhận PASS.

## [009] Leader — Chốt Backend T-02 / Q-T02-1 — 2026-10-31

- Status: CHỐT / APPROVED BY LEADER.
- Duyệt implementation T-02 theo phương án staged verification. T-01 đã được nghiệm thu và merge; cập nhật T-01 ✅ trong PR T-02. T-02 chỉ được đánh ✅ sau implementation, quality gates và independent source review đạt điều kiện delivery.
- S = 24 operations trong locked `contracts/openapi-community.yaml` (21 paths), với expected inventory độc lập cho method/path/operationId/exact response statuses.
- H = Community handlers thực sự đăng ký trong Spring MVC, nhận diện theo package `com.cnhsk.community` và package con hoặc Community route prefixes.
- D = Community operations từ `/v3/api-docs` thật do Springdoc sinh ra.
- Luôn assert H subset S, D equals H, exact response statuses cho operations đã có. Không bỏ qua handler thiếu documentation; extra/hidden/method/status drift phải FAIL.
- Missing operations phải báo số lượng cụ thể và danh sách ổn định, tối thiểu `source=24, runtime=<H>, documented=<D>, missing=<24-H>`.
- Chưa có controllers, kể cả H = D = 0, không đồng nghĩa runtime parity PASS. Khi đó chỉ xác nhận source contract và staged consistency, chưa chứng nhận đủ API.
- Không dùng mode flag, system property, cấu hình chế độ hoặc profile riêng để thay đổi strictness assertions. T-02 luôn chạy cùng một bộ assertions staged đã duyệt.
- T-14 bắt buộc đưa `src/test/java/com/cnhsk/contract/CommunityContractTest.java` vào Shadow Plan và proposed file allowlist, chuyển assertion thành H = D = S, yêu cầu runtime đủ 24 operations và exact statuses. Missing/hidden/extra handlers vẫn phải bị phát hiện. Nếu Shadow Plan T-14 thiếu mục này, Leader không duyệt T-14. Không triển khai gate T-14 trong T-02.
- Mỗi controller Community ở T-03–T-14 phải khai đủ `@ApiResponses` theo approved contract.
- Chỉ 3 file T-02 được phép thay đổi: `src/test/java/com/cnhsk/contract/CommunityContractTest.java`, `specs/009-community-blog-moderation/tasks.md`, `specs/009-community-blog-moderation/questions.md`.
- Không thay shared/auth/learning, locked contracts, migrations, dependencies hoặc test configuration dùng chung. Chỉ cô lập Spring test context trong test mới khi cần; không mock MVC handlers, Springdoc hoặc `/v3/api-docs`, không tắt toàn bộ security filters.
- Bảy FE findings FE009-AUD-01…07 vẫn P2 / OPEN, không sửa trong T-02.
- **T-18 Visual QA: NOT COMPLETED / DEFERRED.** Không có xác nhận E2E với backend Community thật.

## [009] Leader — Backend T-03 Implementation Authorization — CHỐT 2026-11-01

- Status: CHỐT / APPROVED BY LEADER. Leader duyệt implementation T-03 theo Shadow Plan đã review; chưa duyệt T-04.
- Base phải là `origin/main` mới nhất sau fetch, không khóa vào mốc lịch sử. Base T-03 được xác minh khi bắt đầu: `1a2beaaba74d23156f4e26816d8b59e86408e551`. T-01/T-02 đã merge; không pull/merge/rebase main vào nhánh T-03 sau khi tạo.
- Allowlist tối đa 14 file, không thay thế hoặc thêm file thứ 15 khi chưa được Leader duyệt:

```text
src/main/java/com/cnhsk/shared/config/SecurityConfig.java
src/main/java/com/cnhsk/shared/exception/ErrorCode.java
src/test/java/com/cnhsk/shared/exception/GlobalExceptionHandlerTest.java
specs/009-community-blog-moderation/tasks.md
specs/009-community-blog-moderation/questions.md
src/main/java/com/cnhsk/community/common/CommunityProperties.java
src/main/java/com/cnhsk/community/common/CommunityLimits.java
src/main/java/com/cnhsk/community/common/CommunityAuthors.java
src/main/java/com/cnhsk/community/common/UserRef.java
src/test/java/com/cnhsk/community/common/CommunityPropertiesTest.java
src/test/java/com/cnhsk/community/common/CommunityAuthorsTest.java
src/test/java/com/cnhsk/community/common/CommunityLimitsTest.java
src/test/java/com/cnhsk/community/common/CommunityErrorResponseTest.java
src/test/java/com/cnhsk/shared/config/CommunitySecurityConfigIntegrationTest.java
```

- `SecurityConfig` chỉ thêm đúng ba matcher: `GET /api/public/community/**` → `permitAll`; `/api/community/moderation/**` → `hasRole("MODERATOR")`; `/api/community/**` → `hasRole("LEARNER")`. Moderation phải đứng trước matcher learner rộng và tất cả trước default `denyAll`; không đổi các matcher cũ, JWT, CSRF, filter order hoặc cookie/bearer precedence.
- Chèn đúng 13 ErrorCode sau đây, cùng thứ tự, ngay trước `INTERNAL_ERROR`: `SELF_MODERATION`, `SELF_REPORT`, `POST_NOT_FOUND`, `COMMENT_NOT_FOUND`, `TARGET_NOT_FOUND`, `REPORT_CASE_NOT_FOUND`, `POST_NOT_EDITABLE`, `POST_NOT_PENDING`, `COMMENT_NOT_EDITABLE`, `COMMENT_EDIT_WINDOW_EXPIRED`, `COMMENT_NOT_RESTORABLE`, `DUPLICATE_REPORT`, `REPORT_ALREADY_HANDLED`. Giữ nguyên mọi mã cũ, kể cả Spec-006; cập nhật đúng expected strings trong `GlobalExceptionHandlerTest`, giữ `containsExactly`.
- `CommunityProperties` dùng prefix `cnhsk.community`, defaults 5 bài/ngày, 30 comments/replies mỗi 60 phút, 20 reports/ngày và edit window 15 phút; counts phải nguyên dương, Duration dương/không null, được override theo configuration hiện có. Không thêm YAML hoặc dependency.
- `CommunityLimits` chỉ là foundation: quota policy, time snapshot, boundaries, retry và `ApiException`. Count/oldest do service sau cung cấp; không có repository query, DB count, Redis counter, lock, transaction hoặc concurrency enforcement trong T-03.
- Tái sử dụng bean Clock UTC hiện hữu; mỗi snapshot chụp đúng một `Instant now`. Ngày quota dùng `Asia/Ho_Chi_Minh`; không thêm Clock bean hoặc đổi timezone hệ thống/DB.
- Post/report retry tới midnight Việt Nam tiếp theo. Comments dùng rolling 60 phút với `created_at > now - 60 phút`, không reset theo giờ tròn; đúng cutoff đã hết quota. Khi bị chặn, retry tới lúc oldest comment còn hiệu lực hết hạn. Làm tròn lên giây, tối thiểu 1 giây; thiếu oldest cần thiết phải báo invariant, không bịa retry.
- `CommunityAuthors.resolve(Set<Long>)` trả `Map<Long, UserRef>` chỉ có `{id, displayName}`. Empty không gọi AuthApi; nonempty gọi đúng một batch `AuthApi.displayNames`, missing name = null, không trả ID ngoài request hoặc fallback email/username. T-05/08/12/13/14 phải gom toàn bộ IDs trước một lần gọi adapter.
- ProbeController là static nested class trong `CommunitySecurityConfigIntegrationTest`, chỉ đăng ký qua `@Import` của đúng test đó; không đăng ký thêm qua `@Bean`/component scan. Có thể dùng test-only profile guard để cô lập khỏi các SpringBootTest khác. Không mock/bypass security filters, không tạo controller production.
- Regression `CommunityContractTest` phải tiếp tục báo `source=24, runtime=0, documented=0, missing=24`; đây là staged consistency, không phải full runtime API parity. Không sửa test T-02 để bỏ qua probe leakage.
- Shadow Plan T-04, T-07, T-11 bắt buộc có chiến lược chống hai request đồng thời vượt quota khi count trên DB; thiếu mục này Leader không duyệt.
- Controllers từ T-04 trở đi phải khai đúng `operationId` và đủ `@ApiResponses` theo locked OpenAPI. T-14 bắt buộc nâng `CommunityContractTest` thành full parity H = D = S.
- Không sửa SharedConfiguration, application configuration, POM, GlobalExceptionHandler production, Auth/Learning/FE, migration hoặc locked Spec/OpenAPI. Không stage/commit/push trước independent source review PASS; review bundle/log/manifest nằm ngoài repository.
- Bảy FE findings FE009-AUD-01…07 vẫn P2 / OPEN; **T-18 Visual QA: NOT COMPLETED / DEFERRED.**

## [009] Leader — Backend T-04 Implementation Authorization — CHỐT 2026-11-02

- Status: CHỐT / APPROVED BY LEADER. Leader duyệt implementation Backend T-04 theo Shadow Plan đã review; không mở rộng sang T-05 hoặc task sau.
- Base là `origin/main` mới nhất sau fetch. T-04 dùng worktree và branch riêng; không tái sử dụng worktree T-03, không pull/merge/rebase main vào branch sau khi tạo.
- Allowlist chính xác 11 file: 7 file CREATE (`PostService.java`, `MyPostController.java`, `PostWrite.java`, `OwnPost.java`, `PostServiceTest.java`, `MyPostControllerIntegrationTest.java`, `PostQuotaConcurrencyIntegrationTest.java`) và 4 file MODIFY (`PostRepository.java`, `CommunityArchitectureTest.java`, `tasks.md`, `questions.md`). Không tạo file thứ 12.
- Chỉ cung cấp `POST /api/community/posts` (`createPost`, 201/401/403/422/429) và `PUT /api/community/posts/{id}` (`updatePost`, 200/401/403/409/422), theo locked OpenAPI; controller khai đúng operationId và toàn bộ response statuses. Không sửa OpenAPI hoặc CommunityContractTest.
- `PostWrite` chỉ có `title`, `content`, `category`, `submit`; `submit` là `Boolean` + `@NotNull`. Compact constructor dùng `String.strip()`, Bean Validation `@Size` đếm UTF-16 bằng `String.length()`, không NFC. Unknown fields bị bỏ qua; không nhận metadata do client điều khiển.
- Chuỗi HTML/Markdown được lưu nguyên văn sau `strip()`, không reject, sanitize, filter hoặc HTML-escape ở backend. Giá trị sau JSON decoding phải giữ nguyên trong response và DB.
- Validation/authorization precedence: security 401/403 trước; MVC `@Valid` trả 422; sau đó kiểm tra verified email và trả 403 `ACCOUNT_UNVERIFIED`; POST mới kiểm quota, PUT mới khóa và kiểm tra trạng thái. Body sai của user chưa xác minh vẫn trả 422.
- `MyPostController` tự xử lý cục bộ `HttpMessageNotReadableException` và `MethodArgumentTypeMismatchException` thành 422 `VALIDATION_FAILED`, message tiếng Việt ổn định, không lộ parser details; giữ request ID theo convention. Không sửa global handler.
- Update lần đọc Post đầu tiên là `findByIdForUpdate` với `PESSIMISTIC_WRITE`. Nonexistent và non-owner trả cùng 403 response ngoài `request_id`; HIDDEN của owner trả 409 `POST_NOT_EDITABLE`. T-12 sau này dùng conditional UPDATE theo trạng thái, không bắt buộc dùng chung row-lock method của T-04.
- Update giữ nguyên `id`, `author_id`, `created_at`, `published_at` và `review_reason`; `updated_at` lấy từ một Clock snapshot. APPROVED khi sửa chuyển DRAFT/PENDING và rời tập public. OwnPost chỉ trả `reviewReason` khi REJECTED/HIDDEN; trạng thái khác trả null.
- POST quota khóa PostgreSQL transaction-level bằng key `community:post-quota:{authorId}` và parameterized `pg_advisory_xact_lock(hashtextextended(:key, 0))` trong `PostService`. Sau khi lấy lock mới chụp một snapshot, count mọi trạng thái từ `dayStart`, kiểm quota rồi insert; timestamp create/update lấy cùng snapshot. PUT không tính quota. Không dùng Redis/JVM locks.
- `PostRepository` chỉ được khai báo thêm `countByAuthorIdAndCreatedAtGreaterThanEqual` và `findByIdForUpdate`; ba Community repositories còn lại giữ zero declared methods. T-05 read path không dùng row-lock method.
- Concurrency integration test dùng PostgreSQL Testcontainers, fixtures user synthetic qua HTTP, bốn bài đã commit và hai HTTP workers; kiểm `pg_locks` xác nhận hai waiter trước khi nhả lock, sau đó đúng một 201, một 429 và tổng quota count bằng 5. Không dùng repository mock để chứng minh concurrency.
- Controller phải trả đúng response OwnPost, không lộ dữ liệu Auth; response `reviewReason` tuân theo trạng thái. CommunityContractTest tiếp tục dùng S=24; H=2, D=2, missing=22 là kỳ vọng sau khi đo runtime, không phải kết quả được tuyên bố trước.
- Giữ nguyên Auth/Learning/FE/shared infrastructure ngoài các file đã duyệt, migration/schema, POM/config, locked Spec/OpenAPI và toàn bộ lịch sử questions.md. T-04 chỉ đổi checkbox của chính task sang ✅ khi implementation, gates và review đạt điều kiện delivery; T-01/T-02/T-03 giữ ✅, task khác không đổi.
- Bảy FE findings FE009-AUD-01…07 vẫn P2 / OPEN, xử lý bằng PR FE riêng. **T-18 Visual QA: NOT COMPLETED / DEFERRED**; không ghi PASS.
- Sau implementation/gates phải có Independent Source Review PASS trước commit. Delivery là một Conventional Commit, push branch T-04 và Draft PR mới vào main; không merge PR. T-05 cần Shadow Plan và approval riêng.

## [009] Leader — Q-T04-FR005 — CHỐT 2026-11-02

- Leader xác nhận “không nhận HTML/Markdown” nghĩa là hệ thống không phân tích hay diễn giải markup. Backend lưu và trả nguyên văn sau `strip()`, kể cả `<img src=x onerror=alert(1)>` và `**đậm**`; không từ chối, không lọc, không escape. Frontend chỉ hiển thị dạng văn bản thuần theo AT-48.
- Đây là diễn giải quyết định 2 đã chốt, không thay đổi nghiệp vụ. Không sửa locked Spec/OpenAPI và không thêm thư viện sanitize.
- Bổ sung integration tests round-trip đúng hai chuỗi trên, HTTP validation trước verified-email, owner PUT trên HIDDEN, và hai thứ tự author/moderator tranh khóa bằng PostgreSQL transactions thật. Conditional SQL moderation chỉ nằm trong test, chưa triển khai T-12.
- T-04 giữ ⬜ trong khi findings chưa khép lại; chỉ đánh ✅ khi đủ điều kiện delivery. Sau corrections và gates phải xuất review ZIP mới, dừng trước stage/commit cho Independent Source Review; quyết định này không tự chuyển review thành PASS.

## [009] Leader — Backend T-05 Implementation Authorization — CHỐT 2026-11-02

- Leader duyệt implementation T-05, đúng 15 files: 9 CREATE và 6 MODIFY; không mở rộng sang T-06. Base là origin/main mới nhất sau fetch, có T-04 đã nghiệm thu; dùng worktree/branch T-05 mới, không pull/merge/rebase main vào branch sau khi tạo.
- Allowlist CREATE: `src/main/java/com/cnhsk/community/post/PostQueryService.java`, `src/main/java/com/cnhsk/community/post/web/PublicPostController.java`, `src/main/java/com/cnhsk/community/post/web/PostSummary.java`, `src/main/java/com/cnhsk/community/post/web/PostDetail.java`, `src/main/java/com/cnhsk/community/post/web/PostSummaryPage.java`, `src/main/java/com/cnhsk/community/common/PostCount.java`, `src/test/java/com/cnhsk/community/post/PostQueryServiceTest.java`, `src/test/java/com/cnhsk/community/post/PublicPostControllerIntegrationTest.java`, `src/test/java/com/cnhsk/community/post/PublicPostQueryCountIntegrationTest.java`.
- Allowlist MODIFY: `src/main/java/com/cnhsk/community/post/PostRepository.java`, `src/main/java/com/cnhsk/community/comment/CommentRepository.java`, `src/main/java/com/cnhsk/community/reaction/ReactionRepository.java`, `src/test/java/com/cnhsk/community/CommunityArchitectureTest.java`, `specs/009-community-blog-moderation/tasks.md`, `specs/009-community-blog-moderation/questions.md`.
- **Q-T05-01:** PostRepository thêm đúng `findPublished(id, category, pageable)` trả List<Post> và `countPublished(category)` trả scalar count. Cả hai dùng JPQL cố định APPROVED ở repository. List/detail dùng chung findPublished; sorting publishedAt DESC, id DESC trong query. Không COUNT trong service, không đọc public bằng findAll/findById/findByIdForUpdate hoặc filter trạng thái trong Java. Nếu nullable parameter binding lỗi trên PostgreSQL thật, dừng báo Leader; không tự chuyển native SQL hoặc đổi repository API.
- **Q-T05-02:** Excerpt tối đa 200 Unicode code points, dùng codePointCount/offsetByCodePoints, không tách surrogate pair, không NFC, strip thêm hoặc diễn giải markup khi đọc. Giữ nguyên prefix của literal text đã lưu.
- **Q-T05-03:** PostCount là record `(Long postId, Long count)`. CommentRepository thêm đúng một JPQL GROUP BY VISIBLE comments; ReactionRepository thêm đúng một JPQL GROUP BY POST/LIKE theo `Reaction.id.*`. Mỗi query nhận tập IDs của cả page; missing aggregate row trả 0. Không EntityManager aggregate trong service, không query từng bài, không IN rỗng.
- **Q-T05-04:** Leader duyệt tracking trong exact 15-file allowlist. T-01…04 giữ ✅; chỉ đánh T-05 ✅ khi đủ điều kiện delivery, không sửa task sau. Questions append quyết định, giữ nguyên lịch sử.
- **Q-T05-05:** page/size sai kiểu, overflow integer hoặc ngoài range trả 422 VALIDATION_FAILED; category sai enum trả 422. id sai kiểu/overflow int64, nonexistent hoặc non-APPROVED trả cùng 404 POST_NOT_FOUND ngoài request_id. `category=`, `page=`, `size=` được coi như absent, dùng optional/default conversion của Spring MVC, phải kiểm bằng HTTP integration tests. Không shared handler changes hoặc backlog 405/415.
- **Q-T05-06:** Reuse schema `#/components/schemas/ErrorResponse` và ApiException/GlobalExceptionHandler convention. Binding exceptions còn thiếu xử lý local trong PublicPostController; không thêm shared advice, schema trùng hoặc status ngoài contract.
- Chỉ hai public GET: `listPublishedPosts` có statuses 200/422 và `getPublishedPost` có statuses 200/404. Controller khai đúng operationId/schema/public security. Không sửa locked OpenAPI hoặc CommunityContractTest; S=24/H=4/D=4/missing=20 là mục tiêu phải đo thực tế.
- List phải count trước, tính offset `(long) page * size`; offset >= total trả 200 items rỗng cùng page/size/total, không gọi findPublished, GROUP BY hoặc CommunityAuthors. Chỉ tạo Pageable sau khi kiểm tra này. Bao phủ page=2147483647,size=50; không cho int overflow xảy ra trước cast.
- Query budget gồm toàn bộ HTTP request và serialization: list có dữ liệu tối đa 5 SQL (count, page, comments, likes, batch author); detail tối đa 4; page rỗng/out-of-range chỉ count; detail 404 chỉ find. Gom distinct author IDs và gọi CommunityAuthors.resolve đúng một lần cho nonempty result. Không lộ email/passwordHash/roles/status/review metadata/viewer-state; displayName thiếu trả null.
- Đo SQL bằng PostgreSQL Testcontainers thật và test-local StatementInspector; fixtures commit trước capture. Kiểm page5/page20 cùng số query <=5, hai GROUP BY, một author batch và không N+1 khi tăng dataset. Đo p95 list20 mục tiêu <300ms, công bố samples/warm-up/environment, có content dài. Nếu acceptance/NFR không đạt, dừng báo; không đổi threshold/index/schema.
- Architecture giữ exact custom-method counts: Post=4, Comment=1, Reaction=1, ModerationCase=0; không nới gate. Không sửa entities, Auth/Learning/FE/shared, POM/YAML, migrations hoặc locked contracts; cần V5/file thứ 16 phải dừng xin Leader.
- Conditional delivery chỉ áp dụng T-05 nếu full verify chỉ fail trong tập ba test đã biết: FR023 import performance; LoginAttempt Redis expiry; LoginAttempt CapturedOutput safe-warning assertion. Tất cả targeted/contract/architecture/acceptance/NFR phải đạt. Công bố full verify FAILED nếu còn failures; không failure-ignore, sửa Auth/Import hoặc hạ ngưỡng. Failure/error khác phải dừng báo Leader.
- Delivery: một Conventional Commit, exact 15-file staging, push branch T-05, Draft PR vào main; không Ready/merge, chỉ Leader nghiệm thu/merge. Bảy FE findings vẫn P2/OPEN; T-18 Visual QA deferred. Backlog PEXPIRETIME và global HTTP 405/415 không thuộc T-05.

## [009] Leader — Backend T-06 Implementation Authorization — CHỐT 2026-11-03

- Leader duyệt implementation T-06 trên origin/main đã xác minh tương ứng `de6dd71a0518f8cce995e3455a086a3d3cd50b62`; T-05 phải là ancestor. Dùng branch/worktree T-06 mới, không tái sử dụng T-03/T-04/T-05 hoặc pull/merge/rebase/cherry-pick main vào branch. Không mở rộng T-07.
- Preflight: T-05 ancestor exit 0; tree main `b98d4beac05bb1719f8e0f8bfb7164951f962954`. PR #55 chỉ đổi tài liệu/RFC và thêm specs 010–015, không đổi source, POM hoặc Spec-009; Constitution v1.5.0 về quyền tài chính/free_usage không thay quyền Community hoặc persistence T-06. Bằng chứng buildability nền là source T-05 đã được Leader nghiệm thu; không tuyên bố đã chạy lại verify trên base docs-only.
- Exact 13-file allowlist, CREATE 4: `src/main/java/com/cnhsk/community/post/web/OwnPostPage.java`, `src/main/java/com/cnhsk/community/post/web/ViewerState.java`, `src/test/java/com/cnhsk/community/post/MyPostQueryControllerIntegrationTest.java`, `src/test/java/com/cnhsk/community/post/MyPostQueryCountIntegrationTest.java`.
- MODIFY 9: `src/main/java/com/cnhsk/community/post/PostQueryService.java`, `src/main/java/com/cnhsk/community/post/web/MyPostController.java`, `src/main/java/com/cnhsk/community/post/PostRepository.java`, `src/main/java/com/cnhsk/community/reaction/ReactionRepository.java`, `src/main/java/com/cnhsk/community/post/PostService.java`, `src/test/java/com/cnhsk/community/post/PostQueryServiceTest.java`, `src/test/java/com/cnhsk/community/CommunityArchitectureTest.java`, `specs/009-community-blog-moderation/tasks.md`, `specs/009-community-blog-moderation/questions.md`. Cần file thứ 14 hoặc V5 phải dừng xin Leader.
- PostService chỉ đổi visibility `private static toOwnPost` thành package-private static, không đổi tên/signature/logic hoặc create/update/quota/locking. Query service tái sử dụng `PostService.toOwnPost`; không viết lại REJECTED/HIDDEN mapping. Review reason chỉ trả ở REJECTED/HIDDEN, trạng thái khác null; DB không đổi.
- **Q-T06-01 — A:** thêm đúng `findOwned(authorId,id,status,pageable)` và `countOwned(authorId,status)` vào PostRepository, cả hai có JPQL `p.authorId = :authorId` từ authenticated user. Không findPublished/findById/findAll/findByIdForUpdate hoặc unscoped load rồi kiểm Java cho mine reads. Non-owner và nonexistent cùng một nhánh 403 FORBIDDEN, cùng body ngoài request_id, không query thứ hai; mọi PostStatus của chính author được đọc.
- **Q-T06-02 — A:** ReactionRepository thêm đúng `findPostViewerKinds(userId,postId)`, lọc viewer hiện tại, targetType POST, targetId và kinds LIKE/REPORT. Visibility kiểm trước bằng findPublished T-05; chỉ APPROVED trả flags 200, mọi trạng thái khác/nonexistent cùng 404 POST_NOT_FOUND kể cả author. Không đọc moderation case hoặc reactions khi visibility không hợp lệ, không lộ private status/existence.
- **Q-T06-03 — A:** list sort tại DB `updatedAt DESC, id DESC`; không filter author/status hoặc sort trong Java.
- **Q-T06-04 — A:** page default 0, size default 20 trong 1..50, status optional. Empty status/page/size như absent bằng MVC conversion/defaults; invalid/overflow/range trả 422. Count owner-scoped trước, offset `(long) page * size`; offset >= total trả empty cùng total riêng caller, không findOwned/aggregate/lookup thừa. Test page99 và max-int page size50; không thêm category/authorId filter.
- **Q-T06-05 — B:** giữ exact mappings, không regex/route phụ/shared handler. Local mismatch dispatch theo handler method metadata: mine-detail ID malformed/overflow →403, viewer-state ID →404, mine query →422, PUT T-04 ID vẫn422. Với `/api/community/posts/mine/viewer-state`, phải kiểm HTTP và HandlerMethod thực tế: kỳ vọng getViewerState, id="mine", 404 POST_NOT_FOUND; không hardcode segment mine để ép403. Routing trái dự đoán và không giải quyết đúng contract trong scope phải dừng báo.
- **Q-T06-06 — tracking:** tasks/questions được phép trong exact13files. T-01…05 giữ ✅; chỉ đánh T-06 ✅ khi đủ điều kiện delivery, không sửa task sau hoặc lịch sử questions.
- Chỉ ba GET: listMyPosts (`/api/community/posts/mine`, 200/401/403/422, OwnPostPage), getMyPost (`/api/community/posts/mine/{id}`, 200/401/403, OwnPost), getViewerState (`/api/community/posts/{id}/viewer-state`, 200/401/403/404, ViewerState). Actual LEARNER cookie/bearer; guest401/wrongrole403; unverified learner vẫn đọc. OperationId/status/error-schema refs theo locked contract; không sửa OpenAPI/CommunityContractTest. S=24/H=7/D=7/missing=17 là mục tiêu cần đo.
- PostgreSQL Testcontainers/StatementInspector đo cả MVC serialization: mine-list có data2SQL, empty/out-of-range1, own-detail1, approved viewer-state2, nonpublic/nonexistent viewer-state1. Kiểm WHERE author predicate cùng fixtures A/B; không dùng mock repository chứng minh privacy/query budget. Architecture exact Post6/Comment1/Reaction2/ModerationCase0, giữ invariants khác.
- Gates gồm targeted T-06, contract, architecture/module, T-04/T-05/security regressions, full Docker Maven verify, JaCoCo và diff/BOM/TODO/secret/scope checks. Có Independent Source Review checkpoint trước stage/commit, kèm full source/diffs, SHA manifest, sanitized results, SQL ownership và route-selection evidence.
- **Conditional delivery (b), riêng T-06:** targeted/contract/architecture và chức năng/security/ownership/query-budget phải PASS; full verify hoặc PASS, hoặc chỉ FAIL trong tập con `FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget`, `FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry`, `redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings`. Coverage check phải đạt; failure/error khác hoặc hạ tầng mất khả năng kiểm chứng phải STOP không commit/PR. Khi verify FAIL phải ghi FULL VERIFY FAILED, exit/counts/evidence redacted, không ghi BUILD SUCCESS, failure-ignore hoặc sửa Auth/Import/threshold. Leader phải verify trước merge; không tự chuyển policy sang T-07.
- Sau source review và đủ quality conditions: exact13-file staging, một Conventional Commit, push branch T-06 và Draft PR vào main; không Ready/merge, chỉ Leader nghiệm thu/merge. Không sửa Auth/Learning/FE/POM/YAML/shared/entities/migrations/contracts. Bảy FE findings vẫn P2/OPEN; T-18 Visual QA deferred. PEXPIRETIME và HTTP405/415 backlog không thuộc T-06.

## [009] Leader — Backend T-07 / Observer Fix và Conditional Delivery — CHỐT 2026-11-04

- Leader duyệt T-07 tạo bình luận/trả lời, observer fix, verification, Independent Source Review rồi một commit/push/Draft PR. Giữ branch `spec/009-community-blog-moderation-t07`, worktree `.worktrees/spec009-t07`, base `f4227b7887cb922e78a3b8c75a7fe7ffff0e4b5a`; không merge/rebase/pull main. Chỉ Leader nghiệm thu/merge.
- Exact 11-file allowlist gồm bảy CREATE (CommentService, CommentController, CommentWrite, CommentResponse và ba comment tests), bốn MODIFY (CommentRepository, CommunityArchitectureTest, tasks.md, questions.md), theo danh sách đường dẫn phía dưới. Observer correction chỉ sửa concurrency test, không sửa production. Tracking chỉ cập nhật sau source review PASS; cần file thứ12 hoặc migration phải dừng xin Leader. Không sửa PostRepository/Auth/Learning/FE/shared/POM/YAML/locked contract.
- Hai operations: `POST /api/community/posts/{id}/comments` (`createComment`) và `POST /api/community/comments/{id}/replies` (`replyToComment`), request CommentWrite, response201 schema Comment; exact statuses `{201,401,403,404,422,429}`. Cookie/bearer, role LEARNER, verified email, cookie mutation CSRF theo conventions hiện hữu.
- **Q-T07-01 — A / L1:** PostgreSQL transaction advisory lock theo authenticated author; key riêng `community:comment-quota:<authorId>`, dùng chung root/reply trên mọi post. Không dùng key post-quota, Redis counter hoặc Java synchronized.
- **Q-T07-02 — A:** test-local controllable Clock, không chờ thật61 phút; sau +61 phút refresh authentication cho cùng userId. Request201 phải do cửa sổ quota hết hạn, không do đổi tác giả.
- **Q-T07-03 — A / L2:** cùng Spring transaction READ_COMMITTED: advisory quota lock -> Post pessimistic row lock bằng existing PostRepository.findByIdForUpdate -> một CommunityLimits.snapshot -> quota -> INSERT. Reply tìm parent trên published Post trước khi khóa/kiểm lại Post; root đọc target bằng locking query. Snapshot chỉ sau khi đã lấy đủ hai khóa. COUNT/oldest/INSERT cùng transaction/connection; không đảo thứ tự, không thêm loại khóa thứ ba. Post nonpublic sau khi chờ khóa bị từ chối trước quota, không INSERT.
- **Q-T07-04 — A:** malformed/overflow path ID422 VALIDATION_FAILED qua narrow controller-local handlers. Numeric missing/nonpublic root404 POST_NOT_FOUND; missing parent hoặc parent trên nonpublic Post cùng404 COMMENT_NOT_FOUND, body giống nhau ngoài request_id, không lộ nội dung cha.
- **Q-T07-05 — A:** content strip(), 1–5.000 Java UTF-16 units, không NFC; lưu/trả literal HTML/Markdown, không phân tích hay diễn giải markup. Thứ tự lỗi Security/CSRF -> MVC validation -> verified email -> visibility -> quota -> creation. Hết quota nhưng target invalid/nonpublic vẫn404 trước429.
- Quota30 comment/reply/60 phút theo authenticated author, mọi post/depth/CommentStatus; predicate createdAt > snapshot.commentCutoff(). Đúng cutoff đã hết hạn, cutoff+1 microsecond còn tính. Giữ CommunityLimits.assertCanComment; HTTP429 JSON có retryAfterSeconds tới khi oldest active comment hết hạn, ceil, tối thiểu1 giây.
- Root mới parentId=null/depth0/VISIBLE/editedAt=null. Parent VISIBLE/HIDDEN/DELETED đều reply được khi Post APPROVED. Depth<3 gắn vào parent; depth3 gắn vào parent.parentId, giữ depth3. Không sửa parent_id/post_id sau INSERT hoặc thêm edit/reparent endpoint.
- **L3:** CommentResponse khai @Schema(name="Comment"), đúng chín fields id,postId,parentId,depth,status,content,author,createdAt,editedAt. Không parent content/PII/moderation metadata/counters; author qua một batch CommunityAuthors/AuthApi, chỉ UserRef{id,displayName}, missing name=null.
- CommentRepository thêm đúng countByAuthorIdAndCreatedAtAfter, findOldestCreatedAtByAuthorIdAndCreatedAtAfter, findReplyParentOnPublishedPost; giữ existing aggregate. Architecture exact Post6/Comment4/Reaction2/ModerationCase0, giữ các module/schema invariants khác.
- PostgreSQL Testcontainers thật, fixtures commit, không outer test transaction; pool3 cho holder+hai HTTP workers. Bắt buộc hai ungranted advisory waiters thật, responses{201,429}, final count30; bounded timeout thiếu waiter phải FAIL. Post visibility race và snapshot sau Post row lock phải được chứng minh, không thay waiter bằng Future completion.
- **Observer root cause/fix được duyệt:** worker thật sự chờ Post SQL FOR NO KEY UPDATE với transactionid wait và đúng blocking PID, nhưng pg_stat_activity.query bị cached trong observer transaction. Chọn A: pg_stat_clear_snapshot() trên cùng connection trước MỖI poll. Giữ query filter/pg_blocking_pids/ungranted transactionid predicate/exactly-one/timeout20 giây/holder modes/transactions và41 assertions. Dọn temporary SQL/Future/Clock tracing; chỉ giữ sanitized lock diagnostics khi FAIL. Không sửa production để xử lý observer.
- Verification: targeted3/3; ba executions độc lập toàn concurrency class, mỗi lượt8/8, source SHA256 `74B252A1F454DCE3375A07C237EF28425CF0DA004FBFD2CDF9918EB5C6640406` không đổi. Đạt postWaiter1, reply404/noInsert, snapshotAfterPost/timestampMatches, quotaWaiters2, statuses201/429, finalCount30. Unit20+HTTP11+contract12=43/43; architecture/module14/14; post/security/contracts regressions135/135. Contract thực đo S24/H9/D9/missing15; staged consistency, chưa full parity. T-14 vẫn phải nâng H=D=S.
- **FULL VERIFY FAILED (ba test môi trường được duyệt):** .\mvnw.cmd -B verify, Java17/Docker/DEBUG=false; 2.056 tests, 3 failures, 0 errors, 0 skipped, exit1. Exact methods: FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget; FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry; redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings. Không công bố full verify PASS.
- FR023 benchmark: `T10_BENCHMARK rows=5000 samples_ms=[38549, 32477, 45479, 45056, 46457] local_p95_ms=46457`; p95=46.457 ms vượt ngưỡng dưới30.000 ms. Redis expiry thấp hơn cận dưới351 ms; CapturedOutput assertion fail, evidence đã redacted. Không kết luận clock-skew khi chưa đo trực tiếp; không sửa Auth/Import tests hoặc thresholds.
- **Conditional delivery riêng T-07:** chỉ cho phép full verify failures trong tập ba methods trên, không failure/error khác; mọi targeted/contract/architecture/concurrency/regression phải PASS, không functional/security/SQL/transaction defect mới, đủ evidence và JaCoCo check PASS. Công bố FULL VERIFY FAILED với exit/counts/names/sanitized evidence; không failure-ignore. Không chuyển policy sang T-08.
- JaCoCo từ chính full-run exec mới, không dùng dữ liệu T-06/targeted: configured jacoco:report@report jacoco:check@check exit0; LINE6036/6232=96,854942%, Community474/476=99,579832%, BUNDLE LINE minimum80% giữ nguyên. Independent Source Review đã kiểm source/hash/evidence và SOURCE PASS; tracking diff phải được review trước delivery.
- Leader duyệt tiếp tục trên base f4227b7 dù main có PR #64/V5 Learning và #65 Placement; checkpoint `5a6da76c2be35fbcf06b42c662cbcd39341495ca`. Không tích hợp main. Leader verify bản gộp main mới nhất và chạy concurrency ít nhất ba lần trước merge; thay đổi mới thực sự ảnh hưởng trực tiếp T-07 phải báo.
- Sau review/tracking hợp lệ: exact11-path staging, một Conventional Commit, push đúng branch, Draft PR vào main; không Ready/merge/force-push/push main. Không commit logs/ZIP/manifests/audit reports/secrets, không bắt đầu T-08. Bảy FE findings P2 vẫn OPEN; T-18 Visual QA deferred; PEXPIRETIME, HTTP405/415 và ghi nhận T-06 là backlog riêng.

Exact paths T-07:
- `src/main/java/com/cnhsk/community/comment/CommentRepository.java`
- `src/test/java/com/cnhsk/community/CommunityArchitectureTest.java`
- `src/main/java/com/cnhsk/community/comment/CommentService.java`
- `src/main/java/com/cnhsk/community/comment/web/CommentController.java`
- `src/main/java/com/cnhsk/community/comment/web/CommentResponse.java`
- `src/main/java/com/cnhsk/community/comment/web/CommentWrite.java`
- `src/test/java/com/cnhsk/community/comment/CommentControllerIntegrationTest.java`
- `src/test/java/com/cnhsk/community/comment/CommentQuotaConcurrencyIntegrationTest.java`
- `src/test/java/com/cnhsk/community/comment/CommentServiceTest.java`
- `specs/009-community-blog-moderation/tasks.md`
- `specs/009-community-blog-moderation/questions.md`

## [009] Leader — Backend T-08 / Public Comment Tree và NFR-P02 — CHỐT 2026-11-04

- Leader duyệt exact 11-file allowlist: bốn ADD (CommentTreeBuilder, CommentTreeBuilderTest, PublicCommentControllerIntegrationTest, PublicCommentQueryCountIntegrationTest), bảy MODIFY (CommentRepository, PostQueryService, PublicPostController, PostQueryServiceTest, CommunityArchitectureTest, tasks.md, questions.md). Không sửa production trong các vòng diagnostic/finalize NFR-P02; không mở rộng T-09 hoặc task khác.
- Base giữ nguyên `2364704408a5968628bc10a4a05007d7c08059c3`, branch `spec/009-community-blog-moderation-t08`, worktree `.worktrees/spec009-t08`. Leader đã chấp nhận bốn shared changes tới checkpoint `4d08b3d14f8d34254c32cb9f0782d6d2168ab318`; main quan sát trước delivery là `c9f418a7a75df780fc627e634e68bdc40168d400`. Thay đổi sau checkpoint thuộc Learning/Question Import, không đổi Community/public security/error envelope liên quan. Không merge/rebase/pull main; Leader kiểm tra bản gộp main mới nhất trước merge.
- **Q-T08-01:** tái sử dụng schema Comment và CommentResponse đúng chín keys: id, postId, parentId, depth, status, content, author, createdAt, editedAt. Retained HIDDEN/DELETED ancestors giữ identity/reference/time, content/author/editedAt đều hiện diện với JSON null. VISIBLE giữ literal content, author chỉ UserRef; editedAt theo FR-025. Không sửa locked Spec/OpenAPI hoặc frontend.
- **Q-T08-02:** flat repository query lấy mọi CommentStatus của Post, sắp createdAt ASC, id ASC; response giữ global flat order, không DFS-flatten hoặc thêm children. Pure Java builder truyền retention từ lá lên, giữ mọi ancestor của VISIBLE descendant và bỏ invisible branches không còn retained descendants; O(n) trên forest hợp lệ.
- **Q-T08-03:** parentId khác null nhưng parent không nằm trong flat set thì ném IllegalStateException; dùng global handler hiện hữu thành INTERNAL_ERROR, không thêm code/status vào locked operation. Không promote orphan, âm thầm bỏ node, sửa liên kết hoặc thêm cycle/duplicate detection.
- **Q-T08-04:** chỉ method mới PostQueryService.publishedComments dùng read-only REPEATABLE_READ qua Spring proxy, bao phủ findPublished và flat comment read trong cùng snapshot. Không dùng write lock; snapshot không ngăn Post writer commit đồng thời. Missing/non-APPROVED Post cùng404 POST_NOT_FOUND; không đọc comments/authors khi visibility miss.
- **NFR-P02 chính thức:** Leader chốt 200 comments x500 ký tự, năm authors, depth tối đa3, năm HTTP warm-ups và20 measured requests, nearest-rank p95 dưới300 ms; không loại outliers và đo qua MVC/Jackson serialization hoàn tất. NFR-P02 không quy định content length;5000 là maximum validation FR-017, không phải fixture acceptance bắt buộc. Chỉ còn một performance gate: PublicCommentQueryCountIntegrationTest.FR018_twoHundredFiveHundredCharacterCommentsMeetTheHttpPipelineP95Budget. Đã gỡ temporary Jackson/ORM/author/CPU timing và comparison-only benchmark; giữ SQL capture, snapshot coordination và author invocation assertions.
- Query accounting có dữ liệu: một Post visibility SELECT dùng existing findPublished trả List (không pagination COUNT), một flat Comment SELECT, một batch CommunityAuthors/AuthApi cho distinct retained VISIBLE authors. Không lookup placeholder-only authors; missing name=null. Empty/pruned tree2SQL, visibility miss1SQL. FR-027 aggregate countVisibleByPostIds đã lọc VISIBLE nên không đổi logic đếm.
- Historical stress diagnostic 200 x5000: p95=312,7089 ms, response=3.037.061 bytes, Jackson converter write chiếm khoảng69% pipeline theo median tỷ trọng quan sát; đây là diagnostic, không phải NFR acceptance PASS. Rủi ro response lớn khi cây dài thuộc backlog; T-08 không pagination/content truncation, không index/migration hoặc đổi threshold.
- Verification trên corrected source: official Gate2 12/12 PASS, p95=103,871 ms; contract12/12, thực đo source24/runtime10/documented10/missing14; architecture/module14/14, inventory Post6/Comment5/Reaction2/ModerationCase0; regressions193/193 PASS. AT-22/23/24, FR-027, public404/nullable schema và repeatable-read snapshot đều đạt. T-07 concurrency giữ nguyên,8/8 PASS với hai quota waiters,201/429,count30 và Post-lock/snapshot assertions.
- **FULL VERIFY FAILED — FR023-only:** .\mvnw.cmd -B verify với Java17/Docker/DEBUG=false:2.257 tests,1 failure,0 errors,0 skipped,exit1. Failure duy nhất FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget tại ImportConfirmIntegrationTest.java:367; actual48369 ms vượt ngưỡng dưới30000 ms. Benchmark: T10_BENCHMARK rows=5000 samples_ms=[48369, 42083, 33252, 27511, 27938] local_p95_ms=48369. LoginAttemptServiceIntegrationTest7/7 PASS; không sửa Auth/Import hoặc ngưỡng. NFR-P02 trong full suite PASS với p95=47,324 ms; không rerun full verify.
- Conditional delivery thuộc policy Leader đã duyệt cho failures trong tập FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget, FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry, redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings; không có failure/error khác. Công bố FULL VERIFY FAILED trung thực; không failure-ignore. Leader phải verify bản gộp trước merge.
- JaCoCo dùng đúng exec của full run, configured jacoco:report@report và jacoco:check@check exit0, giữ threshold80%: project LINE6526/6753=96,638531%, Community526/528=99,621212%. Không dùng artifact diagnostic cũ.
- Member self-review đối chiếu FR/AT, contract, privacy, boundaries, query count, NFR và source hashes. Một lỗi cleanup testCompile trước đó dừng khi0 tests chạy; Leader duyệt xóa đúng callback timing mồ côi, giữ assertions/fixture/threshold. Independent Source Review T-08 do Leader thực hiện trên Draft PR; chưa tuyên bố Leader review PASS. Delivery một Conventional Commit đúng11paths, push T-08, Draft PR; không Ready/merge. Bảy FE findings vẫn P2/OPEN, T-18 Visual QA deferred; không bắt đầu T-09.

## [009] Leader — Backend T-09 / Sửa và tự xoá mềm bình luận — CHỐT 2026-11-05

- Leader duyệt exact 9-file allowlist: thêm CommentMutationConcurrencyIntegrationTest; sửa CommentService, CommentRepository, CommentController, CommentServiceTest, CommentControllerIntegrationTest, CommunityArchitectureTest và hai tracking documents. Không đổi entity/DTO, shared/auth/learning, PostRepository, public tree/count implementation, migrations, POM, frontend hoặc locked Spec/OpenAPI. Không mở rộng T-10.
- Worktree mới `.worktrees/spec009-t09`, branch `spec/009-community-blog-moderation-t09`, base `cd7d1cbdb3e0a29c0a226442896ecd79cde6da78`. Merge T-08 `ee74b24438a35f702c57f97cb8267cdbeafbce9d` là ancestor, exit0. Main sau fetch trước delivery `bdccaacdee554769f57740696b9ecf2956fd2a5f`: PR91 Exam import, PR92 Question update/archive và PR93 Exam Question import không đổi Community/Spec009 hoặc chín paths được duyệt. Shared security chỉ thêm Admin Questions PUT/PATCH; ErrorCode thêm bốn Question codes; error handler chỉ mở details cho MALFORMED_QUESTION. Không đổi Community access/codes/envelope; không merge/rebase/pull. Verification dưới đây thuộc base đã duyệt, chưa phải kiểm thử bản gộp main mới nhất.
- **Q-T09-01 — A:** scalar ownership lookup không tải Comment entity -> Post PESSIMISTIC_WRITE -> owned Comment PESSIMISTIC_WRITE -> kiểm dữ liệu đọc lại -> một CommunityLimits.snapshot sau đủ hai khóa. READ_COMMITTED cho update/delete; không quota advisory lock và không yêu cầu Post APPROVED. Đúng deadline createdAt + configured commentEditWindow được sửa; sau deadline bị từ chối, kể cả request chờ khóa tới quá hạn.
- CommentRepository thêm đúng findOwnedPostId(id, authorId) trả scalar postId và findOwnedForUpdate(id, authorId, postId) tái kiểm ownership/post dưới PESSIMISTIC_WRITE. Cả hai query có authorId từ authenticated session. Giữ nguyên năm methods cũ; architecture exact Post6/Comment7/Reaction2/ModerationCase0, không nới module boundaries.
- **Q-T09-02 — A:** eligibility dùng snapshot Clock; updatedAt ghi với precision microsecond bằng truncatedTo(MICROS), không cộng thời gian giả hoặc đổi createdAt. editedAt chỉ bằng updatedAt khi updatedAt > createdAt, còn lại null. AT-25 tại phút10 có editedAt khác null và khớp DB; edit cùng instant không tự tạo editedAt. Không trộn system time hoặc DB now() vào mutation.
- **Q-T09-03 — Leader mở rộng:** malformed/overflow int64 ID của cả PUT và DELETE trả generic403 FORBIDDEN, giống missing/nonowner ngoài request_id. Hai handler mới nhận String rồi parse sau body validation; generated OpenAPI vẫn required integer/int64. POST reply giữ malformed-ID422 cũ; không đổi shared handler hoặc locked contract.
- **Validation-first:** authentication/LEARNER/verified email/CSRF trước body binding cho hai mutations mới bằng controller-local model hook giới hạn theo handler metadata. PUT reuse @Valid CommentWrite; body invalid trả422 độc lập với ID/existence/ownership. Body hợp lệ mới xét ID/ownership403; owned non-VISIBLE409 COMMENT_NOT_EDITABLE trước expired409 COMMENT_EDIT_WINDOW_EXPIRED. Không query không ownership để phân biệt missing/nonowner hoặc tiết lộ trạng thái người khác.
- **Q-T09-04 — A:** row locks serialize mutations; PUT thực thi sau cùng quyết định content cuối. Không @Version/If-Match hoặc optimistic stale-client detection. Không dùng entity tải trước Post lock; không ghi protected fields. PUT sau DELETE trả409 NOT_EDITABLE, không phục hồi DELETED.
- DELETE VISIBLE đặt DELETED, moderatedBy=current author, moderationReason=AUTHOR_DELETED và updatedAt theo snapshot; giữ row/content/references/replies. DELETE HIDDEN/DELETED trả204 không body, không setter/SQL UPDATE hoặc thay timestamps/metadata. Không audit author delete. Public count giảm đúng một lần; deleted ancestor còn visible descendants giữ placeholder chín keys với content/author/editedAt null; không sửa T-08 production.
- **N5:** reuse CommentWrite strip(), NotBlank/Size1–5000 Java UTF-16 units, ignore unknown fields và literal markup semantics đã duyệt T-07; không DTO mới. Author projection chỉ UserRef id/displayName qua một batch CommunityAuthors, thiếu tên=null.
- **N6:** HIDE/RESTORE ở T-14 không được vô tình tăng updated_at làm comment chưa sửa nội dung hiện editedAt. Chi tiết timestamp policy cần tiếp tục chốt/kiểm khi làm T-14; T-09 không đổi FR-025, thêm content_edited_at hoặc migration. **N7:** response cây dài vẫn backlog; không pagination/truncation. Bảy FE findings P2 vẫn OPEN, T-18 Visual QA deferred.
- Targeted gates thực chạy trên source cố định: CommentServiceTest43/43; CommentControllerIntegrationTest21/21 (giữ11 tests T-07); CommentMutationConcurrencyIntegrationTest10/10. Không failure/error/skip hoặc testCompile correction. AT-25/26, body-invalid+malformed-ID422, valid-body invalid-ID403, exact15min/+1microsecond/raw+1nanosecond boundaries, protected fields, nine-field response, public tree/count và SQL no-op đều PASS.
- PostgreSQL18/Testcontainers thật, pool3, fixtures commit, bounded pg_blocking_pids + ungranted transactionid observers với pg_stat_clear_snapshot mỗi poll: worker đầu chờ Comment khi đã giữ Post; worker sau chờ Post do worker đầu chặn. PUT/PUT200/200 last executing wins; DELETE/DELETE204/204 đúng1UPDATE; DELETE->PUT204/409 không resurrection; PUT->DELETE200/204; reply/delete204/201 giữ references, không deadlock. Chờ Post hoặc Comment qua deadline bị expired, không UPDATE; status được đọc lại sau khóa. Không sửa T-07 concurrency tests.
- Contract12/12 PASS, thực đo S24/H12/D12/missing12; updateComment PUT200/401/403/409/422 và deleteComment DELETE204/401/403, schema Comment/CommentWrite/ErrorResponse và int64 đúng locked contract. Architecture/ModuleBoundary14/14 PASS; regression T-04…T-08/security/contracts217/217 PASS. NFR-P02 regression p95=63,751ms; full run44,296ms; Post/Comment/Author SQL1/1/1, authorBatch1, COUNT0.
- **FULL VERIFY FAILED — ENVIRONMENTAL EXCEPTIONS ONLY:** `.\mvnw.cmd -B verify`, Java17.0.9/Docker Linux27.2.0/DEBUG=false; 2.848 tests,2 failures,0 errors,0 skipped,exit1. Chỉ ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget (34812ms >=30000; samples34812,29656,28253,27865,28473) và QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries (46481ms >=30000; samples46481,31822,34511,31085,31091). LoginAttemptServiceIntegrationTest7/7 PASS. Không gọi full verify PASS, sửa/skip benchmark hoặc rerun để chọn kết quả xanh.
- Conditional policy riêng T-09 được Leader hợp nhất: bất kỳ tổ hợp failure nào chỉ trong hai benchmark trên và LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry / redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings được conditional Draft PR nếu mọi chức năng/security/concurrency/contract/architecture và coverage đạt. Failure/error ngoài nhóm hoặc thiếu evidence phải STOP; không tự coi compile/infrastructure failure là ngoại lệ.
- JaCoCo dùng exec cùng full run, provenance/hash được xác minh: configured `jacoco:report@report jacoco:check@check` exit0, threshold80%, project LINE8189/8432=97,118121%, Community571/573=99,650960%. Không chạy lại tests hoặc dùng report cũ.
- Member self-review và read-only member source check không phát hiện finding cụ thể; bảy source/test hashes giữ nguyên xuyên suốt các gates. Tracking chỉ cập nhật sau verification/freshness đủ điều kiện. Một Conventional Commit đúng9paths, push task branch và Draft PR; Independent Source Review và integration verification do Leader thực hiện trên Draft PR, chưa tuyên bố Leader review PASS. Không Ready/merge hoặc bắt đầu T-10.

## [009] Leader — Backend T-10 / Thích và bỏ thích bài — CHỐT 2026-11-05

- Leader đã duyệt implementation, exact 10-file allowlist, một vòng chẩn đoán → sửa annotation OpenAPI → retest → các gates còn lại → conditional Draft PR. Phiên resume giữ branch `spec/009-community-blog-moderation-t10`, approved base `211e7e3437d786ad2d94c23eb669d0dbca5bcdb1`; tiếp quản khi annotation đã sửa và Gates 1–6 đã PASS. Tám source/test hashes khớp source-freeze; không patch lại, chạy trùng các gates đã có evidence, hoặc viết lại implementation. Tracking chỉ cập nhật sau verification đủ điều kiện.
- **Q-T10-01, Q-T10-02, Q-T10-03, Q-T10-04, Q-T10-05 — APPROVED:** phần dưới tổng hợp các quyết định Leader trong handoff, không mở lại lựa chọn nghiệp vụ:
  - Authenticated LEARNER được LIKE/UNLIKE dù chưa verified email, được thích bài mình, không quota. Leader đã sửa chỉ đạo sai trước đây về verified email; giữ FR-002/AT-03, không gọi `requireVerifiedEmail` cho hai operations này.
  - Post phải APPROVED tại visibility SELECT bằng existing `findPublished`. Missing/nonpublic cùng404 POST_NOT_FOUND. Không khóa Post; trạng thái chuyển sau SELECT không làm mutation đã qua visibility check bị từ chối lại.
  - Composite PK hiện có + `INSERT ... ON CONFLICT DO NOTHING` bảo đảm idempotency. UNLIKE chỉ xóa tuple caller/POST/id/LIKE, giữ REPORT và reactions người khác; không row vẫn200. Không Post/advisory lock, cached counter hoặc migration; không tuyên bố mixed LIKE/UNLIKE last-commit-wins.
  - Malformed/overflow int64 path ID trả404 POST_NOT_FOUND riêng `likePost`/`unlikePost`; giữ behavior các handler cũ. Contract giữ POST/DELETE, không request body, statuses200/401/403/404 và required integer/int64 path.
  - Mutation và existing `countLikesByPostIds(Set.of(postId))` trong cùng READ_COMMITTED transaction; đếm từ DB sau DML, thiếu aggregate row →0. Không suy count từ affected rows hoặc thêm method đếm khác.
- **OpenAPI failure và approved fix:** initial Gate1 19/19 PASS; initial Gate2 7 tests/1 failure/0 errors/0 skips/exit1, tại LikeControllerIntegrationTest.java:256: expected integer, actual string. Snapshot GET `/v3/api-docs` thật dùng OpenAPI3.1.0: LikeState.liked boolean; likeCount string/int64; required=[likeCount,liked]; PostDetail.likeCount integer/int64; cả hai response200 `$ref`=#/components/schemas/LikeState.
- Chỉ bỏ `type = "integer", format = "int64"` tại annotation likeCount trong LikeState, giữ Java `long` và `@Schema(requiredMode = Schema.RequiredMode.REQUIRED)`; không đổi liked/tests/DTO khác/contract/config. SHA-256 trước `A16A485AACB1A4F99BB5CEA865D99A3347D03E81B9DEF353FA4778A592ED5B3F`; sau `68F9AE87D1014B4962FA0B0B6E7266F044E4A21ABCABB1D2533DA41E332FF00B`. Bảy source/test files khác giữ hashes ban đầu.
- Generated schema sau fix: OpenAPI3.1.0; LikeState.liked boolean, likeCount integer/int64, cả hai required; hai response200 refs giữ nguyên. PostDetail.likeCount vẫn integer/int64. Convention DTO: tham khảo PostDetail, để Springdoc suy integer/int64 từ Java long, không khai explicit type/format cho trường này. Evidence gồm Gate2 assertions và snapshot GET200; external snapshot helper có cleanup IllegalStateException/exit1 sau khi lưu JSON thành công, không phải Maven gate/test failure, không chạy lại helper. Xác nhận interaction annotation/tool trong stack hiện tại, không suy rộng thành kết luận upstream defect.

### Verification trên source cố định

Mọi lệnh Maven dùng Java17.0.9, Docker/Testcontainers và DEBUG=false. Gates 1–6 được đối chiếu result JSON với XML gốc và current hashes; không chạy lại vì mất context.

| Gate | Command (`.\mvnw.cmd -B`) | Tests | Failures | Errors | Skips | Exit |
|---|---|---:|---:|---:|---:|---:|
| 1 sau fix | `-Dtest=LikeServiceTest test` | 19 | 0 | 0 | 0 | 0 |
| 2 sau fix | `-Dtest=LikeControllerIntegrationTest test` | 7 | 0 | 0 | 0 | 0 |
| 3 concurrency | `-Dtest=LikeConcurrencyIntegrationTest test` | 12 | 0 | 0 | 0 | 0 |
| 4 contract | `-Dtest=CommunityContractTest test` | 12 | 0 | 0 | 0 | 0 |
| 5 architecture | `-Dtest=CommunityArchitectureTest,ModuleBoundaryTest test` | 14 | 0 | 0 | 0 | 0 |
| 6 regression | `-Dtest=PostServiceTest,MyPostControllerIntegrationTest,PostQuotaConcurrencyIntegrationTest,PostQueryServiceTest,PublicPostControllerIntegrationTest,PublicPostQueryCountIntegrationTest,MyPostQueryControllerIntegrationTest,MyPostQueryCountIntegrationTest,CommentServiceTest,CommentControllerIntegrationTest,CommentQuotaConcurrencyIntegrationTest,CommentMutationConcurrencyIntegrationTest,CommentTreeBuilderTest,PublicCommentControllerIntegrationTest,PublicCommentQueryCountIntegrationTest,CommunitySecurityConfigIntegrationTest,SecurityConfigIntegrationTest,CommunityContractTest test` | 227 | 0 | 0 | 0 | 0 |
| 7 full verify | `verify` | 3002 | 2 | 0 | 0 | 1 |
| 8 configured coverage | `jacoco:report@report jacoco:check@check` | N/A | N/A | N/A | N/A | 0 |

- AT-03/27/28/29 PASS: cookie/bearer và CSRF policy giữ nguyên; repeat LIKE/UNLIKE không tăng row/đổi timestamp lặp; self-like/unverified được200; missing/nonpublic/ID lỗi404; REPORT và reaction người khác được giữ. T-10 trong full run cũng19/7/12 tests PASS.
- Contract actual `S=24 / H=14 / D=14 / missing=10`; missing là operations T-11…T-14 chưa triển khai. Architecture exact `Post6 / Comment7 / Reaction4 / Moderation0`; không nới module boundary.
- SQL mutation budget: một Post visibility SELECT + một DML + một grouped count, tổng3 Community SQL/0 Auth SQL; không Post/advisory lock. Public grouped counts/query budgets và viewer-state regression PASS.
- PostgreSQL concurrency thật:10 HTTP LIKE cùng user/post sau holder ROLLBACK → đúng1 row; unique conflict holder COMMIT → worker affected0, ROLLBACK → affected1, transaction vẫn dùng được. Observer dùng `pg_blocking_pids` và ungranted transactionid waits với `pg_stat_clear_snapshot` mỗi poll, không dùng Future.isDone làm bằng chứng; recorded holderPid75/workerPid74, cùng lúc hai HTTP insert waiters74/76. Mutation/count cùng backendPid74/transactionId792, isolation read committed. Mixed insert/delete và nonlocking visibility race PASS, gồm DELETE không thấy uncommitted INSERT; không có bảo đảm last-commit-wins.
- **FULL VERIFY FAILED — ENVIRONMENTAL EXCEPTIONS ONLY:** actual Maven exit1,3002 tests,2 failures,0 errors,0 skipped. Chỉ `ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` tại dòng398: samples_ms=[49023,40856,37188,38429,40249], p95=49023ms >=30000ms; và `QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` tại dòng304: samples_ms=[67202,46070,50095,52936,45524], p95=67202ms >=30000ms. LoginAttemptServiceIntegrationTest7/7 PASS, không có Redis expiry/log failure. Không sửa/skip benchmarks, failure-ignore hoặc rerun để chọn PASS.
- Lần khởi chạy Full Verify trong sandbox bị chặn mạng khi resolve parent POM,0 tests/exit1. Tool yêu cầu quyền chạy ngoài sandbox, sau đó chỉ có một lượt full tests thực tế nêu trên; giữ riêng cả hai logs. Đây không phải environmental exception được miễn trừ; lỗi quyền mạng đã được giải quyết trước full tests.
- **JaCoCo current full run:** configured report/check exit0, threshold80% không đổi; project LINE8697/8943=97,249245%; Community600/602=99,667774%. Một session từ2026-11-04T20:00:34.550Z đến20:25:51.588Z, nằm trong full-run interval20:00:26.040Z…20:25:53.151Z. Exec SHA-256 `0FB4528BC97741669424499C7032A70A7076314347302024A767466C3664F78F` giữ nguyên trước/sau report/check; XML mới SHA-256 `40DCFE5647D31219264C97D854A6D32A3297CACFBEDFC89B45416B2430A795B0`. Không dùng report lịch sử.
- Evidence local: `C:\WINDOWS\TEMP\cnhsk-spec009-t10-20261105-021253\`: source-freeze/provenance, before/after schema, result JSON/XML từng gate, SQL/concurrency proof, current-run exec/XML và coverage proof. Raw logs/XML giữ private; tracking/PR chỉ ghi sanitized evidence.
- Freshness trước delivery (2026-11-04T20:30:15Z): main `51879c3b95e69a4b133fb8305bbd8208b1c4c486`, ahead6/behind0 so với approved base. Changes thuộc Library/Spec014 và Mastery/Spec011; migration V6 chỉ đổi learning.user_progress, không đổi Community/reactions. Shared ErrorCode chỉ thêm NOTE_NOT_RESTORABLE, không đổi error behavior của hai endpoints. Không semantic conflict được xác định; không pull/merge/rebase. Verification thuộc approved base, chưa phải kiểm bản gộp main mới nhất.
- Self-review source và read-only quality review không phát hiện actionable defect; không thay source trong resume. Exact10paths; tám source/test hashes cố định; không BOM/TODO/test disabled/secret mới/dependency/schema/POM/security/locked-contract change. Tracking chỉ đánh✅T-10 và append mục này; không sửa T-11…T-14. Conditional delivery riêng T-10 theo policy Leader đã duyệt: chỉ hai benchmark được nêu và LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry / LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings, bất kỳ tổ hợp; failure/error ngoài nhóm phải STOP. Không gọi Full Verify PASS. Một Conventional Commit, push đúng task branch, Draft PR vào main; chờ Leader Independent Source Review và integration verification, không Ready/merge hoặc bắt đầu T-11.

## [009] Leader — Backend T-11 / Báo cáo vi phạm — CHỐT 2026-11-05

- Leader cho phép implementation và conditional delivery T-11 trên branch `spec/009-community-blog-moderation-t11`, approved base `5530c76c694075d3f0847962207c18103c65e28d`. Exact13files gồm11 source/test files đã đóng băng và hai tracking documents này. Tracking chỉ đánh✅T-11; Git delivery còn pending, chưa commit/push/Draft PR hoặc Leader code review T-11. Không mở rộng T-12…T-14, đổi schema/migration/dependency/POM/config/security/Auth/Learning/FE hoặc locked Spec/OpenAPI.
- **Q-T11-01 — APPROVED:** quota theo authenticated reporter, một PostgreSQL transaction advisory key `community:report-quota:<userId>` dùng chung POST/COMMENT; tối đa20 `REPORT` mỗi ngày `Asia/Ho_Chi_Minh`, đếm reactions trong `[dayStart,nextDayStart)`. Không Redis counter/Java synchronized hoặc quota theo target/case.
- **Q-T11-02 — APPROVED / exact order:** Security authentication/LEARNER/CSRF trước body; MVC body validation → service verified email → visibility404 `TARGET_NOT_FOUND` → self422 `SELF_REPORT` → reporter advisory lock → một `CommunityLimits.snapshot()` → duplicate409 `DUPLICATE_REPORT` → quota429 `TOO_MANY_REQUESTS` → INSERT REPORT → case UPSERT. Duplicate thắng quota đầy; visibility/self không chờ quota lock. Clock chỉ đọc sau khi lấy advisory lock; `snapshot.now().truncatedTo(MICROS)` được truyền tường minh, dùng chung `reactions.created_at`, new-case `created_at`, `ReportEntry.reportedAt`.
- **Q-T11-03 — APPROVED:** POST phải APPROVED tại visibility SELECT bằng existing findPublished; COMMENT phải VISIBLE và parent Post APPROVED tại SELECT bằng existing findReplyParentOnPublishedPost. Không khóa Post/Comment, không kiểm lại visibility sau advisory lock. Leader chấp nhận consistency limit: target chuyển nonpublic sau SELECT thì report đã qua guard vẫn ghi; không tự thêm khóa hoặc stronger consistency.
- Native composite-PK `INSERT ... ON CONFLICT DO NOTHING` affected0→409, không UPSERT. Partial-index `(target_type,target_id) WHERE status='PENDING'` UPSERT cộng report_count/nối reasons atomically trong cùng READ_COMMITTED transaction với reaction. `List.of(ReportEntry)` serialize array1, giữ stored userId/reason/explicit null/Unicode/escaping/ISO Instant, không truy vấn điều kiện JSONB. Detail strip(), non-OTHER blank→null, OTHER null/blank hoặc detail>1000 sau strip→422. Response201 chỉ `{received:true}`, không caseId/count/reporter; ReportService/ReportController không production logger.
- AT-03/30…34 PASS: verified-email, role/cookie/bearer/CSRF, invalid enum/type/missing/null/`reason:"FAKE"`/OTHER-whitespace/1001 ký tự, boundary1000 và unknown metadata; invalid body422 trước verified-email theo POST convention. Missing/nonpublic POST/COMMENT404, self422, duplicate409, quota429 đúng thứ tự. JSONB null/Unicode/escaping/microsecond timestamps round-trip PASS. Không auto-hide, `auto_hidden=false`. E9: case đã đóng giữ nguyên, reporter mới tạo PENDING case mới, reporter cũ vẫn409 sau khi đóng.
- PostgreSQL18/Testcontainers thật: mandatory19/20/21, midnight, race ở report thứ20, duplicate/rollback, unique-PK conflict, partial-index UPSERT và transaction rollback PASS. Hai HTTP advisory waiters được quan sát trước khi nhả khóa, đúng một201/một429, tổng REPORT20. Observer dùng pg_blocking_pids, ungranted advisory/transactionid waits và pg_stat_clear_snapshot; không Future.isDone. Safe proof:10 reports/1 pending case/10 entries và3 reports/1 case/3 entries; count/insert/upsert cùng backendPid74/transactionId905, quota transaction backendPid84/transactionId971.
- Actual `/v3/api-docs` HTTP200/OpenAPI3.1.0: chỉ POST reportContent, consumes application/json, required body ref ReportWrite; exact statuses201/401/403/404/409/422/429,201→ReportReceived, lỗi→ErrorResponse. Required targetType/targetId/reason; targetId integer/int64, targetType POST/COMMENT và năm reason đúng contract; detail nullable/max1000, không validation-helper property; received required boolean/`const:true` kiểu boolean; cookie/bearer là hai OR security alternatives. Contract parity thực đo `S24/H15/D15/missing9`; repository inventory exact `Post6/Comment7/Reaction6/Moderation1`, module boundaries giữ nguyên.

### Verification trên 11 source/test hashes cố định

Java17.0.9, Docker/Testcontainers, DEBUG=false. Kết quả từ safe result JSON và evidence lượt thực chạy; không chạy lại Maven hoặc sửa source/tests trong tracking sync.

| Gate | Command (`.\mvnw.cmd -B`) | Tests | Failures | Errors | Skips | Exit |
|---|---|---:|---:|---:|---:|---:|
| 1 unit | `-Dtest=ReportServiceTest test` | 32 | 0 | 0 | 0 | 0 |
| 2 HTTP | `-Dtest=ReportControllerIntegrationTest test` | 9 | 0 | 0 | 0 | 0 |
| 3 PostgreSQL concurrency | `-Dtest=ReportConcurrencyIntegrationTest,ReportQuotaConcurrencyIntegrationTest test` | 14 | 0 | 0 | 0 | 0 |
| 4 contract | `-Dtest=CommunityContractTest test` | 12 | 0 | 0 | 0 | 0 |
| 5 architecture | `-Dtest=CommunityArchitectureTest,ModuleBoundaryTest test` | 14 | 0 | 0 | 0 | 0 |
| 6 regression | `-Dtest=PostServiceTest,MyPostControllerIntegrationTest,PostQuotaConcurrencyIntegrationTest,PostQueryServiceTest,PublicPostControllerIntegrationTest,PublicPostQueryCountIntegrationTest,MyPostQueryControllerIntegrationTest,MyPostQueryCountIntegrationTest,CommentServiceTest,CommentControllerIntegrationTest,CommentQuotaConcurrencyIntegrationTest,CommentMutationConcurrencyIntegrationTest,CommentTreeBuilderTest,PublicCommentControllerIntegrationTest,PublicCommentQueryCountIntegrationTest,CommunitySecurityConfigIntegrationTest,SecurityConfigIntegrationTest,CommunityContractTest,LikeServiceTest,LikeControllerIntegrationTest,LikeConcurrencyIntegrationTest test` | 265 | 0 | 0 | 0 | 0 |
| 7 full verify | `verify` | 3158 | 4 | 0 | 0 | 1 |
| 8 configured coverage | `jacoco:report@report jacoco:check@check` | N/A — no tests | N/A | N/A | N/A | 0 |

- **FULL VERIFY FAILED — ENVIRONMENTAL EXCEPTIONS ONLY:** `.\mvnw.cmd -B verify` exit1,3158 tests,4 failures,0 errors,0 skipped. Exact bốn failures dưới đây khớp whitelist conditional delivery riêng T-11 Leader đã duyệt; không gọi Full Verify PASS/BUILD SUCCESS, failure-ignore, skip/hạ threshold, sửa Auth/Import hoặc rerun để chọn xanh. Failure/error ngoài whitelist hoặc thiếu khả năng kiểm chứng phải STOP.

| Exact whitelisted failure | Safe actual evidence |
|---|---|
| `com.cnhsk.learning.content.importer.ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` | `ImportConfirmIntegrationTest.java:398`; samples_ms=[35376,30540,28765,28989,29688], p95=35376ms ≥30000ms |
| `com.cnhsk.learning.content.importer.QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` | `QuestionImportIntegrationTest.java:304`; samples_ms=[50910,32832,36201,35311,37225], p95=50910ms ≥30000ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry` | `LoginAttemptServiceIntegrationTest.java:133`; PEXPIRETIME actual1793831299382, expected[1793831299585,1793831300276], thấp hơn cận dưới203ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput)` | `LoginAttemptServiceIntegrationTest.java:313`; captured MockMvc login diagnostic chứa REDACTED_EMAIL. Assertion captured output này khác warning predicate tại dòng312; không công bố fixture email/IP/password |

- **JaCoCo current full run:** configured report/check exit0, threshold80% giữ nguyên; project LINE9022/9297=97,0420566%, Community684/689=99,2743106%. Full interval `2026-11-04T22:09:40.3169459Z`…`2026-11-04T22:31:01.4019250Z`; một exec session `22:09:48.733Z`…`22:31:00.375Z` nằm trọn trong interval. Exec SHA-256 `7E878EBC3F3AA109AFAEF2D622BB27526F3516F43F749D6992F2E3E0122D3DA2` giữ nguyên trước/sau report/check; fresh XML SHA-256 `6B38E9C710AD8D00FC333C88D7F00AA7C0DC1C6050B0C595AE10B2C4560A811A`, sinh `2026-11-04T22:34:10.1920015Z`. Không dùng coverage lịch sử hoặc chạy lại tests ở Gate8.
- Evidence local: `C:\WINDOWS\TEMP\cnhsk-spec009-t11-20261105-044327\`: safe result JSON Gates1…8, source-freeze-sha256, before/after hash checks, gate3-safe-evidence, current-full-run exec/provenance/XML/coverage-proof và delivery-freshness. Raw logs/XML/failure messages giữ private; tracking chỉ dùng sanitized evidence. Cả11 source/test hashes khớp source-freeze trước/sau tracking sync.
- Freshness `2026-11-04T22:36:05.0998838Z`: origin/main `9579029244f45b7a73202889d4de1de422f2c823` ahead2 so với approved base; PR#105 chỉ Library/Spec014, shared production ErrorCode chỉ thêm CARD_DUPLICATE. Không Community/security/AuthApi/production GlobalExceptionHandler/limits/schema/POM semantic impact được xác định. Tests vẫn trên approved base `5530c76c694075d3f0847962207c18103c65e28d`; không pull/merge/rebase/integrate main mới.
- Self-check tracking: chỉ hai documents, T-11 checkbox và append mục này; T-01…T-10/T-12…T-18 và lịch sử questions giữ nguyên, UTF-8 không BOM, diff check sạch. Không sửa11 source/test files hoặc tạo file khác. Conditional delivery riêng T-11 đủ điều kiện; Git delivery còn pending. Chưa tuyên bố Leader Independent Source Review/code review PASS, commit/push/Draft PR đã xong, Ready/merge hoặc bắt đầu T-12.

## [009] Leader — Backend T-12 / Post Moderation — CHỐT 2026-11-05

- Leader duyệt implementation, Audit Option A, exact allowlist16 (9 ADD/7 MODIFY), một vòng sửa metadata Gate2 và limited Docker recovery với đúng một lần rerun Gate3. Branch `spec/009-community-blog-moderation-t12`, approved base `ff2c2352a2df75b635392992dbdd6702dc9e3d11`. Resume giữ nguyên14 source/test hashes; không implementation lại, sửa Java/tests, chạy lại Gate1–2 hoặc mở vòng fix thứ hai. Hai plan artifacts đã được sao lưu ngoài repository rồi xóa riêng từng file theo phê duyệt; không đưa vào implementation commit.

### Quyết định nghiệp vụ, Clock và audit

- **Q-T12-CLOCK — APPROVED:** "now() trong FR-041/FR-043 được hiểu là giờ từ Clock", theo AC-08. Mỗi mutation dùng đúng một `clock.instant().truncatedTo(ChronoUnit.MICROS)` cho reviewed_at; approve đặt published_at=COALESCE(published_at,reviewedAt), giữ timestamp không liên quan.
- FR-039/AT-35: hàng đợi chỉ PENDING, created_at ASC/id ASC, page>=0, size1..50/default20, excerpt theo FR-012. CommunityAuthors map author theo batch; queue5 và queue20 đều3 SQL, out-of-range1 SQL; không N+1. FR-040: detail mọi trạng thái, author/reviewer qua CommunityAuthors; missing404 POST_NOT_FOUND.
- FR-041…043/045, AT-36…39: MODERATOR-only; SELECT author không khóa, missing404 trước self403 SELF_MODERATION; conditional UPDATE theo id AND PENDING, affected0→409 POST_NOT_PENDING, affected1 mới audit. Không explicit Post pessimistic/advisory lock. Flush trước bulk UPDATE, clearAutomatically rồi fresh reload; không dựng response từ managed entity cũ. Chỉ structured constraintName chính xác ck_post_self→403; lỗi integrity khác không bị đổi thành403. Reject strip() rồi kiểm1..1000 Unicode code points; blank/wrong-type/malformed JSON422 qua controller-local mapping, không sửa global handlers.
- **Q-T12-AUDIT — APPROVED Option A:** thêm public `AuditService.recordDetail(Long actorId, String action, String targetType, long targetId, Map<String, ?> detail)`, giữ nguyên API/behavior legacy record(). Validation action!=null/max50, targetType khớp `^[A-Z][A-Z0-9_]{0,29}$`, targetId>0, detail!=null; ObjectMapper serialize nguyên detail, không bọc before/after. REQUIRED tham gia cùng transaction Post UPDATE, không REQUIRES_NEW. APPROVE action COMMUNITY_POST_APPROVE/target POST/detail{}; REJECT action COMMUNITY_POST_REJECT/target POST/detail{reason:strippedReason}. Không audit self hoặc affected0.

### One approved Gate-2 metadata fix round — đúng bốn files

- Initial Gate2 FAIL1/11 tại generatedOpenApiMatchesLockedOperationsStatusesSecurityAndExactSchemas, line456: page generated string thay integer. Evidence FAIL cũ được giữ nguyên. Một vòng sửa duy nhất, không đổi tests/assertions/fixtures/business/SQL/audit/transaction:
  - ModerationController.java: page/size thêm explicit Schema type="integer", giữ Java int/defaults/range/local type-mismatch422; không format:int32 hoặc implementation=Integer.class.
  - UserRef.java: id REQUIRED; displayName REQUIRED với types string/null. Chỉ metadata, không đổi runtime JSON/constructor/accessors/JsonInclude.
  - ModerationPostSummary.java: category enumAsRef=true.
  - ModerationPost.java: category/status enumAsRef=true; không sửa reviewedBy sau quyết định Leader.
- Gate1 mới31/31 và Gate2 mới11/11 PASS trên frozen14 source/test. Runtime OpenAPI3.1 snapshot SHA-256 `9C10F29FE6732AA9E8E9357CD83D9DAEB00681BC55F7CB73984D758990157E6E`: page integer/min0/default0; size integer/min1/max50/default20; không int32. UserRef required[id,displayName], id integer/int64, name string/null. Moderation category/status refs đúng; summary7/page4/detail8 required properties, long inference/int64 và Instant/date-time, RejectWrite reason required/min1/max1000, không helper properties. reviewedBy optional và có deviation nullable ref được Leader chấp nhận bên dưới.

### BACKLOG — KHÔNG SỬA TRONG T-12

- Springdoc2.8 sinh nullable `$ref` thành `{type:null,$ref:...}` thay vì `oneOf:[{$ref:...},{type:null}]`. Đã biết CommentResponse.author (T-08), ModerationPost.reviewedBy (T-12); handledBy là rủi ro dự kiến T-13. Leader chấp nhận documented deviation cụ thể này, không bỏ qua failure contract khác. Đề xuất xử lý tập trung bằng OpenApiCustomizer ở task sau khi có phê duyệt riêng; không sửa annotations/customizer/tests/locked contract trong T-12.
- MyPostController/PublicPostController hiện sinh page/size kiểu string, lệch locked contract; Leader để task sau, không sửa trong T-12.

### Docker recovery và PostgreSQL evidence

- Gate3 đầu tiên FAILED:9 tests/0 failures/9 errors/0 skips/exit1, `Could not find a valid Docker environment` tại AbstractIntegrationTest.java:39; cả9 test bodies chưa chạy. Đây là infrastructure initialization error, không có AT-38/rollback evidence ở lần đó và không phải environmental exception được bỏ qua. XML/log/STOP evidence giữ nguyên.
- Leader duyệt read-only docker version/info/context ls/ps, minimal recovery và đúng một lần Gate3 rerun. Bốn lệnh trong sandbox đều exit1 do config/context metadata/pipe access denied. Cùng bốn lệnh PowerShell ngoài sandbox đều exit0; Docker client/server27.2.0, context desktop-linux, daemon ready. Case B được kiểm chứng ở lần chẩn đoán hiện tại; nguyên nhân cụ thể của lần Gate3 lỗi trước vẫn chưa xác định, không suy diễn rằng sandbox chắc chắn gây lỗi trước đó.
- Recovery thực tế: chạy verification trong PowerShell ngoài sandbox có docker info thành công ngay trước Maven. Không restart/mở lại Docker Desktop, đổi DOCKER_HOST/context/config/ACL, prune hoặc xóa Docker data. Gate1–2 kế thừa evidence/hash đã xác minh, không chạy lại; Gate3 đầu tiên được ghi nhận ngoài sandbox nhưng lỗi; Gate3 authorized rerun và Gate4–8 của resume đều PowerShell ngoài sandbox, Java17.0.9/DEBUG=false.
- Authorized Gate3 rerun đúng một lần9/9 PASS. PostgreSQL thật, committed fixtures, transactions riêng, bounded pg_blocking_pids/ungranted transactionid observer: holderPID74/waiterPID75 chứng minh conditional UPDATE thực bị chặn. Hai thứ tự approve/reject đều200/409, status/reviewedBy/time/reason thuộc winner và đúng1 audit. UPDATE+audit cùng backend74 và XID783/791/799/806/814. Audit failure thật rollback Post về PENDING/không audit; rollback-race cả hai thứ tự500/200, firstRealAuditRolledBack=true, waiterDecidedPending=true. Fresh response khớp database, không partial mutation hoặc JPA stale status. Không sửa PostgreSQL observers/test bodies để đạt PASS.

### Verification — frozen14 source/test hashes

| Gate | Command (`.\mvnw.cmd -B`) | Tests | Failures | Errors | Skips | Exit |
|---|---|---:|---:|---:|---:|---:|
| 1 inherited unit | `-Dtest=PostModerationServiceTest test` | 31 | 0 | 0 | 0 | 0 |
| 2 inherited HTTP/OpenAPI | `-Dtest=ModerationControllerIntegrationTest test` | 11 | 0 | 0 | 0 | 0 |
| 3 initial Docker failure, preserved | `-Dtest=PostModerationConcurrencyIntegrationTest test` | 9 | 0 | 9 | 0 | 1 |
| 3 one authorized rerun | `-Dtest=PostModerationConcurrencyIntegrationTest test` | 9 | 0 | 0 | 0 | 0 |
| 4 contract | `-Dtest=CommunityContractTest test` | 12 | 0 | 0 | 0 | 0 |
| 5 architecture | `-Dtest=CommunityArchitectureTest,ModuleBoundaryTest test` | 14 | 0 | 0 | 0 | 0 |
| 6 regression, exact51-class selector below | `-Dtest=<gate6-selector.txt> test` | 736 | 0 | 0 | 0 | 0 |
| 7 full verify | `verify` | 3313 | 4 | 0 | 0 | 1 |
| 8 configured JaCoCo | `jacoco:report@report jacoco:check@check` | N/A, no tests | N/A | N/A | N/A | 0 |

- Gate6 actual51 classes: toàn bộ30 existing Community suites T-04…T-11, CommunityContractTest (chạy lại theo yêu cầu UserRef shared), CommunitySecurityConfigIntegrationTest, SecurityConfigIntegrationTest, AuditServiceIntegrationTest, AdminUserQueryIntegrationTest, BanManagementIntegrationTest, BootstrapAdminRunnerIntegrationTest, RoleManagementIntegrationTest, LoginIntegrationTest, LoginRedisUnavailableIntegrationTest, RefreshTokenIntegrationTest, AuthLifecycleIntegrationTest, AuthApiIntegrationTest, AuthApiServiceTest, CurrentUserIntegrationTest, MeProfileIntegrationTest, QuestionAdminServiceTest, QuestionAdminServiceIntegrationTest, QuestionAdminControllerIntegrationTest, QuestionAdminConcurrencyIntegrationTest, ImportConfirmTest. Exact full command/selector và XML51 suites giữ trong gate6-selector.txt/gate6-regression-result.json. AuditServiceIntegrationTest9/9 PASS: exact direct detail/empty/Unicode/invalid params/transaction rollback và legacy record before/after behavior; auth/learning legacy callers PASS. Full Verify tiếp tục chạy toàn bộ project, không bỏ tests.
- Contract actual từ Gate4: **S24/H19/D19/missing5**. Năm missing operations thuộc scope sau: listPostCommentsForModeration, listPendingReportCases, getReportCase, moderateComment, decideReportCase. Repository inventory actual từ architecture và compiled declared methods: **Post11/Comment7/Reaction6/ModerationCase1**; boundaries PASS. Không điền số dự kiến thay kết quả đo.
- **FULL VERIFY FAILED — ONLY FOUR LEADER-APPROVED EXCEPTIONS:** exit1,3313 tests/4 failures/0 errors/0 skips,203 fresh XML suites, không failure/error khác. Không gọi Full Verify PASS, sửa/skip benchmarks, đổi assertions/thresholds hoặc rerun để chọn xanh.

| Exact failure method | Safe actual evidence |
|---|---|
| `com.cnhsk.learning.content.importer.ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` | line398; samples_ms=[37011,31988,28690,30283,37483], p95=37483ms, yêu cầu<30000ms |
| `com.cnhsk.learning.content.importer.QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` | line304; samples_ms=[75696,52927,58242,53747,52633], p95=75696ms, yêu cầu<30000ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry` | line133; PEXPIRETIME1793879415722, expected[1793879415755,1793879416965], thấp hơn cận dưới33ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput)` | line313; captured diagnostic có fixture email; không xuất giá trị email/IP/password hoặc raw captured output |

- **JaCoCo current-run configured check PASS**, exit0, threshold80% giữ nguyên. Project LINE9426/9716=97,0152326%; Community774/780=99,2307692%. Full interval `2026-11-05T11:31:46.9244041Z`…`11:59:10.6240247Z`; một session `11:31:55.257Z`…`11:59:08.381Z` nằm trọn trong full run. Exec SHA-256 `8C0BB467CB53F1A0C211D7B41C1C7C471AC4D287E613E3EB57CBAE09A54F3768` không đổi trước/sau coverage; fresh XML SHA-256 `62A76675123AC793BE08497BC704266E7B6110FC024830811E9DA87AA46F3FB8`, sinh12:00:04.2731750Z. Không class-mismatch warning, exec/report cũ hoặc test rerun ở Gate8.
- Evidence local: `C:\WINDOWS\TEMP\cnhsk-spec009-t12-20261105-072050\approved-fix-round-20261105-083000\`: frozen14/source hashes, old evidence preservation hashes, logs/XML/results từng gate, actual-openapi.json, Docker diagnostics/authorization, gate3-safe-evidence, gate5-inventory-proof, gate6-scope/selector, gate7-safe-evidence/current exec/provenance, gate8 report/check/XML. Raw logs/XML giữ private; chỉ sanitized metrics trong tracking/PR.
- Final freshness fetch: origin/main `df81d04616abd97e3ab5fe35c12640b6e16393a2`, merge-base vẫn approved ff2c235; PR108/Spec011 thêm FSRS scheduler/config/tests và independent cnhsk.fsrs application properties,7 paths. Không Community/shared audit/UserRef/security/AuthApi/handler/schema/POM/locked contract change hoặc semantic conflict T-12 được xác định. Tests vẫn trên approved base; không integrate/merge/rebase main mới.
- Self-check: exact16 files (9 ADD/7 MODIFY), tracking chỉ T-12 checkbox và append mục này; frozen14 giữ nguyên, không dependency/migration/security config/handler/contract changes, plan artifacts không trong commit. Member source review không phát hiện finding mới; chưa tuyên bố Leader Independent Source Review PASS. Conditional delivery được phép theo quyết định Leader; Git commit/push/Draft PR được thực hiện sau self-review/provenance/scope check, không Ready/merge hoặc bắt đầu T-13.

## [009] Leader — Chốt Backend T-13 / Q-T13 và conditional delivery — 2026-11-06

- Status: CHỐT / APPROVED BY LEADER. Phạm vi FR-046…FR-051 / AT-40…AT-45; exact allowlist 18 files, 11 ADD + 7 MODIFY. T-13 được đánh ✅ theo quyết định bàn giao có điều kiện dưới đây; đây không phải xác nhận Full Verify hoặc coverage PASS.
- Worktree `.worktrees/spec009-t13`, branch `spec/009-community-blog-moderation-t13`, approved base HEAD `2171960a2898135f32bf00f8106dce46f28a200c`. Giữ nguyên 16 source/test files sau sealed fix; chỉ sửa checkbox T-13 và append quyết định vào file này. Không thêm file thứ 19, không rerun Maven, rebase/reset/clean hoặc tác động worktree khác.

### Q-T13 — Quyết định nghiệp vụ và kỹ thuật đã đóng băng

- Chỉ `MODERATOR` dùng cả ba operation hàng đợi/chi tiết/quyết định; không yêu cầu thêm `LEARNER` hoặc xác thực email. Các role khác, kể cả `ADMIN` đơn lẻ, bị chặn trước khi truy cập dữ liệu. Giữ nguyên bốn route kiểm duyệt bài T-12.
- Hàng đợi chỉ lấy `PENDING`, `report_count` giảm dần rồi `created_at` tăng dần; `page >= 0`, `size` 1–50. Tải target theo lô, không gọi repository loại còn lại khi không cần; `latestReason` lấy phần tử cuối mảng `reasons`, preview tối đa 120 Unicode code points. Trang rỗng/ngoài phạm vi chỉ count; không bịa preview cho target bị orphan.
- Chi tiết đọc mọi trạng thái hồ sơ, giữ nội dung gốc của target cho moderator, kể cả bình luận đã xoá; tên tác giả/người báo cáo/người xử lý qua một lần `CommunityAuthors.resolve`. Response chỉ dùng `UserRef`, không lộ email; hồ sơ không tồn tại trả 404 `REPORT_CASE_NOT_FOUND`.
- Thứ tự lỗi đã kiểm: validation độc lập 422 → hồ sơ thiếu 404 → target/parent thiếu hoặc loại không hỗ trợ fail closed 500 → tự kiểm duyệt 403 `SELF_MODERATION` → validation `commentAction` theo ngữ cảnh 422 → conditional close thua 409 `REPORT_ALREADY_HANDLED`. Lỗi nội bộ dùng handler đã có, chỉ log caseId/targetType, không trả stack trace hoặc nội dung ra client.
- JSON decision kiểm đúng kiểu/enum, từ chối null tường minh cho `commentAction`, không coercion scalar hoặc nhận thêm JSON token. `note` strip và kiểm 1–1.000 Unicode code points. `commentAction=HIDE|DELETE` bắt buộc đúng khi `ACTION` trên COMMENT và phải vắng mặt trong các ngữ cảnh khác; không có quyết định cấm/treo tài khoản.
- Quyết định dùng một transaction `READ_COMMITTED`: conditional UPDATE đóng case trước, sau đó conditional target DML, audit, rồi fresh reload response; cùng snapshot `Clock` với độ chính xác microsecond. Không thêm explicit lock hoặc đổi T-11 upsert. Case thua không ghi target/audit; lỗi SQL/constraint/audit thật rollback case, target và audit cùng transaction.
- `REJECT` chỉ đóng case và audit. `ACTION` ẩn bài hoặc HIDE/DELETE bình luận; target đã ở trạng thái đích không bị ghi lại metadata, case vẫn đóng/audit. Giữ content, editedAt/updatedAt, cây và reply con của bình luận; HIDDEN → DELETED vẫn được phép. E9 giữ lifetime duplicate cho reporter cũ, reporter mới có thể tạo case PENDING mới sau khi đóng case trước.
- Audit giữ `COMMUNITY_REPORT_ACTION` / `COMMUNITY_REPORT_REJECT`, `target_type=REPORT_CASE`, detail `{targetType,targetId,commentAction?}`; chỉ COMMENT ACTION có `commentAction`. Không sửa schema/migration, shared audit, AuthApi, security config, dependency hoặc locked OpenAPI.

### Một vòng sửa OpenAPI được Leader duyệt

- Java 17 nested sealed `ReportCaseDetail.Target permits ReportedPost, ReportedComment`; hai record target implement interface và service dùng kiểu này. Vòng sửa chỉ chạm `ReportCaseService`, `ReportCaseDetail`, `ReportedPost`, `ReportedComment`, `ReportCaseControllerIntegrationTest`; 11 source/test files còn lại giữ nguyên. Không đổi business SQL, transaction hoặc security behavior trong vòng sửa.
- Runtime schema dùng named component `ReportTarget`, `oneOf` đúng `ReportedPost`/`ReportedComment`, discriminator `kind`, required const `POST`/`COMMENT`; `ReportCaseDetail.target` trỏ `$ref` tới component đó. Leader chấp nhận named-component deviation so với inline oneOf của locked contract; không thêm DTO file hay sửa contract để đổi hình thức schema. Wire JSON không có wrapper, field sets/kind/privacy và bảy moderation operations đã được kiểm bằng HTTP thật.
- Gate 1–2 được reverify sau vòng sửa; evidence trước vòng sửa giữ riêng, không lấy làm kết quả hiện tại. Snapshot mới `gate2-actual-openapi.json` SHA-256 `1AAA16EEE533212310A50D8CF093A2CA7CEB022264D8A401C35F37B157D1E5C7`.
- Backlog Springdoc nullable `$ref` đã duyệt: `handledBy` runtime hiện sinh `type: null` kèm `$ref: #/components/schemas/UserRef`. Giữ JSON null/non-null behavior đã kiểm, không mở thêm vòng sửa source/contract trong T-13; theo dõi khi xử lý tài liệu OpenAPI sau này.
- Gate 4 đo thực tế source=24, runtime=22, documented=22, missing=2. Hai operation còn thiếu thuộc T-14: `GET /api/community/moderation/posts/{id}/comments` và `PATCH /api/community/moderation/comments/{id}`. Không thêm chúng trong T-13; T-14 chỉ bắt đầu sau khi Leader nghiệm thu PR T-13.

### Verification — evidence sau sealed fix, không rerun khi account handoff/delivery

| Gate | Command (`.\mvnw.cmd -B`) | Tests | Failures | Errors | Skips | Maven exit |
|---|---|---:|---:|---:|---:|---:|
| 1 unit | `-Dtest=ReportCaseServiceTest test` | 50 | 0 | 0 | 0 | 0 |
| 2 HTTP/OpenAPI | `-Dtest=ReportCaseControllerIntegrationTest -Dcommunity.openapi.snapshot=<evidence>/gate2-actual-openapi.json test` | 16 | 0 | 0 | 0 | 0 |
| 3 PostgreSQL/concurrency | `-Dtest=ReportCaseConcurrencyIntegrationTest test` | 24 | 0 | 0 | 0 | 0 |
| 4 contract | `-Dtest=CommunityContractTest test` | 12 | 0 | 0 | 0 | 0 |
| 5 architecture | `-Dtest=CommunityArchitectureTest,ModuleBoundaryTest test` | 14 | 0 | 0 | 0 | 0 |
| 6 regression, 52 suites | `-Dtest=<gate6-handoff-scope.json:selector> test` | 770 | 0 | 0 | 0 | 0 |
| 7 Full Verify, interrupted | `verify` | 3000 | 5 | 0 | 0 | 1 |
| 8 JaCoCo | Không chạy; execution data không hợp lệ | N/A | N/A | N/A | N/A | N/A |

- Gates 1–4 được khôi phục từ result JSON, BUILD SUCCESS, Surefire XML, hash trước/sau; Gate 4 đã kết thúc trước khi account handoff, không khởi chạy bản thứ hai. Gates 5–6 chạy tiếp một lần. Gate 6 kế thừa selector cũ, loại `CommunityContractTest` và `CommunityArchitectureTest` đã PASS ở Gates 4–5; exact 52 suites và 770 tests khớp XML/selector, gồm T-04…T-12, T-11 reports/upsert/quota/concurrency, T-12 post moderation/concurrency, shared audit, auth/security và legacy callers.
- Gate 3 dùng PostgreSQL thật với bounded blocking observer: `ungrantedTransactionIdWait=true`, `sameCaseTargetAuditTransaction=true`; hai thứ tự upsert/close, competing moderators, rollback thật, E9, orphan fail closed và no-op metadata đều PASS. Không đổi observer hoặc assertions để lấy kết quả xanh.
- Docker readiness exit 0 trong PowerShell ngoài sandbox, cùng execution environment với Maven; Java 17, `DEBUG=false`, Docker Desktop 27.2.0, Testcontainers 1.21.4, PostgreSQL 18-alpine, Redis 7-alpine và Mailpit. Không restart/prune hay đổi Docker config/context/ACL, không dùng DB chung thay Testcontainers.
- Gate 7 giữ nguyên FAILED: từ `2026-11-05T18:06:20.8491404Z` đến `2026-11-05T18:30:42.6465854Z`, 176 suite có XML hoàn tất, 3.000 tests / 5 failures / 0 errors / 0 skips, Maven exit 1, BUILD FAILURE. Sau khi phát hiện NFR_P02 ngoài bốn ngoại lệ ban đầu, dừng đúng Surefire JVM thuộc Maven của worktree này theo STOP policy; fork exit -1. Đây là số liệu các suite hoàn tất trong lượt bị ngắt, không phải toàn bộ test inventory; không sửa/rerun để thay kết quả.

| Exact failure method | Safe actual evidence |
|---|---|
| `com.cnhsk.learning.content.importer.ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget` | line398; samples_ms=[39648,34313,30627,31305,30905], p95=39648ms, yêu cầu<30000ms |
| `com.cnhsk.learning.content.importer.QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries` | line304; samples_ms=[62702,40704,38894,36917,35791], p95=62702ms, yêu cầu<30000ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry` | line133; PEXPIRETIME1793903128065, expected[1793903128184,1793903128941], thấp hơn cận dưới119ms |
| `com.cnhsk.auth.service.LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput)` | line313; CapturedOutput failure, raw captured diagnostic và fixture email/IP/password giữ private |
| `com.cnhsk.learning.knowledge.QuestionAdminControllerIntegrationTest.NFR_P02_fiveThousandSelfAuthoredQuestionsAreListedByKidWithinOneHundredMillisecondsP95` | line252; p95=156ms, yêu cầu<100ms; suite91 tests/1 failure/0 errors/0 skips |

### Leader-approved conditional delivery

- Leader công nhận NFR_P02 là ngoại lệ môi trường thứ năm cùng FR023, NFR_P04, PEXPIRETIME và CapturedOutput. Cho phép bàn giao T-13 sau mandatory STOP; không chạy lại Gate 7/Full Verify và không chuyển kết quả FAILED thành PASS.
- Gate 8 không có JaCoCo hợp lệ: execution file sau khi dừng là 0 byte; Project/Community coverage đều không có số liệu. Leader chấp nhận thiếu coverage ở lần bàn giao này và sẽ chạy Full Verify cùng JaCoCo đầy đủ khi tích hợp vào main. Không dùng coverage cũ, hạ threshold 80% hoặc sửa configuration để thay thế.
- Nội dung bắt buộc trong PR: "Gate 1–6 PASS; Gate 7 FAILED, bị ngắt sau 176 suite, 5 fail đều là test môi trường (FR023 39,6 s · NFR_P04 62,7 s · PEXPIRETIME · CapturedOutput · NFR_P02 156 ms); Gate 8 không có số liệu. Leader verify lại khi merge."
- Quyết định hiện tại cho phép cập nhật tracking, stage exact18, một commit `feat(community): handle report moderation cases`, push branch T-13 và mở Draft PR vào main sau preflight. Không tuyên bố independent source review PASS chỉ từ việc được duyệt delivery; không merge, chuyển Ready for Review hoặc bắt đầu T-14.
- Source integrity: 16/16 file khớp `source-freeze-sha256.json` sau sealed fix; SHA-256 của manifest `ACF83860133CE7B7719EF2B3E95E98AA4A949B9C523E7BB001C0203231EA9AEE`. 27 artifact trước vòng sửa giữ nguyên hash; không ghi đè evidence. Self-check: đúng allowlist, không đổi source/tests, dependency, schema/migration, security, locked contract hoặc worktree khác; precommit kiểm staged manifest/bytes/whitespace/secrets trước commit.
- Evidence local: `C:\WINDOWS\TEMP\cnhsk-spec009-t13-20261105-implementation\approved-sealed-fix-round\`: source-freeze manifest, before/after hashes, result JSON/private logs/Surefire XML Gates1–7, Gate2 OpenAPI/schema proof, Gate3 PostgreSQL transaction/blocking proof, Gate6 exact scope, Gate7 mandatory-stop JSON/XML và execution provenance. Evidence trước sealed fix nằm ở thư mục cha, không trộn vào kết quả hiện tại. Chỉ đưa safe metrics vào tracking/PR, không commit raw logs/XML hoặc secrets.

## T-14 — Comment moderation: quyết định Leader và conditional delivery (2026-11-06)

- Phạm vi FR-052…FR-055, FR-063 / AT-45, AT-46, AT-51; branch `spec/009-community-blog-moderation-t14`, base `a3dc0dafe56403ea34d522cf8a82cac15b256718`, worktree `.worktrees/spec009-t14`. Exact12files: 6 ADD + 6 MODIFY, trong đó 10 source/test files đã đóng băng và hai tracking documents. Chỉ cập nhật tracking sau khi Gate 7 rerun đủ trường hợp B và Gate 8 PASS; không sửa source/tests trong infrastructure recovery.

### Q-T14-1…6 — các quyết định đã duyệt

- **Q-T14-1 — transaction và khóa dòng:** `findByIdForUpdate(id)` dùng `@Lock(PESSIMISTIC_WRITE)`; direct moderation chạy một transaction READ_COMMITTED. Role và validation độc lập trước khi đọc khóa; missing comment → 404 COMMENT_NOT_FOUND; SELF_MODERATION kiểm trên author của dòng đã khóa → 403. Không CAS/retry, row-version hoặc migration. Mutation chỉ cập nhật status/moderated_by/moderation_reason bằng native update; flush/reload fresh state trước DTO.
- **Q-T14-2 — transition matrix:** dùng trạng thái thực tế sau khi có row lock theo bảng dưới. No-op trả 200 và phản ánh state thực tế; không UPDATE thật, không audit, không thay moderated_by/moderation_reason/updated_at. HIDE trên DELETED không hồi sinh comment hoặc thay marker.
- **Q-T14-3 — concurrency và T-13:** hai moderators tuần tự hóa theo row lock; HIDE đồng thời chỉ một mutation/một comment audit. T-13 ReportCaseService giữ semantics audit hồ sơ riêng; T-14 chỉ audit direct transition thực sự. Không sửa source/tests T-13. PostgreSQL tests kiểm transaction-id wait thật, hai thứ tự actions, cross-operation races, author edit/delete races và không deadlock/lost update; không chỉ dựa timing hoặc Future.isDone().
- **Q-T14-4 — audit và N6:** dùng `AuditService.recordDetail(actorId, action, "COMMENT", id, detail)`; actions COMMUNITY_COMMENT_HIDE/DELETE/RESTORE; detail đúng ba keys `{before, after, reason}`, status strings và reason đã strip, không thêm thông tin định danh. Audit cùng transaction mutation; PostgreSQL kiểm cùng backend PID/XID, audit insert thật và rollback khi audit failure. Native mutation không chạm updated_at; DB timestamp, public editedAt, content khi RESTORE, parent_id, depth, replies/cây đều giữ nguyên. No-op kiểm SQL UPDATE=0 và xmin không đổi.
- **Q-T14-5 — AUTHOR_DELETED (quyết định thay thế chính thức):** nhận diện bằng đồng thời `status == DELETED && "AUTHOR_DELETED".equals(moderationReason) && Objects.equals(moderatedBy, authorId)`. CommentService thực sự đặt moderated_by=authorId khi author tự xóa; không dùng IS NULL, không sửa CommentService. Author-deleted HIDE/DELETE → 200 no-op, giữ toàn bộ metadata; RESTORE → 409 COMMENT_NOT_RESTORABLE. Legacy marker nhưng moderated_by khác authorId, kể cả null, được coi là moderator-deleted và RESTORE được phép. Không có chuỗi direct actions hồi sinh author-deleted. `reason.strip()` phải 1–1.000 Unicode code points; literal AUTHOR_DELETED sau strip bị 422 VALIDATION_FAILED, case-sensitive. Invalid/missing/null/wrong-type action/reason → 422; không sửa ErrorCode.
- **Q-T14-6 — FR-063 và contract hoàn chỉnh:** GET moderation comments chỉ MODERATOR; guest 401, LEARNER/ADMIN không có MODERATOR 403, post missing 404 POST_NOT_FOUND. Post mọi trạng thái được phép; trả tất cả comments mọi status, raw content/parentId/depth, sort created_at ASC/id ASC. Một batch CommunityAuthors.resolve cho cả author và moderator, không N+1 hoặc public CommentTreeBuilder. ModerationComment đủ 10 required keys, parentId/moderatedBy/moderationReason nullable. Hai operations mới moderateComment/listPostCommentsForModeration giữ response application/json; cookie/bearer OR và CSRF được kiểm HTTP. Strict CommunityContractTest: source=runtime=documented=24, missing=0; giữ negative tests missing/extra/hidden/wrong operationId/statuses, không có mode flag.

| Trạng thái sau khóa | HIDE | DELETE | RESTORE |
|---|---|---|---|
| VISIBLE | HIDDEN | DELETED | No-op 200 |
| HIDDEN | No-op 200 | DELETED | VISIBLE |
| DELETED by MODERATOR | No-op 200 | No-op 200 | VISIBLE |
| DELETED by AUTHOR | No-op 200 | No-op 200 | 409 COMMENT_NOT_RESTORABLE |

### Springdoc — quyết định metadata và backlog

- Leader duyệt helper cho operations cũ chọn application/json → application/* → */* chỉ khi key trước không tồn tại; schema của key đã chọn sai/thiếu vẫn FAIL, không fallback che mismatch. Hai operations T-14 luôn strict application/json; không sửa assertions hoặc locked OpenAPI.
- ModerationComment.depth giữ Java short, required, min0/max3; metadata cuối cùng có đồng thời `type = "integer"` và `types = {"integer"}`, không thêm format. Preflight sau sửa PASS một lần; generated schema integer/min0/max3 và đủ 24 Community operations. Hai preflight FAILED trước giữ nguyên log/XML/snapshot; một bước metadata scan phát hiện type đã tồn tại và không chạy Maven.
- Đính chính của Leader: CommentResponse.depth của T-08, khi không khai báo explicit type, thực tế sinh type: integer kèm format: int32, không phải type: string. Không sửa DTO T-08.
- Với Springdoc OpenAPI 3.1 hiện tại, field số có minimum/maximum muốn sinh đúng integer không có format phải khai báo đồng thời `type = "integer"` và `types = {"integer"}`.
- 401/403 của một số endpoint cũ sinh content: */* thay vì application/json; contract test chỉ kiểm path + status nên chưa bắt được; gom sửa cùng OpenApiCustomizer.
- Giữ backlog nullable $ref đã duyệt: moderatedBy sinh type: null kèm $ref UserRef theo pattern T-12/T-13; runtime null/non-null đã kiểm. Không triển khai OpenApiCustomizer hoặc tự miễn schema mismatch khác trong T-14.

### Verification — kết quả thực tế, không đổi FAILED thành PASS

Các command dưới dùng `.\mvnw.cmd -B`; exact command đầy đủ, timestamps, XML/log và hash trước/sau lưu trong result JSON tương ứng.

| Gate | Command/selector | Tests | Failures | Errors | Skips | Maven exit |
|---|---|---:|---:|---:|---:|---:|
| 1 unit, giữ nguyên | -Dtest=CommentModerationServiceTest test | 75 | 0 | 0 | 0 | 0 |
| OpenAPI preflight cuối | -Dtest=CommentModerationControllerIntegrationTest#generatedOpenApiMatchesLockedContract -Dcommunity.openapi.snapshot=<evidence>/openapi-springdoc31-recovery-actual.json test | 1 | 0 | 0 | 0 | 0 |
| 2 HTTP/OpenAPI/security | -Dtest=CommentModerationControllerIntegrationTest -Dcommunity.openapi.snapshot=<evidence>/gate2-actual-openapi.json test | 11 | 0 | 0 | 0 | 0 |
| 3 PostgreSQL/concurrency/N6/rollback | -Dtest=CommentModerationConcurrencyIntegrationTest test | 34 | 0 | 0 | 0 | 0 |
| 4 strict contract | -Dtest=CommunityContractTest test | 16 | 0 | 0 | 0 | 0 |
| 5 architecture | -Dtest=CommunityArchitectureTest,ModuleBoundaryTest test | 14 | 0 | 0 | 0 | 0 |
| 6 regression, 55 suites | -Dtest=<gate6-selector.txt> test | 860 | 0 | 0 | 0 | 0 |
| 7 lần đầu, FAILED, 211 suites | verify | 3432 | 9 | 495 | 0 | 1 |
| 7 rerun duy nhất, FAILED, 211 suites | verify | 3600 | 4 | 0 | 0 | 1 |
| 8 current-run JaCoCo, PASS | jacoco:report@report jacoco:check@check | N/A | N/A | N/A | N/A | 0 |

- Gate 1 giữ PASS theo Leader, không rerun sau helper/metadata recovery vì không thay behavior; Gates 1–6 không rerun trong infrastructure recovery. Gate 6 XML khớp exact55selectors, gồm T-04…T-13, shared audit/auth/security và legacy callers.
- Gate 7 lần đầu: UTC 2026-11-06T00:23:37.0653420Z → 04:00:27.7820092Z, BUILD FAILURE. PostgreSQL Connection refused đầu tiên tại Maven log line14511, 07:43:32.245+07, trong QuestionAdminControllerIntegrationTest; Redis refusal từ 07:43:12.006+07. Suite PASS ngay trước là QuestionAdminConcurrencyIntegrationTest (6/6); XML trước NFR_P02 có AT21_invalidContentsReturnFieldErrorsAndDoNotChangeTheDatabase(String)[9] PASS, nhưng không suy diễn thời gian tuyệt đối chỉ từ thứ tự XML.
- Docker VM init.log ghi POST /shutdown lúc 00:43:26.815439609Z và all processes have shutdown lúc 00:43:27.244326194Z; actor không xác định. Surviving PostgreSQL/Redis container states giữ FinishedAt 00:43:02/03Z, StartedAt sau khi engine mở lại 04:12:26/27Z. docker events trả 0 events sau restart nên không dùng để phủ nhận sự cố; không có affirmative OOM evidence. Không suy đoán ai tắt engine hoặc nguyên nhân OOM.
- Phân loại lượt đầu: 9 failures gồm 4 ngoại lệ đã xác minh, 2 có evidence connection trong testcase và 3 chưa chứng minh nguyên nhân riêng (NFR_P02 HTTP500 và hai PracticeRateLimiter assertions). 495 errors gồm 45 direct connection, 17 context-load có connection, 376 context-threshold cascade và 57 chưa đủ chứng cứ riêng. Không tự miễn các lỗi này; chỉ rerun sau approval mới của Leader.
- Recovery: Docker Desktop 27.2.0, 16 CPU, memory 8.166.666.240 bytes (~7,61 GiB), version/info/ps exit0 trong PowerShell ngoài sandbox, không còn Maven/Surefire cũ. Engine đã được mở lại trước recovery; không đổi config/RAM, restart thêm, prune, xóa volume/data hoặc tác động worktree khác. Giữ 332 artifact cũ qua hash check; live Docker events được lưu cho rerun. Observer chỉ theo dõi lượt hiện có, ancestry + Surefire boot-JAR manifest ràng buộc target/test-classes T-14; không tạo thêm Maven run.
- Gate 7 rerun duy nhất: UTC 04:39:31.1723902Z → 05:04:33.3238582Z, 3600/4/0/0, exit1, BUILD FAILURE. Không lỗi kết nối, không trigger mandatory STOP ngoài allowlist. QuestionAdminControllerIntegrationTest 91/91 PASS; NFR_P02 p95=36ms, không có HTTP500. Năm ngoại lệ được phép chỉ là FR023/NFR_P04/PEXPIRETIME/CapturedOutput/NFR_P02 vượt p95; lần này chỉ bốn ngoại lệ đầu xảy ra.

| Failure trong rerun | Actual/expected an toàn |
|---|---|
| ImportConfirmIntegrationTest.FR023_fiveIndependent5000RowImportsMeetTheLocalP95Budget | line398, p95=36404ms, yêu cầu <30000ms |
| QuestionImportIntegrationTest.NFR_P04_fiveThousandQuestionsMeetTheThirtySecondConfirmBudgetAndUseBatchedPreviewQueries | line304, p95=49924ms, yêu cầu <30000ms |
| LoginAttemptServiceIntegrationTest.FR046_FR047_FR048_fifthFailureBlocksOnlyTheEmailIpPairAndUsesActualRedisExpiry | line133, PEXPIRETIME1793941113724, expected[1793941113951,1793941114623], thấp hơn cận dưới227ms |
| LoginAttemptServiceIntegrationTest.redisFailuresInAllThreeOperationsFailOpenAndOnlyLogSafeWarnings(CapturedOutput) | line313; raw captured diagnostic giữ private, không đưa fixture PII/password vào tracking hoặc PR |

- **Điều kiện B của Leader:** được conditional delivery với đúng bốn failures trên; Gate 7 vẫn FAILED, không gọi PASS. Không chạy lần thứ ba. Gate 8 PASS: configured report/check exit0, All coverage checks have been met; project LINE 9767/10061=97,08% (>=80%), Community LINE 1012/1022=99,02%, class mismatch=0. Execution data 1.141.125 bytes, SHA256 `11846CDA87ACFFE3EE6A11128B616D8C0E551A3F8EC26A2072088003070446EB`; session 04:39:40.946Z → 05:04:31.806Z nằm trong rerun. Report XML SHA256 `47FDAA5D67FAB1CD4C42629DCAD5E563A644260E0F15A28CC7BB2FFC002E405E`. Không dùng coverage lượt đầu hoặc miễn Gate 8.
- Source integrity cuối: 10/10 khớp `source-freeze-springdoc31-recovery-sha256.json`, manifest SHA256 `3BF5E65C71B660C9EB555A52652D561F642DB96136C9AC6852637212E66AB3E0`; chain manifest trước helper/metadata và evidence hai lượt Gate 7 giữ nguyên. Chỉ hai tracking files được cập nhật khi đủ điều kiện B + Gate8. Delivery preflight phải kiểm exact12 (6 ADD/6 MODIFY), hashes, BOM/UTF-8/EOF/whitespace/secrets, staged diff-check; một Conventional Commit, push task branch, Draft PR main. Không Ready/merge hoặc bắt đầu SPEC-013.
- Evidence local: `C:\WINDOWS\TEMP\cnhsk-spec009-t14-20261106-implementation\`: manifests, gate result JSON/private logs/Surefire XML, OpenAPI snapshots, PostgreSQL blocking/transaction proof, `gate7-infrastructure-investigation/`, first-run STOP, rerun classification/live events/once marker và Gate8 JaCoCo execution/report. Raw logs/XML không commit hoặc đăng lên PR; chỉ safe metrics.
