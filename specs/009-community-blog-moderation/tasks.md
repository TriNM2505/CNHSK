# Tasks: Blog cộng đồng có kiểm duyệt — Spec #009

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-community.yaml`](contracts/openapi-community.yaml) · [`plan.md`](plan.md)
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
| T-01 | Entity + repository `Post`, `Comment`, `Reaction` (+ `ReactionId`), `ModerationCase` (`reasons` → `List<ReportEntry>`); enum `PostStatus`, `PostCategory`, `CommentStatus`, `ReactionKind`, `TargetType`, `CaseStatus`, `ReportReason`; test khởi động với `ddl-auto: validate` trên Testcontainers | `…/community/{post,comment,reaction,moderation,common}/*` | §4, `AC-05`, `AC-06` | ⬜ |
| T-02 | Hợp đồng: test so `/v3/api-docs` với `contracts/openapi-community.yaml` (path + method + status code), theo mẫu test hợp đồng spec 001 | `t/contract/CommunityContractTest.java` | §13 · `AC-01` | ⬜ |
| T-03 | `SecurityConfig` thêm 3 matcher; `ErrorCode` thêm mã §5; `CommunityProperties`, `CommunityAuthors`, `CommunityLimits` (đồng hồ `Clock`, giờ VN) | `…/shared/config/SecurityConfig.java`, `…/shared/exception/ErrorCode.java`, `…/community/common/*` | FR-001, FR-006, §5 · AT-01, AT-02 | ⬜ |

## Đợt 1 · Bài viết

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-04 | `PostService.create/update` + `MyPostController` POST/PUT: whitelist trường, xác thực email, giới hạn 5 bài/ngày, chuyển trạng thái FR-009/FR-011, `HIDDEN` → 409 | `…/community/post/*`, `…/community/post/web/MyPostController.java` | FR-002…FR-011 · AT-03…AT-10 | ⬜ |
| T-05 | `PostQueryService` + `PublicPostController` danh sách/chi tiết: `findPublished…` duy nhất, lọc danh mục, phân trang, đếm theo lô, tên tác giả theo lô | `…/community/post/*`, `…/community/post/web/PublicPostController.java` | FR-012…FR-015 · AT-11…AT-14, AT-17 | ⬜ |
| T-06 | `GET /posts/mine`, `/posts/mine/{id}`, `/posts/{id}/viewer-state` | `MyPostController.java`, `PostQueryService.java` | FR-016, FR-056 · AT-15, AT-16, AT-47 | ⬜ |

## Đợt 2 · Bình luận và thích

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-07 | `CommentService.create/reply` + `CommentController` POST: bài `APPROVED`, độ dài, 30/giờ, quy tắc cấp 3, không lộ nội dung cha | `…/community/comment/*` | FR-017, FR-019…FR-023 · AT-18…AT-21 | ⬜ |
| T-08 | `CommentTreeBuilder` (test đơn vị) + `GET /api/public/community/posts/{id}/comments` một truy vấn phẳng | `…/community/comment/CommentTreeBuilder.java`, `PublicPostController.java` | FR-018, FR-027 · AT-22…AT-24 | ⬜ |
| T-09 | Sửa trong 15 phút + tự xoá mềm (`AUTHOR_DELETED`) | `CommentService.java`, `CommentController.java` | FR-024…FR-026 · AT-25, AT-26 | ⬜ |
| T-10 | `LikeService` + POST/DELETE `/like`: `ON CONFLICT DO NOTHING`, idempotent, đếm từ `reactions` | `…/community/reaction/*`, `MyPostController.java` | FR-028…FR-031 · AT-27…AT-29 | ⬜ |

## Đợt 3 · Báo cáo và kiểm duyệt

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-11 | `ReportService` + `ReportController`: kiểm nội dung công khai, tự báo cáo, 20/ngày, một transaction chèn `REPORT` + upsert hồ sơ nối `reasons` | `…/community/moderation/ReportService.java`, `…/moderation/web/ReportController.java` | FR-032…FR-038 · AT-30…AT-34 | ⬜ |
| T-12 | `PostModerationService` + hàng đợi/chi tiết/duyệt/từ chối: `UPDATE` có điều kiện, `SELF_MODERATION`, audit | `…/moderation/PostModerationService.java`, `…/moderation/web/ModerationController.java` | FR-039…FR-045 · AT-35…AT-39 | ⬜ |
| T-13 | `ReportCaseService` hàng đợi/chi tiết hồ sơ (người báo cáo qua `CommunityAuthors`) + quyết định `ACTION`/`REJECT` một transaction + audit | `…/moderation/ReportCaseService.java`, `ModerationController.java` | FR-046…FR-051 · AT-40…AT-45 | ⬜ |
| T-14 | `CommentModerationService` HIDE/DELETE/RESTORE, idempotent, `COMMENT_NOT_RESTORABLE`, audit; `GET /moderation/posts/{id}/comments` cây đầy đủ cho `MODERATOR` | `…/moderation/CommentModerationService.java`, `ModerationController.java` | FR-052…FR-055, FR-063 · AT-45, AT-46, AT-51 | ⬜ |

## Đợt 4 · Frontend

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-15 | Nền FE: `api/*` theo OpenAPI, `types.ts`, nhãn FR-061, `screens.ts`; thêm route §12 vào `routeAccess.ts` + catalog vào `screenRegistry.ts` (báo leader trước) | `cnhsk-web/src/features/community/**`, `cnhsk-web/src/app/routeAccess.ts`, `cnhsk-web/src/app/screenRegistry.ts` | §12, FR-061 | ✅ |
| T-16 | SCR-039 danh sách + tab của tôi; SCR-041 tạo/sửa bài | `cnhsk-web/src/features/community/**` | FR-057, FR-059 · AT-49 | ⬜ |
| T-17 | SCR-040 chi tiết: cây bình luận, thích, bình luận/trả lời/sửa/xoá, báo cáo, nút kiểm duyệt cho `MODERATOR`; nội dung chỉ render text | `cnhsk-web/src/features/community/**` | FR-005, FR-058, FR-063 · AT-48, AT-49 | ⬜ |
| T-18 | SCR-060/061 kiểm duyệt bài; SCR-062/063 hồ sơ báo cáo | `cnhsk-web/src/features/community/**` | FR-060, FR-062 · AT-49 | ⬜ |

---

**Thứ tự:** T-01 → T-02 → T-03 → T-04 → T-05 → T-06 → (T-07 → T-08 → T-09) ∥ T-10 → T-11 → T-12 → T-13 → T-14 → T-15 → T-16 → T-17 → T-18.
Sau T-14: chạy toàn bộ AT-01…AT-47, AT-50, AT-51 trong `./mvnw verify`. Sau T-18: chuyển PR sang Ready, báo leader review cả nhánh.
**Rủi ro cao:** T-05 (lọc trạng thái + đếm theo lô), T-11 (upsert hồ sơ đồng thời), T-13 (transaction đóng hồ sơ + đổi nội dung).
