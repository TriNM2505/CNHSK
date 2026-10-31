# Tasks: Nhận diện và viết chữ Hán — Spec #006

> Nguồn: [`spec.md`](spec.md) v1.0.0 · [`contracts/openapi-hanzi.yaml`](contracts/openapi-hanzi.yaml)

**Tổng: 9 task**, ≤ 4 giờ/task, một task một PR `agent/006-<task>`. Quy ước chung: `specs/001-auth-rbac/tasks.md`.
**Điều kiện:** spec 003 đợt 0 + T-13 xong. Q1 (§9, `hanzi-writer`) đã duyệt trước T-06.

| ID | Task | File | Spec · AT | TT |
|---|---|---|---|---|
| T-01 | Thêm 3 nguồn, 3 loại câu; `CharacterQueryService` + `GET /api/characters`, `GET /api/characters/{id}` | `…/learning/hanzi/*` | FR-001…FR-003 · AT-01, AT-02 | ✅ |
| T-02 | `RecognitionService` + `POST /recognition-sessions`: chọn chữ, nhiễu cùng bộ thủ, đồng âm | `…/learning/hanzi/RecognitionService.java`, mở rộng `ChoiceItemFactory` (tầng 0 theo `radical`) | FR-004…FR-009 · AT-03…AT-07 | ✅ |
| T-03 | `WritingService` + `POST /api/writing/attempts`: luật FR-011…FR-015, ghi lượt ghi sẵn, `meta`, 10 phút, giới hạn 300/giờ | `…/learning/hanzi/WritingService.java` | FR-010…FR-019 · AT-08…AT-16 | ⬜ |
| T-04 | `WritingChallengeService` + `POST /api/writing/challenges` (tập đủ điều kiện, chọn 10, tiếp tục) | `…/learning/hanzi/WritingChallengeService.java` | FR-020…FR-023 · AT-17, AT-18 | ⬜ |
| T-05 | `WritingChallengeHandler` (`validateAnswer`, `enrichMeta`, `onFinish`) | `…/learning/hanzi/WritingChallengeHandler.java` | FR-024…FR-027 · AT-19…AT-22 | ⬜ |
| T-06 | Thêm `hanzi-writer@3.7.3`; tạo nền `HanziCanvas` (`charDataLoader` ghim + `VITE_HANZI_DATA_URL`, lỗi dữ liệu, co giãn) và test mock. AT-23 còn phần T-08/T-09. | `cnhsk-web/src/features/hanzi/components/HanziCanvas.tsx`, `cnhsk-web/src/features/hanzi/components/HanziCanvas.test.tsx`, `cnhsk-web/package.json`, `cnhsk-web/package-lock.json`, `cnhsk-web/.env.example` | FR-030 · AT-23 (một phần; phần còn lại T-08/T-09) | ✅ |
| T-07 | SCR-015 Character Recognition (component chung spec 003) | `cnhsk-web/src/features/hanzi/pages/Scr015CharacterRecognitionPage.tsx` | FR-028 | ⬜ |
| T-08 | SCR-014 chọn chữ + luyện 3 chế độ STROKE/RECALL/DICTATION | `cnhsk-web/src/features/hanzi/**` | FR-029, FR-031…FR-033 · AT-23 | ⬜ |
| T-09 | SCR-014 thử thách (đồng hồ, lỗi tối đa, tự nộp, giữ kết quả khi mất mạng) | `cnhsk-web/src/features/hanzi/**` | FR-034 · AT-23 | ⬜ |

**Rủi ro cao:** T-03 (luật hợp lý — test từng mã lỗi), T-08 (tương tác `hanzi-writer` trên cảm ứng — kiểm tay trên điện thoại).
