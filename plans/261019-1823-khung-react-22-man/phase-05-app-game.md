---
phase: 5
title: "App game"
status: pending
priority: P1
dependencies: [2]
effort: "8h"
---

# Phase 5: App `cnhsk-game` — SCR-035, SCR-036

## Overview

Cấu trúc theo spec 007 FR-023: `src/{app, games/<code-kebab>/, shared/{api, auth, ui}}`. Component chung lấy qua alias `@ui` (phase 1 FE-05).
Bố cục: `scr-035-game-hub.html`, `scr-036-game-session.html`. Bỏ: "tăng mức thành thạo", "Bảng xếp hạng", "Tạm dừng".

## Tasks

| ID | Việc | Spec | Ghi chú |
|---|---|---|---|
| FE-31 | `shared/api/client.ts` game: `credentials:'include'`, Bearer khi có `window.__CNHSK_TOKEN__`, XSRF khi không có token, refresh/`TOKEN_EXPIRED`; `useCurrentUser`; cầu nối WebView (`postMessage` 4 loại + nhận `TOKEN_REFRESHED`); `public/webview-harness.html` | 007 FR-024…026, FR-030 | Dùng lại `useApiQuery` bằng cách copy nhỏ hoặc alias `@web/shared/api/useApiQuery` |
| FE-32 | SCR-035 Game Hub | 007 FR-028, 008 §0.2 | 4 thẻ (tên, mô tả, kỹ năng, thời lượng, "Cách chơi" mở rộng, thành tích khi đăng nhập), nút "Chơi" (khoá "Chưa đủ dữ liệu"), chọn cấp HSK tuỳ chọn, khách → "Đăng nhập để chơi" |
| FE-33 | `GameShell` (SCR-036 khung) | 007 FR-029, FR-031 | 4 View: sẵn sàng · đếm ngược 3 s · đang chơi (đồng hồ, điểm, nút Thoát + `ConfirmPanel`, vùng `children` cho game) · kết quả (điểm, đúng/sai/bỏ lỡ, độ chính xác, thời gian, "Kỷ lục mới!", Chơi lại, Về danh sách). Hàng đợi gửi lượt |
| FE-34 | Khung 4 game | 008 FR-005, FR-012, FR-018, FR-025 | Mỗi game một thư mục `games/char-rain`, `pinyin-match`, `radical-match`, `char-catch`: View tĩnh nhận `items` + `uiConfig` + `submitAnswer` — bố cục đúng spec 008 (4 nút đáy / 2 cột ghép / chữ to + 4 bộ thủ / lưới 3×2), **chưa** làm hoạt ảnh rơi và đếm giờ từng lượt (để người làm spec 008) |

## Success Criteria

- [ ] `cnhsk-game` chạy 5174; SCR-035, SCR-036 (4 View shell + 4 game) xem được ở `/__preview` của app game (làm như phase 2 mục 7)
- [ ] Mở `webview-harness.html`, tiêm token giả → client gửi `Authorization`
- [ ] Không có nút bảng xếp hạng, không có tạm dừng
