---
phase: 1
title: "Khoi tao hai app"
status: pending
priority: P1
dependencies: []
effort: "4h"
---

# Phase 1: Khởi tạo `cnhsk-web` và `cnhsk-game`

## Overview

Tạo hai app Vite React TypeScript trong repo `learning-service/` (cùng cấp `src/`), cấu hình Tailwind từ token, lint, typecheck, test.
Trùng spec 001 T-28 và spec 007 T-08 — làm xong phase này thì hai task đó coi như xong.

## Requirements

**Phiên bản (ghim chính xác, không `^`/`~`)** — `AGENTS.md` §3:

| Gói | Bản |
|---|---|
| `react`, `react-dom` | 18.3.1 |
| `react-router-dom` | 6.28.x (bản cụ thể mới nhất của 6.28) |
| `typescript` | 5.7.x |
| `vite` | 5.4.x · `@vitejs/plugin-react` 4.3.x |
| `tailwindcss` | 3.4.x · `postcss`, `autoprefixer` (bắt buộc cho Tailwind 3) |
| `lucide-react`, `clsx`, `tailwind-merge` | bản ổn định hiện hành |

**Dev tool** (thuộc spec 001 T-28, duyệt cùng spec 001): `eslint` 9 + `typescript-eslint` + `eslint-plugin-react-hooks` + `eslint-plugin-react-refresh`,
`prettier`, `vitest` 2.1.x, `jsdom`, `@testing-library/react` 16, `@testing-library/jest-dom`, `@testing-library/user-event`.
**Không thêm gì khác.**

## Tasks

| ID | Task | File |
|---|---|---|
| FE-01 | `cnhsk-web`: Vite react-ts, cổng 5173, alias `@/` → `src/`, `strict: true`, `noUncheckedIndexedAccess: true`; script `dev`, `build`, `preview`, `lint`, `typecheck` (`tsc -b --noEmit`), `test` (`vitest run`) | `cnhsk-web/**` |
| FE-02 | `tailwind.preset.cjs` tại `cnhsk-web/` — chuyển **toàn bộ** `C:\CLHSK\cnhsk-mockup-79\assets\tokens.css` (= `design.md` §6): màu (`brand`, `accent`, `canvas`, `surface`, `surface-subtle`, `text-primary/secondary/muted`, `border`, `success/warning/danger/info` + `-bg`, `focus`), font `sans`/`hanzi`, cỡ chữ `display/h1/h2/h3/body/body-sm/label/caption/hanzi-lg/hanzi-md` (kèm line-height, weight), spacing thang 4 px, radius `sm/md/lg/pill`, shadow `sm/md/overlay`, `maxWidth.content=1280px`, `maxWidth.reading=720px`, `zIndex` 5 mức, breakpoint `sm 640 / lg 1024 / xl 1440`. `tailwind.config.ts` dùng preset này | `cnhsk-web/tailwind.preset.cjs`, `tailwind.config.ts`, `src/styles/index.css` |
| FE-03 | `index.css`: `@tailwind base/components/utilities`; nạp font Google `Be Vietnam Pro` (400/500/600/700) và `Noto Serif SC` (400/500/700) trong `index.html`; reset tối thiểu như `tokens.css` (nền `canvas`, focus ring `focus`, `[hidden]`, `prefers-reduced-motion`) | `index.html`, `src/styles/index.css` |
| FE-04 | ESLint: cấm hex màu trong `className` (`no-restricted-syntax` regex `#[0-9a-fA-F]{3,8}` trong JSX attribute), cấm import `*.preview` từ file không phải preview (`no-restricted-imports`), cấm class `c-*`/`u-*`/`t-*` (regex) | `cnhsk-web/eslint.config.js` |
| FE-05 | `cnhsk-game`: như FE-01 (cổng **5174**); `tailwind.config.ts` dùng **cùng** `../cnhsk-web/tailwind.preset.cjs`; alias `@ui` → `../cnhsk-web/src/shared/ui` (+ `server.fs.allow: ['..']`, `content` Tailwind gồm đường dẫn đó) để dùng chung component | `cnhsk-game/**` |
| FE-06 | `.env.example` mỗi app: `cnhsk-web`: `VITE_API_BASE_URL=http://localhost:8080`, `VITE_GAME_ORIGIN=http://localhost:5174`; `cnhsk-game`: `VITE_API_BASE_URL`, `VITE_WEB_ORIGIN=http://localhost:5173`. `.gitignore` thêm `node_modules`, thư mục build của Vite. README mỗi app: 3 lệnh chạy | `.env.example`, `.gitignore`, `README.md` |

## Success Criteria

- [ ] `npm run dev` mở được cả hai app; `npm run lint && npm run typecheck && npm test && npm run build` xanh
- [ ] Một component thử dùng `bg-brand-600 text-h1 font-hanzi` ra đúng màu/cỡ của `tokens.css`
- [ ] Viết `className="text-[#AF0000]"` → lint báo lỗi
- [ ] `package.json` chỉ có các gói ở bảng trên, đều ghim chính xác

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Alias `@ui` sang app khác làm build `cnhsk-game` lỗi | Test `npm run build` trong FE-05; nếu không ổn → chuyển `shared/ui` thành thư mục `frontend-ui/` ở gốc repo, cả hai app alias tới (đổi một dòng) |
