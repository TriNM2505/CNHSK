import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import type { ImportTemplate } from '../types'
import { getImportTemplateFile, getImportTemplates } from './imports'

const origin = 'http://imports.test'

// Server metadata from ImportTemplate on main at 34f5dff (012/T-07).
function column(
  name: string,
  required: boolean,
  maxLength: number | null,
  description: string,
  example: string,
) {
  return { name, required, maxLength, description, example }
}

const templates: ImportTemplate[] = [
  {
    dataType: 'LEXEME',
    label: 'Chữ và từ vựng',
    naturalKey: ['kind', 'text'],
    dependsOn: [],
    columns: [
      column('kind', true, null, 'Loại dữ liệu: CHAR hoặc WORD.', 'WORD'),
      column(
        'text',
        true,
        20,
        'CHAR: đúng 1 chữ Hán; WORD: từ 1 đến 20 ký tự, toàn bộ là chữ Hán.',
        '我们',
      ),
      column(
        'pinyin',
        true,
        200,
        "Pinyin có dấu; chỉ chữ a–z, ü, nguyên âm có dấu thanh, khoảng trắng, ' và -.",
        'wǒmen',
      ),
      column('sino_viet', false, 200, 'Âm Hán-Việt.', 'ngã môn'),
      column(
        'meaning_vi',
        true,
        1000,
        'Nghĩa tiếng Việt; ngăn nhiều nghĩa bằng dấu ;.',
        'chúng tôi; chúng ta',
      ),
      column('hsk_level', true, null, 'Cấp HSK: số nguyên từ 1 đến 9.', '1'),
      column(
        'audio_url',
        false,
        500,
        'URL http:// hoặc https://, không có khoảng trắng.',
        'https://example.com/women.mp3',
      ),
      column(
        'radical',
        false,
        1,
        'Chỉ dùng cho CHAR: đúng 1 chữ Hán hoặc bộ thủ U+2E80–U+2FDF.',
        '',
      ),
      column('stroke_count', false, null, 'Chỉ dùng cho CHAR: số nét từ 1 đến 64.', ''),
      column(
        'example_cn',
        false,
        300,
        'Câu ví dụ chứa ít nhất 1 chữ Hán; nếu có thì example_vi bắt buộc.',
        '我们一起学习。',
      ),
      column('example_pinyin', false, 500, 'Pinyin của câu ví dụ.', 'Wǒmen yìqǐ xuéxí.'),
      column(
        'example_vi',
        false,
        500,
        'Nghĩa câu ví dụ; bắt buộc khi example_cn có giá trị.',
        'Chúng ta cùng học.',
      ),
      column(
        'tags',
        false,
        null,
        'Tối đa 10 nhãn, mỗi nhãn tối đa 30 ký tự; ngăn bằng dấu |.',
        'đại từ|giao tiếp',
      ),
    ],
  },
  {
    dataType: 'TOPIC',
    label: 'Chủ đề từ vựng',
    naturalKey: ['code'],
    dependsOn: [],
    columns: [
      column('code', true, 50, 'Mã chủ đề theo mẫu ^[A-Z0-9_-]{2,50}$.', 'DAILY_LIFE'),
      column('name', true, 200, 'Tên chủ đề.', 'Sinh hoạt hằng ngày'),
      column('hsk_level', true, null, 'Cấp HSK: số nguyên từ 1 đến 9.', '1'),
      column('order_index', false, null, 'Thứ tự: số nguyên từ 0 đến 100000, mặc định 0.', '0'),
    ],
  },
  {
    dataType: 'TOPIC_ITEM',
    label: 'Từ trong chủ đề',
    naturalKey: ['topic_code', 'kind', 'text'],
    dependsOn: ['TOPIC', 'LEXEME'],
    columns: [
      column(
        'topic_code',
        true,
        null,
        'Mã chủ đề đã tồn tại; nhập mẫu Chủ đề từ vựng trước.',
        'DAILY_LIFE',
      ),
      column('kind', true, null, 'Loại dữ liệu: CHAR hoặc WORD.', 'WORD'),
      column(
        'text',
        true,
        null,
        'Chữ hoặc từ đã tồn tại với cùng kind; nhập mẫu Chữ và từ vựng trước.',
        '我们',
      ),
      column('order_index', false, null, 'Thứ tự: số nguyên từ 0 đến 100000, mặc định 0.', '0'),
    ],
  },
  {
    dataType: 'GRAMMAR',
    label: 'Điểm ngữ pháp',
    naturalKey: ['text'],
    dependsOn: [],
    columns: [
      column('text', true, 200, 'Điểm ngữ pháp chứa ít nhất 1 chữ Hán.', '是……的'),
      column('hsk_level', true, null, 'Cấp HSK: số nguyên từ 1 đến 9.', '2'),
      column('title_vi', true, 300, 'Tên ngắn tiếng Việt.', 'Nhấn mạnh thông tin đã xảy ra'),
      column(
        'structure',
        false,
        300,
        'Cấu trúc ngữ pháp.',
        'Chủ ngữ + 是 + thông tin nhấn mạnh + động từ + 的',
      ),
      column(
        'explanation_vi',
        true,
        3000,
        'Giải thích ngữ pháp bằng tiếng Việt.',
        'Dùng 是……的 để nhấn mạnh thời gian hoặc cách thức của một việc đã xảy ra.',
      ),
      column(
        'common_mistake_vi',
        false,
        1000,
        'Lỗi thường gặp.',
        'Dùng cấu trúc này để kể một việc chưa xảy ra.',
      ),
      column('example1_cn', true, 300, 'Ví dụ 1 chứa chữ Hán; cần example1_vi.', '我是昨天来的。'),
      column(
        'example2_cn',
        false,
        300,
        'Ví dụ 2 chứa chữ Hán; nếu có thì example2_vi bắt buộc.',
        '他是坐车来的。',
      ),
      column(
        'example3_cn',
        false,
        300,
        'Ví dụ 3 chứa chữ Hán; nếu có thì example3_vi bắt buộc.',
        '她是在家学的。',
      ),
      column('example1_pinyin', false, 500, 'Pinyin của ví dụ 1.', 'Wǒ shì zuótiān lái de.'),
      column('example2_pinyin', false, 500, 'Pinyin của ví dụ 2.', 'Tā shì zuò chē lái de.'),
      column('example3_pinyin', false, 500, 'Pinyin của ví dụ 3.', 'Tā shì zài jiā xué de.'),
      column('example1_vi', true, 500, 'Nghĩa tiếng Việt của ví dụ 1.', 'Tôi đến hôm qua.'),
      column(
        'example2_vi',
        false,
        500,
        'Nghĩa tiếng Việt của ví dụ 2; bắt buộc khi example2_cn có giá trị.',
        'Anh ấy đến bằng xe.',
      ),
      column(
        'example3_vi',
        false,
        500,
        'Nghĩa tiếng Việt của ví dụ 3; bắt buộc khi example3_cn có giá trị.',
        'Cô ấy đã học ở nhà.',
      ),
    ],
  },
  {
    dataType: 'GRAMMAR_EXERCISE',
    label: 'Bài tập ngữ pháp',
    naturalKey: ['grammar_text', 'exercise_no'],
    dependsOn: ['GRAMMAR'],
    columns: [
      column(
        'grammar_text',
        true,
        null,
        'Điểm ngữ pháp đã tồn tại; nhập mẫu Điểm ngữ pháp trước.',
        '是……的',
      ),
      column('exercise_no', true, null, 'Số bài tập: số nguyên từ 1 đến 20.', '1'),
      column('stem', true, 500, 'Nội dung bài tập; có thể chứa ___.', '我是昨天来___。'),
      column(
        'option_a',
        true,
        200,
        'Phương án A; các phương án phải khác nhau sau chuẩn hóa.',
        '的',
      ),
      column(
        'option_b',
        true,
        200,
        'Phương án B; các phương án phải khác nhau sau chuẩn hóa.',
        '吗',
      ),
      column('option_c', false, 200, 'Phương án C; bắt buộc khi option_d có giá trị.', '呢'),
      column('option_d', false, 200, 'Phương án D; nếu có thì option_c bắt buộc.', '吧'),
      column('answer', true, null, 'Đáp án A–D, phải trỏ tới phương án có nội dung.', 'A'),
      column(
        'explanation_vi',
        false,
        1000,
        'Giải thích đáp án.',
        'Điền 的 để hoàn thành cấu trúc 是……的.',
      ),
    ],
  },
  {
    dataType: 'PRON_STAGE',
    label: 'Tầng phát âm',
    naturalKey: ['stage_index'],
    dependsOn: [],
    columns: [
      column('stage_index', true, null, 'Số tầng: số nguyên từ 1 đến 8.', '1'),
      column('name', true, 100, 'Tên tầng phát âm.', 'Phân biệt thanh mẫu'),
      column(
        'description',
        false,
        2000,
        'Mô tả tầng phát âm.',
        'Luyện nghe và phân biệt thanh mẫu b với p.',
      ),
      column(
        'target_sounds',
        false,
        null,
        'Tối đa 30 âm, mỗi âm tối đa 20 ký tự; ngăn bằng dấu |.',
        'b|p',
      ),
      column(
        'pass_percent',
        false,
        null,
        'Ngưỡng đạt từ 0 đến 100, tối đa 2 chữ số thập phân, mặc định 80.',
        '80',
      ),
    ],
  },
  {
    dataType: 'PRON_UNIT',
    label: 'Bài nghe phát âm',
    naturalKey: ['stage_index', 'unit_code'],
    dependsOn: ['PRON_STAGE'],
    columns: [
      column(
        'stage_index',
        true,
        null,
        'Tầng phát âm đã tồn tại; nhập mẫu Tầng phát âm trước.',
        '1',
      ),
      column('unit_code', true, 30, 'Mã bài nghe theo mẫu ^[A-Za-z0-9_-]{1,30}$.', 'BP_01'),
      column(
        'audio_url',
        true,
        500,
        'URL http:// hoặc https://, không có khoảng trắng.',
        'https://example.com/ba.mp3',
      ),
      column(
        'prompt_vi',
        false,
        200,
        'Yêu cầu bài nghe, mặc định: Nghe và chọn đáp án đúng.',
        'Nghe và chọn đáp án đúng',
      ),
      column('option_a', true, 50, 'Phương án A: pinyin, thanh mẫu, vận mẫu hoặc số thanh.', 'b'),
      column('option_b', true, 50, 'Phương án B: pinyin, thanh mẫu, vận mẫu hoặc số thanh.', 'p'),
      column('option_c', false, 50, 'Phương án C: pinyin, thanh mẫu, vận mẫu hoặc số thanh.', 'm'),
      column('option_d', false, 50, 'Phương án D: pinyin, thanh mẫu, vận mẫu hoặc số thanh.', 'f'),
      column('answer', true, null, 'Đáp án A–D, phải trỏ tới phương án có nội dung.', 'A'),
      column(
        'explanation_vi',
        false,
        500,
        'Giải thích đáp án.',
        'Âm nghe được bắt đầu bằng thanh mẫu b.',
      ),
    ],
  },
  {
    dataType: 'KNOWLEDGE_NODE',
    label: 'Nút cây năng lực',
    naturalKey: ['kid'],
    dependsOn: [],
    columns: [
      column(
        'kid',
        true,
        null,
        'Mã nút dạng miền + cấp 1–7 + ba chữ số, chuẩn hoá chữ hoa; ví dụ TV1-001.',
        'TV1-001',
      ),
      column('name_vi', true, 200, 'Tên tiếng Việt của nút.', 'Đại từ nhân xưng'),
      column('name_zh', true, 200, 'Tên tiếng Trung, có ít nhất một ký tự CJK.', '人称代词'),
      column(
        'can_do',
        false,
        1000,
        'Năng lực người học đạt được.',
        'Nhận biết và sử dụng đại từ trong câu ngắn.',
      ),
      column(
        'skill_type',
        false,
        null,
        'Kỹ năng chính: LISTENING, READING, WRITING, VOCAB, GRAMMAR hoặc CHARACTER.',
        'VOCAB',
      ),
    ],
  },
  {
    dataType: 'KNOWLEDGE_EDGE',
    label: 'Quan hệ giữa các nút',
    naturalKey: ['from_kid', 'to_kid'],
    dependsOn: ['KNOWLEDGE_NODE'],
    columns: [
      column('from_kid', true, null, 'KID nút đứng trước, đã tồn tại.', 'TV1-001'),
      column('to_kid', true, null, 'KID nút đích, đã tồn tại.', 'TV2-001'),
      column('edge_type', true, null, 'PREREQ: tiên quyết; SUPPORT: hỗ trợ.', 'PREREQ'),
    ],
  },
  {
    dataType: 'QUESTION',
    label: 'Câu hỏi',
    naturalKey: ['code'],
    dependsOn: ['KNOWLEDGE_NODE'],
    columns: [
      column('code', true, 50, 'Mã câu hỏi theo mẫu ^[A-Z0-9_-]{3,50}$.', 'DEMO_TV1_001'),
      column(
        'qtype',
        true,
        null,
        'MULTIPLE_CHOICE, SENTENCE_MATCH, IMAGE_MATCH, TRUE_FALSE, FILL_BLANK hoặc SENTENCE_ORDER; chưa hỗ trợ ESSAY.',
        'MULTIPLE_CHOICE',
      ),
      column('hsk_level', true, null, 'Cấp HSK: số nguyên từ 1 đến 9.', '1'),
      column(
        'difficulty',
        false,
        null,
        'Độ khó từ 1 đến 3; tạo mới mặc định 2, cập nhật để trống giữ giá trị cũ.',
        '2',
      ),
      column(
        'stem',
        true,
        2000,
        'Nội dung câu hỏi; FILL_BLANK phải chứa ___.',
        '我和你一起学习。我们指的是谁？',
      ),
      column(
        'option_a',
        false,
        300,
        'Phương án A; liên tiếp từ A, không trùng nội dung kể cả mảnh câu. Thay nguyên cấu trúc; để trống xoá phương án cũ tương ứng.',
        '我和你',
      ),
      column(
        'option_b',
        false,
        300,
        'Phương án B; thay nguyên cấu trúc, để trống xoá phương án B cũ.',
        '他和她',
      ),
      column('option_c', false, 300, 'Phương án C nếu có; để trống xoá phương án C cũ.', ''),
      column('option_d', false, 300, 'Phương án D nếu có; để trống xoá phương án D cũ.', ''),
      column('option_e', false, 300, 'Phương án E nếu có; để trống xoá phương án E cũ.', ''),
      column('option_f', false, 300, 'Phương án F nếu có; để trống xoá phương án F cũ.', ''),
      column(
        'correct',
        true,
        null,
        'Thay nguyên đáp án: khoá đúng; TRUE/FALSE; đáp án điền tối đa 50 ký tự; hoặc thứ tự mỗi mảnh câu đúng một lần.',
        'A',
      ),
      column(
        'explanation',
        true,
        3000,
        'Lời giải câu hỏi.',
        '我们 bao gồm người nói và người đang cùng học trong câu.',
      ),
      column(
        'audio_url',
        false,
        500,
        'URL http:// hoặc https://, không khoảng trắng; cập nhật để trống giữ giá trị cũ.',
        '',
      ),
      column(
        'image_url',
        false,
        500,
        'URL ảnh; IMAGE_MATCH cần ảnh; cập nhật để trống giữ giá trị cũ.',
        '',
      ),
      column(
        'kid_primary',
        true,
        null,
        'Một KID chính đã tồn tại; thay toàn bộ nhãn KID theo dòng nhập.',
        'TV1-001',
      ),
      column(
        'kid_secondary',
        false,
        null,
        'Tối đa hai KID phụ ngăn bằng |, không trùng nhau hoặc trùng KID chính; để trống xoá toàn bộ KID phụ cũ.',
        '',
      ),
      column(
        'distractor_errors',
        false,
        null,
        'Tối đa sáu mục KEY:KID hoặc KEY:KID:ghi chú ngăn bằng |; khoá phải là phương án sai của loại chọn đáp án, ghi chú tối đa 200 ký tự; để trống xoá toàn bộ nhãn lỗi cũ.',
        'B:TV1-001:Nhầm nhóm người được nhắc đến',
      ),
    ],
  },
  {
    dataType: 'NODE_LINK',
    label: 'Gắn nội dung vào nút',
    naturalKey: ['target_type', 'target_key'],
    dependsOn: ['KNOWLEDGE_NODE', 'TOPIC', 'LEXEME', 'GRAMMAR'],
    columns: [
      column(
        'target_type',
        true,
        null,
        'TOPIC hoặc WORD thuộc miền TV; CHAR thuộc CH; GRAMMAR thuộc NP.',
        'WORD',
      ),
      column(
        'target_key',
        true,
        null,
        'TOPIC: mã chủ đề; loại khác: nội dung mục từ đúng kind, đã tồn tại.',
        '我们',
      ),
      column('kid', true, null, 'KID đã tồn tại, khớp miền của nội dung.', 'TV1-001'),
    ],
  },
  {
    dataType: 'EXAM',
    label: 'Đề thi',
    naturalKey: ['code'],
    dependsOn: [],
    columns: [
      column('code', true, 50, 'Mã đề theo mẫu ^[A-Z0-9_-]{3,50}$.', 'DEMO_EXAM_01'),
      column('title', true, 200, 'Tên đề thi.', 'Đề tự soạn số 1'),
      column('hsk_level', true, null, 'Cấp HSK từ 1 đến 6.', '1'),
      column('sections', true, null, 'Từ 1 đến 10 phần, chỉ số tăng dần.', '1:Đọc hiểu|2:Ngữ pháp'),
      column('status', true, null, 'DRAFT, PUBLISHED hoặc ARCHIVED.', 'DRAFT'),
    ],
  },
  {
    dataType: 'EXAM_QUESTION',
    label: 'Câu trong đề thi',
    naturalKey: ['exam_code', 'question_code'],
    dependsOn: ['EXAM', 'QUESTION'],
    columns: [
      column('exam_code', true, null, 'Mã đề đã tồn tại.', 'DEMO_EXAM_01'),
      column('question_code', true, null, 'Mã câu hỏi đã tồn tại.', 'DEMO_TV1_001'),
      column('section_index', true, null, 'Chỉ số phần có trong đề.', '1'),
      column('order_index', true, null, 'Thứ tự câu từ 1 đến 999.', '1'),
      column('points', false, null, 'Điểm từ 1 đến 10, mặc định 1.', '1'),
    ],
  },
]

beforeEach(() => vi.stubEnv('VITE_API_BASE_URL', origin))
afterEach(() => {
  vi.unstubAllGlobals()
  vi.unstubAllEnvs()
})

describe('SCR-068 templates with the real shared client (§13 · AT-28)', () => {
  it('reads all 13 server templates without losing nullable metadata, dependencies or blank-cell instructions', async () => {
    const server = vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
      expect(String(input)).toBe(`${origin}/api/admin/imports/templates`)
      expect(init?.method).toBe('GET')
      expect(init?.credentials).toBe('include')
      expect(init?.body).toBeUndefined()
      return new Response(JSON.stringify(templates), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      })
    })
    vi.stubGlobal('fetch', server)
    const signal = new AbortController().signal
    const result = await getImportTemplates(signal)
    expect(result).toEqual(templates)
    expect(result.map(({ dataType }) => dataType)).toEqual(
      templates.map(({ dataType }) => dataType),
    )
    expect(result).toHaveLength(13)
    expect(
      result
        .find(({ dataType }) => dataType === 'QUESTION')
        ?.columns.find(({ name }) => name === 'kid_secondary'),
    ).toMatchObject({
      maxLength: null,
      description:
        'Tối đa hai KID phụ ngăn bằng |, không trùng nhau hoặc trùng KID chính; để trống xoá toàn bộ KID phụ cũ.',
    })
    expect(result.find(({ dataType }) => dataType === 'EXAM')).toMatchObject({
      naturalKey: ['code'],
      dependsOn: [],
      columns: ['code', 'title', 'hsk_level', 'sections', 'status'].map((name) =>
        expect.objectContaining({ name }),
      ),
    })
    expect(result.find(({ dataType }) => dataType === 'EXAM_QUESTION')).toMatchObject({
      naturalKey: ['exam_code', 'question_code'],
      dependsOn: ['EXAM', 'QUESTION'],
      columns: ['exam_code', 'question_code', 'section_index', 'order_index', 'points'].map(
        (name) => expect.objectContaining({ name }),
      ),
    })
    expect(server.mock.calls[0]![1]?.signal).toBe(signal)
    const headers = new Headers(server.mock.calls[0]![1]?.headers)
    expect(headers.get('X-Client-Type')).toBe('WEB')
    expect(headers.has('Authorization')).toBe(false)
    expect(headers.has('X-XSRF-TOKEN')).toBe(false)
  })

  it.each(['KNOWLEDGE_NODE', 'KNOWLEDGE_EDGE', 'QUESTION', 'NODE_LINK'] as const)(
    'downloads the server CSV for %s through the real client',
    async (dataType) => {
      vi.stubGlobal(
        'fetch',
        vi.fn<typeof fetch>().mockImplementation(async (input, init) => {
          expect(String(input)).toBe(`${origin}/api/admin/imports/templates/${dataType}.csv`)
          expect(init).toMatchObject({ method: 'GET', credentials: 'include' })
          return new Response('kid,name_zh\nNP1-901,示例', {
            status: 200,
            headers: {
              'Content-Type': 'text/csv; charset=utf-8',
              'Content-Disposition': `attachment; filename="${dataType}-server.csv"`,
            },
          })
        }),
      )
      const result = await getImportTemplateFile(dataType)
      expect(result?.fileName).toBe(`${dataType}-server.csv`)
      expect(await result?.blob.text()).toBe('kid,name_zh\nNP1-901,示例')
    },
  )

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
  ] as const)('preserves templates %s %s with the request id', async (status, errorCode) => {
    vi.stubGlobal(
      'fetch',
      vi.fn<typeof fetch>().mockResolvedValue(
        new Response(
          JSON.stringify({
            error_code: errorCode,
            message: 'Không thể tải mẫu.',
            request_id: 'req-templates',
          }),
          { status, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    )
    await expect(getImportTemplates()).rejects.toMatchObject({
      status,
      errorCode,
      requestId: 'req-templates',
    })
  })
})
