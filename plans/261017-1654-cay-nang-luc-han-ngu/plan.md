---
title: "Cay nang luc Han ngu - dua CNHSK ve dung cay tri thuc HSK 3.0"
description: >-
  Dua thiet ke CNHSK (knowledge point, mastery, on tap, lo trinh) ve dung
  Cay Nang Luc Han Ngu HSK 1-9 v2.0 cua Hoc Ba: nut KID, DAG tien quyet,
  ma tran Q, ma loi, minh chung, mastery MP-1.0, tang muc tu. Chi ap phan loi,
  bo phan danh cho LMS lop hoc.
status: pending
priority: P1
branch: "main"
tags:
  - knowledge-tree
  - database
  - learning
  - docs
blockedBy: []
blocks: []
created: "2026-10-17T09:54:37.760Z"
createdBy: "ck:plan"
source: skill
---

# Cây năng lực Hán ngữ — đưa CNHSK về đúng cây tri thức HSK 3.0

## Overview

Học Bá đã chốt **Cây Năng Lực Hán Ngữ HSK 1–9 v2.0** là cây tri thức đúng
(`Cay-Nang-Luc-Han-Ngu_HSK1-9_Dac-ta-v2.0.pdf`). Thiết kế hiện tại của CNHSK lệch ở phần lõi:
coi **mỗi từ là một knowledge point**, trộn kỹ năng với miền, tính mastery bằng FSRS ghi đè,
không lưu minh chứng, tiên quyết chỉ ở mức chủ đề.

Plan này đưa phần lõi về đúng đặc tả và **bỏ** phần dành cho LMS lớp học (Mentor, Tester,
mastery chính thức, đánh giá giáo viên, Event Bus) — CNHSK là web tự học, không có lớp.

- Tài liệu để hiểu cây: [`docs/reference/cay-nang-luc/giai-thich-cay-nang-luc.md`](../../docs/reference/cay-nang-luc/giai-thich-cay-nang-luc.md)
- Đặc tả gốc: [`Cay-Nang-Luc-Han-Ngu_HSK1-9_Dac-ta-v2.0.pdf`](../../Cay-Nang-Luc-Han-Ngu_HSK1-9_Dac-ta-v2.0.pdf)

## Mười điểm lệch

| # | Đặc tả (đúng) | CNHSK hiện tại | Mức | Phase |
|---|---|---|---|---|
| 1 | Từ không phải nút; gộp 30–60 từ theo cấp × chủ đề | knowledge point = 1 từ / 1 điểm ngữ pháp | 🔴 | 2, 3 |
| 2 | 6 miền NA CH TV NP NL VB; kỹ năng là thuộc tính | `skill_type` trộn kỹ năng + miền | 🔴 | 2 |
| 3 | MP-1.0: trung bình có trọng số 8 minh chứng, có trạng thái | FSRS ghi đè `mastery` 0–1 | 🔴 | 4 |
| 4 | Tầng mục từ (Leitner) gộp lên nút TV/CH | `lexemes` gộp cả ngữ pháp; FSRS trong `user_progress` | 🔴 | 3 |
| 5 | Tiên quyết DAG giữa nút, cạnh prereq/support | `topics.prereq_ids` + cổng 90% | 🟠 | 2, 5 |
| 6 | Ma trận Q: 1 KID chính + ≤ 2 phụ; mã lỗi → KID | không phân vai, không mã lỗi | 🟠 | 3 |
| 7 | Phiên bản cây, minh chứng ghi `tree_version` | không có | 🟠 | 2 |
| 8 | Chẩn đoán thích ứng 25–35 câu | không có | 🟠 | 4, 5 |
| 9 | HSK 1–9, dựng theo bậc | Report 1 loại HSK 7–9 (LI-08) | 🟡 | 1, 6 |
| 10 | Rubric → KID mục tiêu | chấm viết không gắn KID | 🟡 | 3 |

## Ba hướng đã cân nhắc

| Hướng | Nội dung | Đánh giá |
|---|---|---|
| A · Áp nguyên phụ lục F | ~20 bảng, kể cả mastery chính thức, duyệt cây, Event Bus | ❌ YAGNI — kéo theo phần LMS lớp học; không kịp 6 tuần code |
| **B · Áp phần lõi** ⭐ | `kt_node` `kt_edge` `item_kid` `error_code` `evidence` `mastery` `lexical_item/state`; nhập cây từ bảng tính Học Bá | ✅ Khuyến nghị — đúng đặc tả ở chỗ quan trọng, bỏ chỗ không dùng |
| C · Vá tối thiểu | Thêm cột `kid` vào `knowledge_points`, giữ FSRS | ❌ Vẫn sai gốc điểm 1, 3, 4 |

**Phase 2–6 viết theo hướng B.** Nếu phase 1 chốt hướng khác thì viết lại phase 2–5.

## Phases

| Phase | Name | Status |
|-------|------|--------|
| 1 | [Chot quyet dinh](./phase-01-chot-quyet-dinh.md) | Pending |
| 2 | [Luoc do cay va nap nut](./phase-02-luoc-do-cay-va-nap-nut.md) | Pending |
| 3 | [Ma tran Q ma loi va tang muc tu](./phase-03-ma-tran-q-ma-loi-va-tang-muc-tu.md) | Pending |
| 4 | [Mastery engine va goi y on](./phase-04-mastery-engine-va-goi-y-on.md) | Pending |
| 5 | [Luong hoc vien va man hinh](./phase-05-luong-hoc-vien-va-man-hinh.md) | Pending |
| 6 | [Dong bo tai lieu va bao cao](./phase-06-dong-bo-tai-lieu-va-bao-cao.md) | Pending |

```
1 Chốt ──► 2 Lược đồ + nạp nút ──► 3 Ma trận Q, mã lỗi, mục từ ──► 4 Mastery engine ──► 5 Luồng + màn hình
   │                                                                                         │
   └────────────────────────────► 6 Đồng bộ tài liệu, report, slide ◄────────────────────────┘
```

Phase 6 bắt đầu được ngay sau phase 1 cho phần report (định nghĩa, giải pháp); phần ERD/SRS
chờ phase 2–4 xong.

## Quyết định đã chốt

- **QĐ-01 · 18/10:** chờ data HSK chuẩn của Học Bá cho **cả cấu trúc và dữ liệu cây**. Phase 2–5 bị chặn đến khi có data;
  làm ngay phần ngoài cây (sửa V1, vị trí Flyway, Java 17/21). Xem lại nếu hết tuần 7 chưa có data.
  Chi tiết: [`reports/quyet-dinh-cay-nang-luc.md`](./reports/quyet-dinh-cay-nang-luc.md)

## Các quyết định cần chốt (phase 1)

| # | Quyết định | Khuyến nghị |
|---|---|---|
| Q1 | Hướng sửa A / B / C | **B** |
| Q2 | Ôn mục từ: Leitner 5 hộp (đặc tả) hay giữ FSRS | Leitner — khớp công thức gộp; FSRS để V2 nếu Học Bá đồng ý |
| Q3 | Phạm vi: mô hình 1–9 + dữ liệu sơ cấp / giữ 1–6 / nạp đủ 497 nút | Mô hình 1–9, **nạp 174 nút sơ cấp** |
| Q4 | Cổng 90% chủ đề: thay bằng biên học tập / giữ cổng đổi thước đo | Thay bằng **biên học tập** |
| Q5 | Nguồn cây: chờ bảng tính chính thức Học Bá hay nạp phụ lục B ngay | Nạp phụ lục B làm `KT-2026.0-draft`, thay khi có bản chính thức |
| Q6 | Tham số MP-1.0 (trọng số bối cảnh) cho các hoạt động riêng của CNHSK: game, video, flashcard | Game 0,5 · flashcard chỉ vào tầng mục từ · đề HSK đầy đủ 1,0 |

## Acceptance criteria (toàn plan)

- [ ] Không còn bảng/cột nào coi một từ là một nút mastery
- [ ] Nạp được cây (≥ 174 nút sơ cấp) và qua 4 truy vấn kiểm tra: không vòng, không tiên quyết cấp cao hơn, không bắc cầu thừa, ≤ 4 tiên quyết
- [ ] Mỗi câu hỏi được duyệt có đúng 1 KID chính
- [ ] Mastery tính lại được từ bảng `evidence` cho ví dụ "Quốc Bảo NP3-004" của đặc tả: 53,3 → 61,1
- [ ] Học viên mới thấy "chưa đủ minh chứng", không bị báo yếu
- [ ] Report 1, Report 3, `database.md`, `feature-tree.md`, slide không còn câu mâu thuẫn với cây (FSRS, "một từ là một knowledge point")

## Dependencies

- **Ảnh hưởng plan đang dở:**
  [`261015-2036-79-man-html-sang-react`](../261015-2036-79-man-html-sang-react/plan.md) và
  [`261016-0256-ra-soat-79-man-theo-hanzicozy`](../261016-0256-ra-soat-79-man-theo-hanzicozy/plan.md)
  — nhóm màn học tập (cây chủ đề, tiến độ, ôn tập, kết quả thi) sẽ đổi ở phase 5.
  Chưa gắn `blockedBy` vì quyết định Q1/Q4 chưa chốt; gắn sau phase 1.
- **Phụ thuộc ngoài:** Học Bá cung cấp bảng tính cây chính thức (phụ lục H) và danh sách từ/chữ có mã W/Z.

## Unresolved questions

1. Học Bá có đồng ý CNHSK dùng Leitner hay FSRS cho tầng mục từ? (đặc tả chỉ nói Leitner)
2. Bao giờ có bảng tính cây chính thức — hay dùng phụ lục B cho đồ án?
3. CNHSK có cần "mastery chính thức" cho đề HSK đầy đủ không, hay chỉ một loại mastery học tập?
4. `database.md` (15/10) và `db/migration/V1, V2` (01/10) đang lệch nhau — bản nào là gốc để viết migration mới?
