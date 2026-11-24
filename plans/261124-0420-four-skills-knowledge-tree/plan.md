---
title: Bon ky nang di qua cay tri thuc v2.0 (HSK 1-3)
description: >-
  Nghe, Noi, Doc, Viet di qua cay tri thuc v2.0: skills la thuoc tinh nut va cau
  hoi, nhiem vu Noi/Viet khai bao KID muc tieu, rubric R-NOI/R-VIET va ma loi do
  minh chung ve dung KID, nghe gan nut NA, menu 6 muc theo HSK 1-3, data pack v3
  tu nguon uy tin.
status: in-progress
priority: P1
branch: feat/learning-flow-pass
tags:
  - knowledge-tree
  - learning
  - grading
  - mastery
  - data
blockedBy: []
blocks: []
created: '2026-11-23T21:13:05.021Z'
createdBy: 'ck:plan'
source: skill
---

# Bốn kỹ năng đi qua cây tri thức v2.0 (HSK 1–3)

## Overview
Brainstorm đã duyệt: [`brainstorm-261124-0402-knowledge-tree-four-skills-report.md`](../reports/brainstorm-261124-0402-knowledge-tree-four-skills-report.md).
Chuẩn: đặc tả **v2.0** (`C:\CLHSK\Caytrithuc\...Dac-ta-v2.0.pdf`, text `convert/cay-nang-luc-v2.txt`). Không theo 3 file nháp v1.0 (7 miền kiểu IELTS).
Thay hai mục còn treo của plan cũ `261017-1654-cay-nang-luc-han-ngu` (mã lỗi, rubric → KID).

Cách làm: **TDD** — mỗi phase viết test khoá hành vi hiện có + test cho hành vi mới trước, rồi mới sửa code.

## Phases
| Phase | Name | Status |
|-------|------|--------|
| 1 | [Cay skills nhiem vu ma loi va importer](./phase-01-cay-skills-nhiem-vu-ma-loi-va-importer.md) | Pending |
| 2 | [Data pack v3 tu nguon uy tin](./phase-02-data-pack-v3-tu-nguon-uy-tin.md) | Pending |
| 3 | [Minh chung tu nghe va rubric noi viet](./phase-03-minh-chung-tu-nghe-va-rubric-noi-viet.md) | Completed |
| 4 | [Man hoc 6 muc theo HSK](./phase-04-man-hoc-6-muc-theo-hsk.md) | Completed |
| 5 | [Ky nang tren dashboard va AI](./phase-05-ky-nang-tren-dashboard-va-ai.md) | Completed |
| 6 | [Gate tai lieu va hop dong](./phase-06-gate-tai-lieu-va-hop-dong.md) | In Progress |

```
1 Cây + importer ─► 2 Data pack (leader nạp Neon) ─► 3 Minh chứng nghe + rubric ─► 4 Màn học ─► 5 Dashboard + AI ─► 6 Gate
                        └─ leader/GV duyệt file skills + dữ liệu AI nháp (song song 3–4)
```

## Quyết định đã chốt (24/11)
- Kỹ năng = `skills` (nhiều giá trị **LISTENING/SPEAKING/READING/WRITING**, khớp mã đã có) trên nút + `skill` trên câu hỏi; giao diện hiện Nghe/Nói/Đọc/Viết. NL theo phụ lục B; KT suy theo miền → duyệt.
- Duyệt dữ liệu AI: **câu hỏi** nạp `source=AI` → hàng `/content/ai-review` sẵn có; **ví dụ ngữ pháp + đề nhiệm vụ** GV duyệt trên review sheet rồi mới nạp (APPROVED).
- Viết tự do (không nhiệm vụ) giữ nguyên, chấm R-VIET, chỉ mã lỗi sinh minh chứng.
- Nói → R-NOI (C1–C5, 0–3); Viết → R-VIET (C1–C4, 0–5); hiển thị quy /100; bài cũ 4×25 vẫn đọc được (`rubric = LEGACY`).
- Mã lỗi E-xx HSK 1–3 (phụ lục D) làm ngay.
- Menu trái: Nghe · Luyện nói · Chủ đề · Ngữ pháp · Chữ Hán · Viết; mỗi trang chọn HSK 1/2/3.
- Audio: giọng trình duyệt. Data thiếu nguồn: AI nháp → nạp **chờ GV duyệt**.
- Làm đủ, không cắt; gate cuối mỗi phase theo [[gate-scope-by-change]].

## Ràng buộc
- Migration chạy Flyway trên Neon khi leader bật BE → báo trước mỗi migration. Chỉ thêm cột/bảng, không xoá.
- Không commit dữ liệu Học Bá/đại cương; data pack ở `C:\CLHSK\Caytrithuc\convert\v3\`.
- Không sửa openapi đã khoá; hợp đồng mới ở file phụ lục `-v1-1.yaml` ([[locked-contract-tests-appendix-files]]).
- ModuleBoundary: learning không gọi community; shared không gọi auth/learning.

## Tiêu chí hoàn thành
- 174 nút có `skills`; 100% mục từ HSK 1–3 có KID; 8 tầng nghe có KID NA; 22 nút NL có ≥ 1 nhiệm vụ + KID mục tiêu; mã lỗi HSK 1–3 nạp đủ.
- Làm 1 bài Nghe / Nói / Viết → minh chứng ở đúng KID (API mastery).
- Dashboard hiện % 4 kỹ năng + "x/y nút ở mức tốt" theo miền.
- Toàn bộ FE test + `mvnw verify` xanh.

## Validation Log

### Session 1 — 2026-11-24
**Verification Results** (tier Full, 6 phase)
- Claims checked: 18 · Verified: 13 · Failed: 5 · Unverified: 0
- F1 bối cảnh `REVIEWED` không cần — đã có `GRADED: 0.6` (`application.yaml` mục mastery.context-weights).
- F2 dạng `RUBRIC` không cần — đã có `OPEN: 1.0` (form-weights).
- F3 `attempt_items` chỉ có `is_correct`; `EvidenceAllocator.java:125` `score = correct ? 100 : 0`; CHECK `attempt_items_skill_check` không có SPEAKING.
- F4 API mastery là `/api/mastery/...` (`MasteryController` RequestMapping), hợp đồng `openapi-mastery.yaml` → phụ lục `openapi-mastery-v1-1.yaml`.
- F5 hàng duyệt AI chỉ lọc `q.source='AI'` (`AiReviewService.java:65`) → câu import chờ duyệt sẽ không ai thấy.

**Quyết định (4 câu hỏi)**
| Câu hỏi | Chốt |
|---|---|
| Mã kỹ năng | Tiếng Anh LISTENING/SPEAKING/READING/WRITING (thêm SPEAKING) |
| Duyệt dữ liệu AI | Câu hỏi `source=AI` qua `/content/ai-review`; ví dụ + nhiệm vụ duyệt trên file |
| Điểm lẻ rubric | `attempt_items.meta.score` (0–100) + `meta.targetKid`; không thêm cột |
| Viết tự do | Giữ, chấm R-VIET, chỉ mã lỗi sinh minh chứng |

Lan truyền: phase 1 (mã skill, CHECK SPEAKING trong V18, importer `source=AI`), phase 2 (mã skill, luồng duyệt), phase 3 (GRADED/OPEN, meta.score, bỏ V19), phase 5 (đường dẫn API), phase 6 (tên hợp đồng, chỉ V18).

### Whole-Plan Consistency Sweep
- Đã quét plan.md + 6 phase tìm: mã NGHE/NOI/DOC/VIET, `REVIEWED`, form `RUBRIC`, `V19`, `/api/me/mastery`, `review_status`. Còn lại chỉ ở Validation Log (ghi lịch sử) → **0 mâu thuẫn chưa giải quyết**.
- Ghi chú: báo cáo brainstorm vẫn ghi mã kỹ năng tiếng Việt — đã được thay bằng quyết định Session 1 (mã tiếng Anh).
- Migration duy nhất: **V18** (thêm cột/bảng + mở CHECK SPEAKING).

## Dependencies
- Không chặn/bị chặn plan đang mở khác. Plan `261017-1654` phần mã lỗi/rubric được thay bởi plan này.
