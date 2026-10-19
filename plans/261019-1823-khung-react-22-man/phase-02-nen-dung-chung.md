---
phase: 2
title: "Nen dung chung"
status: pending
priority: P1
dependencies: [1]
effort: "14h"
---

# Phase 2: Nền dùng chung — component, layout, router, API, trang xem trước

## Overview

Mọi màn ở phase 3–5 chỉ **ghép** những thứ phase này làm ra. Làm kỹ ở đây thì 22 màn nhất quán và người sau dễ làm đẹp.
Gồm phần FE của spec 001 T-29 và spec 003 T-13.

## 1 · Cấu trúc thư mục `cnhsk-web/src`

```
src/
├── app/
│   ├── main.tsx · App.tsx
│   ├── routes.tsx              ← khai mọi route, lazy theo feature
│   └── preview/                ← CHỈ DEV: trang /__preview
├── shared/
│   ├── ui/                     ← component dùng ≥ 2 feature (bảng mục 2)
│   ├── layout/                 ← AppShell + 3 biến thể
│   ├── api/                    ← client.ts, useApiQuery.ts, useApiMutation.ts, errors.ts
│   ├── auth/                   ← AuthProvider, RequireRole, useCurrentUser
│   └── lib/                    ← cn() = clsx + tailwind-merge, format ngày/số tiếng Việt
└── features/
    ├── auth/      (SCR-001…006, 079)     ├── account/   (SCR-007, 008)
    ├── admin-users/ (SCR-076, 077)       ├── content-import/ (SCR-068, 069)
    ├── practice/  (component luyện chung)├── vocab/     (SCR-011, 012)
    ├── grammar/   (SCR-017, 018)         ├── pronunciation/ (SCR-016)
    └── hanzi/     (SCR-014, 015)
```

**Mỗi feature** có: `api/` (hàm gọi API), `types.ts`, `pages/`, `components/`. **Mỗi màn** gồm 3 file:

| File | Vai trò | Luật |
|---|---|---|
| `pages/Scr011TopicListPage.tsx` | **Container**: đọc route params, gọi `useApiQuery(getTopics)`, giữ state lọc, chuyển trang | Không có JSX trình bày chi tiết; trả về `<TopicListView … />` |
| `components/TopicListView.tsx` | **View thuần**: nhận `props` gồm `state: 'loading' \| 'empty' \| 'error' \| 'ready'`, `data?`, `error?`, callback (`onRetry`, `onFilterChange`, …) | Không gọi API, không `useEffect` lấy dữ liệu. Đây là file người sau **làm đẹp** |
| `components/TopicListView.preview.ts` | Dữ liệu mẫu cho 4 trạng thái, chỉ dùng ở `/__preview` | Kiểu dữ liệu đúng `types.ts`; nội dung mẫu ghi rõ "mẫu"; **cấm** import từ file thường (lint FE-04) |

Màn nhiều bước (SCR-012, 014, 016, 036) tách view theo bước, mỗi view có file preview riêng.

## 2 · Component `shared/ui` (viết lại từ `components.css`, theo `design.md` §7)

| Component | Lấy hình từ | Biến thể / props chính |
|---|---|---|
| `Button` | `.c-Button` | `variant: primary\|secondary\|outline\|ghost\|danger`, `size: sm\|md`, `loading` (khoá + giữ nhãn), `asChild`-free: dùng `as="a"` cho link |
| `IconButton` | `.c-IconButton` | `label` **bắt buộc** (aria-label), `variant: default\|danger` |
| `Card` | `.c-Card` | `variant: default\|interactive\|selected` (bỏ `locked` — đợt này không khoá) |
| `Section`, `PageHeader` | `.c-Section`, `.c-PageHeader` | `title`, `subtitle`, `actions` |
| `FormField` | `.c-FormField` | `label`, `htmlFor`, `hint`, `error` (aria-describedby), con là `Input`/`Select`/`Textarea`/`PasswordInput` (nút hiện/ẩn) |
| `StatusBadge` | `.c-StatusBadge` | `tone: success\|warning\|danger\|info\|neutral`, luôn icon + chữ |
| `LevelBadge` | `.c-LevelBadge` | `level: 1…9` → "HSK 1"…"HSK 7–9" màu cố định theo cấp |
| `Progress` | `.c-Progress` | `linear` có giá trị chữ; bỏ biến thể `mastery` |
| `StatTile` | `.c-StatTile` | `label`, `value` |
| `DataTable` | `.c-DataTable` | `columns`, `rows`, `rowKey`, `onRowClick`; **< 1024 px tự đổi thành danh sách card** (`design.md` §8) |
| `Pagination` | (mới) | `page`, `size`, `total`, `onChange` — nhãn tiếng Việt |
| `Tabs` | (mới) | dùng cho SCR-017, SCR-012 bước |
| `ConfirmPanel` | `.c-Dialog` | xác nhận **trong trang** (không `window.confirm`): `title`, `message`, `confirmLabel`, `danger`, `onConfirm`, `onCancel`; trên mobile hiện dạng sheet đáy |
| `Toast` + `useToast` | `.c-Toast` | `success\|error\|info`, tự ẩn 4 s, `role="status"` |
| `EmptyState` | `.c-EmptyState` | `title`, `description`, `action?` |
| `ErrorState` | `.c-ErrorState` | `message`, `requestId?` (hiện "Mã lỗi: …"), `onRetry?` |
| `Skeleton` | `.c-Skeleton` | `text\|card\|table`, chỉ hiện sau 300 ms (`design.md` §9) |
| `PageState` | (mới) | Bọc 4 trạng thái: `state`, `loading`, `empty`, `error`, `children` — mọi View dùng để không quên trạng thái nào |
| `HanziText` | `.t-hanzi-*` | `size: lg\|md\|sm`, luôn `font-hanzi`, `lang="zh-CN"` |

Không làm: `MasteryIndicator`, `AIChatPanel`, `QuestionRenderer` 7 dạng (ngoài phạm vi). Component luyện tập ở mục 6.

## 3 · Layout `shared/layout`

| Layout | Dùng cho | Nội dung |
|---|---|---|
| `PublicLayout` | SCR-001…006, 079 | Header: logo + "Đăng nhập"/"Đăng ký" (hoặc tên người dùng khi đã đăng nhập) |
| `LearnerLayout` | SCR-007, 008, 011, 012, 014–018 | Desktop: sidebar 240 px; mobile: bottom nav ≤ 5 mục (`design.md` §8). Mục: Chủ đề · Ngữ pháp · Phát âm · Chữ Hán · Game (link ngoài tới `VITE_GAME_ORIGIN`) + menu tài khoản (Hồ sơ, Đổi mật khẩu, Đăng xuất) |
| `AdminLayout` | SCR-068, 069, 076, 077 | Sidebar theo role: `CONTENT_EDITOR` → "Nhập dữ liệu"; `ADMIN` → "Người dùng". Người nhiều role thấy hợp các mục (`design.md` §5.3) |

Banner "Xác minh email để dùng đầy đủ" trên `LearnerLayout` khi `emailVerified=false` (spec 001 §12.2).

## 4 · API `shared/api`

| File | Nội dung |
|---|---|
| `client.ts` | Đúng spec 001 §12.2: `fetch` + `credentials:'include'`, `X-Client-Type: WEB`, `X-XSRF-TOKEN` từ cookie cho POST/PUT/PATCH/DELETE (thiếu → `GET /api/auth/csrf` một lần), 401 `TOKEN_EXPIRED`/`SESSION_REVOKED` → refresh **một lần** (gộp request song song) rồi gửi lại, refresh lỗi → về `/login?redirect=` |
| `errors.ts` | `ApiError { status, errorCode, message, requestId, details?, extra }` đọc từ body `{error_code, message, request_id, …}` |
| `useApiQuery.ts` | Hook nhỏ (không thư viện): `useApiQuery(fn, deps)` → `{state, data, error, reload}`; `state='empty'` khi `isEmpty(data)` (tham số tuỳ chọn) |
| `useApiMutation.ts` | `{run, pending, error}`; khoá nút khi `pending` (`design.md` §9 "Đang gửi") |

Hàm API từng feature (`features/*/api/*.ts`) **gọi endpoint thật** đúng OpenAPI của spec (`specs/00X-*/contracts/*.yaml`). Kiểu trong `types.ts` **viết tay** khớp tên schema OpenAPI (không thêm thư viện sinh code).
Backend chưa chạy → hàm trả lỗi mạng → màn hiện `ErrorState`. Đó là đúng.

## 5 · Router + quyền `app/routes.tsx`, `shared/auth`

- Khai 22 route ở bảng `plan.md`, lazy theo feature (`React.lazy` + `Suspense` với `Skeleton`).
- `AuthProvider` gọi `GET /api/auth/me` khi tải (spec 001 FR-027).
- `RequireRole roles={[...]}`: chưa đăng nhập → `/login?redirect=<đường hiện tại>`; thiếu role → `/403` (SCR-079). Role đúng theo cột Guard spec §12. **Không** suy quyền LEARNER từ role khác.
- Route lạ → trang 404 đơn giản (không tính là SCR mới) có nút về `/`.

## 6 · Component luyện tập chung `features/practice` (spec 003 T-13)

| Component / hook | Việc |
|---|---|
| `usePracticeSession` | Bắt đầu / khôi phục (`/api/practice/sessions/active`, `GET /sessions/{id}`) / trả lời / kết thúc / bỏ — spec 003 §0.11 |
| `ChoiceQuestion` | Câu chọn 2–6 phương án: prompt (chữ Hán `HanziText`, câu có chỗ trống, gợi ý), phương án, khoá sau khi chọn, hiện đúng/sai + đáp án đúng + giải thích |
| `PinyinInput` | Ô gõ pinyin + gợi ý "gõ số thanh, vd ni3" |
| `AudioButton` | Phát `<audio>` khi bấm, đếm nghe lại (`maxReplays`), nút "Không nghe được" |
| `StudyCard` | Thẻ từ: chữ, pinyin, âm Hán Việt, nghĩa, ví dụ, nút nghe, "Tiếp", "Tôi đã biết từ này" (spec 003 FR-023) |
| `SessionProgress` | "Câu i/n" + thanh tiến độ |
| `SessionSummary` | Điểm, đúng/sai, danh sách câu sai, các nút hành động |

## 7 · Trang xem trước `/__preview` (chỉ dev)

- Chỉ đăng ký khi `import.meta.env.DEV` (dynamic import → không vào bản build).
- `/__preview`: danh sách 22 màn (+ bước con) nhóm theo spec; `/__preview/:viewId/:state` render View với props từ file `.preview.ts` tương ứng, bọc đúng Layout; nút chuyển nhanh 4 trạng thái + ô chọn độ rộng 390 / 768 / 1280.
- Registry `app/preview/registry.ts` liệt kê `{viewId, scr, title, component, previews}` — mỗi màn ở phase 3–5 **phải** đăng ký.

## Tasks

| ID | Task | Phần |
|---|---|---|
| FE-07 | `shared/lib`, 10 component đầu: `Button`, `IconButton`, `Card`, `Section`, `PageHeader`, `FormField` (+ Input/Select/Textarea/PasswordInput), `StatusBadge`, `LevelBadge`, `HanziText`, `StatTile` + test | Mục 2 |
| FE-08 | Component còn lại: `Progress`, `DataTable` (+ chế độ card), `Pagination`, `Tabs`, `ConfirmPanel`, `Toast`, `EmptyState`, `ErrorState`, `Skeleton`, `PageState` + test | Mục 2 |
| FE-09 | `shared/api` 4 file + test (refresh một lần cho 3 request song song; XSRF) | Mục 4 |
| FE-10 | `shared/auth` + `routes.tsx` khung (22 route trỏ trang "đang dựng" tạm, sẽ thay ở phase 3–4) + 404 | Mục 5 |
| FE-11 | 3 layout + banner xác minh email + menu theo role | Mục 3 |
| FE-12 | `/__preview` + registry; đăng ký sẵn gallery component `shared/ui` (mỗi component mọi biến thể) | Mục 7 |
| FE-13 | `features/practice` 7 phần + preview | Mục 6 |

## Success Criteria

- [ ] `/__preview` hiện mọi component với mọi biến thể; đổi độ rộng 390 px không cuộn ngang
- [ ] `DataTable` ở 390 px hiển thị dạng card
- [ ] Không component nào dùng hex; mọi nút có trạng thái focus thấy rõ (`focus` token)
- [ ] Test: `RequireRole` chặn đúng; `client.ts` refresh một lần
- [ ] Build production **không** chứa chuỗi "`__preview`" (kiểm bằng grep trên thư mục build)
