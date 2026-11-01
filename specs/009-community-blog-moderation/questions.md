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
