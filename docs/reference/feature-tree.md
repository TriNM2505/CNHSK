# Feature Tree — CNHSK

> **Bản final.** Cập nhật 2026-10-06 · **33 tính năng** · 7 nhóm
> (32 tính năng phải làm — 5.4 Gia sư đã cắt khỏi scope)
> Thay thế bản 1 (30 tính năng) sau tài liệu chốt của nhóm ngày 22/09

## Quy ước

| Trường | Ý nghĩa |
|---|---|
| **Mô tả** | Người học làm được gì |
| **Client** | Web · Mobile · Shared · Admin |
| **Phạm vi** | **MVP** (3 tháng) · **V2** · **V3+** |
| **Trạng thái** | Đã có · Mở rộng · Làm mới |
| **Schema** | `L` = `learning` · `C` = `community` · `A` = `auth` — ba schema trong **một database** |

> **Tham chiếu UI — đồng bộ 2026-10-09:** số tính năng ở tài liệu này không phải số
> màn hình. Bộ UI đã duyệt có **79 màn**, giữ nguyên ID và tên. Chi tiết trường chỉ
> duy trì tại [screen-fields.md](screen-fields.md); bố cục và flow tại
> [design.md](design.md). Các tham chiếu màn dưới đây không thay đổi phạm vi nghiệp vụ.

> **Cập nhật 2026-09-25 theo kiến trúc bản 5.** Ký hiệu `L`/`C` trước đây chỉ *hai service riêng*;
> giờ chỉ **schema trong cùng một ứng dụng** (`cnhsk-api`, cổng 8080). Không còn cổng 8081,
> không còn đồng bộ 2h sáng — module gọi nhau bằng lời gọi hàm.
> Chi tiết: `docs/kien-truc.md` · `docs/database.md`

## Tổng quan thay đổi so với bản 1

| Loại | Số lượng | Chi tiết |
|---|---|---|
| Tính năng mới | **8** | Học từ theo chủ đề · Học qua video · Translate đầy đủ · Chia sẻ flashcard · Kiểm duyệt bài · Cuộc thi có thưởng · Gói Free/Premium · Hệ thống 6 role |
| Chuyển nhóm | 2 | Game Box và Game gõ pinyin → Community |
| Cắt bớt | 2 | Gia sư (dùng bên thứ ba) · Sổ tay bỏ liên kết học |
| Làm rõ | 3 | Ngưỡng cổng 90% · Trợ lý ảo xuyên suốt · Đề đủ HSK 1–6 |

---

# 1 · Học & luyện tập

## 1.1 · Luyện viết chữ Hán — Đã có

| | |
|---|---|
| **Mô tả** | Tập viết theo thứ tự nét chuẩn. 5 chế độ: Theo nét · Nhớ rồi viết · Thử thách · Phát âm · Nghe chép |
| **Client** | Shared · Web/Mobile |
| **Phạm vi** | MVP |
| **Trạng thái** | Đã có — clone từ app.CNHSK.today, nối vào tài khoản server |
| **Bảng DB** | `characters` `user_knowledge_state` (L) |
| **Kỹ thuật** | `hanzi-writer` 3.7.3 (MIT) |
| **Nghiệm thu** | Viết đúng nét → mastery tăng; tiến độ đồng bộ web ↔ mobile |

## 1.2 · Nhận diện chữ — Đã có

| | |
|---|---|
| **Mô tả** | Trắc nghiệm nhận mặt chữ. Nhìn chữ chọn nghĩa · nghe âm chọn chữ |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Trạng thái** | Đã có |
| **Bảng DB** | `questions` `question_options` (L) |
| **Nghiệm thu** | Đáp án nhiễu là chữ gần giống, không trùng đáp án đúng |

## 1.3 · Luyện phát âm — Đã có

| | |
|---|---|
| **Mô tả** | 8 tầng: 23 thanh mẫu · 39 vận mẫu · 4 thanh điệu · biến điệu |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Trạng thái** | Đã có — cần bổ sung bảng DB |
| **Giới hạn** | Nghe và chọn, **chưa chấm phát âm qua micro** |
| **Bảng DB** | `pronunciation_units` `pronunciation_stages` `user_pronunciation_progress` (L) |
| **Nghiệm thu** | Qua tầng 1 mới mở tầng 2 |

## 1.4 · Ngữ pháp HSK có lặp ngắt quãng — Mở rộng

| | |
|---|---|
| **Mô tả** | 593 điểm ngữ pháp, tự nhắc ôn theo trí nhớ |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Trạng thái** | Mở rộng — chuyển từ Leitner sang FSRS, lên server |
| **Phân bổ** | HSK1 70 · HSK2 78 · HSK3 96 · HSK4 95 · HSK5 70 · HSK6 50 · HSK7-9 134 |
| **Bảng DB** | `grammar_points` `knowledge_points` `user_knowledge_state` (L) |
| **Nghiệm thu** | Đúng 3 lần liên tiếp → giãn ngày ôn |

## 1.5 · Học từ vựng theo chủ đề — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Học từ mới theo từng chủ đề (Gia đình, Số đếm, Thời gian…), mô hình như phần Learn của Hanzii |
| **Client** | Web chính · Mobile · Shared |
| **Phạm vi** | **MVP** |
| **Trạng thái** | Làm mới |
| **Luồng học một chủ đề** | ① Xem danh sách từ mới → ② Học từng từ (chữ, pinyin, nghĩa, ví dụ, audio) → ③ Luyện nhận diện → ④ Luyện nghe → ⑤ Luyện viết → ⑥ Kiểm tra cuối chủ đề |
| **Mỗi từ hiển thị** | Chữ Hán · pinyin · âm Hán-Việt · nghĩa · câu ví dụ · audio · từ liên quan |
| **Theo dõi** | Mỗi từ có trạng thái: Chưa học · Đang học · Đã thuộc. Tính % hoàn thành chủ đề |
| **Bảng DB** | `topics` `topic_words` (mới) `words` `user_knowledge_state` (L) |
| **API** | `GET /api/topics` · `GET /api/topics/{id}/words` · `POST /api/topics/{id}/progress` |
| **Phụ thuộc** | 3.1 Mastery · 6.3 Kho câu hỏi |
| **Liên kết quan trọng** | **Đây là nền của 3.2** — cây chủ đề lấy % hoàn thành từ đây để quyết định mở chủ đề tiếp |
| **Nghiệm thu** | Học đủ từ trong chủ đề → % tăng đúng; đạt 90% → chủ đề sau mở khoá |

## 1.6 · Học qua video — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Học tiếng Trung qua video YouTube với **ba cơ chế học chủ động**: phụ đề tương tác · Dictation (nghe rồi gõ lại) · Shadowing (nói nhái, có phản hồi phát âm) |
| **Client** | Web chính · Mobile |
| **Phạm vi** | **V2** |
| **Trạng thái** | Làm mới |
| **Luồng dự kiến** | ① Chọn video theo cấp HSK/chủ đề → ② Xem với phụ đề song ngữ → ③ Bấm từ trong phụ đề xem nghĩa → ④ Lưu từ vào sổ tay → ⑤ **Dictation**: nghe một câu, gõ lại, server chấm → ⑥ **Shadowing**: đọc theo câu đó, nhận phản hồi phát âm |
| **Phụ đề tương tác** | Phụ đề Trung + pinyin + nghĩa Việt. Bấm từ nào hiện popup nghĩa từ đó |
| **Điều khiển học** | Tua lại câu đang nghe · giảm tốc độ · lặp một câu · ẩn/hiện từng lớp phụ đề |
| **Dictation** 🆕 | Nghe một câu phụ đề → gõ lại chữ Hán → server so chuỗi, trả **hai điểm riêng**: `char_score` (đúng chữ) và `tone_score` (đúng thanh điệu). Chấm bằng cách tách pinyin thành phụ âm đầu · phần vận · thanh điệu rồi so từng cặp |
| **Shadowing** 🆕 | Đọc theo câu → ghi âm → **server** gửi tới dịch vụ đánh giá phát âm → trả điểm theo **âm tiết**. Tính vào hạn mức lượt (`BUS-03`). Không lưu file audio |
| **Bảng DB** | `videos` + `subtitles` JSONB (L) · **`dictation_attempts`** 🆕 · **`shadowing_attempts`** 🆕. Tiến độ xem: `user_progress(target_type='VIDEO', position_ms)` |
| **API** | `GET /api/videos` · `GET /api/videos/{id}/subtitles` · `POST /api/videos/{id}/progress` · `POST /api/videos/{id}/dictation` 🆕 · `POST /api/videos/{id}/shadowing` 🆕 |
| **Nguồn video** | **Nhúng YouTube** — chốt 2026-10-08. `videos.source_type = 'YOUTUBE'`, `external_id` lưu YouTube video ID |
| **Cảnh báo bản quyền** | Không tải video của người khác về máy chủ. Chỉ nhúng, không lưu. Phụ đề do nhóm tự làm và gắn timestamp |
| **Khối lượng** | **Lớn** — 3–5 tuần. Phụ đề + Dictation ~1 tuần · Shadowing ~1 tuần · phần khó nhất vẫn là đồng bộ phụ đề theo thời gian video |
| **Nghiệm thu** | Bấm từ hiện đúng nghĩa · lưu từ vào sổ tay được · lặp câu chạy đúng · **Dictation phân biệt được sai chữ với sai thanh** · Shadowing trả điểm từng âm tiết |

> ✅ **Đã khảo sát schinese.net** — 2026-10-08, xác nhận chéo hai nguồn độc lập.
> Trang này dựa trên **Dictation + Shadowing** chứ không phải chỉ phụ đề tương tác, và
> nhấn mạnh *"học chủ động thay vì xem thụ động"*. Mô tả cũ của mục này chỉ có phần
> **vỏ** (player + phụ đề) mà thiếu phần **ruột**. Đã sửa.
> Nguồn: `schinese.net/zh` · `thucnguyen.substack.com/p/cung-ai-hoc-tieng-trung-va-ngoai-ngu`

> ⚠️ **Hai giới hạn kỹ thuật đã xác minh — biết trước khi code:**
>
> **1 · Dịch vụ đánh giá phát âm không chấm thanh điệu riêng cho tiếng Trung.**
> Azure Pronunciation Assessment hỗ trợ `zh-CN` với điểm theo âm tiết, nhưng *Prosody*
> chỉ có ở `en-US` và không tài liệu hoá điểm thanh điệu. Shadowing nói được *"âm tiết
> này chưa đúng"* nhưng **không chỉ ra "bạn đọc thanh 2 thành thanh 3"** — đúng cái
> người Việt sai nhiều nhất. **Dictation bù được chỗ này** nhờ có `tone_score` riêng.
>
> **2 · YouTube iframe không lặp được đoạn.** `loop=1` chỉ dùng với `playlist=`. Phải
> tự đọc `getCurrentTime()` mỗi ~100ms rồi `seekTo()`. Độ chính xác YouTube ≈ **±250ms**,
> nên chỉ bật lặp cho câu ≥ 3 giây.

> **[CHỜ CHỐT]** Cách trả điểm Shadowing và hạn mức lượt — chủ dự án quyết **cuối dự án**.

> ✅ **`NO_PROGRESS_TABLE` đã giải quyết.** Cảnh báo "thiếu bảng tiến độ" ở bản trước
> không còn đúng: `user_progress` có `target_type='VIDEO'` và `position_ms`
> (`database.md` §4.9). `UC-030` cần bỏ mã lỗi này.

---

# 2 · Luyện thi HSK

## 2.1 · Làm đề thi thử — Mở rộng

| | |
|---|---|
| **Mô tả** | Làm đề HSK **để luyện tập**: làm bài → nộp → chấm điểm → xem đáp án và lời giải |
| **Client** | Web chính · Mobile · Shared |
| **Phạm vi** | MVP |
| **Trạng thái** | Mở rộng — engine đã chạy |
| **Phạm vi đề** | **Đủ HSK 1–6**. Data thầy cung cấp |
| **Đã có** | HSK 1–4, riêng HSK 1 có 17 bộ đề |
| **Việc cần làm** | Bổ sung HSK 5–6 · gắn nhãn kiến thức · lưu kết quả lên server |
| **Bảng DB** | `exams` `exam_sections` `questions` `attempts` `attempt_answers` (L) |
| **Chấm điểm** | **Bắt buộc ở server** |
| **Nghiệm thu** | Nộp bài hiện điểm + đáp án + lời giải; kết quả đổ về mastery |

> **KHÔNG làm — mô phỏng kỳ thi thật:** đếm ngược đúng giờ chuẩn HSK · môi trường thi nghiêm ngặt · chống gian lận khi thi. **Lý do:** không đủ thời gian. Để sau nếu còn thời gian.

## 2.2 · Luyện theo dạng câu hỏi — Mở rộng

| | |
|---|---|
| **Mô tả** | Luyện riêng một dạng bài của đề HSK |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **7 dạng** | `MULTIPLE_CHOICE` · `TRUE_FALSE` · `IMAGE_MATCH` · `SENTENCE_MATCH` · `FILL_BLANK` · `SENTENCE_ORDER` · `ESSAY` |
| **Bảng DB** | `questions` `question_knowledge_points` (L) |
| **Liên kết** | Nơi 3.3 AI sinh bài đẩy bài luyện vào |
| **Nghiệm thu** | Lọc đúng theo dạng và cấp HSK |

## 2.3 · Phân tích lỗi sai — Làm mới

| | |
|---|---|
| **Mô tả** | Sau mỗi bài thi chỉ rõ sai ở đâu, vì sao |
| **Client** | Web chính · Mobile |
| **Phạm vi** | MVP |
| **Màn hình kết quả** | Số câu đúng/sai · kết quả từng kỹ năng · thời gian · so sánh lần trước |
| **Từng câu sai** | Đáp án đúng · lời giải · điểm kiến thức liên quan · nút luyện ngay |
| **Tổng kết** | 3–5 điểm yếu nhất, xếp theo mức độ |
| **Phụ thuộc** | **6.3 Nhãn kiến thức** — không có nhãn thì không chạy |
| **Nghiệm thu** | Mỗi câu sai chỉ đúng điểm kiến thức; bấm "luyện ngay" mở đúng dạng |

---

# 3 · Lộ trình thông minh

> Mô hình **adaptive learning** trên **cây tri thức**.

## 3.1 · Đo mức thành thạo (mastery) — Mở rộng

| | |
|---|---|
| **Mô tả** | Hệ thống biết người học nắm chắc phần nào, yếu phần nào |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Đơn vị đo** | Từng điểm kiến thức: từ vựng · ngữ pháp · chữ · kỹ năng |
| **Cập nhật khi** | Làm bài thi · luyện tập · học từ theo chủ đề · **chơi game (tức thì)** |
| **Bảng DB** | `knowledge_points` `user_knowledge_state` (L) |
| **Kỹ thuật** | FSRS |
| **Index bắt buộc** | `(user_id, due_at)` và `(user_id, mastery)` |
| **Nghiệm thu** | Đúng → mastery tăng, hạn ôn giãn; sai → giảm và hạn ôn gần lại. **Chơi game xong mastery đổi ngay, không phải chờ** |

> **Bản 5 cải thiện:** trước đây điểm game đẩy từ Community sang Học tập lúc 2h sáng nên mastery
> trễ tới một ngày. Giờ hai module cùng tiến trình — `MasteryUpdater.applyGameResult()` chạy
> **cùng transaction** với lúc lưu điểm.

## 3.2 · Cây chủ đề có cổng kiểm tra — Làm mới

| | |
|---|---|
| **Mô tả** | Cây tri thức: học đạt **90%** một chủ đề thì mở chủ đề tiếp theo |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Ngưỡng qua cổng** | **90%** — chốt trong tài liệu nhóm |
| **Nếu chưa đạt** | Hệ thống chỉ sinh bài cho phần yếu, không bắt học lại cả chủ đề |
| **Bảng DB** | `topics` `topic_knowledge_points` `user_topic_progress` (L) |
| **Phụ thuộc** | **1.5 Học từ theo chủ đề** — % hoàn thành lấy từ đó |
| **Nghiệm thu** | Đạt 90% → chủ đề sau mở; dưới 90% → vẫn khoá |

## 3.3 · AI tự sinh bài luyện theo điểm yếu — Làm mới

| | |
|---|---|
| **Mô tả** | AI sinh câu hỏi mới bám đúng phần đang yếu, có kiểm duyệt |
| **Client** | Shared |
| **Phạm vi** | MVP |
| **Chọn điểm yếu** | Xếp hạng mastery từ thấp lên, ưu tiên mục đến hạn ôn |
| **Kiểm duyệt** | Mọi câu AI sinh vào hàng đợi duyệt (6.6). Chỉ câu đã duyệt mới đến người học |
| **Tái sử dụng** | Câu đã duyệt vào kho chung, dùng lại cho người khác cùng điểm yếu |
| **Bảng DB** | `ai_generation_jobs` `ai_usage_quota` `questions` (L) |
| **Chạy nền** | Không bắt người học chờ |
| **Nghiệm thu** | Sinh đúng điểm yếu; vượt hạn mức báo rõ; câu chưa duyệt không hiện |

## 3.4 · Thống kê tiến độ cá nhân — Làm mới

| | |
|---|---|
| **Mô tả** | Người học nhìn thấy mình đang ở đâu và tiến bộ ra sao |
| **Client** | Web chính · Mobile |
| **Phạm vi** | MVP |
| **Hiển thị** | Ngày học liên tiếp · tổng từ/chữ đã thuộc · biểu đồ 7/30/90 ngày · điểm thi qua các lần · bản đồ mạnh-yếu theo kỹ năng · **% hoàn thành từng chủ đề trên cây tri thức** |
| **Bảng DB** | `study_sessions` `attempts` `user_knowledge_state` `user_topic_progress` (L) |
| **Lưu ý** | Mục "Analytics" của app cũ là dashboard GA4 đo lưu lượng web — **khác hoàn toàn** |
| **Nghiệm thu** | Số liệu khớp hoạt động thật; đọc được trên màn hình điện thoại |

## 3.5 · Nhắc lịch học tự động — Làm mới

| | |
|---|---|
| **Mô tả** | Nhắc đúng lúc kiến thức sắp quên |
| **Client** | Web · Email · Mobile (đẩy) |
| **Phạm vi** | MVP (web + email) · V2 (đẩy mobile) |
| **Tùy chỉnh** | Chọn giờ nhắc · tắt bật từng kênh |
| **Bảng DB** | `user_notification_settings` `user_devices` (L) |
| **Nghiệm thu** | Đặt 20h thì nhận nhắc lúc 20h giờ Việt Nam |

---

# 4 · Thư viện tra cứu

## 4.1 · Tra cứu và dịch — Mở rộng ⭐

| | |
|---|---|
| **Mô tả** | Tra từ điển **và** dịch câu/đoạn văn, mô hình như hanzii.net/translate |
| **Client** | Web chính · Mobile · Shared |
| **Phạm vi** | MVP |
| **Trạng thái** | Mở rộng — gộp tra cứu và dịch thành một |
| **Tra bằng** | Chữ Hán · pinyin · nghĩa Việt · bộ thủ · số nét |
| **Kết quả tra chữ** | Pinyin · nghĩa · Hán-Việt · cấu tạo · câu chuyện chữ · animation nét · từ ghép chứa chữ |
| **Dịch câu/đoạn** | Trung ↔ Việt. Kết quả kèm **pinyin** và **tách từ** — bấm từng từ xem nghĩa |
| **Nối vào học** | Từ trong kết quả thêm được vào sổ tay và flashcard |
| **Bảng DB** | `characters` `words` `grammar_points` `translation_history` (L) |
| **Cache** | Redis `learn:dict:*` và `learn:translate:*` |
| **Chi phí** | Dịch gọi API ngoài, **tốn tiền mỗi lượt** → cần hạn mức (xem 6.1) |
| **Giới hạn** | Chặn đoạn quá dài (2.000 ký tự) |
| **Nghiệm thu** | Dịch câu 20 từ dưới 3 giây; bấm từ trong kết quả mở được nghĩa |

## 4.2 · Sổ tay ghi chú cá nhân — Làm mới

| | |
|---|---|
| **Mô tả** | Sổ tay **thuần ghi chú** — người học tự note những gì cần |
| **Client** | Web chính · Mobile |
| **Phạm vi** | MVP |
| **Viết gì cũng được** | Mẹo nhớ chữ · câu mẫu · lỗi hay sai · tóm tắt bài học |
| **Tổ chức** | Nhiều ghi chú · tiêu đề · gắn thẻ · tìm kiếm theo nội dung |
| **Bảng DB** | `notes` (L) |
| **Thay đổi so với bản 1** | **Bỏ liên kết vào học** — không còn `note_links`, từ trong ghi chú không tự vào lịch ôn. Đơn giản hơn |
| **Nghiệm thu** | Viết, sửa, xoá, tìm kiếm ghi chú |

## 4.3 · Flashcard — Làm mới

| | |
|---|---|
| **Mô tả** | Ôn từ vựng bằng thẻ lật theo lịch tự động. **Chia sẻ bộ thẻ lên cộng đồng được** |
| **Client** | Shared |
| **Phạm vi** | MVP (dùng riêng) · V2 (chia sẻ) |
| **Cách ôn** | Mặt trước chữ, mặt sau nghĩa + pinyin. Tự đánh giá nhớ/quên → giãn hoặc rút ngắn ngày ôn |
| **Nguồn thẻ** | Tự tạo · bộ dựng sẵn theo cấp HSK · **bộ người khác chia sẻ** |
| **Chia sẻ** | Đánh dấu bộ là công khai → hiện trên cộng đồng → người khác sao chép về dùng |
| **Bảng DB** | `flashcard_decks` `flashcards` (L) — chia sẻ lên cộng đồng đọc **thẳng** cột `is_public`, không còn bảng `shared_decks` |
| **Nghiệm thu** | Thẻ "quên" xuất hiện lại sớm hơn; bộ chia sẻ sao chép về được |

---

# 5 · Cộng đồng & thi đua

> Toàn bộ nhóm này ở **module `community`** — schema `community`, cùng ứng dụng `cnhsk-api` (8080).
> Đường dẫn API giữ tiền tố `/api/community/**` để sau này tách service không phải sửa client.

## 5.1 · Blog cộng đồng có kiểm duyệt — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Mạng xã hội học tập kiểu Facebook: đăng bài, hỏi bài, chia sẻ kiến thức |
| **Client** | Web chính · Mobile |
| **Phạm vi** | V2 |
| **Trạng thái** | Làm mới |
| **Hiện trạng app cũ** | Blog có **đúng 1 bài**, chỉ admin đăng |
| **Luồng đăng bài** | Người dùng viết → **trạng thái PENDING** → role Manager duyệt → ACCEPT thì hiện công khai, REJECT thì báo lý do |
| **Tương tác** | Bình luận (lồng nhau) · thích · theo dõi người khác |
| **Phân loại** | Chia sẻ kinh nghiệm · hỏi bài · tìm bạn học · văn hóa · việc làm |
| **Bảng DB** | `posts` `comments` `likes` `follows` `moderation_reports` (C) — nhật ký duyệt gộp vào `posts.reviewed_by` · `reviewed_at` · `review_reason` |
| **Vấn đề cần lường** | Kiểm duyệt thủ công **không mở rộng được** — 100 bài/ngày là Manager không duyệt xuể. Cân nhắc: chỉ duyệt bài của tài khoản mới, hoặc duyệt sau khi có người báo cáo |
| **Nghiệm thu** | Bài chưa duyệt không ai thấy trừ tác giả; Manager duyệt xong bài hiện ngay |

## 5.2 · Quiz theo chủ đề — Làm mới

| | |
|---|---|
| **Mô tả** | Mỗi chủ đề có quiz riêng cho cộng đồng chơi, **mỗi quiz một bảng xếp hạng riêng** |
| **Client** | Web chính · Mobile |
| **Phạm vi** | V2 |
| **Cách chơi** | Chơi bất cứ lúc nào, không cần chờ nhau |
| **Tính điểm** | Số câu đúng, bằng điểm thì xét thời gian |
| **Chống gian lận** | Chỉ tính **lần làm đầu tiên** cho mỗi bộ đề |
| **Bảng DB** | `quiz_sets` `quiz_questions` `quiz_attempts` `quiz_answers` (C) |
| **Nguồn câu hỏi** | **Đọc thẳng** `learning.questions` (status `APPROVED`) qua `ContentLookup` |
| **Lưu ý** | Chơi quiz **không cập nhật mastery** — cần nói rõ trên giao diện |
| **Nghiệm thu** | Làm lại lần 2 vẫn chơi được nhưng không đổi thứ hạng. **Thầy duyệt câu hỏi xong là quiz dùng được ngay** |

> **Bản 5 cải thiện:** trước đây câu hỏi là bản sao đồng bộ lúc 2h sáng nên quiz thấy câu mới
> trễ tới một ngày. Giờ đọc thẳng bảng gốc — duyệt xong dùng được ngay.

## 5.3 · Bảng xếp hạng công khai — Làm mới

| | |
|---|---|
| **Mô tả** | Xếp hạng theo từng chủ đề và từng game |
| **Client** | Web chính · Mobile |
| **Phạm vi** | V2 |
| **Cách xếp** | Điểm trước, bằng điểm thì xét thời gian |
| **Lọc** | Tuần · tháng · mọi thời điểm |
| **Redis** | `rank:topic:{id}:{period}` — `ZADD` ghi · `ZREVRANK` tra hạng (O(log N), dưới 1ms) |
| **Mẹo hai tiêu chí** | `score = điểm × 1.000.000 − giây_hoàn_thành` |
| **Bảng DB** | `ranking_entries` (C) — bản gốc dựng lại Redis khi mất |
| **Nghiệm thu** | Redis mất vẫn dựng lại được; cùng điểm ai nhanh hơn đứng trên |

## 5.4 · Gia sư — CẮT KHỎI SCOPE

| | |
|---|---|
| **Quyết định** | **Dùng bên thứ ba.** Hệ thống không làm mảng này |
| **Lý do** | Không phải thế mạnh của sản phẩm |
| **Thay thế** | Nếu cần, chỉ đặt link ra ngoài. **Không** làm hồ sơ gia sư, không nhắn tin trong hệ thống |
| **Bảng DB đã bỏ** | ~~`tutor_profiles`~~ |

## 5.5 · Trợ lý ảo AI hỏi đáp — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Hộp chat AI là panel có ngữ cảnh bên trong màn cho USER được gán tường minh; không phải màn riêng. Điều kiện hiện ở màn làm bài theo UC-085 và Open Issues trong screen-fields.md |
| **Client** | Web · Mobile · Shared |
| **Phạm vi** | **MVP** (đổi từ V3+ vì yêu cầu xuyên suốt) |
| **Trạng thái** | Làm mới |
| **Giao diện** | Nút mở panel trong các màn được phép, không che nội dung học hoặc CTA. Không tự đưa panel vào khu quản trị hoặc public chỉ vì dùng chung layout |
| **Biết ngữ cảnh** | Đang ở trang nào thì gợi ý theo trang đó. Ví dụ đang học chủ đề "Gia đình" thì AI biết |
| **Trả lời được** | Giải thích nghĩa từ · phân tích cấu trúc câu · so sánh hai từ gần nghĩa · giải thích điểm ngữ pháp · gợi ý cách nhớ chữ |
| **KHÔNG hiện ở** | Màn hình thi mô phỏng thật (nếu sau này làm) — để tránh gian lận |
| **Bảng DB** | `ai_chat_sessions` `ai_chat_messages` (mới, L) |
| **API** | `POST /api/assistant/ask` · `GET /api/assistant/history` |
| **Chi phí** | **Tốn tiền mỗi lượt hỏi** → bắt buộc có hạn mức theo gói (xem 6.1) |
| **Giới hạn kỹ thuật** | Chặn câu hỏi quá dài · giới hạn số lượt/ngày · lưu lịch sử để người dùng xem lại |
| **Nghiệm thu** | Mở được ở ngữ cảnh được phép; hết hạn mức báo rõ; trả lời dưới 5 giây |

## 5.6 · Game Box — Mở rộng

| | |
|---|---|
| **Mô tả** | 4 game: Mưa chữ · Ghép Pinyin · Ghép Bộ thủ · Bắt Chữ |
| **Client** | Web chính · Mobile |
| **Phạm vi** | MVP |
| **Trạng thái** | Mở rộng — **chuyển từ nhóm 1 sang Community**, thêm lưu điểm |
| **Hiện trạng** | Game đã có nhưng chơi xong **không lưu điểm** |
| **Bảng DB** | `game_scores` (**C**) — 5 game khai **enum trong code**, không có bảng `game_catalog` |
| **Nguồn từ vựng** | **Đọc thẳng** `learning.words` · `learning.characters` qua `ContentLookup` |
| **Mastery** | **Cộng ngay, cùng transaction** với lúc lưu điểm |
| **Chống gian lận** | Server kiểm trần điểm · `submitted_at − served_at` hợp lý · giới hạn số ván/giờ |
| **Chạy ở đâu** | Trang web riêng `game.cnhsk.com`; mobile mở **cùng trang đó** trong WebView |
| **Màn hình** | SCR-036 Game Session hiển thị kết quả cá nhân của ván chơi; SCR-047 Game Leaderboard là màn V2 riêng |
| **Nghiệm thu** | Điểm lưu lại, kết quả cá nhân hiển thị trong SCR-036 sau xác nhận, **mastery đổi ngay**; gửi điểm vượt trần bị từ chối. Bảng xếp hạng công khai SCR-047 thuộc V2 theo UC-083 |

## 5.7 · Game gõ pinyin — Làm mới

| | |
|---|---|
| **Mô tả** | Chữ rơi xuống, gõ đúng pinyin kèm thanh điệu để bắn trúng |
| **Client** | Web chính (cần bàn phím) · Mobile hạn chế |
| **Phạm vi** | V2 |
| **Chế độ** | 60 giây · qua màn |
| **Khác biệt** | 4 game kia dùng chuột/chạm. Game này luyện **gõ bàn phím tiếng Trung** |
| **Bảng DB** | `game_scores` (C) — dùng chung với 4 game kia, phân biệt bằng cột `game_code` |
| **Chạy ở đâu** | `game.cnhsk.com` — mobile hạn chế vì cần bàn phím |
| **Nghiệm thu** | Gõ pinyin có dấu thanh nhận đúng |

## 5.8 · Cuộc thi có thưởng — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Cuộc thi quiz/game do công ty tổ chức, **có phần thưởng thật**, giới hạn khung giờ |
| **Client** | Web chính · Mobile |
| **Phạm vi** | **V2** |
| **Trạng thái** | Làm mới |
| **Cách tổ chức** | `CONTENT_ADMIN` tạo cuộc thi → đặt khung giờ (ví dụ 20h–21h) → đăng bài quảng bá → người dùng đăng ký → đúng giờ mở → hết giờ đóng → công bố xếp hạng; nếu thưởng bằng điểm tài chính, `FINANCE_ADMIN` thực hiện cộng điểm và ghi sổ cái |
| **Ai tham gia** | **Tài khoản mới cũng tham gia free** — dùng để thu hút người dùng |
| **Tính điểm** | Như game: điểm + thời gian. Xếp hạng riêng cho mỗi cuộc thi |
| **Phần thưởng** | Hiện trên trang cuộc thi. Thưởng hiện vật do bên tổ chức trao; thưởng bằng điểm tài chính do `FINANCE_ADMIN` xử lý và ghi sổ cái |
| **Bảng DB** | `contests` `contest_participants` `contest_submissions` (C) — giải thưởng gộp vào `contests.prizes` JSONB, nhật ký gian lận gộp vào `contest_participants.cheat_events` JSONB |
| **API** | `GET /api/contests` · `POST /api/contests/{id}/join` · `POST /api/contests/{id}/submit` |
| **Nghiệm thu** | Ngoài khung giờ không vào thi được; xếp hạng công bố đúng sau khi đóng |

### 5.8.1 · Chống gian lận trong cuộc thi

Bạn đề xuất **phát hiện chuyển tab**. Đây là biện pháp hợp lý nhưng dễ vượt qua. Dưới đây là các cách phổ biến, xếp theo hiệu quả trên chi phí:

| # | Biện pháp | Cách hoạt động | Độ khó | Hiệu quả |
|---|---|---|---|---|
| 1 | **Chấm ở server** | Client không bao giờ biết đáp án đúng | Thấp | **Rất cao** — nền tảng của mọi biện pháp khác |
| 2 | **Giới hạn thời gian mỗi câu** | Server ghi thời điểm gửi câu hỏi, quá hạn thì không nhận | Thấp | **Cao** — chặn tra cứu ngoài |
| 3 | **Phát hiện chuyển tab** | `visibilitychange` báo khi rời tab. Cảnh báo lần 1, trừ điểm lần 2, loại lần 3 | Thấp | Trung bình — vượt được bằng hai màn hình |
| 4 | **Xáo câu và đáp án** | Mỗi người thứ tự khác nhau | Thấp | **Cao** — chặn chép bài nhau |
| 5 | **Một tài khoản một phiên** | Đang thi ở máy này thì máy khác không vào được | Thấp | Cao |
| 6 | **Chặn copy/paste và chuột phải** | Không cho bôi đen đề bài | Rất thấp | Thấp — chỉ cản người lười |
| 7 | **Phát hiện tốc độ bất thường** | Trả lời đúng 20 câu trong 10 giây là bất thường | Trung bình | **Cao** — bắt được bot |
| 8 | **Ghi nhật ký hành vi** | Lưu thời gian từng câu, số lần rời tab. Xem lại khi nghi ngờ | Trung bình | Cao — dùng để xử lý sau |
| 9 | **Chặn nhiều tài khoản cùng IP** | Cảnh báo khi nhiều tài khoản thi từ một IP | Trung bình | Trung bình — nhà chung IP sẽ báo nhầm |
| 10 | **Fullscreen bắt buộc** | Thoát fullscreen là cảnh báo | Thấp | Trung bình |

**Khuyến nghị cho đồ án:** làm **1, 2, 4, 5, 3, 8** — sáu cái này rẻ và đủ chặn phần lớn gian lận. Bỏ 9 (báo nhầm nhiều) và 6 (gần như vô dụng).

> **Nguyên tắc:** không có cách nào chặn được 100%. Mục tiêu là làm gian lận **khó hơn học thật**, và **ghi nhật ký đủ để xử lý sau** khi phát hiện.

---

# 6 · Tài khoản & hệ thống

## 6.1 · Tài khoản, gói dịch vụ và thanh toán — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Hai loại tài khoản: **thường** (giới hạn) và **premium** (mua gói hoặc điểm) |
| **Client** | Shared · Web/Mobile |
| **Phạm vi** | **MVP** |
| **Trạng thái** | Làm mới |

### Gói dịch vụ

| Gói | Quyền | Giá |
|---|---|---|
| **Thường** | Mỗi tính năng tốn phí dùng **10 lượt free** | 0 |
| **Premium tháng** | Không giới hạn trong tháng | Nhóm tự set |
| **Điểm tài chính** | Mỗi lượt dùng trừ 1 điểm. Ví dụ 10.000đ = 100 điểm | Nhóm tự set |

**Tính năng tốn lượt:** AI sinh bài (3.3) · dịch (4.1) · trợ lý ảo (5.5) · nhờ teacher chấm bài (6.2)

### Thanh toán bằng thẻ nạp

| | |
|---|---|
| **Cách mua** | Người dùng liên hệ qua Zalo hoặc nền tảng riêng → công ty cung cấp mã thẻ |
| **Cách nạp** | Người dùng nhập mã vào hệ thống → cộng điểm hoặc kích hoạt gói tháng |
| **Bảng DB** | `plans` `user_subscriptions` `credit_cards` `credit_transactions` `feature_usage` (mới, L) |
| **API** | `POST /api/credits/redeem` · `GET /api/me/subscription` · `GET /api/me/credits` |

### ⚠️ Bảo mật bắt buộc — đây là hệ thống tiền thật

| # | Rủi ro | Bắt buộc phòng |
|---|---|---|
| 1 | **Đoán mã thẻ** | Mã dài ≥16 ký tự, sinh bằng `SecureRandom`, **không** dùng số thứ tự hay `Random` thường |
| 2 | **Dùng lại mã** | Khoá bản ghi khi nhập (`SELECT FOR UPDATE`), đổi trạng thái sang USED trong **cùng transaction** |
| 3 | **Thử mã hàng loạt** | Giới hạn 5 lần nhập sai/giờ mỗi tài khoản, sai nhiều thì khoá tạm |
| 4 | **Tranh chấp** | `credit_transactions` ghi mọi thay đổi điểm: ai, khi nào, lý do, số dư trước và sau. **Không bao giờ xoá** |
| 5 | **Trừ điểm sai** | Trừ điểm và ghi nhật ký trong cùng transaction. Gọi AI lỗi thì hoàn điểm |
| 6 | **Lộ mã trong log** | Không ghi mã thẻ vào log. Lưu **hash** của mã, không lưu mã thô |

> **Cảnh báo về đồ án:** đây là chỗ thầy sẽ hỏi kỹ nhất khi bảo vệ. Chuẩn bị giải thích được 6 điểm trên.

### Đăng nhập dùng chung ba client

Một tài khoản dùng cho web chính, trang game và mobile. Một ứng dụng nên **không cần khoá công khai
giữa service** — `SecurityConfig` ở module `shared` kiểm JWT một lần cho mọi request.

| Client | Cách gửi token |
|---|---|
| Web chính `cnhsk.com` | **Cookie** `Domain=cnhsk.com` · `HttpOnly` · `SameSite=Lax` |
| Trang game `game.cnhsk.com` | **Cùng cookie đó** — tên miền phụ nên tự nhận |
| Mobile | **Header** `Authorization: Bearer` — React Native không có cookie |
| Mobile chơi game (WebView) | **Header** — app tiêm token trước khi trang chạy |

> `JwtFilter` đọc **cookie trước, không có thì đọc header** — thiếu một trong hai là một loại client
> không đăng nhập được. Chi tiết: `docs/kien-truc.md` mục 2.

## 6.2 · Hệ thống phân quyền 6 role — Làm mới ⭐

| | |
|---|---|
| **Mô tả** | Sáu role trong cơ sở dữ liệu với quyền tách biệt; `GUEST` và `SYSTEM` là actor, không phải role |
| **Client** | Trang quản trị (web) · Shared |
| **Phạm vi** | **MVP** |
| **Trạng thái** | Làm mới — thống nhất với Hiến pháp và 119 use case |

| Role | Quyền | Phạm vi |
|---|---|---|
| **USER** | Học, thi, chơi game, đăng bài (chờ duyệt) | Người dùng cuối |
| **TEACHER** | Duyệt câu hỏi AI (cùng CONTENT_ADMIN), sửa nội dung học, nhận và chấm bài viết | Học tập |
| **MANAGER** | Duyệt bài đăng, xử lý báo cáo và kiểm duyệt bình luận | Cộng đồng |
| **CONTENT_ADMIN** | Quản lý đề thi, kho câu hỏi, nhập dữ liệu và cuộc thi | Nội dung |
| **FINANCE_ADMIN** | Quản lý gói dịch vụ, mã thẻ nạp, sổ cái điểm và tranh chấp | Tài chính |
| **SUPER_ADMIN** | Quản lý người dùng, cấp/thu hồi role và cấu hình hệ thống | Hệ thống |

### Teacher chấm bài thuê

| | |
|---|---|
| **Luồng** | Người học làm bài viết → chọn "nhờ chấm" → trừ điểm tài chính → vào hàng đợi → teacher nhận chấm → trả kết quả kèm nhận xét |
| **Bảng DB** | `grading_requests` (L) — kết quả chấm gộp vào cùng bảng: `score` · `feedback` · `corrections` · `teacher_payout` |
| **Cần quyết** | Teacher nhận bao nhiêu phần trong số điểm người học trả? Có hạn thời gian phải chấm xong không? |
| **Nghiệm thu** | Trừ điểm đúng lúc gửi yêu cầu; teacher không nhận thì hoàn điểm |

> **Một người có nhiều role được** — bảng nối `user_roles` đã thiết kế cho việc này. `SUPER_ADMIN` không tự động có quyền của `CONTENT_ADMIN` hoặc `FINANCE_ADMIN`. `GUEST` và `SYSTEM` không được thêm vào `user_roles`.

> Cấp/thu hồi role nằm tại **SCR-077 User Detail**, do `SUPER_ADMIN` thực hiện.
> Bộ 79 màn không có màn CRUD danh mục role riêng. **SCR-078 System Settings** đã
> có trong inventory; triển khai Deferred theo UC-116 đến khi chốt danh sách cấu hình và nơi lưu.

## 6.3 · Kho câu hỏi và nhãn kiến thức — Làm mới

| | |
|---|---|
| **Mô tả** | Nền dữ liệu đỡ toàn bộ phần thông minh |
| **Phạm vi** | MVP |
| **Một kho chung** | Mọi câu hỏi — đề thi, bài luyện, quiz, game — nằm chung một nơi |
| **Nhãn kiến thức** | Mỗi câu gắn với điểm kiến thức mà nó đo |
| **Bảng DB** | `questions` `question_options` `question_knowledge_points` `knowledge_points` (L) |
| **Vì sao sống còn** | Bỏ nhãn = mất **toàn bộ** khả năng "yếu chỗ nào luyện chỗ đó", và không sửa được nếu không nhập lại từ đầu |
| **Nghiệm thu** | Một câu gắn nhiều nhãn; truy vấn "câu nào đo điểm X" dưới 100ms |

## 6.4 · Nhập dữ liệu của thầy — Làm mới

| | |
|---|---|
| **Mô tả** | Đưa đề thi, từ vựng, ngữ pháp vào hệ thống |
| **Client** | Admin |
| **Phạm vi** | MVP |
| **Gồm** | Nhập có kiểm tra định dạng · báo cáo dòng lỗi · chạy lại không tạo bản trùng |
| **Bảng DB** | `import_batches` (L) |
| **Chưa gỡ được** | **Chưa ai thấy file thật của thầy** — 8 câu cần hỏi ở tài liệu database mục 11 |
| **Nghiệm thu** | File lỗi báo rõ dòng nào sai, không ghi dữ liệu hỏng |

## 6.5 · Ứng dụng di động — Làm mới

| | |
|---|---|
| **Mô tả** | Bản điện thoại dùng chung tài khoản và tiến độ |
| **Phạm vi** | V2 |
| **Phạm vi màn hình** | Học chính · game · lộ trình · tra cứu · cộng đồng · nhắc học · làm đề luyện tập |
| **Kỹ thuật** | React Native + **Expo** |
| **Lưu ý** | Nhóm chưa quen RN (máy có 2 project Flutter). **Flutter là phương án dự phòng** — backend không đổi |
| **Nghiệm thu** | Đăng nhập cùng tài khoản thấy đúng tiến độ |

## 6.6 · Quản trị nội dung — Làm mới

| | |
|---|---|
| **Mô tả** | Nơi `TEACHER`, `MANAGER`, `CONTENT_ADMIN`, `FINANCE_ADMIN` và `SUPER_ADMIN` thực hiện chức năng quản trị theo đúng quyền riêng |
| **Client** | Admin |
| **Phạm vi** | MVP |
| **Gồm** | `TEACHER`: hàng đợi câu hỏi AI, sửa nội dung học · `MANAGER`: duyệt bài đăng và xử lý báo cáo · `CONTENT_ADMIN`: đề thi, kho câu hỏi, nhập dữ liệu, cuộc thi · `FINANCE_ADMIN`: gói dịch vụ, thẻ nạp, sổ cái và tranh chấp · `SUPER_ADMIN`: người dùng, role và cấu hình |
| **Bảng DB** | `review_actions` `question_reports` `moderation_reports` `import_runs` · duyệt bài đọc cột `posts.reviewed_*` |
| **Nghiệm thu** | Mỗi role chỉ thấy phần mình quản; duyệt hàng loạt 20 mục dưới 2 phút |

---

# 7 · Nội dung tham khảo

## 7.1 · Kênh & Podcast — Đã có

| | |
|---|---|
| **Mô tả** | Danh sách kênh YouTube, podcast học tiếng Trung tuyển chọn |
| **Màn hình** | SCR-053 YouTube & Podcasts |
| **Phạm vi** | V2 |
| **Bảng DB** | `media_channels` (L) |
| **Lưu ý** | **Chỉ liên kết ra ngoài**, không nhúng hay tải về |

## 7.2 · Sách bản quyền — Đã có

| | |
|---|---|
| **Mô tả** | Danh mục sách học tiếng Trung, kèm giới thiệu và nơi mua |
| **Màn hình** | SCR-054 Book Catalogue → SCR-055 Book Detail |
| **Phạm vi** | V2 |
| **Bảng DB** | `books` (L) |
| **Lưu ý** | **Chỉ giới thiệu và dẫn link mua** — không lưu trữ nội dung sách |

---

# Phụ lục A · Phân bổ phạm vi

| Phạm vi | Số | Danh sách |
|---|---|---|
| **MVP** | **21** | 1.1–1.5 · 2.1–2.3 · 3.1–3.5 · 4.1–4.3 · 5.5 · 5.6 · 6.1–6.4 · 6.6 |
| **V2** | **11** | 1.6 · 5.1–5.3 · 5.7 · 5.8 · 6.5 · 7.1 · 7.2 |
| **Cắt** | 1 | 5.4 Gia sư |

## ⚠️ Cảnh báo về khối lượng MVP

**21 tính năng MVP với 3–5 người trong 3 tháng là quá nhiều.** Trong đó có 4 tính năng lớn:

| Tính năng | Ước tính |
|---|---|
| 6.1 Gói dịch vụ + thanh toán thẻ | 3–4 tuần |
| 6.2 Sáu role + teacher chấm thuê | 2–3 tuần |
| 5.5 Trợ lý ảo xuyên suốt | 2 tuần |
| 1.5 Học từ theo chủ đề | 2 tuần |

Cộng lại đã **9–11 tuần cho một người**, chưa tính 17 tính năng còn lại.

### Nếu phải cắt, cắt theo thứ tự này

| # | Cắt gì | Vì sao an toàn |
|---|---|---|
| 1 | **Teacher chấm bài thuê** (trong 6.2) | Giữ 6 role nhưng bỏ phần chấm thuê. Tiết kiệm 1–2 tuần |
| 2 | **Gói tháng** (giữ điểm tài chính) | Một cơ chế tính phí thay vì hai. Tiết kiệm 1 tuần |
| 3 | **1.3 Luyện phát âm** | Đã có ở app cũ, chưa chấm micro nên giá trị thấp |
| 4 | **5.6 Game Box** | Vui nhưng không phải lõi học tập |
| 5 | **4.1 phần dịch** (giữ tra từ) | Dịch tốn chi phí API mà tra từ đã đủ dùng |

**Không được cắt:** 6.3 Kho câu hỏi · 3.1 Mastery · 3.2 Cây chủ đề · 1.5 Học theo chủ đề · 2.1 Làm đề. Năm cái này là lõi, cắt là hỏng sản phẩm.

---

# Phụ lục B · Bảng DB phục vụ tính năng

> **Cập nhật theo bản 5.** Phụ lục này trước đây tính ra 76 bảng dựa trên ERD 58 bảng cũ.
> Số chốt hiện tại là **31 bảng · 4 schema** — xem `database.md`.

| Bảng | Schema | Phục vụ |
|---|---|---|
| `topic_words` | L | 1.5 Học từ theo chủ đề |
| `videos` `video_subtitles` | L | 1.6 Học qua video (`video_vocabulary` gộp vào `videos.vocabulary` JSONB) |
| `flashcard_decks` `flashcards` | L | 4.3 Bộ flashcard — chia sẻ đọc thẳng cột `is_public` |
| `ai_chat_sessions` `ai_chat_messages` | L | 5.5 Trợ lý ảo |
| `plans` `user_subscriptions` `user_credits` `credit_cards` `credit_transactions` `feature_usage` | L | 6.1 Gói và thanh toán |
| `grading_requests` | L | 6.2 Teacher chấm thuê (gộp `grading_results`) |
| `posts` + cột `reviewed_*` | C | 5.1 Kiểm duyệt bài (gộp `post_reviews`) |
| `contests` `contest_participants` `contest_submissions` | C | 5.8 Cuộc thi (`contest_prizes` gộp vào `contests.prizes` JSONB) |
| `game_scores` | C | 5.6 · 5.7 Game (không có `game_catalog` — 5 game khai enum trong code) |
| ~~`tutor_profiles`~~ | — | **Bỏ** — 5.4 cắt khỏi scope |
| ~~`note_links`~~ | — | **Bỏ** — 4.2 bỏ liên kết học |
| ~~`user_refs` `topic_refs` `vocab_refs` `sync_run_items`~~ | — | **Bỏ ở bản 5** — monolith đọc thẳng bảng gốc |

**Tổng chốt: 31 bảng** — `auth` 3 · `learning` 22 · `community` 5 · `shared` 1.
> *(Con số 59 bảng của bản thiết kế cũ đã bị thay thế — xem `database.md`.)*

---

# Phụ lục C · Việc cần quyết

| # | Việc | Ai quyết | Chặn gì |
|---|---|---|---|
| 1 | **Nguồn video cho 1.6** — tự quay, mua bản quyền, hay nhúng YouTube | Nhóm | Thiết kế bảng `videos` |
| 2 | **Giá gói tháng và tỷ giá điểm** | Nhóm | Dữ liệu bảng `plans` |
| 3 | **Teacher nhận bao nhiêu phần khi chấm thuê** | Nhóm | Logic chia tiền |
| 4 | **Kiểm duyệt bài: duyệt hết hay chỉ tài khoản mới** | Nhóm | Luồng 5.1 |
| 5 | **Cấu trúc file dữ liệu của thầy** | Thầy | Bảng đề thi |
| 6 | **Ai duyệt câu hỏi AI** | Thầy | Feature 6.6; UI dùng SCR-058 AI Review Queue và SCR-059 Question Edit chung theo BUS-07 |
| 7 | **Nền tảng deploy** | Nhóm | Nơi chạy backend + 2 trang web |
| 8 | **schinese.net làm học video thế nào** | Cần khảo sát | Đặc tả 1.6 |
| 9 | **Tên miền thật** | Nhóm — **gấp** | Cookie `Domain=` và CORS, xem 6.1 |
| 10 | **Trần điểm mỗi game** | Nhóm | Chống gian lận 5.6 |
| 11 | **Lưu tiến độ xem video ở đâu** | Nhóm | API `POST /videos/{id}/progress` của 1.6 — bảng `user_video_progress` bị bỏ khi gộp ở bản 3, chưa có chỗ thay. Tính năng V2 nên chưa chặn ngay |
