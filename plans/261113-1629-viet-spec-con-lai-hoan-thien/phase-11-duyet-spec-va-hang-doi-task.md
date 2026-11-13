---
phase: 11
title: "Duyệt spec + hàng đợi task"
status: pending
priority: P1
dependencies: [2, 3, 4, 5, 6, 7, 8, 9, 10, 12]
effort: "leader 1–2 ngày duyệt"
---

# Phase 11: Duyệt spec + hàng đợi task (workflow phụ thuộc)

## Overview
Leader duyệt từng spec khi xong (không chờ đủ 8), rồi chốt file `workflow-phu-thuoc-task-hoan-thien.md` trong thư mục plan này — khuôn như `workflow-phu-thuoc-task-010-015.md`.

## Checklist duyệt mỗi spec
- [ ] Đúng H1–H13; không bảng mới ngoài `reference_items`, `app_settings`; cấu hình nghiệp vụ mới đọc qua `SettingsApi`
- [ ] §0.4 liệt kê file dùng chung; mã lỗi mới đặt trước `INTERNAL_ERROR`
- [ ] OpenAPI khớp FR; mỗi FR có AT; ví dụ tự soạn, không dữ liệu HSK có bản quyền
- [ ] tasks.md đánh dấu task chờ ⚿ nào
- [ ] Constitution check §8 điền đủ

## Điểm khoá ⚿ (làm sớm, merge sớm)

| ⚿ | Việc | Người | Mở khoá cho |
|---|---|---|---|
| K1 | PR enum: `PracticeSource.QUIZ`, `CONTEST` | Dũng, đầu tuần 2 (cần cho 021 sớm nhất) | 019 engine, 021 engine |
| K2 | Migration `reference_items` | Xuân Huy | toàn bộ 018 BE |
| K8 | `learning.api.ContestAttemptApi` + index unique 1 lượt CONTEST | Dũng (task đầu 021) | toàn bộ phần community của 021 |
| K7 | V7 `app_settings` + `SettingKey` + `SettingsApi` | Tường, đầu tuần 2 (trước 013/T-14 nếu kịp) | giá/hạn 022, 020, 019, chuyển chỗ đọc 013/014/015, màn 078 |
| K3 | 007 ghi `attempts` GAME (server chấm) | Đạt | dữ liệu thật cho 017 game LB |
| K5 | 008 APPROVED | Leader | 007/008 BE |
| K6 | 013/T-09…T-13 xong | Quốc Huy | Quốc Huy bắt đầu code 022 (spec viết song song trong tuần 1) |

## Hàng đợi không tránh được (→ = sau)

```
Quốc Huy : 013 T-09 → T-10 → T-11 → T-13 → 022 BE → 022 FE ; phụ lục 009 (042) xen kẽ
Tường    : ⚿K7 SettingsApi → 013 T-14 (chờ T-09/T-11 theo màn) → 023 (đề thi, tiên quyết, 078, chuyển chỗ đọc) → 019 quiz [engine chờ ⚿K1]
Đạt      : sửa nhánh game → ⚿K5 → 007 BE (⚿K3) → 008 + phụ lục 2 game → nối FE game ; 012/T-19 khi có người thử
Dũng     : ⚿K1 (PR enum) → nợ N+1 → 020 (độc lập) → 021 (⚿K8 trước) → 017 (fixture; game LB thật sau ⚿K3)
Xuân Huy : ⚿K2 → 018 → phụ lục 014 → 024 (độc lập, mọi phụ thuộc đã merge)
Leader   : RFC → duyệt spec → chạy V7 Neon → chạy thật 015/T-07, 024 với API key ; seed tài khoản TEACHER demo
```

**Song song tối đa:** 020, 018, phụ lục 014, 024, 017 (với fixture), 023, 022 (consume/refund và LedgerService đã merge) không chờ spec mới. Chỉ 2 chỗ phải chờ: engine 019/021 (⚿K1, 1 PR nhỏ), dữ liệu game LB (⚿K3).

## Luật merge (giữ từ đợt trước)
- L2: file dùng chung merge lần lượt, rebase main trước review.
- L3: migration theo số, leader chạy Neon ngay sau merge.
- L4: `PracticeSource`/engine chỉ một người sửa một lúc (⚿K1 gom enum).

## Success Criteria
- [ ] 11 đầu việc spec `APPROVED` (8 spec mới + phụ lục 014, 009 + 008)
- [ ] File workflow có bảng ⚿ + thứ tự 5 người khớp tasks.md thật
- [ ] Tin giao việc gửi 5 người

## Risk Assessment
- Leader duyệt chậm là nút thắt → duyệt cuốn chiếu, ưu tiên spec không phụ thuộc (020, 018, 024) để người đó code trước.
