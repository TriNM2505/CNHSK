# constraints/safety.md — Guardrail AN TOÀN của Agent ("tuyến phòng thủ cuối")

**Version:** 1.0.0 | **Cập nhật:** 2026-09-28 | **Maintainer:** Trí (leader)
**Enforcement:** **Human approval gate (nghiêm nhất)** — không cho merge nếu vi phạm.

> Ngăn agent làm hành động **không thể hoàn tác** khi thiếu context hoặc bị "confused".
> Trả lời câu hỏi: **"Agent KHÔNG được làm gì / phải hỏi trước khi làm gì?"**
> 📎 Canonical: constitution **HR-02** (secrets), **AC-05** (Flyway).

---

## 1. Data Safety (⛔ chặn — cần leader xác nhận)

**KHÔNG được:**

- `DROP TABLE` / `TRUNCATE` trong file migration
- `DELETE FROM ...` **không có** `WHERE` — thảm hoạ mất dữ liệu
- **Xoá hoặc sửa migration cũ** đã có trong repo → Flyway báo lỗi checksum, cả nhóm phải xoá DB
- Đổi kiểu dữ liệu của cột đang có data mà chưa hỏi
- Chạy `./mvnw spring-boot:run` lên **database dùng chung của nhóm** — sẽ tự áp migration.
  Chỉ được `compile` và `test`.

**PHẢI:**

- Trước mọi thay đổi schema: nhắc leader *"đã backup chưa?"* + có kế hoạch rollback
- Migration mới `V{n}__*.sql`: **leader cấp số**, tuyệt đối không tự đoán
  *Lý do: hai người cùng viết `V4__` là Flyway từ chối khởi động, cả hai ngồi chơi.*

---

## 2. Code Safety (⛔ hỏi trước)

**KHÔNG tự ý làm, phải hỏi leader trước:**

- `git push` · `merge` · `checkout` đổi nhánh · tạo nhánh mới
- Thêm dependency vào `pom.xml` hoặc `package.json` — xem `global.md` §5
- Sửa `SecurityConfig`, cấu hình JWT / RBAC / CORS / cookie
- Sửa `application.yaml` phần credentials hoặc URL · `.env` · `.env.example`
- Sửa file hiến pháp: `AGENTS.md` · `CLAUDE.md` · `.specify/**` (gồm constitution và
  constraints) · `spec.md` · `docs/CONTEXT.md`
- **Sửa hơn 3 file cùng lúc** — tóm tắt kế hoạch trước, chờ duyệt
- Xoá bất kỳ file nào

`git add` và `git commit` thì được làm, `push` thì không.

---

## 3. Production & Secret Safety

- **KHÔNG** hardcode credentials — JWT secret, mật khẩu DB, API key AI, key thanh toán.
  Đọc qua `@Value` / `@ConfigurationProperties` từ `.env` (**HR-02**)
- **KHÔNG** log dữ liệu nhạy cảm: mật khẩu, JWT secret, PII chưa mask — xem `business.md` §6
- **KHÔNG** bypass auth middleware "cho nhanh khi dev"
- **KHÔNG** commit dữ liệu đề thi HSK của giảng viên — bản quyền (**HR-08**)
- **KHÔNG** dùng `*` cho CORS dù chỉ để test cục bộ (**HR-04**)

---

## 4. Khi KHÔNG chắc chắn — quy tắc mặc định

- **Dừng lại và báo cáo, KHÔNG giả định.**
  *"Tôi không chắc về ràng buộc X. Anh muốn làm thế nào?"*
- Phát hiện bug, mâu thuẫn hoặc thiếu thông tin → đưa **các phương án kèm ưu nhược** cho
  leader, không tự quyết
- **GIỮ SCOPE:** chỉ sửa đúng thứ được yêu cầu. Thấy chỗ khác cần sửa → ghi TODO, đừng tự đụng
- Ba TODO đang treo trong constitution (`REDIS_PLACEMENT`, `PAYMENT_SCOPE`,
  `COVERAGE_TARGET`) — **không tự điền câu trả lời**

> **Hỏi rồi chậm còn hơn đoán rồi sai.**

---

## 5. Self-check sau khi code

Sau khi viết hoặc sửa code, tự kiểm và báo ✅ PASS / ❌ FAIL từng dòng:

```
## Global (constraints/global.md)
- [ ] Không thêm thư viện ngoài danh sách đã duyệt?
- [ ] Naming conventions đúng?
- [ ] Không vi phạm ranh giới module (không JOIN xuyên schema)?

## Business (constraints/business.md)
- [ ] Mật khẩu đúng thuật toán? Tiền dùng NUMERIC, điểm dùng BIGINT?
- [ ] PII không xuất hiện thô trong log?
- [ ] Điểm game được server kiểm lại (trần điểm, thời gian, tần suất)?

## Safety (file này)
- [ ] Không DELETE thiếu WHERE, không DROP/TRUNCATE?
- [ ] Không hardcode credentials?
- [ ] Không tự push, không tự thêm dependency?
- [ ] Không sửa quá 3 file mà chưa báo?
```

Có ❌ FAIL → **sửa trước khi nộp.**
Chạm vào tiền, auth, hoặc quyền → chạy thêm AI Self-Check Protocol trong constitution.
