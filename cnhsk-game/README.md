# CNHSK Game Box

Ứng dụng React/Vite riêng cho `game.cnhsk.com`, gồm sáu game chơi được:

| Game | Đường dẫn |
|---|---|
| Mưa Chữ | `/play/char-rain` |
| Ghép Pinyin | `/play/pinyin-match` |
| Ghép Bộ Thủ | `/play/radical-match` |
| Bắt Chữ | `/play/char-catch` |
| Thần Bút | `/arcade/brush` |
| Phi Đao | `/arcade/dart` |

## Chạy thử

```powershell
cd cnhsk-game
npm ci
npm run dev
```

Mở địa chỉ Local do Vite in ra (mặc định `http://127.0.0.1:5174/`). Kiểm tra bằng `npm run typecheck`, `npm run test` và `npm run build`.

## Phạm vi hiện tại

Đây là **bản demo độc lập** dùng dữ liệu từ vựng tự soạn tại `src/data/words.ts`. Điểm chỉ tồn tại trong phiên chơi, chưa gửi lên backend và chưa cập nhật tiến độ học. Không dùng điểm demo làm thành tích hoặc xếp hạng. Bốn game đầu dựa trên spec 007/008; Thần Bút và Phi Đao là phần mở rộng, chưa có mã game trong API hiện hành. Xem [BACKEND_DEPENDENCIES.md](BACKEND_DEPENDENCIES.md) trước khi tích hợp production.
