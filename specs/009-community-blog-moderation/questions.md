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
