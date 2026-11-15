# CNHSK Game Box

App game riêng cho `game.cnhsk.com`, cùng stack và design token với web chính.

## Chạy local

Mở hai terminal từ gốc repository:

```powershell
# Terminal 1: web chính
cd cnhsk-web
npm ci
npm run dev

# Terminal 2: Game Box
cd cnhsk-game
npm ci
npm run dev
```

Web chính dùng cổng 5173, Game Box dùng 5174 (`strictPort`, không tự chuyển cổng).
Mở `http://localhost:5174`. Hai app local cùng host chia sẻ cookie theo host, không theo cổng.
Game Box dev chỉ bind loopback; backend local, khi cần ở task sau, dùng cổng 8080.
Không cần backend để xem khung ba route hiện tại.

| Route          | Màn     | Phạm vi hiện tại          |
| -------------- | ------- | ------------------------- |
| `/`            | SCR-035 | Khung Game Hub            |
| `/play/:code`  | SCR-036 | Khung ván chơi, giữ param |
| Các đường khác | 404     | Có đường về Hub           |

Trang ván chơi hiện chưa tạo lượt hay gọi API. Danh mục game, auth/client và GameShell được nối ở các task tiếp theo.
Production hosting cần SPA fallback về `index.html` khi tải lại deep link.

## Biến môi trường

Chỉ khai tên biến ở đây, không lưu secret:

- `VITE_API_BASE_URL`: API base cho client ở task tiếp theo; bỏ trống để dùng `/api` qua proxy local 8080.
- `VITE_WEB_ORIGIN`: origin web chính cho luồng đăng nhập ở task tiếp theo; local là `http://localhost:5173`.

Biến Vite có thể xuất hiện trong bundle; tuyệt đối không đặt token hoặc API key vào đó.
T-08 không đọc token, không làm auth/API/WebView, không lưu trạng thái vào browser storage.

## Quyết định đã duyệt

- Base `b88eb70`, nhánh `spec/007-t08`, allowlist 25 file, toàn bộ trong `cnhsk-game/`.
- React 18.3.1, Vite 5.4.21, TypeScript 5.7.3, Tailwind 3.4.19, Router 6.28.2; công cụ test/lint dùng phiên bản đang có của web.
- Q-T08-01: lockfile mới của riêng Game Box được commit để `npm ci` tái lập được; không đổi lockfile web hay tạo lockfile gốc.
- Q-T08-02: quyết định/gate ghi ở README và PR; không sửa specs/007 để tránh cùng ghi với T-06. Leader cập nhật trạng thái T-08 khi merge.
- Import read-only preset `../cnhsk-web/tailwind.preset.cjs` và các luật lint hiện có; không đổi file web, không copy token thành nguồn thứ hai.
- Không merge nhánh `feat/dat-six-games`, không thêm engine game hoặc dữ liệu HSK có bản quyền.

## Gate

```powershell
npm ci
npm run test
npm run lint
npm run build
```

Build bao gồm typecheck. QA trình duyệt kiểm ba route ở 320/375/640 px, focus và reduced motion.
Kết quả trên base `b88eb70`:

| Bước                  | Kết quả                                                     |
| --------------------- | ----------------------------------------------------------- |
| `npm ci`              | PASS, 326 package                                           |
| Vitest, 2 worker      | 12/12 test, 1 file, PASS                                    |
| Lint                  | PASS, 0 lỗi, 0 warning                                      |
| Build + TypeScript    | PASS                                                        |
| Chrome 320/375/640 px | PASS, 15 kiểm tra                                           |
| Bundle gzip           | 72.724 byte (HTML 353 + CSS 2.830 + JS 69.541), dưới 250 KB |

Do ổ hệ thống ít dung lượng, `npm ci` chạy trên bản sao `package.json` và `package-lock.json` giống từng byte trên ổ D;
`node_modules` trong app là junction tới thư mục cài đặt đó. Test/lint/build chạy tại chính worktree.
Cách cài đặt này chỉ dùng cho môi trường kiểm tra, không thay đổi cấu hình hệ thống hay nội dung manifest/lockfile.

QA gồm ba route ở mỗi kích thước, không tràn ngang, link thao tác cao ít nhất 44 px, tải lại deep link,
giữ tham số `code`, đường về Hub, skip link bằng bàn phím và `prefers-reduced-motion`.
Chrome không ghi nhận lỗi runtime hoặc request API. Ảnh QA và log giữ ngoài repository;
preview server và Chrome profile kiểm tra đã được dừng sau khi chạy.

Setup test dùng cùng cách tương thích `Request.signal` như web chính cho memory router trong jsdom;
matcher jest-dom được đăng ký trực tiếp để chạy được cả khi `node_modules` là junction.
Không có spy hàm timer toàn cục hoặc thay đổi assertion để bỏ lỗi.
