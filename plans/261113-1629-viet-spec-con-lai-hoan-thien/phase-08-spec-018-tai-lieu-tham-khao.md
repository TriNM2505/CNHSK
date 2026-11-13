---
phase: 8
title: "Spec 018 tài liệu tham khảo"
status: pending
priority: P3
dependencies: [1]
effort: "spec 1,5 · BE 3 · FE 3 ngày công"
owner: "Xuân Huy"
---

# Phase 8: Spec 018 — Kênh, podcast và sách tham khảo

## Overview
Feature 7.1 + 7.2. Màn SCR-053 YouTube & Podcasts, SCR-054 Book Catalogue, SCR-055 Book Detail (công khai); SCR-072 Reference Management (quản trị). Thêm 1 bảng: `learning.reference_items` (H3, dự kiến V8 — H4; 023 giữ V7).

## Context links
- UC-117 Xem kênh YouTube và podcast, UC-118 Xem danh mục sách, UC-119 Quản lý danh mục tham khảo (use-cases-08)
- `feature-tree.md` §7.1, §7.2 ("Đã có" ở app cũ — chỉ danh mục, **không** lưu/phát nội dung có bản quyền)
- 014 (mẫu module + trang công khai `/api/public/...`), `audit_logs`

## Thiết kế đề xuất
- Bảng gộp (KISS, một bảng như phong cách `database.md`):
  `reference_items(id, kind CHECK IN ('YOUTUBE_CHANNEL','PODCAST','BOOK'), title, description, url, thumbnail_url, author, publisher, hsk_min, hsk_max, tags VARCHAR[], status CHECK IN ('DRAFT','PUBLISHED','ARCHIVED'), sort_order, created_by, updated_at, created_at)` + index `(kind, status, sort_order)`.
- Chỉ lưu **liên kết ngoài** + mô tả tự viết; ảnh bìa là URL ngoài hoặc không có; không tải file sách (bản quyền).
- Công khai: lọc theo loại, HSK, tag; chi tiết sách (055) có link mua/đọc ngoài.
- Quản trị (072): vai trò đề xuất `CONTENT_EDITOR` (UC-119 ghi actor nào thì theo UC); CRUD, sắp xếp, ẩn; kiểm URL `https`, chặn domain lạ? (để `questions.md`).
- Không embed iframe YouTube trong đợt này (CSP) — link mở tab mới.

## Requirements
- Functional: 3 màn công khai + 1 màn quản trị; khách xem được.
- Non-functional: URL chỉ `https`, độ dài giới hạn; sanitize mô tả (text thuần).

## Implementation Steps (viết spec)
1. §4 Data model + migration (số chốt khi merge; đề xuất SQL đầy đủ, `GRANT` cho `svc_app` theo `database.md` §10).
2. Error matrix: `REFERENCE_NOT_FOUND`, `INVALID_REFERENCE_URL`.
3. API `contracts/openapi-references.yaml`: `GET /api/public/references?kind=&hsk=&tag=`, `GET /api/public/references/{id}`, admin `GET/POST/PUT /api/admin/references`, `POST …/{id}/publish|archive`.
4. Dữ liệu mẫu tự soạn (≥ 10 mục) trong `docs/demo-data/` — không dùng tài liệu có bản quyền của Hoc Ba.

## Success Criteria
- [ ] Đúng 1 bảng mới, đã có trong RFC (H3); số migration xin leader
- [ ] Không lưu/phát nội dung bản quyền

## Risk Assessment
- Migration chạy Neon chỉ leader → báo leader ngay khi merge (L3 cũ).
- Spec độc lập hoàn toàn → làm sớm nhất được.
