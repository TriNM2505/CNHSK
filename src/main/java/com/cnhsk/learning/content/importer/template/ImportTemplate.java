package com.cnhsk.learning.content.importer.template;

import java.util.List;
import java.util.Objects;

/** Shared definitions for CSV headers, validation guidance and template downloads. */
public record ImportTemplate(
        ImportDataType dataType,
        String label,
        List<String> naturalKey,
        List<ImportDataType> dependsOn,
        List<ColumnSpec> columns) {

    private static final List<ImportTemplate> TEMPLATES = List.of(
            lexeme(), topic(), topicItem(), grammar(), grammarExercise(), pronStage(), pronUnit());

    public ImportTemplate {
        naturalKey = List.copyOf(naturalKey);
        dependsOn = List.copyOf(dependsOn);
        columns = List.copyOf(columns);
    }

    public static List<ImportTemplate> all() {
        return TEMPLATES;
    }

    public static ImportTemplate forType(ImportDataType dataType) {
        Objects.requireNonNull(dataType, "dataType must not be null");
        return TEMPLATES.stream()
                .filter(template -> template.dataType() == dataType)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported import data type: " + dataType));
    }

    private static ImportTemplate lexeme() {
        return new ImportTemplate(ImportDataType.LEXEME, "Chữ và từ vựng",
                List.of("kind", "text"), List.of(), List.of(
                        new ColumnSpec("kind", true, null, "Loại dữ liệu: CHAR hoặc WORD.", "WORD"),
                        new ColumnSpec("text", true, 20,
                                "CHAR: đúng 1 chữ Hán; WORD: từ 1 đến 20 ký tự, toàn bộ là chữ Hán.", "我们"),
                        new ColumnSpec("pinyin", true, 200,
                                "Pinyin có dấu; chỉ chữ a–z, ü, nguyên âm có dấu thanh, khoảng trắng, ' và -.", "wǒmen"),
                        new ColumnSpec("sino_viet", false, 200, "Âm Hán-Việt.", "ngã môn"),
                        new ColumnSpec("meaning_vi", true, 1000,
                                "Nghĩa tiếng Việt; ngăn nhiều nghĩa bằng dấu ;.", "chúng tôi; chúng ta"),
                        new ColumnSpec("hsk_level", true, null, "Cấp HSK: số nguyên từ 1 đến 9.", "1"),
                        new ColumnSpec("audio_url", false, 500,
                                "URL http:// hoặc https://, không có khoảng trắng.", "https://example.com/women.mp3"),
                        new ColumnSpec("radical", false, 1,
                                "Chỉ dùng cho CHAR: đúng 1 chữ Hán hoặc bộ thủ U+2E80–U+2FDF.", ""),
                        new ColumnSpec("stroke_count", false, null,
                                "Chỉ dùng cho CHAR: số nét từ 1 đến 64.", ""),
                        new ColumnSpec("example_cn", false, 300,
                                "Câu ví dụ chứa ít nhất 1 chữ Hán; nếu có thì example_vi bắt buộc.", "我们一起学习。"),
                        new ColumnSpec("example_pinyin", false, 500,
                                "Pinyin của câu ví dụ.", "Wǒmen yìqǐ xuéxí."),
                        new ColumnSpec("example_vi", false, 500,
                                "Nghĩa câu ví dụ; bắt buộc khi example_cn có giá trị.", "Chúng ta cùng học."),
                        new ColumnSpec("tags", false, null,
                                "Tối đa 10 nhãn, mỗi nhãn tối đa 30 ký tự; ngăn bằng dấu |.", "đại từ|giao tiếp")));
    }

    private static ImportTemplate topic() {
        return new ImportTemplate(ImportDataType.TOPIC, "Chủ đề từ vựng",
                List.of("code"), List.of(), List.of(
                        new ColumnSpec("code", true, 50,
                                "Mã chủ đề theo mẫu ^[A-Z0-9_-]{2,50}$.", "DAILY_LIFE"),
                        new ColumnSpec("name", true, 200, "Tên chủ đề.", "Sinh hoạt hằng ngày"),
                        new ColumnSpec("hsk_level", true, null, "Cấp HSK: số nguyên từ 1 đến 9.", "1"),
                        new ColumnSpec("order_index", false, null,
                                "Thứ tự: số nguyên từ 0 đến 100000, mặc định 0.", "0")));
    }

    private static ImportTemplate topicItem() {
        return new ImportTemplate(ImportDataType.TOPIC_ITEM, "Từ trong chủ đề",
                List.of("topic_code", "kind", "text"), List.of(ImportDataType.TOPIC, ImportDataType.LEXEME), List.of(
                        new ColumnSpec("topic_code", true, null,
                                "Mã chủ đề đã tồn tại; nhập mẫu Chủ đề từ vựng trước.", "DAILY_LIFE"),
                        new ColumnSpec("kind", true, null, "Loại dữ liệu: CHAR hoặc WORD.", "WORD"),
                        new ColumnSpec("text", true, null,
                                "Chữ hoặc từ đã tồn tại với cùng kind; nhập mẫu Chữ và từ vựng trước.", "我们"),
                        new ColumnSpec("order_index", false, null,
                                "Thứ tự: số nguyên từ 0 đến 100000, mặc định 0.", "0")));
    }

    private static ImportTemplate grammar() {
        return new ImportTemplate(ImportDataType.GRAMMAR, "Điểm ngữ pháp",
                List.of("text"), List.of(), List.of(
                        new ColumnSpec("text", true, 200,
                                "Điểm ngữ pháp chứa ít nhất 1 chữ Hán.", "是……的"),
                        new ColumnSpec("hsk_level", true, null, "Cấp HSK: số nguyên từ 1 đến 9.", "2"),
                        new ColumnSpec("title_vi", true, 300,
                                "Tên ngắn tiếng Việt.", "Nhấn mạnh thông tin đã xảy ra"),
                        new ColumnSpec("structure", false, 300,
                                "Cấu trúc ngữ pháp.", "Chủ ngữ + 是 + thông tin nhấn mạnh + động từ + 的"),
                        new ColumnSpec("explanation_vi", true, 3000,
                                "Giải thích ngữ pháp bằng tiếng Việt.",
                                "Dùng 是……的 để nhấn mạnh thời gian hoặc cách thức của một việc đã xảy ra."),
                        new ColumnSpec("common_mistake_vi", false, 1000,
                                "Lỗi thường gặp.", "Dùng cấu trúc này để kể một việc chưa xảy ra."),
                        new ColumnSpec("example1_cn", true, 300,
                                "Ví dụ 1 chứa chữ Hán; cần example1_vi.", "我是昨天来的。"),
                        new ColumnSpec("example2_cn", false, 300,
                                "Ví dụ 2 chứa chữ Hán; nếu có thì example2_vi bắt buộc.", "他是坐车来的。"),
                        new ColumnSpec("example3_cn", false, 300,
                                "Ví dụ 3 chứa chữ Hán; nếu có thì example3_vi bắt buộc.", "她是在家学的。"),
                        new ColumnSpec("example1_pinyin", false, 500,
                                "Pinyin của ví dụ 1.", "Wǒ shì zuótiān lái de."),
                        new ColumnSpec("example2_pinyin", false, 500,
                                "Pinyin của ví dụ 2.", "Tā shì zuò chē lái de."),
                        new ColumnSpec("example3_pinyin", false, 500,
                                "Pinyin của ví dụ 3.", "Tā shì zài jiā xué de."),
                        new ColumnSpec("example1_vi", true, 500,
                                "Nghĩa tiếng Việt của ví dụ 1.", "Tôi đến hôm qua."),
                        new ColumnSpec("example2_vi", false, 500,
                                "Nghĩa tiếng Việt của ví dụ 2; bắt buộc khi example2_cn có giá trị.", "Anh ấy đến bằng xe."),
                        new ColumnSpec("example3_vi", false, 500,
                                "Nghĩa tiếng Việt của ví dụ 3; bắt buộc khi example3_cn có giá trị.", "Cô ấy đã học ở nhà.")));
    }

    private static ImportTemplate grammarExercise() {
        return new ImportTemplate(ImportDataType.GRAMMAR_EXERCISE, "Bài tập ngữ pháp",
                List.of("grammar_text", "exercise_no"), List.of(ImportDataType.GRAMMAR), List.of(
                        new ColumnSpec("grammar_text", true, null,
                                "Điểm ngữ pháp đã tồn tại; nhập mẫu Điểm ngữ pháp trước.", "是……的"),
                        new ColumnSpec("exercise_no", true, null, "Số bài tập: số nguyên từ 1 đến 20.", "1"),
                        new ColumnSpec("stem", true, 500, "Nội dung bài tập; có thể chứa ___.", "我是昨天来___。"),
                        new ColumnSpec("option_a", true, 200,
                                "Phương án A; các phương án phải khác nhau sau chuẩn hóa.", "的"),
                        new ColumnSpec("option_b", true, 200,
                                "Phương án B; các phương án phải khác nhau sau chuẩn hóa.", "吗"),
                        new ColumnSpec("option_c", false, 200,
                                "Phương án C; bắt buộc khi option_d có giá trị.", "呢"),
                        new ColumnSpec("option_d", false, 200,
                                "Phương án D; nếu có thì option_c bắt buộc.", "吧"),
                        new ColumnSpec("answer", true, null,
                                "Đáp án A–D, phải trỏ tới phương án có nội dung.", "A"),
                        new ColumnSpec("explanation_vi", false, 1000,
                                "Giải thích đáp án.", "Điền 的 để hoàn thành cấu trúc 是……的.")));
    }

    private static ImportTemplate pronStage() {
        return new ImportTemplate(ImportDataType.PRON_STAGE, "Tầng phát âm",
                List.of("stage_index"), List.of(), List.of(
                        new ColumnSpec("stage_index", true, null, "Số tầng: số nguyên từ 1 đến 8.", "1"),
                        new ColumnSpec("name", true, 100, "Tên tầng phát âm.", "Phân biệt thanh mẫu"),
                        new ColumnSpec("description", false, 2000,
                                "Mô tả tầng phát âm.", "Luyện nghe và phân biệt thanh mẫu b với p."),
                        new ColumnSpec("target_sounds", false, null,
                                "Tối đa 30 âm, mỗi âm tối đa 20 ký tự; ngăn bằng dấu |.", "b|p"),
                        new ColumnSpec("pass_percent", false, null,
                                "Ngưỡng đạt từ 0 đến 100, tối đa 2 chữ số thập phân, mặc định 80.", "80")));
    }

    private static ImportTemplate pronUnit() {
        return new ImportTemplate(ImportDataType.PRON_UNIT, "Bài nghe phát âm",
                List.of("stage_index", "unit_code"), List.of(ImportDataType.PRON_STAGE), List.of(
                        new ColumnSpec("stage_index", true, null,
                                "Tầng phát âm đã tồn tại; nhập mẫu Tầng phát âm trước.", "1"),
                        new ColumnSpec("unit_code", true, 30,
                                "Mã bài nghe theo mẫu ^[A-Za-z0-9_-]{1,30}$.", "BP_01"),
                        new ColumnSpec("audio_url", true, 500,
                                "URL http:// hoặc https://, không có khoảng trắng.", "https://example.com/ba.mp3"),
                        new ColumnSpec("prompt_vi", false, 200,
                                "Yêu cầu bài nghe, mặc định: Nghe và chọn đáp án đúng.", "Nghe và chọn đáp án đúng"),
                        new ColumnSpec("option_a", true, 50,
                                "Phương án A: pinyin, thanh mẫu, vận mẫu hoặc số thanh.", "b"),
                        new ColumnSpec("option_b", true, 50,
                                "Phương án B: pinyin, thanh mẫu, vận mẫu hoặc số thanh.", "p"),
                        new ColumnSpec("option_c", false, 50,
                                "Phương án C: pinyin, thanh mẫu, vận mẫu hoặc số thanh.", "m"),
                        new ColumnSpec("option_d", false, 50,
                                "Phương án D: pinyin, thanh mẫu, vận mẫu hoặc số thanh.", "f"),
                        new ColumnSpec("answer", true, null,
                                "Đáp án A–D, phải trỏ tới phương án có nội dung.", "A"),
                        new ColumnSpec("explanation_vi", false, 500,
                                "Giải thích đáp án.", "Âm nghe được bắt đầu bằng thanh mẫu b.")));
    }
}
