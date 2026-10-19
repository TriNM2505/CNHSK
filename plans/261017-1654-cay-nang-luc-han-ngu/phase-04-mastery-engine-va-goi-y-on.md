---
phase: 4
title: "Mastery engine va goi y on"
status: pending
priority: P1
effort: "5 ngay"
dependencies: [3]
---

# Phase 4: Mastery engine và gợi ý ôn

## Overview

Thay FSRS ở mức knowledge point bằng **MP-1.0**: lưu minh chứng, tính mastery theo trung bình có trọng số,
xếp trạng thái, gợi ý ôn, chẩn đoán đầu vào. Tầng mục từ dùng Leitner (hoặc FSRS nếu Q2 chọn).

## Requirements

- Functional
  - Mỗi câu trả lời đã chấm → minh chứng theo quy tắc phân bổ (đúng / sai có mã lỗi / sai không mã lỗi).
  - Mastery = Σw·s / Σw trên tối đa 8 minh chứng hợp lệ gần nhất (luyện tự chọn ≤ 3).
  - Trạng thái: chưa học tới · chưa đủ minh chứng · giỏ ôn (< 60) · củng cố · mở rộng (≥ 80) · suy ra vững; vùng đệm 3 điểm.
  - Gợi ý mỗi ngày: ≤ 5 nút, ≤ 20 câu, tiên quyết yếu chèn trước, có lý do bằng lời.
  - Chẩn đoán thích ứng (mục 5.8): ≤ 35 câu.
- Non-functional
  - Ghi minh chứng + cập nhật mastery **trong cùng transaction** với lưu bài làm (giữ cam kết kiến trúc hiện tại, mục tiêu < 1 s).
  - Tham số MP lưu trong bảng có phiên bản, không hard-code.
- Không làm: mastery "chính thức", giá trị gia tăng giáo viên, Event Bus, `processed_event`.

## Architecture

```sql
CREATE TABLE mp_policy (
  policy_version VARCHAR(10) PRIMARY KEY,        -- 'MP-1.0'
  params JSONB NOT NULL,                         -- trọng số, cửa sổ 8/3, bán rã 30, ngưỡng, hạn ôn
  is_active BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE evidence (
  id           BIGSERIAL PRIMARY KEY,
  user_id      BIGINT NOT NULL REFERENCES auth.users(id),
  kid          VARCHAR(10) NOT NULL,
  tree_version VARCHAR(20) NOT NULL,
  attempt_item_id BIGINT REFERENCES attempt_items(id),
  context      VARCHAR(12) NOT NULL,   -- exam | quiz | diagnostic | graded | practice
  item_form    VARCHAR(10) NOT NULL,   -- open | mcq4 | mcq3 | binary | criterion
  role         VARCHAR(10) NOT NULL,   -- primary | secondary | criterion | error
  score        NUMERIC(5,2) NOT NULL CHECK (score BETWEEN 0 AND 100),
  w_static     NUMERIC(5,3) NOT NULL,  -- bối cảnh × dạng câu × vai trò (chưa có r(t))
  valid        BOOLEAN NOT NULL,
  invalid_reason VARCHAR(30),
  occurred_at  TIMESTAMPTZ NOT NULL,
  policy_version VARCHAR(10) NOT NULL REFERENCES mp_policy(policy_version)
);
CREATE INDEX ix_ev_lookup ON evidence(user_id, kid, occurred_at DESC) WHERE valid;

CREATE TABLE mastery (
  user_id      BIGINT NOT NULL,
  kid          VARCHAR(10) NOT NULL,
  tree_version VARCHAR(20) NOT NULL,
  mastery      NUMERIC(5,2),          -- NULL khi chưa có minh chứng
  confidence   NUMERIC(6,3) NOT NULL DEFAULT 0,
  state        VARCHAR(12) NOT NULL CHECK (state IN
               ('notyet','insufficient','review','consolidate','extend','inferred')),
  due_at       TIMESTAMPTZ,
  evidence_ids BIGINT[] NOT NULL DEFAULT '{}',
  policy_version VARCHAR(10) NOT NULL,
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, kid, tree_version)
);

CREATE TABLE lexical_state (                 -- tầng mục từ
  user_id   BIGINT NOT NULL,
  lexeme_id BIGINT NOT NULL REFERENCES lexemes(id),
  track     VARCHAR(12) NOT NULL DEFAULT 'recognition' CHECK (track IN ('recognition','production')),
  box       SMALLINT NOT NULL DEFAULT 1 CHECK (box BETWEEN 1 AND 5),
  next_due  DATE,
  PRIMARY KEY (user_id, lexeme_id, track)
);
```

`user_progress` chỉ còn giữ phần **không phải mastery**: % video, tầng phát âm, vị trí xem
(`target_type` VIDEO, PRON_STAGE). Phần FSRS (`mastery`, `stability`, `difficulty`) bỏ.

Thành phần code (Spring, module `learning`):

```
MasteryPolicy      đọc mp_policy đang active
EvidenceWriter     attempt_item đã chấm → 0..n evidence (quy tắc phân bổ mục 5.3)
MasteryCalculator  8 minh chứng gần nhất × r(t) → mastery, confidence, state (có vùng đệm)
LexicalScheduler   Leitner: đúng → lên hộp, sai → hộp 1; gộp lên nút TV/CH (mục 5.9)
Recommender        điểm ưu tiên → ≤ 5 nút, ≤ 20 câu, tiên quyết trước, lý do
DiagnosticRunner   chọn nút nhiều thông tin nhất, suy luận theo kt_edge
```

## Related Code Files

- Create: `db/migration/V5__mastery_engine.sql`
- Create: `src/main/java/.../learning/mastery/*` (6 lớp trên)
- Create: test `MasteryCalculatorTest` với ví dụ Quốc Bảo (53,3 → 61,1) và ví dụ TV2-007 (68,5)
- Modify: `docs/reference/database.md` mục 4.9 (`user_progress`)

## Implementation Steps

1. Migration + nạp `MP-1.0` (tham số mục 5.4–5.6, thêm trọng số Q6 của phase 1).
2. **Viết test trước** từ các ví dụ số trong đặc tả (mục 5.5, 5.9) — đây là chuẩn nghiệm thu có sẵn.
3. `EvidenceWriter` + `MasteryCalculator`, gọi trong cùng transaction lưu bài làm / điểm game.
4. `LexicalScheduler` + công thức gộp nút TV/CH.
5. `Recommender` (mục 5.7) — lý do sinh từ template, không gọi AI.
6. `DiagnosticRunner` (mục 5.8) — dừng ở 35 câu hoặc 30 phút.
7. Job đêm: cập nhật `due_at`, hạ trạng thái khi độ tin cậy < 1,0.

## Success Criteria

- [ ] Test ví dụ đặc tả: 53,3 · 61,1 · 68,5 đều đúng
- [ ] Học viên mới: mọi nút `notyet` / `insufficient`, không có nút nào ở `review`
- [ ] Tính lại mastery từ `evidence` cho ra đúng giá trị đang lưu
- [ ] Chẩn đoán phạm vi ~60 nút kết thúc trong ≤ 35 câu
- [ ] Lưu điểm game → mastery đổi trong cùng transaction

## Risk Assessment

| Rủi ro | Giảm thiểu |
|---|---|
| Bảng `evidence` to nhanh | Chỉ đọc 8 dòng gần nhất qua index; phân vùng theo thời gian khi > 10 triệu dòng |
| Tham số chuyên gia chưa hợp CNHSK (không có thi giữa khoá) | Tham số nằm trong `mp_policy`, đổi không cần deploy |
| Nhóm quen FSRS, hiểu sai công thức | Test từ ví dụ số trong đặc tả là chuẩn, không tranh luận cảm tính |
