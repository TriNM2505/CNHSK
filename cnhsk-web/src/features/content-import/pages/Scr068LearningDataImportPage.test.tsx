import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiError } from '@/shared/api'

import {
  confirmImportRun,
  getImportRuns,
  getImportTemplateFile,
  getImportTemplates,
  uploadImportFile,
} from '../api/imports'
import * as importView from '../components/LearningDataImportView'
import type {
  ImportDataType,
  ImportPreview,
  ImportRun,
  ImportRunPage,
  ImportTemplate,
} from '../types'
import Scr068LearningDataImportPage from './Scr068LearningDataImportPage'

vi.mock('../api/imports', () => ({
  confirmImportRun: vi.fn(),
  getImportRuns: vi.fn(),
  getImportTemplateFile: vi.fn(),
  getImportTemplates: vi.fn(),
  uploadImportFile: vi.fn(),
}))

vi.mock('@/shared/lib/downloadBlob', () => ({ downloadBlob: vi.fn() }))

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

const templatesWithoutExamMetadata = templates.filter(
  ({ dataType }) => dataType !== 'EXAM' && dataType !== 'EXAM_QUESTION',
)

const knowledgeTypes = ['KNOWLEDGE_NODE', 'KNOWLEDGE_EDGE', 'QUESTION', 'NODE_LINK'] as const

const NOW = Date.parse('2026-10-20T05:00:00Z')

function makePreview(overrides: Partial<ImportPreview> = {}): ImportPreview {
  return {
    alreadyImported: false,
    dataType: 'LEXEME',
    errorCount: 1,
    errors: [
      { code: 'INVALID_PINYIN', column: 'pinyin', line: 3, message: 'Pinyin sai.', value: 'x' },
    ],
    expiresAt: '2026-10-20T05:30:00Z',
    fileName: 'tu.csv',
    preview: [
      { action: 'CREATE', line: 2, values: { kind: 'WORD', text: '学习', pinyin: 'xuéxí' } },
    ],
    runId: 42,
    status: 'PENDING',
    totals: { create: 1, invalid: 1, total: 2, unchanged: 0, update: 0, valid: 1 },
    ...overrides,
  }
}

const completedRun: ImportRun = {
  createdAt: '2026-10-20T05:01:00Z',
  createdBy: { displayName: 'Biên tập', id: 1 },
  dataType: 'LEXEME',
  errorCount: 0,
  fileName: 'tu.csv',
  runId: 42,
  status: 'COMPLETED',
}

function csvFile(name = 'tu.csv', size = 10) {
  return new File(['x'.repeat(size)], name, { type: 'text/csv' })
}

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((complete) => {
    resolve = complete
  })
  return { promise, resolve }
}

function Probe() {
  const location = useLocation()
  return <output data-testid="location">{location.pathname}</output>
}

function renderPage(path = '/content/imports') {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route element={<Scr068LearningDataImportPage />} path="/content/imports" />
        <Route element={<Probe />} path="/content/imports/:id" />
      </Routes>
    </MemoryRouter>,
  )
}

async function chooseAndUpload(file = csvFile()) {
  const user = userEvent.setup({ applyAccept: false })
  await user.selectOptions(await screen.findByLabelText(/Loại dữ liệu/), 'LEXEME')
  await user.upload(screen.getByLabelText(/File CSV/), file)
  await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
  return user
}

describe('Scr068LearningDataImportPage', () => {
  beforeEach(() => {
    vi.mocked(getImportTemplates).mockReset().mockResolvedValue(templates)
    vi.mocked(getImportRuns)
      .mockReset()
      .mockResolvedValue({ items: [], page: 0, size: 10, totalElements: 0 })
    vi.mocked(uploadImportFile).mockReset()
    vi.mocked(confirmImportRun).mockReset()
    vi.mocked(getImportTemplateFile).mockReset()
    vi.spyOn(Date, 'now').mockReturnValue(NOW)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('FR006: shows missing, unknown and duplicate column groups below the file input', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
        details: [
          { field: 'pinyin', message: 'Thiếu cột bắt buộc: pinyin' },
          { field: 'hsk_level', message: 'Thiếu cột bắt buộc: hsk_level' },
          { field: 'note', message: 'Cột không có trong mẫu: note' },
          { field: 'text', message: 'Tên cột bị trùng sau khi bỏ khoảng trắng: text' },
        ],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Thiếu cột: pinyin, hsk_level')).toBeVisible()
    expect(within(alert).getByText('Cột không thuộc mẫu: note')).toBeVisible()
    expect(within(alert).getByText('Cột bị trùng: text')).toBeVisible()
    expect(screen.getByLabelText(/File CSV/)).toHaveAttribute(
      'aria-describedby',
      expect.stringContaining('import-upload-error'),
    )
  })

  it('FR006: a template mismatch without details keeps the general message', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Các cột trong file không khớp mẫu đã chọn.')).toBeVisible()
    expect(
      within(alert).queryByText(/Thiếu cột:|Cột không thuộc mẫu:|Cột bị trùng:/),
    ).not.toBeInTheDocument()
  })

  it('FR006: omits empty column groups when only required columns are missing', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        message: 'Sai mẫu',
        status: 422,
        details: [{ field: 'pinyin', message: 'Thiếu cột bắt buộc: pinyin' }],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Thiếu cột: pinyin')).toBeVisible()
    expect(within(alert).queryByText(/Cột không thuộc mẫu:|Cột bị trùng:/)).not.toBeInTheDocument()
  })

  it('FR008_FR026: lịch sử hiển thị lần upload bị chặn trước khi đọc metadata', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [
        {
          ...completedRun,
          dataType: null,
          fileName: null,
          status: 'FAILED',
          failureReason: 'FILE_TOO_LARGE',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    const fileName = await within(table).findByText('Không đọc được tên tệp')
    const row = fileName.closest('tr')!
    expect(within(row).getByText('—')).toBeVisible()
    await userEvent.click(row)
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it.each([
    { reason: 'FILE_TOO_LARGE', message: 'File vượt quá giới hạn 2 MB.' },
    { reason: 'ENCODING_ERROR', message: 'File không phải UTF-8 hoặc chữ Hán bị hỏng.' },
    { reason: 'INTERRUPTED', message: 'Quá trình nhập bị gián đoạn.' },
    { reason: 'PRIVATE_DATABASE_DETAIL', message: 'Không thể hoàn tất lần nhập dữ liệu.' },
    { reason: undefined, message: 'Không thể hoàn tất lần nhập dữ liệu.' },
  ])('FR029: lịch sử FAILED hiện lý do ngắn $reason', async ({ reason, message }) => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [{ ...completedRun, status: 'FAILED', failureReason: reason }],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    expect(within(table).getByText(message)).toBeVisible()
    expect(within(table).queryByText('PRIVATE_DATABASE_DETAIL')).not.toBeInTheDocument()
  })

  it('FR029: chỉ hiện lý do thất bại ở lần nhập FAILED', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [{ ...completedRun, failureReason: 'INTERRUPTED' }],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()
    const table = await screen.findByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' })
    expect(within(table).queryByText('Quá trình nhập bị gián đoạn.')).not.toBeInTheDocument()
  })

  it('FR-036: kiểm tra phía khách trước khi gửi', async () => {
    renderPage()
    const user = userEvent.setup({ applyAccept: false })

    await user.click(await screen.findByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('Hãy chọn loại dữ liệu.')).toBeVisible()
    expect(screen.getByText('Hãy chọn file CSV cần kiểm tra.')).toBeVisible()

    await user.selectOptions(screen.getByLabelText(/Loại dữ liệu/), 'LEXEME')
    await user.upload(screen.getByLabelText(/File CSV/), csvFile('tu.xlsx'))
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('Chỉ chấp nhận file có đuôi .csv.')).toBeVisible()

    await user.upload(screen.getByLabelText(/File CSV/), csvFile('lon.csv', 2 * 1024 * 1024 + 1))
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(screen.getByText('File vượt quá giới hạn 2 MB.')).toBeVisible()
    expect(uploadImportFile).not.toHaveBeenCalled()
  })

  it('chọn sẵn loại dữ liệu từ ?dataType= hợp lệ, bỏ qua giá trị lạ', async () => {
    renderPage('/content/imports?dataType=GRAMMAR')
    await screen.findByText('Cột của mẫu Điểm ngữ pháp')
    expect(screen.getByLabelText(/Loại dữ liệu/)).toHaveValue('GRAMMAR')
  })

  it('bỏ qua ?dataType= không hợp lệ', async () => {
    renderPage('/content/imports?dataType=UNKNOWN_TYPE')
    expect(await screen.findByLabelText(/Loại dữ liệu/)).toHaveValue('')
  })

  it('FR-037: hiện 6 số, cột theo mẫu, link xem tất cả lỗi', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByText('Kết quả kiểm tra')).toBeVisible()
    expect(uploadImportFile).toHaveBeenCalledWith('LEXEME', expect.any(File))
    for (const label of [
      'Tổng số dòng',
      'Hợp lệ',
      'Không hợp lệ',
      'Tạo mới',
      'Cập nhật',
      'Không đổi',
    ]) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0)
    }
    const previewTable = screen.getByRole('table', { name: 'Xem trước các dòng hợp lệ đầu tiên' })
    expect(within(previewTable).getByRole('columnheader', { name: 'text' })).toBeInTheDocument()
    expect(within(previewTable).getAllByText('学习')[0]).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByRole('link', { name: 'Xem tất cả 1 lỗi' })).toHaveAttribute(
      'href',
      '/content/imports/42',
    )
  })

  it('FR-008: lỗi cấp file có runId thì tải lại lịch sử', async () => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        errorCode: 'TEMPLATE_MISMATCH',
        extra: { runId: 7 },
        message: 'x',
        requestId: 'req-9',
        status: 422,
      }),
    )
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByText('Các cột trong file không khớp mẫu đã chọn.')).toBeVisible()
    expect(getImportRuns).toHaveBeenCalledTimes(2)
    expect(screen.getByLabelText(/File CSV/)).toHaveAccessibleDescription(
      /Các cột trong file không khớp mẫu đã chọn/,
    )
    expect(screen.getByText('Mã lỗi: req-9')).toBeVisible()
  })

  it('file đã nhập trước: phải tick mới xác nhận được và gửi acknowledgeRerun=true', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    vi.mocked(confirmImportRun).mockResolvedValue(completedRun)
    renderPage()

    const user = await chooseAndUpload()
    const confirmButton = await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })
    expect(confirmButton).toBeDisabled()

    await user.click(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó'))
    await user.click(confirmButton)
    expect(screen.getByText('Ghi 1 dòng vào kho? Không hoàn tác được.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(confirmImportRun).toHaveBeenCalledWith(42, { acknowledgeRerun: true })
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('valid = 0 thì khoá nút xác nhận', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({
        preview: [],
        totals: { create: 0, invalid: 2, total: 2, unchanged: 0, update: 0, valid: 0 },
      }),
    )
    renderPage()

    await chooseAndUpload()

    expect(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('CONCURRENT_IMPORT giữ kết quả kiểm tra để bấm xác nhận lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'CONCURRENT_IMPORT', message: 'x', status: 409 }),
      )
      .mockResolvedValueOnce(completedRun)
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(await screen.findByText(/Đang có lần nhập khác chạy/)).toBeVisible()
    const confirmButton = screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })
    expect(confirmButton).toBeEnabled()

    await user.click(confirmButton)
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('410 IMPORT_PREVIEW_EXPIRED khoá xác nhận và mời tải lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun).mockRejectedValue(
      new ApiError({ errorCode: 'IMPORT_PREVIEW_EXPIRED', message: 'x', status: 410 }),
    )
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))

    expect(
      await screen.findByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.'),
    ).toBeVisible()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('đồng hồ về 0 thì khoá xác nhận', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({ expiresAt: '2026-10-20T05:00:02Z' }),
    )
    renderPage()

    await chooseAndUpload()
    expect(await screen.findByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeEnabled()
    expect(screen.getByText('00:02')).toBeVisible()

    vi.mocked(Date.now).mockReturnValue(NOW + 3000)
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1100))
    })

    expect(screen.getByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
  })

  it('Huỷ chỉ xoá kết quả trên trang, không gọi API', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    renderPage()

    const user = await chooseAndUpload()
    await user.click(await screen.findByRole('button', { name: 'Huỷ' }))

    expect(screen.queryByText('Kết quả kiểm tra')).not.toBeInTheDocument()
    expect((screen.getByLabelText(/File CSV/) as HTMLInputElement).files).toHaveLength(0)
    expect(confirmImportRun).not.toHaveBeenCalled()
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
  })

  it('tải mẫu dùng tên file từ server, không có thì theo dataType', async () => {
    const { downloadBlob } = await import('@/shared/lib/downloadBlob')
    const blob = new Blob(['kind,text,pinyin'])
    vi.mocked(getImportTemplateFile).mockResolvedValue({ blob })
    renderPage()
    const user = userEvent.setup()

    await user.selectOptions(await screen.findByLabelText(/Loại dữ liệu/), 'LEXEME')
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))

    expect(getImportTemplateFile).toHaveBeenCalledWith('LEXEME')
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'LEXEME.csv')
  })

  it('FR-040: lịch sử rỗng hiện thông báo', async () => {
    renderPage()
    expect(await screen.findByText('Chưa có lần nhập dữ liệu')).toBeVisible()
  })

  it('FR-040: bấm một dòng lịch sử mở báo cáo', async () => {
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [completedRun],
      page: 0,
      size: 10,
      totalElements: 1,
    })
    renderPage()

    const row = await screen.findAllByRole('button', { name: 'Mở báo cáo lần nhập #42' })
    fireEvent.click(row[0] as HTMLElement)

    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it.each([
    ['LEXEME', 'Chữ và từ vựng', 'kind, text', 13],
    ['TOPIC', 'Chủ đề từ vựng', 'code', 4],
    ['TOPIC_ITEM', 'Từ trong chủ đề', 'topic_code, kind, text', 4],
    ['GRAMMAR', 'Điểm ngữ pháp', 'text', 15],
    ['GRAMMAR_EXERCISE', 'Bài tập ngữ pháp', 'grammar_text, exercise_no', 9],
    ['PRON_STAGE', 'Tầng phát âm', 'stage_index', 5],
    ['PRON_UNIT', 'Bài nghe phát âm', 'stage_index, unit_code', 10],
    ['KNOWLEDGE_NODE', 'Nút cây năng lực', 'kid', 5],
    ['KNOWLEDGE_EDGE', 'Quan hệ giữa các nút', 'from_kid, to_kid', 3],
    ['QUESTION', 'Câu hỏi', 'code', 18],
    ['NODE_LINK', 'Gắn nội dung vào nút', 'target_type, target_key', 3],
    ['EXAM', 'Đề thi', 'code', 5],
    ['EXAM_QUESTION', 'Câu trong đề thi', 'exam_code, question_code', 5],
  ] as const)(
    'FR036_FR028_AT28_guide_%s: chọn đủ mười ba mẫu và hướng dẫn đúng khóa/cột',
    async (type, label, key, count) => {
      renderPage()
      const user = userEvent.setup()
      const select = await screen.findByLabelText(/Loại dữ liệu/)
      expect(within(select).getAllByRole('option')).toHaveLength(14)
      expect(within(select).getByRole('option', { name: label })).toHaveValue(type)
      await user.selectOptions(select, type)
      expect(screen.getAllByText(key)[0]).toBeVisible()
      const table = screen.getByRole('table', { name: `Danh sách cột mẫu ${label}` })
      expect(within(table).getAllByRole('row')).toHaveLength(Number(count) + 1)
      for (const header of ['Tên cột', 'Bắt buộc', 'Tối đa', 'Mô tả', 'Ví dụ']) {
        expect(within(table).getByRole('columnheader', { name: header })).toBeVisible()
      }
      expect(screen.getByLabelText(/File CSV/)).toHaveAttribute('accept', '.csv')
    },
  )

  it('FR028_AT28: giữ đúng nhãn và thứ tự mười ba loại dữ liệu', async () => {
    renderPage()
    const select = await screen.findByLabelText(/Loại dữ liệu/)
    expect(
      within(select)
        .getAllByRole('option')
        .map((option) => [(option as HTMLOptionElement).value, option.textContent]),
    ).toEqual([
      ['', 'Chọn loại dữ liệu'],
      ['LEXEME', 'Chữ và từ vựng'],
      ['TOPIC', 'Chủ đề từ vựng'],
      ['TOPIC_ITEM', 'Từ trong chủ đề'],
      ['GRAMMAR', 'Điểm ngữ pháp'],
      ['GRAMMAR_EXERCISE', 'Bài tập ngữ pháp'],
      ['PRON_STAGE', 'Tầng phát âm'],
      ['PRON_UNIT', 'Bài nghe phát âm'],
      ['KNOWLEDGE_NODE', 'Nút cây năng lực'],
      ['KNOWLEDGE_EDGE', 'Quan hệ giữa các nút'],
      ['QUESTION', 'Câu hỏi'],
      ['NODE_LINK', 'Gắn nội dung vào nút'],
      ['EXAM', 'Đề thi'],
      ['EXAM_QUESTION', 'Câu trong đề thi'],
    ])
  })

  it.each(templates.map((template) => template.dataType))(
    'FR028_AT28: đủ 13 metadata server thì mẫu %s sẵn sàng',
    async (dataType) => {
      renderPage(`/content/imports?dataType=${dataType}`)
      const template = templates.find((item) => item.dataType === dataType)!
      expect(
        await screen.findByRole('table', { name: `Danh sách cột mẫu ${template.label}` }),
      ).toBeVisible()
      expect(screen.queryByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Tải mẫu' })).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Tải lên và kiểm tra' })).toBeEnabled()
    },
  )

  it('FR028_AT28: EXAM có metadata và gửi file lên kiểm tra được', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({
        dataType: 'EXAM',
        errorCount: 0,
        errors: [],
        preview: [
          {
            action: 'CREATE',
            line: 2,
            values: {
              code: 'DEMO_EXAM_01',
              title: 'Đề tự soạn số 1',
              hsk_level: '1',
              sections: '1:Đọc hiểu',
              status: 'DRAFT',
            },
          },
        ],
        totals: { create: 1, invalid: 0, total: 1, unchanged: 0, update: 0, valid: 1 },
      }),
    )
    renderPage('/content/imports?dataType=EXAM')
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Danh sách cột mẫu Đề thi' })
    const file = csvFile('de-tu-soan.csv')
    await user.upload(screen.getByLabelText(/File CSV/), file)
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(uploadImportFile).toHaveBeenCalledWith('EXAM', file)
    expect(await screen.findByRole('region', { name: 'Kết quả kiểm tra' })).toBeVisible()
  })

  it('FR028_AT28: lỗi 422 VALIDATION_FAILED theo dataType hiện thông báo server và giữ lựa chọn cùng file', async () => {
    const message = 'Loại dữ liệu này chưa hỗ trợ nhập'
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode: 'VALIDATION_FAILED',
        message,
        requestId: 'req-upload-datatype-validation',
        details: [{ field: 'dataType', message }],
      }),
    )
    renderPage('/content/imports?dataType=EXAM_QUESTION')
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Danh sách cột mẫu Câu trong đề thi' })
    expect(screen.queryByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).not.toBeInTheDocument()
    const select = screen.getByLabelText(/Loại dữ liệu/)
    const fileInput = screen.getByLabelText(/File CSV/) as HTMLInputElement
    const file = csvFile('cau-trong-de.csv')
    await user.upload(fileInput, file)
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    const alert = await screen.findByRole('alert')
    expect(select).toHaveValue('EXAM_QUESTION')
    expect(fileInput.files?.item(0)).toBe(file)
    expect(screen.queryByRole('region', { name: 'Kết quả kiểm tra' })).not.toBeInTheDocument()
    expect(within(alert).getByText('Mã lỗi: req-upload-datatype-validation')).toBeVisible()
    expect(within(alert).getByText(message)).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(uploadImportFile).toHaveBeenNthCalledWith(1, 'EXAM_QUESTION', file)
    expect(uploadImportFile).toHaveBeenNthCalledWith(2, 'EXAM_QUESTION', file)
    expect(select).toHaveValue('EXAM_QUESTION')
    expect(fileInput.files?.item(0)).toBe(file)
  })

  it.each([
    {
      name: 'VALIDATION_FAILED cho file',
      errorCode: 'VALIDATION_FAILED',
      message: 'Cần chọn một tệp có tên.',
      details: [{ field: 'file', message: 'Cần chọn một tệp có tên.' }],
      expected: 'Loại dữ liệu hoặc file chưa hợp lệ.',
    },
    {
      name: 'VALIDATION_FAILED thiếu details',
      errorCode: 'VALIDATION_FAILED',
      message: 'Thông báo kiểm tra từ server.',
      details: undefined,
      expected: 'Loại dữ liệu hoặc file chưa hợp lệ.',
    },
    {
      name: 'VALIDATION_FAILED dataType với message rỗng',
      errorCode: 'VALIDATION_FAILED',
      message: '',
      details: [{ field: 'dataType', message: 'Loại dữ liệu này chưa hỗ trợ nhập' }],
      expected: 'Loại dữ liệu hoặc file chưa hợp lệ.',
    },
    {
      name: 'VALIDATION_FAILED dataType với message chỉ có khoảng trắng',
      errorCode: 'VALIDATION_FAILED',
      message: ' \t ',
      details: [{ field: 'dataType', message: 'Loại dữ liệu này chưa hỗ trợ nhập' }],
      expected: 'Loại dữ liệu hoặc file chưa hợp lệ.',
    },
    {
      name: 'mã khác dù có field dataType',
      errorCode: 'TOO_MANY_ROWS',
      message: 'Thông báo riêng từ server.',
      details: [{ field: 'dataType', message: 'Loại dữ liệu này chưa hỗ trợ nhập' }],
      expected: 'File vượt quá giới hạn 5.000 dòng dữ liệu.',
    },
  ])('FR028_AT28: giữ ánh xạ cũ cho $name', async ({ errorCode, message, details, expected }) => {
    vi.mocked(uploadImportFile).mockRejectedValue(
      new ApiError({
        status: 422,
        errorCode,
        message,
        details,
        requestId: 'req-upload-fallback',
      }),
    )
    renderPage('/content/imports?dataType=EXAM_QUESTION')
    const user = userEvent.setup()
    await screen.findByRole('table', { name: 'Danh sách cột mẫu Câu trong đề thi' })
    const file = csvFile('cau-trong-de.csv')
    const fileInput = screen.getByLabelText(/File CSV/) as HTMLInputElement
    await user.upload(fileInput, file)
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText(expected)).toBeVisible()
    expect(within(alert).getByText('Mã lỗi: req-upload-fallback')).toBeVisible()
    expect(screen.getByLabelText(/Loại dữ liệu/)).toHaveValue('EXAM_QUESTION')
    expect(fileInput.files?.item(0)).toBe(file)
    expect(uploadImportFile).toHaveBeenCalledWith('EXAM_QUESTION', file)
  })

  it('FR028_AT28: hiện nguyên quy tắc ô trống QUESTION từ metadata máy chủ', async () => {
    vi.mocked(getImportTemplates).mockResolvedValue(
      templates.map((template) =>
        template.dataType !== 'QUESTION'
          ? template
          : {
              ...template,
              columns: template.columns.map((item) => ({
                ...item,
                description:
                  item.name === 'option_a'
                    ? 'Thay nguyên cấu trúc; để trống xoá phương án cũ tương ứng.'
                    : item.name === 'kid_secondary'
                      ? 'Để trống xoá toàn bộ KID phụ.'
                      : item.name === 'audio_url'
                        ? 'Cập nhật để trống giữ giá trị cũ.'
                        : item.description,
              })),
            },
      ),
    )
    renderPage('/content/imports?dataType=QUESTION')
    const guide = await screen.findByRole('table', { name: 'Danh sách cột mẫu Câu hỏi' })
    for (const text of [
      'Thay nguyên cấu trúc; để trống xoá phương án cũ tương ứng.',
      'Để trống xoá toàn bộ KID phụ.',
      'Cập nhật để trống giữ giá trị cũ.',
    ])
      expect(within(guide).getByText(text)).toBeVisible()
  })

  it.each(['EXAM', 'EXAM_QUESTION'] as const)(
    'FR028_AT28: mẫu %s mở với cột, khoá và phụ thuộc thật từ 012/T-07',
    async (dataType) => {
      const template = templates.find((item) => item.dataType === dataType)!
      renderPage('/content/imports?dataType=' + dataType)
      const guide = await screen.findByRole('table', {
        name: 'Danh sách cột mẫu ' + template.label,
      })
      for (const item of template.columns) {
        const row = within(guide).getByText(item.name).closest('tr')!
        expect(within(row).getByText(item.description!)).toBeVisible()
        expect(within(row).getByText(item.required ? 'Có' : 'Không')).toBeVisible()
      }
      expect(screen.getAllByText(template.naturalKey.join(', '))[0]).toBeVisible()
      if (dataType === 'EXAM_QUESTION')
        expect(screen.getByText(/Cần nhập trước: Đề thi, Câu hỏi/)).toBeVisible()
      expect(screen.queryByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Tải mẫu' })).toBeEnabled()
      expect(screen.getByRole('button', { name: 'Tải lên và kiểm tra' })).toBeEnabled()
    },
  )

  it.each(['EXAM', 'EXAM_QUESTION'] as const)(
    'FR005_AT28: giữ lựa chọn %s và khoá thao tác khi thiếu metadata',
    async (dataType) => {
      vi.mocked(getImportTemplates).mockResolvedValue(templatesWithoutExamMetadata)
      renderPage()
      const user = userEvent.setup()
      const select = await screen.findByLabelText(/Loại dữ liệu/)
      const fileInput = screen.getByLabelText(/File CSV/)
      await user.upload(fileInput, csvFile())
      await user.selectOptions(select, dataType)

      expect(select).toHaveValue(dataType)
      expect(screen.getByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).toBeVisible()
      const downloadButton = screen.getByRole('button', { name: 'Tải mẫu' })
      const uploadButton = screen.getByRole('button', { name: 'Tải lên và kiểm tra' })
      expect(downloadButton).toBeDisabled()
      expect(uploadButton).toBeDisabled()
      await user.click(downloadButton)
      await user.click(uploadButton)
      fireEvent.submit(fileInput.closest('form')!)
      expect(getImportTemplateFile).not.toHaveBeenCalled()
      expect(uploadImportFile).not.toHaveBeenCalled()
      expect(select).toHaveValue(dataType)
    },
  )

  it.each(knowledgeTypes)('FR028_AT28: lấy hướng dẫn %s từ metadata API', async (dataType) => {
    const template = templates.find((item) => item.dataType === dataType)!
    vi.mocked(getImportTemplates).mockResolvedValue([
      {
        ...template,
        naturalKey: ['server_key'],
        dependsOn: ['PRON_STAGE'],
        columns: [
          {
            name: 'server_column',
            required: true,
            maxLength: 37,
            description: 'Mô tả riêng từ máy chủ.',
            example: '服务器示例',
          },
        ],
      },
    ])
    renderPage(`/content/imports?dataType=${dataType}`)
    const table = await screen.findByRole('table', { name: `Danh sách cột mẫu ${template.label}` })
    expect(within(table).getAllByRole('row')).toHaveLength(2)
    for (const text of [
      'server_column',
      'Có',
      '37 ký tự',
      'Mô tả riêng từ máy chủ.',
      '服务器示例',
    ]) {
      expect(within(table).getByText(text)).toBeVisible()
    }
    expect(screen.getByText('server_key')).toBeVisible()
    expect(screen.getByText(/Cần nhập trước: Tầng phát âm/)).toBeVisible()
  })

  it.each(knowledgeTypes)('FR028_AT28: chọn sẵn, tải mẫu và upload đúng %s', async (dataType) => {
    const { downloadBlob } = await import('@/shared/lib/downloadBlob')
    const blob = new Blob(['server_column\nexample'])
    vi.mocked(getImportTemplateFile).mockResolvedValue({ blob, fileName: `${dataType}-server.csv` })
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ dataType }))
    renderPage(`/content/imports?dataType=${dataType}`)
    const user = userEvent.setup()
    const select = await screen.findByLabelText(/Loại dữ liệu/)
    expect(select).toHaveValue(dataType)
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))
    expect(getImportTemplateFile).toHaveBeenCalledWith(dataType)
    expect(downloadBlob).toHaveBeenCalledWith(blob, `${dataType}-server.csv`)

    const file = csvFile(`${dataType}.csv`)
    await user.upload(screen.getByLabelText(/File CSV/), file)
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(uploadImportFile).toHaveBeenCalledWith(dataType, file)
    expect(await screen.findByRole('region', { name: 'Kết quả kiểm tra' })).toBeVisible()
  })

  it.each(templates.slice(0, 7).map((template) => template.dataType))(
    'FR028_AT28: server chỉ trả bảy mẫu vẫn tải và upload được %s',
    async (dataType: ImportDataType) => {
      vi.mocked(getImportTemplates).mockResolvedValue(templates.slice(0, 7))
      vi.mocked(getImportTemplateFile).mockResolvedValue({ blob: new Blob(['example']) })
      vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ dataType }))
      renderPage(`/content/imports?dataType=${dataType}`)
      const user = userEvent.setup()
      const select = await screen.findByLabelText(/Loại dữ liệu/)
      expect(select).toHaveValue(dataType)
      await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))
      expect(getImportTemplateFile).toHaveBeenCalledWith(dataType)
      const file = csvFile()
      await user.upload(screen.getByLabelText(/File CSV/), file)
      await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
      expect(uploadImportFile).toHaveBeenCalledWith(dataType, file)
    },
  )

  it.each(knowledgeTypes)(
    'FR028_AT28: server có bảy mẫu, chọn %s bằng dropdown giữ lựa chọn và chặn request',
    async (dataType) => {
      vi.mocked(getImportTemplates).mockResolvedValue(templates.slice(0, 7))
      renderPage()
      const user = userEvent.setup()
      const select = await screen.findByLabelText(/Loại dữ liệu/)
      await user.upload(screen.getByLabelText(/File CSV/), csvFile())
      await user.selectOptions(select, dataType)

      expect(select).toHaveValue(dataType)
      expect(screen.getByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).toBeVisible()
      expect(screen.queryByRole('table', { name: /Danh sách cột mẫu/ })).not.toBeInTheDocument()
      const downloadButton = screen.getByRole('button', { name: 'Tải mẫu' })
      const uploadButton = screen.getByRole('button', { name: 'Tải lên và kiểm tra' })
      expect(downloadButton).toBeDisabled()
      expect(uploadButton).toBeDisabled()
      await user.click(downloadButton)
      await user.click(uploadButton)
      fireEvent.submit(screen.getByLabelText(/File CSV/).closest('form')!)
      expect(getImportTemplateFile).not.toHaveBeenCalled()
      expect(uploadImportFile).not.toHaveBeenCalled()
      expect(select).toHaveValue(dataType)
    },
  )

  it.each(knowledgeTypes)(
    'FR028_AT28: server có bảy mẫu, chọn %s qua URL giữ lựa chọn và chặn request',
    async (dataType) => {
      vi.mocked(getImportTemplates).mockResolvedValue(templates.slice(0, 7))
      renderPage(`/content/imports?dataType=${dataType}`)
      const user = userEvent.setup()
      expect(await screen.findByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).toBeVisible()
      const select = screen.getByLabelText(/Loại dữ liệu/)
      expect(select).toHaveValue(dataType)
      expect(screen.queryByRole('table', { name: /Danh sách cột mẫu/ })).not.toBeInTheDocument()
      await user.upload(screen.getByLabelText(/File CSV/), csvFile())
      const downloadButton = screen.getByRole('button', { name: 'Tải mẫu' })
      const uploadButton = screen.getByRole('button', { name: 'Tải lên và kiểm tra' })
      expect(downloadButton).toBeDisabled()
      expect(uploadButton).toBeDisabled()
      await user.click(downloadButton)
      await user.click(uploadButton)
      fireEvent.submit(screen.getByLabelText(/File CSV/).closest('form')!)
      expect(getImportTemplateFile).not.toHaveBeenCalled()
      expect(uploadImportFile).not.toHaveBeenCalled()
      expect(select).toHaveValue(dataType)
    },
  )

  it.each(knowledgeTypes)(
    'FR028_AT28: page tự chặn callback tải mẫu/upload %s khi View không khoá nút',
    async (dataType) => {
      vi.mocked(getImportTemplates).mockResolvedValue(templates.slice(0, 7))
      const view = vi.spyOn(importView, 'LearningDataImportView').mockReturnValue(<div />)
      renderPage(`/content/imports?dataType=${dataType}`)
      await waitFor(() => expect(view.mock.lastCall?.[0].templateUnavailable).toBe(true))
      act(() => view.mock.lastCall![0].onFileChange(csvFile()))
      await act(async () => {
        view.mock.lastCall![0].onDownloadTemplate()
        view.mock.lastCall![0].onUpload()
      })
      expect(view.mock.lastCall![0].dataType).toBe(dataType)
      expect(getImportTemplateFile).not.toHaveBeenCalled()
      expect(uploadImportFile).not.toHaveBeenCalled()
    },
  )

  it('FR028_AT28: chọn lại mẫu có metadata khôi phục tải mẫu/upload và giữ file đã chọn', async () => {
    vi.mocked(getImportTemplates).mockResolvedValue(templates.slice(0, 7))
    vi.mocked(getImportTemplateFile).mockResolvedValue({ blob: new Blob(['example']) })
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    renderPage('/content/imports?dataType=QUESTION')
    const user = userEvent.setup()
    await screen.findByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')
    const file = csvFile()
    await user.upload(screen.getByLabelText(/File CSV/), file)
    await user.selectOptions(screen.getByLabelText(/Loại dữ liệu/), 'LEXEME')

    expect(screen.queryByText('Mẫu nhập này chưa sẵn sàng trên máy chủ')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Tải mẫu' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Tải lên và kiểm tra' })).toBeEnabled()
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))
    await user.click(screen.getByRole('button', { name: 'Tải lên và kiểm tra' }))
    expect(getImportTemplateFile).toHaveBeenCalledWith('LEXEME')
    expect(uploadImportFile).toHaveBeenCalledWith('LEXEME', file)
  })

  it('FR036_guideDetails: hiện giới hạn, ví dụ và mẫu phụ thuộc từ API', async () => {
    renderPage('/content/imports?dataType=LEXEME')
    const table = await screen.findByRole('table', { name: 'Danh sách cột mẫu Chữ và từ vựng' })
    const textRow = within(table).getByText('text').closest('tr')!
    expect(within(textRow).getByText('Có')).toBeVisible()
    expect(within(textRow).getByText('20 ký tự')).toBeVisible()
    expect(within(textRow).getByText('我们')).toHaveAttribute('lang', 'zh-CN')
    await userEvent.selectOptions(screen.getByLabelText(/Loại dữ liệu/), 'TOPIC_ITEM')
    expect(screen.getByText(/Cần nhập trước: Chủ đề từ vựng, Chữ và từ vựng/)).toBeVisible()
  })

  it('FR036_exactFileLimit: nhận đúng 2 MB và khóa thao tác suốt lúc kiểm tra', async () => {
    const pending = deferred<ImportPreview>()
    vi.mocked(uploadImportFile).mockReturnValue(pending.promise)
    renderPage()
    const file = csvFile('du-2mb.csv', 2 * 1024 * 1024)
    const user = await chooseAndUpload(file)
    expect(uploadImportFile).toHaveBeenCalledWith('LEXEME', file)
    expect(screen.getByLabelText(/Loại dữ liệu/)).toBeDisabled()
    expect(screen.getByLabelText(/File CSV/)).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Tải mẫu' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Đang kiểm tra' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Đang kiểm tra' }))
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
    await act(async () => pending.resolve(makePreview()))
    expect(screen.getByLabelText(/File CSV/)).toBeEnabled()
    expect(screen.getByText('Kết quả kiểm tra')).toBeVisible()
  })

  it('FR036_download: giữ tên file server và cho thử lại sau lỗi tải mẫu', async () => {
    const { downloadBlob } = await import('@/shared/lib/downloadBlob')
    const blob = new Blob(['kind,text,pinyin'])
    vi.mocked(getImportTemplateFile)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'x', status: 500 }),
      )
      .mockResolvedValueOnce({ blob, fileName: 'lexeme-v0.csv' })
    renderPage('/content/imports?dataType=LEXEME')
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'Tải mẫu' }))
    expect(await screen.findByText('Chưa tải được file mẫu. Vui lòng thử lại.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Tải mẫu' }))
    expect(downloadBlob).toHaveBeenCalledWith(blob, 'lexeme-v0.csv')
    expect(screen.queryByText('Chưa tải được file mẫu. Vui lòng thử lại.')).not.toBeInTheDocument()
  })

  it('FR037_totals: giữ số liệu server và đủ ba hành động trong bảng xem trước', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(
      makePreview({
        totals: { total: 5, valid: 3, invalid: 2, create: 1, update: 1, unchanged: 1 },
        preview: [
          { line: 2, action: 'CREATE', values: { kind: 'WORD', text: '学习' } },
          { line: 3, action: 'UPDATE', values: { kind: 'WORD', text: '妈妈' } },
          { line: 4, action: 'UNCHANGED', values: { kind: 'WORD', text: '你好' } },
        ],
      }),
    )
    renderPage()
    await chooseAndUpload()
    const section = screen.getByRole('region', { name: 'Kết quả kiểm tra' })
    for (const [label, value] of [
      ['Tổng số dòng', 5],
      ['Hợp lệ', 3],
      ['Không hợp lệ', 2],
      ['Tạo mới', 1],
      ['Cập nhật', 1],
      ['Không đổi', 1],
    ] as const) {
      expect(within(section).getAllByText(label)[0]!.nextElementSibling).toHaveTextContent(
        String(value),
      )
    }
    const table = within(section).getByRole('table', { name: 'Xem trước các dòng hợp lệ đầu tiên' })
    expect(within(table).getAllByRole('columnheader')).toHaveLength(15)
    for (const action of ['Tạo mới', 'Cập nhật', 'Không đổi']) {
      expect(within(table).getByText(action)).toBeVisible()
    }
  })

  it('FR037_rerunChanged: server yêu cầu nhập lại thì hiện tick dù preview ban đầu chưa trùng', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    vi.mocked(confirmImportRun)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'RERUN_CONFIRMATION_REQUIRED', message: 'x', status: 409 }),
      )
      .mockResolvedValueOnce(completedRun)
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    const checkbox = await screen.findByLabelText('Tôi hiểu file này đã nhập trước đó')
    expect(checkbox).not.toBeChecked()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
    expect(confirmImportRun).toHaveBeenNthCalledWith(1, 42, { acknowledgeRerun: false })
    await user.click(checkbox)
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    expect(confirmImportRun).toHaveBeenNthCalledWith(2, 42, { acknowledgeRerun: true })
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('FR037_uncheck: đóng xác nhận khi bỏ tick nhập lại', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    renderPage()
    const user = await chooseAndUpload()
    const checkbox = screen.getByLabelText('Tôi hiểu file này đã nhập trước đó')
    await user.click(checkbox)
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    expect(screen.getByRole('dialog')).toBeVisible()
    fireEvent.click(checkbox)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' })).toBeDisabled()
    expect(confirmImportRun).not.toHaveBeenCalled()
  })

  it.each(['open', 'submit'])(
    'FR037_expiry_%s: kiểm hạn ngay lúc thao tác, trước tick đồng hồ',
    async (step) => {
      vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
      renderPage()
      const user = await chooseAndUpload()
      if (step === 'submit') {
        await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
      }
      vi.mocked(Date.now).mockReturnValue(NOW + 30 * 60 * 1000)
      fireEvent.click(
        screen.getByRole('button', {
          name: step === 'open' ? 'Xác nhận nhập dữ liệu' : 'Ghi vào kho',
        }),
      )
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
      expect(screen.getByText('Kết quả kiểm tra đã hết hạn, hãy tải file lên lại.')).toBeVisible()
      expect(confirmImportRun).not.toHaveBeenCalled()
    },
  )

  it('FR038_confirmation: chỉ gọi confirm sau bước trong trang, có thể hủy', async () => {
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview())
    const browserConfirm = vi.spyOn(window, 'confirm')
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    const panel = screen.getByRole('dialog', { name: 'Xác nhận nhập dữ liệu' })
    expect(within(panel).getByText('Ghi 1 dòng vào kho? Không hoàn tác được.')).toBeVisible()
    expect(confirmImportRun).not.toHaveBeenCalled()
    expect(browserConfirm).not.toHaveBeenCalled()
    await user.click(within(panel).getByRole('button', { name: 'Huỷ' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByText('Kết quả kiểm tra')).toBeVisible()
    expect(confirmImportRun).not.toHaveBeenCalled()
  })

  it('FR038_pending: khóa tải/đổi/hủy/ghi lần hai đến khi confirm trả về', async () => {
    const pending = deferred<ImportRun>()
    vi.mocked(uploadImportFile).mockResolvedValue(makePreview({ alreadyImported: true }))
    vi.mocked(confirmImportRun).mockReturnValue(pending.promise)
    vi.mocked(getImportRuns).mockResolvedValue({
      items: [completedRun],
      page: 0,
      size: 10,
      totalElements: 11,
    })
    renderPage()
    const user = await chooseAndUpload()
    await user.click(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó'))
    await user.click(screen.getByRole('button', { name: 'Xác nhận nhập dữ liệu' }))
    await user.click(screen.getByRole('button', { name: 'Ghi vào kho' }))
    const panel = screen.getByRole('dialog')
    expect(within(panel).getByRole('button', { name: 'Đang xử lý' })).toBeDisabled()
    expect(within(panel).getByRole('button', { name: 'Huỷ' })).toBeDisabled()
    expect(screen.getByLabelText(/Loại dữ liệu/)).toBeDisabled()
    expect(screen.getByLabelText(/File CSV/)).toBeDisabled()
    expect(screen.getByLabelText('Tôi hiểu file này đã nhập trước đó')).toBeDisabled()
    for (const name of ['Tải mẫu', 'Tải lên và kiểm tra', 'Xác nhận nhập dữ liệu', 'Sau']) {
      expect(screen.getByRole('button', { name })).toBeDisabled()
    }
    expect(screen.getAllByRole('button', { name: 'Huỷ' })).toHaveLength(2)
    for (const button of screen.getAllByRole('button', { name: 'Huỷ' }))
      expect(button).toBeDisabled()
    fireEvent.submit(screen.getByLabelText(/File CSV/).closest('form')!)
    fireEvent.click(screen.getByRole('button', { name: 'Mở báo cáo lần nhập #42' }))
    fireEvent.click(screen.getByRole('link', { name: 'Xem tất cả 1 lỗi' }))
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeVisible()
    expect(screen.queryByTestId('location')).not.toBeInTheDocument()
    expect(uploadImportFile).toHaveBeenCalledTimes(1)
    expect(confirmImportRun).toHaveBeenCalledTimes(1)
    await act(async () => pending.resolve(completedRun))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/42')
  })

  it('FR040_pagination: đổi trang lịch sử với 10 lần nhập, giữ trạng thái chờ rồi mở báo cáo', async () => {
    const secondPage = deferred<ImportRunPage>()
    vi.mocked(getImportRuns)
      .mockResolvedValueOnce({ items: [completedRun], page: 0, size: 10, totalElements: 11 })
      .mockReturnValueOnce(secondPage.promise)
    renderPage()
    const pagination = await screen.findByRole('navigation', { name: 'Phân trang lịch sử nhập' })
    expect(within(pagination).getByText('Trang 1 / 2')).toBeVisible()
    await userEvent.click(within(pagination).getByRole('button', { name: 'Sau' }))
    expect(getImportRuns).toHaveBeenNthCalledWith(2, 1, expect.any(AbortSignal))
    expect(
      screen.queryByRole('table', { name: 'Lịch sử các lần nhập dữ liệu' }),
    ).not.toBeInTheDocument()
    expect(await screen.findByRole('status', { name: 'Đang tải' })).toBeVisible()
    await act(async () =>
      secondPage.resolve({
        items: [{ ...completedRun, runId: 43 }],
        page: 1,
        size: 10,
        totalElements: 11,
      }),
    )
    expect(screen.getByText('Trang 2 / 2')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Sau' })).toBeDisabled()
    await userEvent.click(screen.getByRole('button', { name: 'Mở báo cáo lần nhập #43' }))
    expect(await screen.findByTestId('location')).toHaveTextContent('/content/imports/43')
  })

  it('FR040_historyRetry: lỗi lịch sử hiện requestId, thử lại độc lập với phần upload', async () => {
    vi.mocked(getImportRuns)
      .mockRejectedValueOnce(
        new ApiError({
          errorCode: 'INTERNAL_ERROR',
          message: 'Chưa tải được lịch sử.',
          requestId: 'history-42',
          status: 500,
        }),
      )
      .mockResolvedValueOnce({ items: [], page: 0, size: 10, totalElements: 0 })
    renderPage()
    expect(await screen.findByText('Chưa tải được lịch sử.')).toBeVisible()
    expect(screen.getByText('Mã lỗi: history-42')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Tải lên và kiểm tra' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    expect(await screen.findByText('Chưa có lần nhập dữ liệu')).toBeVisible()
    expect(getImportTemplates).toHaveBeenCalledTimes(1)
  })

  it('FR036_templateRetry: lỗi tải mẫu có thể thử lại, lịch sử vẫn hiển thị', async () => {
    vi.mocked(getImportTemplates)
      .mockRejectedValueOnce(
        new ApiError({ errorCode: 'INTERNAL_ERROR', message: 'Chưa tải được mẫu.', status: 500 }),
      )
      .mockResolvedValueOnce(templates)
    renderPage()
    expect(await screen.findByText('Chưa tải được mẫu.')).toBeVisible()
    expect(screen.getByText('Chưa có lần nhập dữ liệu')).toBeVisible()
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await waitFor(() => expect(screen.getByLabelText(/Loại dữ liệu/)).toBeEnabled())
    expect(getImportRuns).toHaveBeenCalledTimes(1)
  })
})
