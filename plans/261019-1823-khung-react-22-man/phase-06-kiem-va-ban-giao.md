---
phase: 6
title: "Kiem va ban giao"
status: pending
priority: P1
dependencies: [3, 4, 5]
effort: "3h"
---

# Phase 6: Kiểm và bàn giao cho từng người

## Tasks

| ID | Việc |
|---|---|
| FE-35 | Rà toàn bộ: `npm run lint && npm run typecheck && npm test && npm run build` hai app; grep thư mục build không có `__preview`, `.preview`; grep `src/` không có hex màu, class `c-`/`u-`/`t-`, chữ trong bảng lệch spec (`thành thạo`, `Gói học`, `Bảng xếp hạng`, `Tạm dừng`, `.xlsx`) |
| FE-36 | Kiểm tay `/__preview` ở 390 / 768 / 1280 px cho 22 màn — chụp ảnh từng màn trạng thái "có dữ liệu" vào mô tả PR |
| FE-37 | Viết `cnhsk-web/docs/frontend-guide.md` ngắn (≤ 150 dòng): cấu trúc thư mục, mẫu 3 file mỗi màn, cách thêm preview, cách nối API thật (sửa `features/*/api`), luật không sửa `shared/` khi chưa báo |
| FE-38 | Bảng giao việc trong `plan.md` mục "Sau khi khung xong": điền tên người cho từng spec |

## Success Criteria

- [ ] Mọi kiểm FE-35 xanh
- [ ] Mỗi thành viên mở được màn của mình ở `/__preview` và biết file nào cần sửa
