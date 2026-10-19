---
phase: 4
title: "Man hoc tap"
status: pending
priority: P1
dependencies: [2]
effort: "14h"
---

# Phase 4: Màn học tập (7 màn, nhiều bước)

## Overview

Dùng component `features/practice` (phase 2 mục 6). **Không** mastery, không "ôn lại sau N ngày", không khoá chủ đề (bảng lệch spec ở `plan.md`).
Mỗi bước là một View riêng + preview riêng.

## Tasks

| ID | Màn | HTML | Spec | View cần có |
|---|---|---|---|---|
| FE-24 | SCR-011 Topic List | `scr-011-topic-list.html` | 003 FR-013…017, FR-041 | Lọc HSK 1–9 ("Tất cả" mặc định), ô tìm + chip trạng thái lọc phía client; lưới thẻ chủ đề (tên, `LevelBadge`, `learnedWords/totalWords`, `Progress`, nhãn Chưa học/Đang học/Hoàn thành, điểm KT cao nhất). Thay 4 ô thống kê cũ bằng: Tổng chủ đề · Đang học · Hoàn thành |
| FE-25 | SCR-012 Topic Study — khung + Học từ mới | `scr-012-topic-study.html` | 003 FR-018…024, FR-042 | Đầu trang chủ đề; 4 bước (`Tabs` hoặc stepper) với lý do khoá từ `actions`; bước học: `StudyCard` lần lượt, tổng kết "Đã học {n} từ mới" |
| FE-26 | SCR-012 — Luyện nhận diện / nghe + Kiểm tra | như trên | 003 FR-025…030, FR-043, FR-044 | Luyện: `ChoiceQuestion` + `AudioButton` + "Bỏ qua"; Kiểm tra: danh sách câu đi lại được, nháp từng câu, `ConfirmPanel` nộp ("còn k câu chưa làm"), kết quả + từ sai + "Luyện lại từ sai" |
| FE-27 | SCR-017 Grammar List · SCR-018 Grammar Lesson | `scr-017-*.html`, `scr-018-*.html` | 004 FR-001…018 | SCR-017: 7 tab, dòng điểm ngữ pháp + "Đã học" + điểm cao nhất. SCR-018: Cấu trúc · Giải thích · Ví dụ · Lỗi thường gặp, "Đã hiểu" (đo thời gian đọc), bài tập `ChoiceQuestion`, "Bỏ qua bài tập" + xác nhận, kết thúc |
| FE-28 | SCR-016 Pronunciation | `scr-016-pronunciation-practice.html` | 005 FR-017…020 | Danh sách 8 thẻ tầng (khoá có lý do, chip âm luyện, độ chính xác tốt nhất, Bắt đầu/Tiếp tục/Luyện lại); màn làm bài (**không** "Bỏ qua"); kết quả ("Đã mở tầng n" / "Còn x%" / hoàn thành lộ trình) |
| FE-29 | SCR-015 Character Recognition | `scr-015-character-recognition.html` | 006 FR-004…009, FR-028 | Chọn chế độ (Nhìn chữ chọn nghĩa / Nghe chọn chữ) + cấp HSK; câu `ChoiceQuestion`; tổng kết. Bỏ dòng mastery |
| FE-30 | SCR-014 Character Writing | `scr-014-character-writing.html` | 006 FR-001…003, FR-029, FR-031…034 | 3 view: chọn chữ (tìm, lọc cấp, danh sách) · luyện một chữ (4 nút chế độ có lý do khoá; **khung viết 300×300 để trống** `data-testid="hanzi-canvas-slot"` — chưa cài `hanzi-writer`; nút "Xem lại nét", "Xem mẫu thêm", đếm ngược, kết quả "Đã ghi nhận"/"Chưa tính") · thử thách (đồng hồ 200 s, lỗi x/3, tiến độ 10 chữ) |

## Success Criteria

- [ ] 7 màn + mọi bước xem được ở `/__preview` đủ 4 trạng thái, 390 px không cuộn ngang
- [ ] Không còn chữ "thành thạo", "mở khóa khi", "ôn lại sau", "Bỏ qua câu này" (SCR-016)
- [ ] Chữ Hán luôn dùng `HanziText` (`font-hanzi`, `lang="zh-CN"`)
