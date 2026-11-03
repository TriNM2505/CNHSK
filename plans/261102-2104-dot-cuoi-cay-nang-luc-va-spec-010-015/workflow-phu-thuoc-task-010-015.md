# Quy trình làm việc đợt cuối — task nào xong thì task nào được làm

Nguồn: `tasks.md` thật của 009 và 010–015 (`specs/009…015/tasks.md`). Tổng **92 task**: 009 còn 9 · 010: 13 · 011: 15 · 012: 19 · 013: 14 · 014: 15 · 015: 7.
Ký hiệu: `010/T-05` = task T-05 của spec 010. **⚿ = điểm khoá** (chữ ký dùng chung, người khác chờ — làm sớm, merge sớm).

---

## 1 · Bốn luật chống chặn nhau (leader chốt 2026-11-03)

| # | Luật | Vì sao |
|---|---|---|
| L1 | **Điểm khoá làm trước, kể cả khi là BE.** Mỗi luồng làm task FE nền (T-01) rồi làm ngay điểm khoá của mình trước các màn còn lại | 6 điểm khoá chặn 30+ task của người khác |
| L2 | **File dùng chung merge lần lượt** (`ErrorCode.java`, `SecurityConfig.java`, `ImportDataType`, `PracticeSource`/`ItemType`, `routeAccess.ts`, `application.yaml`): PR phải rebase lên `main` mới nhất trước khi xin review; mã lỗi mới luôn thêm **ngay trước `INTERNAL_ERROR` theo thứ tự merge thực tế** (thứ tự "sau mã của 0xx" trong spec chỉ là gợi ý) | Tránh conflict và test `containsExactly` vỡ |
| L3 | **Migration theo số:** V5 (010/T-05) → V6 (011/T-05). Leader chạy lên Neon ngay sau khi merge, rồi báo nhóm | V6 chạy sau V5; không ai khác chạy SQL trên Neon |
| L4 | **Engine practice chỉ một người sửa một lúc:** 012/T-06 merge trước 011/T-12 (cùng `PracticeSource`, `ItemType`, `PracticeApi`) | Cùng file, đổi enum |

**Điều kiện bắt đầu chung:** RFC đợt cuối đã đồng thuận, spec 010–015 `APPROVED` (2026-11-03). Spec nằm ở `specs/010…015`; RFC ở `reports/rfc-261103-thay-doi-tai-lieu-goc-dot-cuoi.md` cùng thư mục plan này.

---

## 2 · Sáu điểm khoá ⚿

| Điểm khoá | Người làm | Mở khoá cho |
|---|---|---|
| ⚿ `010/T-05` V5 (`Question`, cột KID) | B | 011/T-05, 012/T-08 |
| ⚿ `010/T-10` `KnowledgeTreeService` | B | 011/T-07, T-09, T-10 · 012/T-11, T-12, T-14 |
| ⚿ `012/T-06` engine: `questionId`, 3 nguồn mới | D | 011/T-07, T-12 |
| ⚿ `011/T-09` `MasteryReader` | C | 012/T-16 |
| ⚿ `013/T-12` `EntitlementApi` | E | 014/T-14 |
| ⚿ `015/T-04` `ClaudeClient` | A | 014/T-14 |

Ngoài ra `013/T-06` (`AuthApi` + `LedgerService`) chặn mọi task BE còn lại của 013 — cùng người E nên chỉ là thứ tự nội bộ.

---

## 3 · Phân công 5 luồng (leader chốt 2026-11-03; tên người điền khi giao)

Mỗi luồng giữ trọn spec chính; **014 chia theo khối** vì không còn ai trống trọn một spec (6 phần việc cho 5 người).

| Luồng | Spec chính | Thêm | Số task |
|---|---|---|---|
| **A** (người làm 009) | 009 (9 còn lại), 015 (7) | 012/T-18 soạn ngân hàng câu + đề · 014/T-13 `Segmenter` | **18** |
| **B** (người làm 002) | 010 (13) | 014 khối sổ tay: T-04, T-08, T-09 · 014/T-15 nối thật | **17** |
| **C** (người làm 003) | 011 (15) | 014 khối flashcard: T-05, T-10, T-11, T-12 | **19** |
| **D** (người làm 004) | 012 (18, trừ T-18) | — | **18** |
| **E** (người làm 006/005) | 013 (14) | 014 khối tra cứu + dịch: T-01, T-02, T-03, T-06, T-07, T-14 | **20** |
| Leader | Review mọi PR, chạy V5/V6, `.env` (`CARD_CODE_PEPPER`, `ANTHROPIC_API_KEY`), sửa tài liệu theo RFC | | — |

Ghi chú: 014/T-01 (nền FE `library`) do E làm đầu tiên — B và C chờ task này mới làm màn của khối mình.

---

## 4 · Thứ tự từng luồng (→ = làm sau; [chờ X] = phải đợi X merge)

### A — 009 → 015 (+ nội dung)

```
009/T-06 → T-07 → … → T-14                 (theo tasks.md 009)
song song ngay tuần 1:
  ⚿ 015/T-04 ClaudeClient                   (không chờ ai; E cần cho 014/T-14)
  015/T-03 tài liệu hướng dẫn               (viết dần, bổ sung khi 010–014 merge)
  012/T-18 soạn ≥ 30 câu/cấp + 3 đề (CSV)   (soạn ngay; nạp thử [chờ 010/T-08, 012/T-09])
  014/T-13 Segmenter                        (không chờ ai)
sau 009:
  015/T-01 → T-02 → T-05 → T-06 → T-07      (T-06 chặn đầy đủ khi [012/T-06] đã merge)
```

### B — 010 (+ khối sổ tay 014)

```
010/T-01 → ⚿ T-05 (V5; leader chạy Neon) → ⚿ T-10
         → T-02 → T-03 ; T-04
         → T-06 → T-07 → T-08 → T-09         (import; T-06 [chờ 010/T-05])
         → T-11 → T-12 → T-13
014/T-08 → T-09                              (không chờ ai — làm khi rảnh giữa 010)
014/T-04 [chờ 014/T-01 của E]
014/T-15 nối thật 014 [chờ mọi task 014]    (cuối tuần 3)
```

### C — 011 (+ khối flashcard 014)

```
011/T-01 → T-06 (thuần) → T-11 (thuần, FSRS)
         → T-02 → T-03 → T-04
         → T-05 V6 [chờ 010/T-05; leader chạy Neon sau V5]
         → T-07 [chờ 010/T-10, 012/T-06] → T-08
         → ⚿ T-09 [chờ 010/T-10] → T-10
         → T-12 [chờ 012/T-06 — L4] → T-13 → T-14 → T-15
014/T-10 → T-11                              (không chờ ai)
014/T-12 [chờ 011/T-11 — của chính C]
014/T-05 [chờ 014/T-01 của E]
```

### D — 012

```
012/T-01 → ⚿ T-06 (engine, full verify 003–008) → T-13 (thuần)
         → T-02 → T-03 → T-04 → T-05
         → T-07 [chờ 010/T-09 — cùng file import] → T-09
         → T-08 [chờ 010/T-05] → T-10
         → T-11 [chờ 010/T-10] → T-12 → T-14 → T-15
         → T-16 [chờ 011/T-09]
         → T-17 → T-19 [chờ 012/T-18 của A]
```

### E — 013 (+ khối tra cứu, dịch 014)

```
013/T-01 → T-05 (thuần) → T-06 (AuthApi, Ledger)
         → T-02 → T-03 → T-04
         → T-07 → T-08 → T-09 ; T-10 ; T-11 ; ⚿ T-12 ; T-13
         → T-14 nối thật 013
014/T-01 (nền FE — làm sớm, B và C chờ) → T-02 → T-03
014/T-06 → T-07                              (không chờ ai)
014/T-14 dịch [chờ 013/T-12, 015/T-04, 014/T-13]
```

---

## 5 · Lịch 3 tuần

| Tuần | Mục tiêu | Mốc kiểm |
|---|---|---|
| **1** | RFC đồng thuận (2 ngày đầu) · mọi T-01 FE · **6 điểm khoá merge** · task thuần (011/T-06, T-11; 012/T-13; 013/T-05; 014/T-13) · V5 rồi V6 lên Neon · bắt đầu soạn nội dung (012/T-18, 015/T-03) | Cuối tuần 1: 6 ⚿ đã merge; V5, V6 đã chạy |
| **2** | FE các màn còn lại · BE toàn bộ (không còn ai chờ điểm khoá) · 009 xong | Cuối tuần 2: mọi task BE ✅ trừ nối thật |
| **3** | Nối thật mọi spec (010/T-13, 011/T-15, 012/T-17, 013/T-14, 014/T-15, 015/T-07) · nạp nội dung demo · thử thật bài xếp trình độ (012/T-19) · bộ 20 câu trợ lý · phase 8 nghiệm thu | Demo đầu-cuối (plan.md, Acceptance criteria) |

**Đường găng (chậm là trễ cả đợt):** `010/T-05 → 010/T-10 → 011/T-09 → 012/T-16` và `012/T-06 → 011/T-07, T-12`. Leader review các PR này **trước** mọi PR khác.

---

## 6 · Bảng tra nhanh: task này chờ gì (chỉ phụ thuộc xuyên spec)

| Task | Chờ |
|---|---|
| 011/T-05 | 010/T-05 |
| 011/T-07 | 010/T-10, 012/T-06 |
| 011/T-09, T-10 | 010/T-10 |
| 011/T-12 | 012/T-06 |
| 012/T-07 | 010/T-09 |
| 012/T-08 | 010/T-05 |
| 012/T-11, T-12, T-14 | 010/T-10 |
| 012/T-16 | 011/T-09 |
| 012/T-19 | 012/T-18 |
| 013/* | chỉ 001 (đã xong) |
| 014/T-04, T-05 | 014/T-01 |
| 014/T-12 | 011/T-11 |
| 014/T-14 | 013/T-12, 015/T-04, 014/T-13 |
| 015/T-06 (chặn đủ 3 nguồn) | 012/T-06 |

Task không có trong bảng: chỉ chờ task trước đó **trong cùng spec** theo `tasks.md`.
