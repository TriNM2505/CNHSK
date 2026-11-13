---
phase: 9
title: "Phụ lục 014 bộ thẻ công khai + menu"
status: pending
priority: P3
dependencies: [1]
effort: "spec 0,5 · BE 1,75 · FE 1,5 ngày công"
owner: "Xuân Huy"
---

# Phase 9: Phụ lục 014 (v1.1) — Bộ thẻ công khai + menu thư viện

## Overview
Không mở spec mới: nâng `specs/014-library/spec.md` lên **1.1.0** thêm SCR-052 Public Flashcard Deck List và menu điều hướng thư viện (việc còn treo). **Không migration** — `collections.is_public`, `shared_at`, `ck_coll_share`, `ix_coll_public` đã có.

## Context links
- `specs/014-library/` (spec, tasks, `contracts/openapi-library.yaml`), DeckController `/api/flashcard-decks`
- use-cases-05 (phần chia sẻ bộ thẻ), `screen-fields.md` SCR-052
- `cnhsk-web/src/shared/layout/navigation.ts`, `LearnerLayout.tsx`
- `V2__community.sql` `reactions`/`moderation_cases` có `target_type 'COLLECTION'` (báo cáo bộ thẻ vi phạm qua 009)

## Thiết kế đề xuất
- Chủ bộ thẻ bật/tắt công khai (đặt `shared_at`); danh sách công khai lọc HSK/từ khoá, sắp mới nhất; xem trước thẻ; **"Sao chép về thư viện của tôi"** → tạo bộ mới, lịch ôn reset (đúng `COPIED_REVIEW_SCHEDULE` ở `database.md` §14), tính vào giới hạn `max-decks`.
- Báo cáo vi phạm dùng luồng 009 (`target_type='COLLECTION'`); MODERATOR ẩn → `is_public=false`.
- Menu thư viện: mục cần leader chốt (đề xuất: Tra từ, Dịch, Sổ tay, Bộ thẻ của tôi, Bộ thẻ cộng đồng, Tài liệu tham khảo khi 018 xong).

## Implementation Steps (viết spec)
1. Thêm Nhóm 6 FR (công khai, danh sách, sao chép, báo cáo) + AT; bump version + changelog.
2. Bổ sung OpenAPI: `PUT /api/flashcard-decks/{id}/visibility`, `GET /api/public/flashcard-decks`, `GET /api/public/flashcard-decks/{id}`, `POST /api/flashcard-decks/{id}/copy`.
3. tasks.md 014: thêm T-16 (BE), T-17 (FE 052), T-18 (menu).

## Success Criteria
- [ ] Không migration; 014 v1.1.0 có changelog
- [ ] Mục menu được leader chốt trước khi giao T-18

## Risk Assessment
- Lộ ghi chú riêng tư nếu nhầm loại collection → chỉ `kind='FLASHCARD_DECK'` được công khai (test).
