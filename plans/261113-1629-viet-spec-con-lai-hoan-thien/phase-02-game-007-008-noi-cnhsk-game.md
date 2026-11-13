---
phase: 2
title: "Game 007/008 nối cnhsk-game"
status: pending
priority: P1
dependencies: [1]
effort: "spec 2 · BE 13,5 · FE 5 ngày công (gồm 2 game thêm)"
owner: "Đạt"
---

# Phase 2: Game 007/008 — cập nhật spec và nối web game

## Overview
Không viết spec mới. Việc là: (a) đưa nhánh `feat/dat-six-games` về trạng thái build được, (b) **duyệt 008** (đang DRAFT), (c) cập nhật 007 cho khớp app `cnhsk-game/` thật, (d) chốt 2 game ngoài spec. SCR-035 Game Hub, SCR-036 Game Session nằm trong `cnhsk-game`; SCR-047 do 017 làm.

## Context links
- `specs/007-game-platform/` (APPROVED, 11 task, contracts `openapi-games.yaml`), `specs/008-vocab-games/` (DRAFT, 8 task)
- Nhánh `origin/feat/dat-six-games` (afed013, base 199c4a8, sau main 322 commit): `cnhsk-game/README.md`, `BACKEND_DEPENDENCIES.md`
- UC-087, UC-088, UC-089 (use-cases-06), UC-005 (cookie trang game), UC-043 (mastery sau game)
- LESSON-001/002/003 trong `CLAUDE.md`; `PracticeSource.GAME_ROUND` **đã có trên main** → kiểm phần nào của 007/T-01 đã xong

## Hiện trạng đã kiểm (2026-11-13)
- 6 game: Mưa Chữ, Ghép Pinyin, Ghép Bộ Thủ, Bắt Chữ (theo 007/008), Thần Bút, Phi Đao (ngoài spec).
- Checkout sạch **không build**: `src/data/{words,levels,strokes}.ts` bị dòng `data/` của `.gitignore` gốc (dòng 74) chặn → typecheck lỗi, 2/7 file test fail (76 test còn lại pass).
- Code dồn dòng (dài nhất 4.283 ký tự, 25 dòng > 200); không ESLint.
- Client tự chấm, tự lấy từ vựng — ngược 007 (server phát ván, chấm từng câu, `finish` trả điểm đã xác nhận).
- Chưa đăng nhập chung, chưa lưu kết quả, chưa thống kê.

## Requirements
- Functional: 4 game 007/008 chơi với nội dung server phát; điểm chỉ từ server; kết quả ghi `attempts` kind `GAME` theo H6; cập nhật mastery theo 011 (UC-043).
- Non-functional: cookie dùng chung (ADR-003; dev `localhost` khác cổng dùng chung cookie), không tin client (H9), dữ liệu tự soạn HSK 1–3.

## Implementation Steps (viết spec)
1. **Sửa nhánh (G0 của Đạt, trước khi viết):** đổi `.gitignore` thành ngoại lệ hẹp `!cnhsk-game/src/data/`; xác nhận 3 file là dữ liệu tự soạn; tách dòng dài, thêm ESLint/Prettier như `cnhsk-web`; base mới = main; Draft PR. Kiểm: checkout sạch → `npm ci && npm run typecheck && npm test && npm run build` xanh.
2. **Duyệt 008:** rà luật 4 game với engine thật đã có (`engine/pinyin.ts` chấm pinyin…); ví dụ trong spec tự soạn; ghi quyết định vào `questions.md`; chuyển `APPROVED 1.0.0`.
3. **Cập nhật 007 (v1.1):**
   - §0 phạm vi: app tách `cnhsk-game/` (React/Vite cổng 5174), đường dẫn `/play/*`.
   - §10 móc nối: ghi hợp đồng H6 (cột `attempts` nào ghi gì) để 017 đọc.
   - Đăng nhập: trang game gọi `/api/auth/me` bằng cookie; chưa đăng nhập → chuyển về `/login` web chính kèm `returnTo`.
   - Bỏ phần WebView/mobile (H1).
   - Rà 11 task: đánh dấu phần đã có trên main (`GAME_ROUND`), tách task FE nối API (thay dữ liệu cục bộ bằng `rounds`/`answers`/`finish`, giữ hình ảnh + engine vẽ).
4. **Thần Bút, Phi Đao — leader chốt 13/11: cả 6 game như nhau** (lưu điểm, xếp hạng, mastery). Viết **phụ lục 008** thêm 2 `GameCode` (`BRUSH_STROKE`, `PINYIN_DART` — tên chốt khi viết) + luật chấm phía server: Thần Bút chấm nét bằng dữ liệu nét server phát (dùng lại cách 006 kiểm `WRITING_RESULT`, client gửi đường vẽ/kết quả nét, server kiểm hợp lệ + trần điểm), Phi Đao chấm pinyin ở server (chuyển `engine/pinyin.ts` thành luật server, giữ bộ test làm ca kiểm). Cập nhật `GameRegistry` "kiểm đủ 4" → đủ 6; `openapi-games.yaml` thêm 2 mã. (+2 ngày công)
5. Cập nhật `tasks.md` 007/008: điểm khoá ⚿K3 = task ghi `attempts` GAME (mở khoá dữ liệu thật cho 017).

## Related Code Files (khi code)
- Create: `src/main/java/com/cnhsk/community/game/**`, `specs/008-vocab-games/questions.md`
- Modify: `.gitignore`, `cnhsk-game/**`, `specs/007-game-platform/{spec.md,tasks.md,contracts/openapi-games.yaml}`, `specs/008-vocab-games/{spec.md,tasks.md}`, `SecurityConfig.java` (H8)

## Success Criteria
- [ ] Nhánh game build xanh từ checkout sạch, Draft PR mở
- [ ] 008 `APPROVED 1.0.0`; 007 `1.1.0` khớp app thật, không còn mobile
- [ ] 007 §10 ghi hợp đồng `attempts` GAME đúng H6
- [ ] Phụ lục 008 có luật server cho Thần Bút, Phi Đao; 6 game cùng ghi `attempts` GAME và vào bảng xếp hạng 017

## Risk Assessment
- Merge nhánh cũ 322 commit đụng `README.md`/`package.json` gốc → rebase sớm, chỉ giữ file trong `cnhsk-game/` + `.gitignore`.
- Viết lại lớp dữ liệu client dễ làm hỏng game → giữ engine + test 76 cái, chỉ thay nguồn dữ liệu/chấm.
- Không xác minh được giao diện tới khi bước 1 xong → bước 1 là việc đầu tiên.
