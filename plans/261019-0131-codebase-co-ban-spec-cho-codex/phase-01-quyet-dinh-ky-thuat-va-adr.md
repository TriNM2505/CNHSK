---
phase: 1
title: "Quyet dinh ky thuat va ADR"
status: pending
priority: P1
effort: "1 ngay"
dependencies: []
---

# Phase 1: Quyết định kỹ thuật và ADR

## Overview

Chốt mọi thứ đang "chưa quyết" mà spec phụ thuộc, ghi thành ADR. Codex không được tự chọn những điều này.

## Requirements

- Mỗi quyết định: bối cảnh, 2–3 phương án, lựa chọn, lý do, hệ quả (mẫu ADR theo sách §7.4).
- Quyết định đã chốt trong `AGENTS.md` §12 "Đã chốt" → **không** mở lại.

## Quyết định cần chốt

| # | Quyết định | Phương án | Khuyến nghị | Chặn |
|---|---|---|---|---|
| D1 | JWT ký HS256 hay RS256 | HS256 (1 secret) · RS256 (cặp khoá, client tự verify) | **HS256** — một backend tự ký tự kiểm, không có bên thứ ba cần verify | Spec 001 FR-016, FR-061 |
| D2 | TTL access / refresh token | 15 phút / 30 ngày (đề xuất cũ) · 30 phút / 14 ngày | **15 phút / 30 ngày**, refresh xoay vòng | Spec 001 |
| D3 | BUS-16: chấm viết chữ | Client (hanzi-writer) + server kiểm hợp lý · server chấm nét | **Client chấm + server kiểm hợp lý** (đã ghi LI-06 Report 1), trọng số thấp khi có mastery | Spec 003 |
| D4 | Redis đặt ở đâu | Sau app (cache, rate limit) · trước app (theo ý GVHD) | **Sau app** cho MVP; ghi câu hỏi mở để hỏi lại GVHD | Spec 000 |
| D5 | Thư viện mới cần duyệt | `hanzi-writer` (viết chữ) · `springdoc-openapi-starter-webmvc-ui` · Testcontainers PostgreSQL · **JaCoCo plugin** (coverage 80%) · Apache Commons CSV / Apache POI (đọc file nhập, theo định dạng Học Bá) | Duyệt cả 5; game **không** thêm engine (V6) | Spec 000, 003, 008 |
| D6 | Vị trí migration Flyway | Chuyển `db/migration` → `src/main/resources/db/migration` · đặt `spring.flyway.locations: filesystem:db/migration` | **Chuyển vào resources** (chuẩn Spring, đóng gói cùng jar) | Spec 000 |
| D7 | Web game | **Đã chốt (Validation V1, V6):** app riêng `cnhsk-game` tại `game.cnhsk.com`, **React 18.3 + Vite 5 + TS + Tailwind (cùng stack `cnhsk-web`), không engine**; đăng nhập chung cookie tên miền cha (UC-005) | — | Spec 000 (CORS, cookie domain), 001, 006a |
<!-- Updated: Validation Session 1 - D7 chốt app riêng React + CSS; D5 thêm JaCoCo, file reader cho nhập dữ liệu -->

| D8 | Baseline migration | Viết lại V1/V2 theo `database.md` · thêm V3 sửa chồng | **Viết lại V1/V2** — Flyway chưa từng chạy chúng (sai vị trí), chưa có DB dùng chung | Spec 000 |
| D9 | Java 17 vs Report 2 ghi 21 | Giữ 17 (AGENTS.md đã chốt) | **Giữ 17**, sửa Report 2 | — |

> D7 khớp `kien-truc.md` (3 client). ADR-007 ghi thêm: không dùng Phaser/PixiJS ở MVP; cần engine thì mở ADR mới.

## Related Code Files

- Create: `docs/adr/ADR-001-jwt-hs256.md` … `ADR-008-baseline-migration.md` (1 file/quyết định)
- Modify: `AGENTS.md` §12 (chuyển mục từ "Chưa chốt" sang "Đã chốt"), `.specify/memory/constraints/global.md` (thư viện được duyệt)

## Implementation Steps

1. Họp nhóm 45 phút với bảng trên; D4 gửi kèm câu hỏi cho GVHD.
2. Viết 8 ADR ngắn (≤ 1 trang mỗi cái).
3. Cập nhật `AGENTS.md` §3 (thư viện), §12 (đã chốt), `global.md`.

## Success Criteria

- [ ] `AGENTS.md` §12 "Chưa chốt" chỉ còn mục không chặn spec nào trong plan này
- [ ] 8 ADR có trạng thái `Accepted`
- [ ] Danh sách thư viện được duyệt cập nhật

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Game không engine thiếu sức cho 4 game | Luật 4 game chốt ở spec 006b; game nào cần chuyển động phức tạp thì mở ADR thêm engine |
| GVHD trả lời D4 muộn | Không chặn: Redis chưa dùng trong 6 spec này ngoài rate limit đăng nhập |
