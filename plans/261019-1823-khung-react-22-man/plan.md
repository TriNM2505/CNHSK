---
title: "Khung React 22 man tu HTML mockup - giao Codex"
description: >-
  Dung khung frontend cnhsk-web + cnhsk-game cho 22 man thuoc spec 001-008,
  viet lai tu HTML cnhsk-mockup-79 theo chuan React. Sau do moi nguoi nhan man
  cua spec minh de lam dep va noi tinh nang.
status: pending
priority: P1
branch: "main"
tags: [frontend, react, codex]
blockedBy: []
blocks: []
created: "2026-10-19T11:23:00.000Z"
createdBy: "ck:plan"
source: skill
---

# Khung React 22 màn — giao Codex

## Overview

Codex dựng **khung giao diện** cho 22 màn của spec 001→008 bằng React, lấy bố cục từ HTML `C:\CLHSK\cnhsk-mockup-79\`.
Khung xong thì từng người nhận màn thuộc spec mình: làm đẹp thêm + nối tính năng thật.

**Thay thế một phần** phase 5 của [`261015-2036-79-man-html-sang-react`](../261015-2036-79-man-html-sang-react/phase-05-chuyen-sang-react-cnhsk-web.md):
plan này làm 22 màn trong phạm vi spec; 57 màn còn lại vẫn thuộc phase 5 cũ (làm khi có spec).

## Ba luật Codex phải nhớ

| # | Luật | Vì sao |
|---|---|---|
| 1 | **HTML = bố cục và hình thức. Spec = nội dung và hành vi.** Lệch nhau → theo spec, xoá phần lệch (bảng ở mục dưới) | Mockup vẽ trước khi chốt spec: có mastery, mở khoá, bảng xếp hạng, gói học — đợt này **không có** |
| 2 | **Viết lại bằng React, không chép HTML sang JSX, không dùng lại `components.css`/`pages.css`** | `design.md` §5.5; stack chốt Tailwind. Class `c-Button…` thành component `Button` + Tailwind |
| 3 | **Không dữ liệu cứng trong trang.** Trang gọi hàm API thật; dữ liệu mẫu chỉ ở file `*.preview.ts` cho trang xem trước ở dev | `development-rules`: không dữ liệu giả trong sản phẩm. Người nối tính năng chỉ sửa hàm API, không sửa trang |

## Phạm vi — 22 màn

| App | Spec | Màn | Route |
|---|---|---|---|
| `cnhsk-web` | 001 | SCR-001 Landing (phần tài khoản) | `/` |
| | 001 | SCR-002 Sign In · 003 Register · 004 Verify Email · 005 Forgot · 006 Reset | `/login` · `/register` · `/verify-email` · `/forgot-password` · `/reset-password` |
| | 001 | SCR-007 Profile · 008 Change Password | `/me/profile` · `/me/password` |
| | 001 | SCR-076 User List · 077 User Detail | `/admin/users` · `/admin/users/:id` |
| | 001 | SCR-079 Access Denied | `/403` |
| | 002 | SCR-068 Import · 069 Import Report | `/content/imports` · `/content/imports/:id` |
| | 003 | SCR-011 Topic List · 012 Topic Study | `/learn/topics` · `/learn/topics/:id/*` |
| | 004 | SCR-017 Grammar List · 018 Grammar Lesson | `/learn/grammar` · `/learn/grammar/:id` |
| | 005 | SCR-016 Pronunciation | `/learn/pronunciation` · `/learn/pronunciation/stages/:stageIndex` |
| | 006 | SCR-015 Recognition · 014 Writing | `/learn/characters/recognition` · `/learn/characters/writing[/:lexemeId \| /challenge]` |
| `cnhsk-game` | 007/008 | SCR-035 Game Hub · 036 Game Session | `/` · `/play/:code` |

Route, field, nhãn, trạng thái: lấy từ §12 của spec tương ứng + `docs/reference/screen-fields.md`.

## Chỗ mockup lệch spec — Codex **bỏ / đổi** theo bảng này

| Màn | Trong HTML | Làm theo spec |
|---|---|---|
| Mọi màn | Thanh `c-DevBar`; menu có link tới màn ngoài phạm vi (Trang chính 010, Ôn tập, Thi thử, Tra cứu, Tiến độ, Cộng đồng) | Bỏ DevBar. Menu **chỉ** có mục đã có route (bảng phạm vi) |
| Mọi màn | "Gói học", "Học phí và gói", "mức thành thạo 0,xx", "ôn lại sau N ngày" | Bỏ hết (gói = spec sau; mastery/FSRS = cây tri thức) |
| SCR-001 | Khối giới thiệu mastery, "Bảng xếp hạng" | Giữ phần giới thiệu chung + nút Đăng nhập/Đăng ký + "Khu vực của bạn" (spec 001 FR-090); bỏ số liệu mastery, bảng xếp hạng |
| SCR-002 | Câu quảng bá mastery | Đổi câu giới thiệu trung tính, không hứa mastery |
| SCR-004 | Nút "Bỏ qua, vào học" | Giữ (FR-010: chưa xác thực vẫn học được) |
| SCR-007 | "Gói học", "Cài đặt nhắc học", "Mục tiêu HSK" | Chỉ 3 trường: tên hiển thị, `avatarUrl`, `hskLevel` (FR-051). Ẩn nhắc học |
| SCR-011 | "mở khóa khi đạt 90%", ô "Đã thành thạo"/"Còn khóa", icon khoá | Trạng thái **Chưa học / Đang học / Hoàn thành**, không khoá (spec 003 FR-016, FR-041) |
| SCR-012 | "Đạt 90% thì chủ đề sau mở khóa", "Mức thành thạo của 妹妹" | 4 bước Học từ mới · Luyện nhận diện · Luyện nghe · Kiểm tra (FR-042); bỏ mastery |
| SCR-014 | "Mức thành thạo…", "Bỏ qua chữ này" | Kết quả = "Đã ghi nhận" / "Chưa tính (luyện lại trong 10 phút)" (spec 006 §12); 4 chế độ STROKE · RECALL · DICTATION · Thử thách |
| SCR-015 | "Mức thành thạo tăng từ… ôn lại sau 6 ngày" | Chỉ đúng/sai + nghĩa đúng |
| SCR-016 | Nút "Bỏ qua câu này" | **Bỏ** (spec 005 FR-018, không cho bỏ qua) |
| SCR-017 | "mức thành thạo · ôn lại sau N ngày", nhãn "Thành thạo" | Nhãn "Đã học" + điểm cao nhất; 7 tab HSK 1…6, 7–9 (spec 004 FR-004) |
| SCR-018 | "mức thành thạo hiện tại", lần ôn tiếp theo | Bỏ (FR-008); giữ "Bỏ qua bài tập" |
| SCR-035 | "Chơi vẫn tăng mức thành thạo", nút "Bảng xếp hạng trò chơi" | Bỏ cả hai (spec 007 FR-028) |
| SCR-036 | "Tạm dừng", "bảng xếp hạng", "Mức thành thạo đã cập nhật" | Bỏ (spec 007 Q2); kết quả = điểm, đúng/sai/bỏ lỡ, độ chính xác, thời gian, "Kỷ lục mới!" |
| SCR-068 | File `.xlsx`, loại "Đề thi", "Điều kiện mở khóa" trong menu | CSV, **7 mẫu** spec 002 §4.0; không có đề thi; bỏ menu "Điều kiện mở khóa" |
| SCR-069 | "Bỏ qua trùng" | Bỏ — nhập lại là upsert (spec 002 FR-020) |
| SCR-076 | Role `USER`, trạng thái "Tạm khoá" | Role theo 6 mã mới (`LEARNER`…); trạng thái `ACTIVE`/`UNVERIFIED`/`BANNED` (spec 001 FR-097) |
| SCR-079 | "Tính năng này cần gói hoặc điểm", "Thiếu gói" | Chỉ thông báo thiếu quyền (gói thuộc spec sau) |

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Khởi tạo hai app](./phase-01-khoi-tao-hai-app.md) | Pending |
| 2 | [Nền dùng chung](./phase-02-nen-dung-chung.md) | Pending |
| 3 | [Màn tài khoản, quản trị, nhập dữ liệu](./phase-03-man-tai-khoan-quan-tri-nhap-lieu.md) | Pending |
| 4 | [Màn học tập](./phase-04-man-hoc-tap.md) | Pending |
| 5 | [App game](./phase-05-app-game.md) | Pending |
| 6 | [Kiểm và bàn giao](./phase-06-kiem-va-ban-giao.md) | Pending |

Thứ tự: **1 → 2 → (3, 4, 5 song song) → 6**. Phase 2 là chỗ quyết định chất lượng — mọi màn dùng lại nó.

## Cách giao Codex

Mỗi task trong phase = một nhánh `agent/fe-base-<task>` = một PR. Prompt mẫu:

```
Đọc theo thứ tự: .specify/memory/constitution.md → AGENTS.md → docs/reference/design.md (§5.3, §5.4, §6–§12)
→ plans/261019-1823-khung-react-22-man/plan.md (đặc biệt "Ba luật" và bảng "Chỗ mockup lệch spec").
Thứ tự ưu tiên khi mâu thuẫn: constitution > AGENTS.md > spec > design.md > plan này > HTML mockup.
Làm task <ID> trong plans/261019-1823-khung-react-22-man/phase-0X-*.md.
Nguồn bố cục: C:\CLHSK\cnhsk-mockup-79\screens\<file>.html. Nguồn nội dung/hành vi: specs/<spec>/spec.md §3, §12.
Không thêm thư viện ngoài danh sách phase 1. Không chép class c-*/u-*.
Xong khi: npm run lint && npm run typecheck && npm test xanh, màn mở được ở /__preview với đủ 4 trạng thái.
Viết shadow plan vào mô tả PR. Commit: feat(web): <SCR-xxx> <mô tả>. Không nhắc AI.
```

## Sau khi khung xong — chia người

Mỗi người nhận màn của spec mình (bảng phạm vi). Việc của họ: làm đẹp thêm + nối API thật (sửa `features/<f>/api/*.ts`)
+ test theo §14 spec. **Không** sửa `shared/` khi chưa báo người giữ phase 2.

## Acceptance criteria

- [ ] 2 app chạy (`5173`, `5174`), đúng stack `AGENTS.md` §3, không thư viện ngoài danh sách
- [ ] 22 màn có route, guard đúng role, đủ 4 trạng thái (đang tải, rỗng, lỗi, có dữ liệu) xem được ở `/__preview`
- [ ] Không còn nội dung trong bảng "Chỗ mockup lệch spec"
- [ ] Không hex màu trong component; không class `c-*`/`u-*`; không dữ liệu mẫu ngoài `*.preview.ts`
- [ ] Màn 390 px không cuộn ngang
- [ ] Lint, typecheck, test xanh

## Unresolved questions

1. Thư viện MSW (giả API có dữ liệu) — **không dùng**; thay bằng trang xem trước `/__preview` (phase 2). Nhóm muốn thêm MSW thì duyệt riêng.
2. `hanzi-writer` (spec 006) chưa được duyệt → phase 4 chỉ dựng khung ô viết (chỗ trống đúng kích thước), không cài thư viện.
