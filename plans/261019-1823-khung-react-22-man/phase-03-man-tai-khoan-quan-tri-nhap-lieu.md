---
phase: 3
title: "Man tai khoan, quan tri, nhap du lieu"
status: pending
priority: P1
dependencies: [2]
effort: "12h"
---

# Phase 3: Màn tài khoản, quản trị, nhập dữ liệu (13 màn)

## Overview

Mỗi màn: container + View + preview (phase 2 mục 1), đủ 4 trạng thái, đăng ký `/__preview`, xoá phần lệch spec (bảng `plan.md`).
**Bố cục:** `C:\CLHSK\cnhsk-mockup-79\screens\<file>`. **Nội dung + hành vi:** spec ghi ở cột Spec.
Form: validate phía client đúng quy tắc spec (server vẫn kiểm), lỗi từng field từ `details` của 422, khoá nút khi gửi.

## Tasks

| ID | Màn | HTML | Spec | View cần có |
|---|---|---|---|---|
| FE-14 | SCR-002 Sign In | `scr-002-sign-in.html` | 001 FR-014…021, FR-047, FR-090 | Form email + mật khẩu; lỗi 401 chung "Email hoặc mật khẩu không đúng"; 423 hiện giờ mở `blockedUntil`; 403 cấm (lý do)/tạm khoá; thành công → điều hướng FR-090 (đọc `redirect`, chấp nhận origin game) |
| FE-15 | SCR-003 Register | `scr-003-register.html` | 001 FR-001…005, FR-091, FR-095 | 5 trường + tick điều khoản; quy tắc mật khẩu hiện dưới ô; 409 email trùng; thành công → SCR-004 |
| FE-16 | SCR-004 Verify Email | `scr-004-verify-email.html` | 001 FR-009…013, FR-087, FR-088, FR-094 | 3 view: "đã gửi tới {email}" + gửi lại (đếm 429 `retryAfterSeconds`) · đang xác thực (có `?token`) · kết quả thành công/lỗi token. Giữ nút "Bỏ qua, vào học" |
| FE-17 | SCR-005 Forgot · SCR-006 Reset | `scr-005-*.html`, `scr-006-*.html` | 001 FR-038…042, FR-045 | SCR-005: một ô email → luôn hiện cùng thông báo (202). SCR-006: mật khẩu mới ×2; 400 token hỏng → link về SCR-005 |
| FE-18 | SCR-007 Profile · SCR-008 Change Password | `scr-007-*.html`, `scr-008-*.html` | 001 FR-043…045, FR-051…053 | SCR-007: **chỉ** tên hiển thị, `avatarUrl` (ô URL + xem trước ảnh), cấp HSK mục tiêu; email chỉ đọc + trạng thái xác minh. SCR-008: 3 ô; 422 sai mật khẩu hiện ở ô hiện tại |
| FE-19 | SCR-001 Landing (phần tài khoản) · SCR-079 Access Denied | `scr-001-landing.html`, `scr-079-*.html` | 001 FR-090, §12.3 | SCR-001: hero + giới thiệu 4 chế độ học (Chủ đề, Ngữ pháp, Phát âm, Chữ Hán) + Game; nút Đăng nhập/Đăng ký; đã đăng nhập nhiều khu vực → khối "Khu vực của bạn". SCR-079: thông báo thiếu quyền + nút về khu vực được phép |
| FE-20 | SCR-076 User List | `scr-076-user-list.html` | 001 FR-097 | Tìm (≥ 2 ký tự), lọc role (6 mã) + trạng thái (`ACTIVE/UNVERIFIED/BANNED` → "Hoạt động/Chưa xác minh/Bị cấm"), `DataTable` (email đã che), phân trang 20 |
| FE-21 | SCR-077 User Detail | `scr-077-user-detail.html` | 001 FR-062…066, FR-096, FR-097 | Thông tin + email đầy đủ; danh sách role (người cấp, thời điểm) + thêm/thu hồi qua `ConfirmPanel`; cấm (lý do 5–500) / gỡ cấm; lỗi 403/409 (tự cấp, admin cuối) hiện thông báo spec |
| FE-22 | SCR-068 Learning Data Import | `scr-068-*.html` | 002 FR-036…038, FR-040, §4.0 | 4 vùng: chọn mẫu (7 mẫu, nhãn §4.0) + bảng cột + tải mẫu · chọn file CSV (≤ 2 MB) · kết quả kiểm tra (6 số, bảng xem trước, 50 lỗi đầu, cảnh báo nhập lại + tick, đồng hồ hết hạn, xác nhận trong trang) · lịch sử phân trang 10 |
| FE-23 | SCR-069 Import Report | `scr-069-*.html` | 002 FR-026…028, FR-039 | Tóm tắt lần nhập; bảng lỗi phân trang 20 (dòng, cột, giá trị, mã, thông báo); "Tải danh sách lỗi" (blob → tải file); "Sửa file và nhập lại" |

## Success Criteria

- [ ] 13 màn mở được ở route thật (guard đúng) và ở `/__preview` đủ 4 trạng thái
- [ ] Không còn: "Gói học", "Tạm khoá", role `USER`, `.xlsx`, "Đề thi", "Bỏ qua trùng", "Điều kiện mở khóa"
- [ ] Test Vitest cho mỗi form: validate client, hiện lỗi field từ 422, khoá nút khi gửi
