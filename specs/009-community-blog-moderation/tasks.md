# Tasks: Blog cộng đồng có kiểm duyệt — Spec #009

> Nguồn: [`spec.md`](spec.md) v1.1.0 · [`contracts/openapi-community.yaml`](contracts/openapi-community.yaml) · [`plan.md`](plan.md)
> ⬜ chưa làm · 🔄 đang làm · ✅ xong · ⏸ hoãn · 🔴 chặn

**Tổng: 18 task**, mỗi task ≤ 4 giờ. Làm trên nhánh `spec/009-community-blog-moderation`, **mỗi task một commit + push**, PR Draft vào `main`.
`…/` = `src/main/java/com/cnhsk/`, `t/` = `src/test/java/com/cnhsk/`.

**Điều kiện bắt đầu:** PR spec 001 đã merge vào `main` (cần `AuthApi`, `AuditService`, `ErrorCode`, `SecurityConfig`, `AbstractIntegrationTest`). Spec này đã được nhóm duyệt (§9 Q2, Q3, Q4, Q6).

### Quy ước cho mọi task

1. Đọc `AGENTS.md` → `spec.md` (mục ở cột *Spec*) → OpenAPI. **Gửi Shadow Plan cho leader trước khi code.** Không sửa file ngoài cột *File*.
2. Mỗi FR trong task có test tên bắt đầu bằng mã FR, vd `FR041_approve_setsReviewerAndPublishedAt`. Kịch bản lấy từ AT ở `spec.md` §14.
3. Xong khi: test của task pass · `./mvnw verify` xanh, JaCoCo ≥ 80% (BE) hoặc `npm run lint && npm run typecheck && npm test` xanh (FE) · `git diff --check` sạch.
4. Spec không nói → dừng, ghi `specs/009-community-blog-moderation/questions.md`. Không tự giả định.
5. Commit: `feat(community): <mô tả> (T-xx)`. Commit từng file cụ thể, không `git add .`. Không nhắc AI.
6. **Không** viết migration, **không** chạy gì lên DB chung, **không** commit `.env`. Cần đổi schema → dừng, báo leader.
7. File ngoài module `community` (`SecurityConfig`, `ErrorCode`, `routeAccess.ts`, `screenRegistry.ts`) chỉ **thêm** dòng, ghi rõ trong Shadow Plan.

---

## Đợt 0 · Nền

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Entity + repository `Post`, `Comment`, `Reaction` (+ `ReactionId`), `ModerationCase` (`reasons` → `List<ReportEntry>`); enum `PostStatus`, `PostCategory`, `CommentStatus`, `ReactionKind`, `TargetType`, `CaseStatus`, `ReportReason`; test khởi động với `ddl-auto: validate` trên Testcontainers | `…/community/{post,comment,reaction,moderation,common}/*` | §4, `AC-05`, `AC-06` | ✅ |
| T-02 | Hợp đồng: test so `/v3/api-docs` với `contracts/openapi-community.yaml` (path + method + status code), theo mẫu test hợp đồng spec 001 | `t/contract/CommunityContractTest.java` | §13 · `AC-01` | ✅ |
| T-03 | `SecurityConfig` thêm 3 matcher; `ErrorCode` thêm mã §5; `CommunityProperties`, `CommunityAuthors`, `CommunityLimits` (đồng hồ `Clock`, giờ VN) | `…/shared/config/SecurityConfig.java`, `…/shared/exception/ErrorCode.java`, `…/community/common/*` | FR-001, FR-006, §5 · AT-01, AT-02 | ✅ |

## Đợt 1 · Bài viết

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-04 | `PostService.create/update` + `MyPostController` POST/PUT: whitelist trường, xác thực email, giới hạn 5 bài/ngày, chuyển trạng thái FR-009/FR-011, `HIDDEN` → 409 | `…/community/post/*`, `…/community/post/web/MyPostController.java` | FR-002…FR-011 · AT-03…AT-10 | ✅ |
| T-05 | `PostQueryService` + `PublicPostController` danh sách/chi tiết: `findPublished…` duy nhất, lọc danh mục, phân trang, đếm theo lô, tên tác giả theo lô | `…/community/post/*`, `…/community/post/web/PublicPostController.java` | FR-012…FR-015 · AT-11…AT-14, AT-17 | ✅ |
| T-06 | `GET /posts/mine`, `/posts/mine/{id}`, `/posts/{id}/viewer-state` | `MyPostController.java`, `PostQueryService.java` | FR-016, FR-056 · AT-15, AT-16, AT-47 | ✅ |

## Đợt 2 · Bình luận và thích

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | `CommentService.create/reply` + `CommentController` POST: bài `APPROVED`, độ dài, 30/giờ, quy tắc cấp 3, không lộ nội dung cha | `…/community/comment/*` | FR-017, FR-019…FR-023 · AT-18…AT-21 | ✅ |
| T-08 | `CommentTreeBuilder` (test đơn vị) + `GET /api/public/community/posts/{id}/comments` một truy vấn phẳng | `…/community/comment/CommentTreeBuilder.java`, `PublicPostController.java` | FR-018, FR-027 · AT-22…AT-24 | ✅ |
| T-09 | Sửa trong 15 phút + tự xoá mềm (`AUTHOR_DELETED`) | `CommentService.java`, `CommentController.java` | FR-024…FR-026 · AT-25, AT-26 | ✅ |
| T-10 | `LikeService` + POST/DELETE `/like`: `ON CONFLICT DO NOTHING`, idempotent, đếm từ `reactions` | `…/community/reaction/*`, `MyPostController.java` | FR-028…FR-031 · AT-27…AT-29 | ✅ |

## Đợt 3 · Báo cáo và kiểm duyệt

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-11 | `ReportService` + `ReportController`: kiểm nội dung công khai, tự báo cáo, 20/ngày, một transaction chèn `REPORT` + upsert hồ sơ nối `reasons` | `…/community/moderation/ReportService.java`, `…/moderation/web/ReportController.java` | FR-032…FR-038 · AT-30…AT-34 | ✅ |
| T-12 | `PostModerationService` + hàng đợi/chi tiết/duyệt/từ chối: `UPDATE` có điều kiện, `SELF_MODERATION`, audit | `…/moderation/PostModerationService.java`, `…/moderation/web/ModerationController.java` | FR-039…FR-045 · AT-35…AT-39 | ✅ |
| T-13 | `ReportCaseService` hàng đợi/chi tiết hồ sơ (người báo cáo qua `CommunityAuthors`) + quyết định `ACTION`/`REJECT` một transaction + audit | `…/moderation/ReportCaseService.java`, `ModerationController.java` | FR-046…FR-051 · AT-40…AT-45 | ✅ |
| T-14 | `CommentModerationService` HIDE/DELETE/RESTORE, idempotent, `COMMENT_NOT_RESTORABLE`, audit; `GET /moderation/posts/{id}/comments` cây đầy đủ cho `MODERATOR` | `…/moderation/CommentModerationService.java`, `ModerationController.java` | FR-052…FR-055, FR-063 · AT-45, AT-46, AT-51 | ✅ |

## Đợt 4 · Frontend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-15 | Nền FE: `api/*` theo OpenAPI, `types.ts`, nhãn FR-061, `screens.ts`; thêm route §12 vào `routeAccess.ts` + catalog vào `screenRegistry.ts` (báo leader trước) | `cnhsk-web/src/features/community/**`, `cnhsk-web/src/app/routeAccess.ts`, `cnhsk-web/src/app/screenRegistry.ts` | §12, FR-061 | ✅ |
| T-16 | SCR-039 danh sách + tab của tôi; SCR-041 tạo/sửa bài | `cnhsk-web/src/features/community/**` | FR-057, FR-059 · AT-49 | ✅ |
| T-17 | SCR-040 chi tiết: cây bình luận, thích, bình luận/trả lời/sửa/xoá, báo cáo, nút kiểm duyệt cho `MODERATOR`; nội dung chỉ render text | `cnhsk-web/src/features/community/**` | FR-005, FR-058, FR-063 · AT-48, AT-49 | ✅ |
| T-18 | SCR-060/061 kiểm duyệt bài; SCR-062/063 hồ sơ báo cáo | `cnhsk-web/src/features/community/**` | FR-060, FR-062 · AT-49 | ✅ |

## Đợt 5 · Dọn cuối (leader thêm 2026-11-06)

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-19 | Sửa 7 lỗi FE mức P2 FE009-AUD-01…07 (SCR-040/041/061/063, điều hướng `MODERATOR`); chạy thử luồng cộng đồng với backend thật | `cnhsk-web/src/features/community/**`, file điều hướng dùng chung (nêu trong Shadow Plan) | FR-057…FR-063 · AT-49 | ✅ — community 597/597; 3 shuffle seed PASS; full Vitest 2309/2309; lint 0 error (1 warning LevelBadge có sẵn); build PASS sau recovery kiểu 2 file test; smoke backend thật PASS |
| T-20 | Sửa annotation OpenAPI sinh sai (`page`/`size` của `MyPostController`, `PublicPostController`; kiểm chứng `CommentResponse.depth` khớp, không sửa) + gom hàm `excerpt` về `community.common` | `…/community/post/web/*Controller.java`, `…/community/common/*`, `PostQueryService`, `PostModerationService` + test | §13 · AT-49 | ✅ — community + contract 710/710; full verify FULL VERIFY FAILED — APPROVED ENVIRONMENTAL EXCEPTIONS ONLY; JaCoCo LINE 96.75% |

## Đợt 6 · Phụ lục v1.1 — hồ sơ cộng đồng + theo dõi (leader thêm 2026-11-14, spec §16)

Người code: Quốc Huy. Nhánh mỗi task `spec/009-<task>`, một PR. Không migration. `AuthApi.publicProfiles`/`bannedUserIds` đã có (017/T-01).

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-21 | `FollowService` + `FollowQueryRepository` + controller follow/unfollow; mã `SELF_FOLLOW`, `FOLLOW_LIMIT_REACHED` (trước `INTERNAL_ERROR`) + `GlobalExceptionHandlerTest` | `…/community/follow/**`, `…/shared/exception/ErrorCode.java` + test | FR-067…FR-069, FR-071 · AT-55…AT-58 | ⬜ |
| T-22 | `CommunityProfileService`: hồ sơ, số đếm loại người bị khoá, danh sách followers/following theo lô; bộ lọc `authorId` cho danh sách bài công khai | `…/community/follow/**`, `…/community/post/**` + test | FR-064…FR-066, FR-070 · AT-52…AT-54, AT-59, AT-60, AT-61 (BE) | ⬜ |
| T-23 | FE SCR-042 + link tác giả ở SCR-039/040; route `/community/users/:id` (`learner`); `SCREEN_NAMES` SCR-042 + `AssistantPromptBuilderTest` | `cnhsk-web/src/features/community/**`, `routeAccess.ts`, `screenRegistry.ts`, `…/learning/assistant/AssistantPromptBuilder.java` + test | FR-072 · AT-61 (FE) | ⬜ |
| T-24 | Gộp `contracts/openapi-community-v1-1.yaml` vào `openapi-community.yaml` (path mới, `authorId`, `COLLECTION`, `ReportedDeck`), xoá file phụ lục; nâng số operation/kiểm tham số trong `CommunityContractTest` | `specs/009-…/contracts/*`, `t/contract/CommunityContractTest.java` | §13, §16 | ⬜ |
| T-25 | Báo cáo bộ thẻ: `targetType=COLLECTION` trong báo cáo, xem và xử lý hồ sơ qua `learning.api.PublicDeckApi` (cần 014/T-16 đã merge) | `…/community/moderation/**`, `…/community/report/**` + test | FR-073…FR-075 · AT-62…AT-64 | ⬜ |
| T-26 | FE SCR-062/063 hiển thị hồ sơ bộ thẻ | `cnhsk-web/src/features/community/**` | FR-076 · AT-65 | ⬜ |

**Thứ tự đợt 6:** T-21 → T-22 → T-24; T-23 song song từ đầu bằng dữ liệu mẫu, nối thật sau T-22. T-25 sau 014/T-16 (cần `PublicDeckApi`), rồi T-26; T-24 gộp hợp đồng sau khi T-22 và T-25 xong.

---

**Thứ tự:** T-01 → T-02 → T-03 → T-04 → T-05 → T-06 → (T-07 → T-08 → T-09) ∥ T-10 → T-11 → T-12 → T-13 → T-14 → T-15 → T-16 → T-17 → T-18 → T-19 → T-20.
Sau T-14: chạy toàn bộ AT-01…AT-47, AT-50, AT-51 trong `./mvnw verify`. Sau T-18: chuyển PR sang Ready, báo leader review cả nhánh.
**Rủi ro cao:** T-05 (lọc trạng thái + đếm theo lô), T-11 (upsert hồ sơ đồng thời), T-13 (transaction đóng hồ sơ + đổi nội dung).
