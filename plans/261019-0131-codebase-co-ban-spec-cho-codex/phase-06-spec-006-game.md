---
phase: 6
title: "Spec 006 game"
status: pending
priority: P2
effort: "3 ngay (006a 1.5 · 006b 1.5)"
dependencies: [3]
---

<!-- Updated: Validation Session 1 - app riêng cnhsk-game (V1), 4 game (V2), React + CSS không engine (V6), tách 006a/006b -->

# Phase 6: Spec 006 — game từ vựng

## Overview

App riêng **`cnhsk-game`** tại `game.cnhsk.com`, **4 game** như use case, React + CSS (không engine).
Tách hai spec để khoá và giao Codex được sớm phần khung:

| Spec | Nội dung |
|---|---|
| **006a Khung game** | App `cnhsk-game`, đăng nhập chung, Game Hub (SCR-035), khung Game Session (SCR-036), API tạo/nộp ván, chấm lại server, chống gian lận, ghi kết quả qua `LearningResultRecorder` |
| **006b Luật 4 game** | Luật từng game: bộ từ, số lượt, thời gian, cách chấm từng lượt, điểm, hết giờ, giao diện chơi |

Không cộng mastery, không bảng xếp hạng (V2).

## Requirements — 006a

| Nhóm | Nội dung |
|---|---|
| App | `cnhsk-game` cùng stack `cnhsk-web` (React 18.3, Vite 5, TS 5.7, Tailwind 3.4); cấu trúc theo `AGENTS.md` §15; deploy riêng |
| Đăng nhập | Cookie chung tên miền cha (spec 001 UC-005); chưa đăng nhập → chuyển về `cnhsk-web` đăng nhập rồi quay lại; `kien-truc.md` §2.2 (trang game vừa tải xong, cookie `HttpOnly`) |
| Mobile | WebView tải chính `cnhsk-game`, app tiêm token vào header (`kien-truc.md` §2.3) — đặc tả hợp đồng; code mobile ngoài phạm vi |
| Luồng ván | UC-087, UC-088 («include»): `POST /api/games/sessions` (server tạo ván, chọn bộ từ, trả `sessionId`, hạn) → client chơi → `POST /api/games/sessions/{id}/submit` (danh sách lượt) → server **chấm lại từng lượt**, kiểm thời gian, giới hạn điểm/ngày → `LearningResultRecorder.record(kind=GAME, game_code)` |
| Chống gian lận | Client không gửi điểm cuối (`BUS-09`); điểm tính từ lượt; `IMPOSSIBLE_DURATION`; mỗi `sessionId` nộp một lần; ván hết hạn bị từ chối; giới hạn điểm/ngày (use case 06 dòng ~146); `kien-truc.md` §5 |
| Hook cây tri thức | UC-088 «include» UC-042 → không làm đợt này; mastery tính lại sau từ `attempt_items` |

## Requirements — 006b

- 4 game theo UC-087 (Game Box) và `feature-tree.md` 5.6, 5.7 (game gõ pinyin).
- Mỗi game: tên, `game_code`, mô tả cách chơi, nguồn bộ từ (theo cấp/chủ đề đã nhập qua spec 008), số lượt, thời gian mỗi lượt và cả ván, cách chấm một lượt (đúng/sai/điểm phần), công thức điểm ván, trường hợp hết giờ, bỏ dở, mất mạng.
- Giao diện chơi từng game (SCR-036 biến thể) lấy mẫu từ `cnhsk-mockup-79/screens/`.
- Chỗ use case không nói → ghi câu hỏi, **cả nhóm chốt** trước khi khoá.

## Related Code Files

- Create: `specs/006a-game-platform/`, `specs/006b-vocab-games/` (mỗi thư mục: `spec.md`, `plan.md`, `tasks.md`, `checklists/`)
- Modify: `docs/api/openapi.yaml`; `AGENTS.md` §15 (cấu trúc `cnhsk-game`)
- Codex sẽ tạo: `cnhsk-game/**`, `src/main/java/com/cnhsk/community/game/**`

## Implementation Steps

1. 006a: `/speckit-specify` từ UC-005, UC-087, UC-088 + `screen-fields.md` SCR-035/036 + `kien-truc.md` §2, §4, §5.
2. 006a mục 12–15, đặc biệt test chống gian lận (gửi điểm giả, nộp hai lần, thời gian 0 ms, ván hết hạn, session của người khác).
3. 006b: liệt kê 4 game từ use case; viết luật đủ để Codex cài đặt không hỏi; nhóm chốt chỗ thiếu.
4. Cả nhóm duyệt → phase 7.

## Success Criteria

- [ ] 006a: ≥ 6 acceptance test chống gian lận; đăng nhập chung chạy được giữa `cnhsk-web` và `cnhsk-game` (theo spec 001)
- [ ] 006b: luật 4 game không còn câu hỏi mở; mỗi game có test chấm lượt và tính điểm ván
- [ ] Không phụ thuộc bảng xếp hạng, mastery, engine game

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| 4 game gấp đôi khối lượng | Tách 006a khoá trước; 006b có thể giao Codex từng game một |
| Đăng nhập chung khác tên miền khó test cục bộ | Spec 000 đặc tả dev dùng `*.cnhsk.localhost` hoặc proxy Vite chung cổng |
| Game cần chuyển động phức tạp hơn React + CSS | Mở ADR thêm engine cho riêng game đó, không chặn 3 game còn lại |
