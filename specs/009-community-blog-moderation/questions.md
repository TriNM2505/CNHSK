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
