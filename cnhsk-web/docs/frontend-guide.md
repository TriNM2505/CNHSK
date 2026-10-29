# Hướng dẫn frontend `cnhsk-web`

Cho thành viên nhận màn của spec mình. Luật gốc: `AGENTS.md` → spec → `docs/reference/design.md`.

## Chạy

```powershell
cd cnhsk-web
npm ci
npm run dev        # http://localhost:5173 — /api được proxy tới backend :8080
npm run lint; npm run typecheck; npm test; npm run build   # phải xanh trước khi commit
```

Xem giao diện **không cần backend**: mở `http://localhost:5173/__preview` (chỉ có ở `npm run dev`, không vào bản build).

## Cấu trúc

```
src/
├── app/          routeAccess.ts (route + quyền) · screenRegistry.ts · preview/ (trang /__preview)
├── shared/       api/ (apiRequest, useApiQuery…) · auth/ · layout/ · ui/ (Button, Card, Select…) · lib/
└── features/<f>/ mỗi feature một thư mục — chỉ sửa feature của spec mình
```

Một feature:

| File | Vai trò |
|---|---|
| `types.ts` | Kiểu dữ liệu, đặt tên trường **đúng OpenAPI** của spec |
| `api/*.ts` | Hàm gọi API qua `apiRequest` — chỗ duy nhất biết URL |
| `components/*View.tsx` | Giao diện thuần: nhận props, không gọi API, không đọc router |
| `components/*.preview.ts` | Dữ liệu mẫu cho `/__preview` — **chỉ** ở đây được có dữ liệu giả |
| `pages/ScrNNN*Page.tsx` | Trang: đọc URL, gọi API (`useApiQuery`, `usePracticeSession`), truyền props cho View |
| `screens.ts` | Gắn mã màn `SCR-NNN` với trang (lazy import) |
| `catalog.preview.ts` | Liệt kê preview của feature |
| `*.test.ts(x)` | Vitest + Testing Library, đặt cạnh file được test |

Mẫu đầy đủ để chép cách làm: `features/admin-users` (danh sách + chi tiết), `features/pronunciation` (lượt luyện).

## Thêm một màn

1. Route đã có trong `app/routeAccess.ts` (mã `SCR-NNN`, quyền, layout). Chưa có → báo leader trước khi thêm.
2. Viết `types.ts` + `api/*.ts` theo `specs/<spec>/contracts/*.yaml`.
3. Viết `components/<Ten>View.tsx` với đủ trạng thái: đang tải · rỗng · lỗi · có dữ liệu (`PageState`, `Skeleton`, `EmptyState`, `ErrorState`).
4. Viết `components/<Ten>View.preview.ts` cho từng trạng thái, thêm vào `catalog.preview.ts`.
5. Viết `pages/ScrNNN…Page.tsx`, đăng ký trong `screens.ts`.
6. Test: hàm API (đúng URL, body) và trang (giả lập `../api/*` bằng `vi.mock`).

Feature mới (thư mục mới): thêm `screens` vào `app/screenRegistry.ts` và `previews` vào `app/preview/registry.ts` — chỉ thêm dòng, báo leader.

## Nối API thật

Trang chỉ gọi hàm trong `features/<f>/api`. Khi backend xong: kiểm hàm API khớp OpenAPI, chạy thử với backend thật
(`npm run dev` + backend :8080), sửa **hàm API**, không sửa trang. Lỗi luôn là `ApiError` (`errorCode`, `message`, `requestId`).

Lượt luyện (spec 003 §0): dùng `usePracticeSession` và component trong `features/practice`
(`ChoiceQuestion`, `AudioButton`, `SessionProgress`, `SessionSummary`) — không tự viết lại.

## Luật

- Màu, chữ, khoảng cách chỉ dùng token Tailwind (`text-h2`, `bg-surface`, `border-line`…) — **không** mã hex, không CSS riêng.
- Chữ Hán luôn qua `HanziText` hoặc `font-hanzi` + `lang="zh-CN"`.
- Cấp HSK hiển thị: `SUPPORTED_HSK_LEVELS` (HSK 1–3) từ `shared/ui`; API vẫn nhận 1–9.
- Nội dung người dùng nhập chỉ render dạng text — không `dangerouslySetInnerHTML`.
- Xác nhận thao tác (xoá, nộp, kết thúc) dùng `ConfirmPanel` trong trang — không `window.confirm`.
- Không sửa `shared/`, `app/` khi chưa báo leader. Không thêm thư viện khi chưa được duyệt.
- Màn 390 px không được cuộn ngang; nút bấm cao tối thiểu `min-h-touch` (44 px).
