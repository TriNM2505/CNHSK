---
phase: 3
title: "Ma tran Q ma loi va tang muc tu"
status: pending
priority: P1
effort: "3 ngay"
dependencies: [2]
---

# Phase 3: Ma trận Q, mã lỗi và tầng mục từ

## Overview

Nối cây với nội dung: câu hỏi ↔ nút (chính/phụ), phương án nhiễu ↔ mã lỗi, từ/chữ ↔ nút TV/CH.
Mọi mục trong `lexemes` (chữ, từ, ngữ pháp) trỏ về nút chứa nó.

## Requirements

- Mỗi câu hỏi đã duyệt có **đúng 1 KID chính**, tối đa 2 KID phụ. Câu không có KID chính không được vào đề.
- Mỗi mã lỗi trỏ về đúng 1 KID.
- Mỗi từ/chữ thuộc đúng 1 nút TV/CH.
- Câu AI sinh: AI đề xuất KID, **giáo viên xác nhận khi duyệt** (thay cho "hai người gắn độc lập" của đặc tả — CNHSK không có đủ người).

## Architecture

```sql
-- Ma trận Q (thay question_knowledge_points)
CREATE TABLE item_kid (
  question_id  BIGINT NOT NULL REFERENCES questions(id),
  tree_version VARCHAR(20) NOT NULL,
  kid          VARCHAR(10) NOT NULL,
  role         VARCHAR(10) NOT NULL CHECK (role IN ('primary','secondary')),
  PRIMARY KEY (question_id, tree_version, kid),
  FOREIGN KEY (tree_version, kid) REFERENCES kt_node(tree_version, id)
);
CREATE UNIQUE INDEX uq_item_one_primary ON item_kid(question_id, tree_version) WHERE role = 'primary';

CREATE TABLE error_code (
  tree_version VARCHAR(20) NOT NULL,
  code        VARCHAR(10) NOT NULL CHECK (code ~ '^E-(NA|CH|TV|NP|NL|VB)-[0-9]{2}$'),
  name_vi     VARCHAR(200) NOT NULL,
  kid         VARCHAR(10),            -- NULL: lấy KID mục tiêu của câu
  severity    SMALLINT NOT NULL CHECK (severity IN (1,2,3)),
  fix_hint    TEXT NOT NULL,
  PRIMARY KEY (tree_version, code)
);
-- phương án nhiễu gắn mã lỗi: thêm "error_code" vào từng phần tử questions.options (JSONB)
--   [{key:'B', text:'我写完了把作业', is_correct:false, error_code:'E-NP-07'}]

-- Tầng mục từ (tách từ lexemes)
ALTER TABLE lexemes
  ADD COLUMN lexical_code VARCHAR(10),          -- 'W2-0388' / 'Z1-0123'
  ADD COLUMN node_kid     VARCHAR(10),          -- nút TV/CH chứa mục này
  ADD COLUMN handwrite_required BOOLEAN NOT NULL DEFAULT false;
-- lexemes.kind giữ 'CHAR' | 'WORD' | 'GRAMMAR'; GRAMMAR là mục tra cứu, node_kid trỏ về nút NP
```

**Vì sao không tạo bảng `lexical_item` mới như phụ lục F:** `lexemes` đã có chữ, pinyin, âm Hán Việt,
nghĩa, cấp — đúng các cột `lexical_item` cần. Thêm 3 cột rẻ hơn tạo bảng trùng (DRY).

## Related Code Files

- Create: `db/migration/V4__q_matrix_error_lexical.sql`
- Create: `db/seed/error-codes-so-cap.csv` (từ phụ lục D)
- Modify: `docs/reference/database.md` mục 4.3 (`lexemes`), 4.6 (`questions`), bảng 12 (`question_knowledge_points`)
- Modify: `docs/reference/use-cases-08-quan-tri-he-thong.md` (nhập dữ liệu, duyệt câu AI: bắt buộc chọn KID chính)

## Implementation Steps

1. Migration `item_kid`, `error_code`, 3 cột mới của `lexemes`; bỏ `question_knowledge_points`.
2. Nạp mã lỗi sơ cấp từ phụ lục D.
3. **Giữ** điểm ngữ pháp trong `lexemes` (kind = GRAMMAR) làm mục tra cứu thư viện; gán `node_kid` trỏ về nút NP chứa nó (210 điểm → ~85–110 nút). Không tạo bảng mới. Chi tiết: [`reports/db-diff-261018-2115-...`](./reports/db-diff-261018-2115-cay-nang-luc-vs-schema-hien-tai-report.md).
4. Gán `node_kid` cho từ/chữ: nút TV theo cấp × chủ đề, nút CH theo tập chữ A/B/C (danh sách từ Học Bá).
5. Luồng duyệt câu AI: AI đề xuất KID chính/phụ + mã lỗi cho phương án nhiễu → giáo viên xác nhận hoặc sửa.
6. Luồng nhập đề (Content Admin): file nhập phải có cột KID chính; thiếu thì vào báo cáo lỗi nhập.
7. Truy vấn kiểm tra độ phủ: số câu đã duyệt mỗi nút (đặc tả cần ≥ 5, đủ 3 mức khó) → danh sách nút thiếu câu.

## Success Criteria

- [ ] Không chèn được câu thứ 2 có `role='primary'` cho cùng câu hỏi
- [ ] Mọi mục `GRAMMAR` trong `lexemes` có `node_kid` trỏ về nút NP
- [ ] Mọi từ/chữ sơ cấp có `node_kid`
- [ ] Báo cáo độ phủ: nút nào < 5 câu

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Gắn sai KID làm sai mastery của mọi người làm câu đó (đặc tả mục 2.1b) | Giáo viên xác nhận; báo cáo câu có tỉ lệ đúng lệch hẳn so với nút |
| Thiếu danh sách từ có mã W/Z | Tạm sinh mã theo thứ tự trong cấp; ghi rõ là tạm |
| Ngân hàng câu quá ít cho 174 nút | Dùng luồng AI sinh + duyệt có sẵn (BF-06) để lấp nút thiếu |
