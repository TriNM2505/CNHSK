# Screen flow — CNHSK

Ảnh render từ `design.md` §5.4. **Nguồn sự thật là `design.md`**, không phải thư
mục này — sửa sơ đồ thì sửa ở `design.md` rồi render lại.

Phủ đủ **32/32 màn** của bảng `design.md` §5.1–5.3.

| # | Sơ đồ | Nội dung |
|---|---|---|
| 1 | [01-toan-canh.png](01-toan-canh.png) | Vào hệ thống, phân nhánh theo actor |
| 2 | [02-hoc-vien.png](02-hoc-vien.png) | Học, ôn, luyện |
| 3 | [03-luyen-thi.png](03-luyen-thi.png) | Làm bài → chấm → sửa điểm yếu |
| 4 | [04-game.png](04-game.png) | Web game và mobile WebView |
| 5 | [05-cong-dong.png](05-cong-dong.png) | Cộng đồng · V2 |
| 6 | [06-quan-tri.png](06-quan-tri.png) | 8 màn admin tách theo 4 role |
| 7 | [07-chuyen-huong-he-thong.png](07-chuyen-huong-he-thong.png) | Token, role, hạn mức — áp cho mọi màn |

Quy ước trong sơ đồ: nét liền là điều hướng do người dùng bấm · nét đứt là
chuyển hướng do hệ thống (guard, redirect, hết hạn) · nhãn `V2` là màn ngoài
phạm vi MVP.

Sơ đồ 7 áp cho **toàn bộ** màn authenticated, nên đọc kèm với 6 sơ đồ còn lại.

## Render lại

```bash
npm install @mermaid-js/mermaid-cli
npx mmdc -i 01-toan-canh.mmd -o 01-toan-canh.png -b white -s 2
```

File `.mmd` trong thư mục này là bản tách ra từ `design.md` để render. Khi
`design.md` §5.4 đổi, tách lại rồi render lại — đừng sửa trực tiếp `.mmd`.
