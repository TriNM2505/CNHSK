---
phase: 5
title: "Spec 021 cuộc thi có thưởng"
status: pending
priority: P1
dependencies: [1]
effort: "spec 1,5 · BE 6,5 · FE 4,5 ngày công (bỏ trao thưởng hệ thống)"
owner: "Dũng"
---

# Phase 5: Spec 021 — Cuộc thi có thưởng

## Overview
Feature 5.8 (+5.8.1 chống gian lận). Màn: SCR-048 Contest List, 049 Contest Detail, 050 Contest Attempt, 051 Contest Results (LEARNER); SCR-070 Admin Contest List, 071 Create/Edit Contest (ADMIN). **Không bảng mới** — `community.contests` đã có.

## Context links
- UC-090 Xem danh sách, UC-091 Đăng ký, UC-092 Thi trong khung giờ (use-cases-06); UC-113 Tạo và quản lý cuộc thi (use-cases-08)
- `feature-tree.md` §5.8, §5.8.1; `V2__community.sql` `contests` (`ck_contest_time`, `ck_contest_rank`, `ck_contest_exam`); `attempts` kind `CONTEST`
- Engine thi 012 (`ExamController`, chấm đề, `served_items`); `ActiveTestApi`; `exams` (đề tạo bằng import 012/T-18 hoặc 023)

## Thiết kế đề xuất
- **Cuộc thi = một `exam` đã PUBLISHED + khung giờ.** Bài thi chạy bằng engine 012 với `PracticeSource.CONTEST` (⚿K1), `attempts.kind='CONTEST'`, `ref_id=contest_id`.
<!-- Updated: Validation Session 1 - ranh giới module + index unique -->
- **Ranh giới module (validate 13/11):** code cuộc thi ở module `community` (bảng `community.contests`); engine thi ở `learning`. ArchUnit chỉ cho community gọi learning qua `learning.api` → **thêm hợp đồng `learning.api.ContestAttemptApi`** (bắt đầu lượt CONTEST cho `examId` + hạn, nộp/chấm, đọc kết quả + xếp hạng theo `ref_id`), cài trong learning. Đây là **điểm khoá ⚿K8**, task đầu tiên của 021.
- **Một lượt/người/cuộc thi:** migration thêm partial unique index `attempts(user_id, ref_id) WHERE kind='CONTEST'` (số V xin leader, H4) — chặn cả hai request đồng thời.
- Vòng đời: `DRAFT` → `PUBLISHED` (bắt buộc `exam_id`) → tự `CLOSED` khi qua `ends_at` (job) → ADMIN công bố xếp hạng (`ranking_published_at ≥ ends_at`) → ban tổ chức liên hệ người đạt giải ngoài hệ thống. `CANCELLED` từ DRAFT/PUBLISHED khi chưa ai nộp.
- **Đăng ký (UC-091):** cần lưu ai đăng ký nhưng không có bảng → đề xuất dùng `attempts` trạng thái `IN_PROGRESS` tạo lúc bắt đầu thi, **bỏ bước đăng ký riêng** (đăng ký = vào thi trong khung giờ). Nếu leader bắt buộc đăng ký trước → dùng `community.reactions` không hợp → phải thêm bảng (ghi vào `questions.md`, mặc định không làm). `max_participants` đếm số `attempts` CONTEST.
- **Chống gian lận (5.8.1, H9):** một lượt/người/cuộc thi (index unique ở trên); giờ do server; nộp sau `ends_at` + ân hạn cấu hình → từ chối; thời gian làm bất thường → `suspicious=true`, ADMIN xem trước khi công bố; trợ lý bị chặn; không trả đáp án trước khi công bố.
- **Xếp hạng:** điểm DESC, `duration_ms` ASC, `submitted_at` ASC; loại `suspicious` khỏi bảng công bố.
- **Thưởng — leader chốt 13/11: không có phần thưởng trong hệ thống.** `prizes` JSONB chỉ là **mô tả chữ** (hạng → giải), chỉ đọc sau công bố. Sau khi công bố xếp hạng, trang kết quả ghi "Ban tổ chức sẽ liên hệ người đạt giải qua email đăng ký"; **chỉ ADMIN** thấy email người đạt giải ở trang quản trị (xem có audit, không xuất ra trang công khai — PII). Không đụng sổ cái 013, không phụ thuộc 013.

## Requirements
- Functional: 6 màn như trên; người học thấy cuộc thi sắp/đang/đã diễn ra; ADMIN CRUD, công bố hạng, xem liên hệ người đạt giải.
- Non-functional: race khi nhiều người nộp cùng lúc; hiển thị giờ theo VN; ADMIN không thi cuộc thi mình tạo (giống R9 của 013).

## Implementation Steps (viết spec)
1. US/FR chia 2 nhóm: người học (048–051), quản trị (070–071, gồm công bố hạng + xem liên hệ người đạt giải).
2. Error matrix dự kiến: `CONTEST_NOT_FOUND`, `CONTEST_NOT_OPEN`, `CONTEST_CLOSED`, `CONTEST_FULL`, `CONTEST_ALREADY_ATTEMPTED`, `CONTEST_RANKING_NOT_PUBLISHED`.
3. API `contracts/openapi-contests.yaml`: `GET /api/contests`, `GET /api/contests/{id}`, `POST /api/contests/{id}/attempts`, `GET /api/contests/{id}/results`; admin `GET/POST/PUT /api/admin/contests`, `POST …/{id}/publish`, `…/cancel`, `…/ranking/publish`, `GET …/{id}/winners` (có email, audit).
4. tasks.md: T-01 = ⚿K8 `ContestAttemptApi` (learning.api) + migration index unique; các task community sau đó. Không task nào chờ 013.

## Success Criteria
- [ ] Không bảng mới; quyết định "bỏ đăng ký riêng" ghi trong `questions.md`
- [ ] Không đụng sổ cái; email người đạt giải chỉ ADMIN thấy
- [ ] Mọi task code song song được (chỉ engine chờ ⚿K1)

## Risk Assessment
- Lộ email người đạt giải → endpoint `winners` chỉ ADMIN + audit; test trang công khai không có email.
- Index unique 1 lượt/người → xin số migration sớm (H4).
- `ContestAttemptApi` sai hình dạng làm 021 sửa lại → viết chữ ký đầy đủ trong spec §10, leader duyệt trước khi code.
- Phụ thuộc đề thi: dùng đề import của 012/T-18 khi 023 chưa xong.
