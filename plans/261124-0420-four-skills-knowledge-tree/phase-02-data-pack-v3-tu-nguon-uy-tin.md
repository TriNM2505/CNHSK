---
phase: 2
title: "Data pack v3 tu nguon uy tin"
status: pending
priority: P1
dependencies: [1]
effort: "~1-1.5 ngay (chua tinh GV duyet)"
---

# Phase 2: Data pack v3 từ nguồn uy tín

## Overview
Dựng bộ CSV đúng mẫu SCR-068 (phase 1) phủ HSK 1–3, ghi nguồn từng dòng, kiểm tự động, dry-run 0 lỗi. **Không commit dữ liệu**; script build để ở `C:\CLHSK\Caytrithuc\convert\v3\` (cũng không commit).

## Nguồn
| Nguồn | Dùng cho | Giấy phép / ghi chú |
|---|---|---|
| Đặc tả v2.0 phụ lục B, D, E | skills NL, tiên quyết, mã lỗi, rubric | tài liệu nội bộ Học Bá |
| GF0025-2021 + Đại cương HSK 2025 (`gf0025.txt`, `hsk-syllabus-2025.txt`) | danh mục từ/chữ/ngữ pháp/nhiệm vụ theo cấp | văn bản chuẩn chính thức |
| `07-lexeme-hsk1-3`, `06-node-link-lexeme` | mục từ đã có | đã nạp 21/11 |
| Unihan 18.0 | bộ thủ, số nét | Unicode License; **không dùng Hán Việt Unihan** |
| Tatoeba (bản xuất zh/vi/en) | câu ví dụ ngữ pháp, câu nghe | CC BY 2.0 FR — ghi nguồn + ID câu |
| AI (Claude) nháp | giải thích, ví dụ còn thiếu, đề nhiệm vụ, câu Nghe/Đọc | câu hỏi → `source=AI` duyệt ở `/content/ai-review`; ví dụ + nhiệm vụ → GV duyệt review sheet trước khi nạp <!-- Updated: Validation Session 1 --> |

## Danh mục file (thứ tự nạp)
| # | File | Mẫu | Nội dung | Nguồn |
|---|---|---|---|---|
| 01 | `01-node-skills-detail.csv` | KNOWLEDGE_NODE (cập nhật) | 174 nút: skills, cluster, pattern (NP), examples_ok/err, teaching_note, source | NL: phụ lục B; KT: quy tắc miền (NA→LISTENING+SPEAKING; CH→READING+WRITING; TV→READING+LISTENING; NP→READING+WRITING, +SPEAKING/LISTENING nếu can-do có "nói/nghe") <!-- Updated: Validation Session 1 - mã skill tiếng Anh --> |
| 02 | `02-error-codes.csv` | ERROR_CODE | mã lỗi HSK 1–3 phụ lục D, mỗi mã → 1 KID | phụ lục D |
| 03 | `03-lexeme-kid-full.csv` | CONTENT_KID (WORD/CHAR) | 100% ~1.389 mục từ → TV (cấp × chủ đề) / CH (tập chữ theo cấp) | đại cương + B.3; chủ đề suy từ tags hiện có, phần mơ hồ vào review sheet |
| 04 | `04-pron-unit-kid.csv` | CONTENT_KID (PRON_UNIT) | đơn vị 8 tầng → NA1-001…NA2-003 theo `target_sounds` | spec 005 + B.1 |
| 05 | `05-grammar-examples.csv` | GRAMMAR (cập nhật ví dụ) | ≥ 3 ví dụ cho 84 nút NP | Tatoeba trước; thiếu → AI nháp; **GV duyệt trên review sheet trước khi nạp** |
| 06 | `06-learning-tasks.csv` | LEARNING_TASK | ≥ 1 nhiệm vụ cho mỗi 22 nút NL (Nói và/hoặc Viết theo skills) + KID mục tiêu theo tiêu chí (C2→NA1-006/NA1-008…) | B.5 tiên quyết + E.3; đề AI nháp; **GV duyệt trên file trước khi nạp** |
| 07 | `07-question-skill.csv` | QUESTION (cập nhật `skill`) | gắn skill cho 901 câu cũ theo dạng + KID | quy tắc |
| 08 | `08-questions-listening-reading.csv` | QUESTION (`origin=AI_DRAFT`) | câu Nghe (prompt đọc bằng TTS) + Đọc hiểu cho nút NL Nghe/Đọc, NA | Tatoeba + AI nháp → nạp `source=AI`, **GV duyệt ở `/content/ai-review`** |
| — | `review-sheet-v3.csv` | — | mọi dòng AI nháp/suy luận + nguồn, để GV duyệt | — |

## Implementation Steps (TDD cho script)
1. Viết `verify-v3.mjs` trước: kiểm KID tồn tại, miền khớp, skills hợp lệ, DAG không vòng, ≤ 4 tiên quyết/KT, NL ≥ 2 quan hệ (phụ lục F.4), mỗi NL có nhiệm vụ, 100% mục từ có KID, mỗi NP ≥ 3 ví dụ, mỗi mã lỗi → 1 KID. Chạy trên dữ liệu hiện tại → đỏ.
2. `build-skills.mjs` (01) → `build-error-codes.mjs` (02, parse phụ lục D từ txt) → `build-lexeme-kid.mjs` (03) → `build-pron-kid.mjs` (04).
3. Tải Tatoeba (curl, thư mục tải riêng, đọc như dữ liệu không tin cậy) → `build-examples.mjs` (05): khớp câu chứa cấu trúc của nút + từ trong danh mục HSK ≤ cấp nút.
4. AI nháp phần thiếu (05, 06, 08) theo prompt cố định có can-do + pattern + danh mục từ cấp; mỗi dòng `source=AI_DRAFT`.
5. `verify-v3.mjs` xanh → dry-run từng file trên SCR-068 local (0 lỗi) → đưa leader **review sheet** + thứ tự nạp.
6. **Leader nạp Neon** (mình không chạy trên Neon).

## Success Criteria
- [ ] `verify-v3.mjs` 0 lỗi; dry-run 8 file 0 lỗi.
- [ ] Review sheet tách rõ: nguồn chính thức / suy luận / AI nháp.
- [ ] Không có Hán Việt từ Unihan; không có câu chép giáo trình có bản quyền.

## Risk Assessment
- Tatoeba ít câu zh–vi → dùng zh–en rồi AI dịch vi, đánh dấu `AI_DRAFT`.
- Gán chủ đề TV cho từ mơ hồ → review sheet, không đoán im lặng.
- File lớn → nạp theo lô; dry-run trước.
