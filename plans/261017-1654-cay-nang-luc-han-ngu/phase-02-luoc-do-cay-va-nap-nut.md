---
phase: 2
title: "Luoc do cay va nap nut"
status: pending
priority: P1
effort: "3 ngay"
dependencies: [1]
---

# Phase 2: Lược đồ cây và nạp nút

## Overview

Tạo các bảng lưu cây (nút, cạnh, phiên bản), nạp 174 nút sơ cấp từ phụ lục B, chạy 4 truy vấn
kiểm tra DAG. Thay `knowledge_points` bằng `kt_node`.

## Requirements

- Functional: lưu nút KID với đủ thuộc tính bắt buộc (mục 3.5); lưu cạnh prereq/support; một phiên bản cây đang dùng.
- Non-functional: cây vài trăm nút, vài nghìn cạnh → PostgreSQL thường, không cần CSDL đồ thị (đặc tả mục 1.4).
- Không làm: quy trình duyệt nhiều bước, bảng chuyển đổi nút (`kt_node_migration`), gắn buổi học (`kt_node_lesson`) — YAGNI cho đồ án.

## Architecture

Rút gọn từ phụ lục F.1 (giữ quan hệ và ràng buộc, bỏ phần quy trình biên soạn):

```sql
-- schema learning
CREATE TABLE kt_version (
  tree_version  VARCHAR(20) PRIMARY KEY,          -- 'KT-2026.0-draft'
  is_active     BOOLEAN NOT NULL DEFAULT false,
  source        TEXT NOT NULL,                    -- 'phu-luc-B' | 'hocba-sheet-...'
  imported_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_kt_active ON kt_version(is_active) WHERE is_active;

CREATE TABLE kt_node (
  tree_version VARCHAR(20) NOT NULL REFERENCES kt_version(tree_version),
  id          VARCHAR(10) NOT NULL CHECK (id ~ '^(NA|CH|TV|NP|NL|VB)[1-7]-[0-9]{3}$'),
  type        VARCHAR(2)  NOT NULL CHECK (type IN ('KT','NL')),
  form        VARCHAR(10) CHECK (form IN ('quy_tac','tap_muc')),
  domain      VARCHAR(2)  NOT NULL CHECK (domain IN ('NA','CH','TV','NP','NL','VB')),
  cluster     VARCHAR(100) NOT NULL,
  hsk_level   SMALLINT NOT NULL CHECK (hsk_level BETWEEN 1 AND 7),   -- 7 = bậc 7–9
  name_vi     VARCHAR(200) NOT NULL,
  name_zh     VARCHAR(200) NOT NULL,
  can_do      TEXT,                    -- phụ lục B chưa có; NOT NULL khi có bản chính thức
  pattern     TEXT,
  skills      VARCHAR(10)[] NOT NULL DEFAULT '{}',
  examples_ok JSONB NOT NULL DEFAULT '[]',
  examples_err JSONB NOT NULL DEFAULT '[]',
  status      VARCHAR(12) NOT NULL DEFAULT 'approved' CHECK (status IN ('draft','approved','deprecated')),
  PRIMARY KEY (tree_version, id)
);

CREATE TABLE kt_edge (
  tree_version VARCHAR(20) NOT NULL,
  from_id     VARCHAR(10) NOT NULL,     -- A là tiên quyết/hỗ trợ của B
  to_id       VARCHAR(10) NOT NULL,
  type        VARCHAR(8)  NOT NULL CHECK (type IN ('prereq','support')),
  PRIMARY KEY (tree_version, from_id, to_id),
  FOREIGN KEY (tree_version, from_id) REFERENCES kt_node(tree_version, id),
  FOREIGN KEY (tree_version, to_id)   REFERENCES kt_node(tree_version, id),
  CHECK (from_id <> to_id)
);
```

Bỏ / thay trong lược đồ hiện tại ([`database.md`](../../docs/reference/database.md) mục 4.4, 4.5):

| Hiện tại | Thành |
|---|---|
| `knowledge_points` (code, title, skill_type, lexeme_id) | `kt_node` |
| `topics.prereq_ids BIGINT[]` | `kt_edge` giữa nút; chủ đề chỉ còn là nhóm hiển thị (Q4) |
| `topic_items` | chủ đề ↔ nút TV (1 chủ đề ≈ 1 nút TV `tap_muc`) |
| `skill_type` 6 giá trị | `domain` (6 miền) + `skills[]` |

## Related Code Files

- Create: `db/migration/V3__knowledge_tree.sql` (số phiên bản theo quyết định câu hỏi 4 của plan.md — **không sửa migration đã chạy**)
- Create: `db/seed/kt-2026-0-draft-so-cap.csv` (174 nút + cạnh, trích từ phụ lục B)
- Create: `docs/reference/cay-nang-luc/tools/extract-nodes-from-spec.mjs` (trích bảng phụ lục B → CSV)
- Modify: `docs/reference/database.md` mục 4.4, 4.5

## Implementation Steps

1. Viết script trích phụ lục B (text đã tách được bằng pdfjs) → CSV `id,name_vi,name_zh,hsk_level,prereqs`.
   Đối chiếu tổng: NA 12 · CH 17 · TV 39 · NP 84 · NL 22 = 174.
2. Viết migration tạo `kt_version`, `kt_node`, `kt_edge`.
3. Nạp CSV vào `KT-2026.0-draft`, đặt `is_active = true`.
4. Chạy 4 truy vấn kiểm tra (phụ lục F.4): V01 vòng lặp · V02 tiên quyết cấp cao hơn · bắc cầu thừa · > 4 tiên quyết. Kết quả phải rỗng.
5. Viết lại mục 4.4–4.5 của `database.md`.
6. Đánh dấu `knowledge_points` là bỏ; liệt kê chỗ code/use case đang trỏ vào nó (grep) để phase 3–5 xử lý.

## Success Criteria

- [ ] 174 nút, mọi tiên quyết tồn tại
- [ ] 4 truy vấn kiểm tra trả 0 dòng
- [ ] CHECK mã KID chặn được mã sai (`NP10-001`, `XX1-001`)
- [ ] `database.md` không còn `knowledge_points` / `skill_type`

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Trích PDF sai (ký tự CJK bị tách, tên nhiều dòng) | Đối chiếu số nút từng miền với tiêu đề phụ lục; soát tay 20 nút ngẫu nhiên |
| Phụ lục B chưa có can_do, ví dụ | Để cột nullable ở bản nháp; NOT NULL khi Học Bá giao bản chính thức |
| Đổi tên bảng làm vỡ use case UC-040→053 | Phase 6 cập nhật use case; giữ bảng ánh xạ cũ → mới |
