import { createElement, useState } from 'react'

import type { PreviewEntry, PreviewState } from '@/app/preview/types'
import { ApiError } from '@/shared/api'

import { QuestionBankListView } from './components/QuestionBankListView'
import { QuestionEditView } from './components/QuestionEditView'
import { questionSearchError, type QuestionListQuery } from './questionListQuery'

import type {
  KnowledgeNode,
  KnowledgeTree,
  QuestionDetail,
  QuestionLabel,
  QuestionPage,
  QuestionType,
  QuestionUpdate,
} from './types'

// Original fixtures; KIDs and questions do not reproduce the source tree or HSK exams.
const nodeRows = [
  ['NA1-901', 'NA', 1, 'Nhận diện âm đơn mẫu', '辨音示例', 'LISTENING'],
  ['CH1-901', 'CH', 1, 'Nhận diện chữ quen thuộc mẫu', '认字示例', 'CHARACTER'],
  ['TV1-901', 'TV', 1, 'Từ chỉ đồ vật mẫu', '物品词示例', 'VOCAB'],
  ['TV2-901', 'TV', 2, 'Từ chỉ hoạt động mẫu', '动作词示例', 'VOCAB'],
  ['NP1-901', 'NP', 1, 'Câu giới thiệu mẫu', '介绍句示例', 'GRAMMAR'],
  ['NP2-901', 'NP', 2, 'Câu chỉ vị trí mẫu', '位置句示例', 'GRAMMAR'],
  ['NP3-901', 'NP', 3, 'Sắp xếp câu ngắn mẫu', '组句示例', 'GRAMMAR'],
  ['NL1-901', 'NL', 1, 'Đọc câu ngắn mẫu', '读句示例', 'READING'],
  ['VB1-901', 'VB', 1, 'Nhận biết ý đoạn ngắn mẫu', '短文示例', null],
] as const

export const knowledgeTreeSample: KnowledgeTree = {
  nodes: nodeRows.map(([kid, domain, hskLevel, nameVi, nameZh, skillType]): KnowledgeNode => ({
    kid,
    domain,
    hskLevel,
    nameVi,
    nameZh,
    canDo: `Thực hành nội dung tự soạn: ${nameVi.toLowerCase()}.`,
    skillType,
    prereqKids: kid === 'NP2-901' ? ['NP1-901'] : kid === 'NP3-901' ? ['NP2-901'] : [],
    supportKids: kid === 'NP2-901' ? ['TV2-901'] : [],
  })),
}

function label(kid: string, role: QuestionLabel['role'] = 'PRIMARY'): QuestionLabel {
  const node = knowledgeTreeSample.nodes.find((entry) => entry.kid === kid)!
  return { kid, nameVi: node.nameVi, role }
}

function question(
  id: number,
  qtype: QuestionType,
  fields: Pick<QuestionDetail, 'stem' | 'options' | 'explanation'> & Partial<QuestionDetail>,
): QuestionDetail {
  return {
    id,
    code: `DEMO_Q_${id}`,
    qtype,
    hskLevel: 1,
    difficulty: 2,
    source: 'IMPORT',
    status: 'APPROVED',
    correctKey: null,
    audioUrl: null,
    imageUrl: null,
    reviewReason: null,
    labels: [label('TV1-901')],
    used: false,
    ...fields,
  }
}

export const questionSamples: QuestionDetail[] = [
  question(101, 'MULTIPLE_CHOICE', {
    stem: 'Chọn nghĩa của 水 trong câu 我喝水。',
    options: [
      { key: 'A', text: 'nước', isCorrect: true },
      { key: 'B', text: 'sách', isCorrect: false, errorKid: 'TV2-901', errorNote: 'Nhầm đồ vật.' },
    ],
    explanation: '水 nghĩa là nước; 我喝水 nghĩa là tôi uống nước.',
    labels: [label('TV1-901'), label('TV2-901', 'SECONDARY')],
    used: true,
  }),
  question(102, 'MULTIPLE_CHOICE', {
    stem: 'Chọn từ chỉ ngày mai.',
    options: [
      { key: 'A', text: '今天', isCorrect: false },
      { key: 'B', text: '明天', isCorrect: true },
      { key: 'C', text: '昨天', isCorrect: false },
    ],
    explanation: '明天 nghĩa là ngày mai.',
    hskLevel: 2,
    code: null,
    source: 'AI',
    status: 'PENDING_REVIEW',
  }),
  question(103, 'TRUE_FALSE', {
    stem: 'Câu 她是老师。 có nghĩa là cô ấy là giáo viên.',
    options: [
      { key: 'A', text: 'Đúng', isCorrect: true },
      { key: 'B', text: 'Sai', isCorrect: false },
    ],
    explanation: '老师 là giáo viên; câu dùng 是 để giới thiệu.',
    labels: [label('NP1-901')],
  }),
  question(104, 'TRUE_FALSE', {
    stem: 'Câu 书在桌子上。 có nghĩa là sách ở dưới bàn.',
    options: [
      { key: 'A', text: 'Đúng', isCorrect: false },
      { key: 'B', text: 'Sai', isCorrect: true },
    ],
    explanation: '上 chỉ phía trên, vì vậy sách ở trên bàn.',
    hskLevel: 2,
    labels: [label('NP2-901')],
    status: 'ARCHIVED',
    used: true,
  }),
  question(105, 'IMAGE_MATCH', {
    stem: 'Hình minh hoạ mẫu là một quyển sách. Chọn từ phù hợp.',
    imageUrl: 'https://assets.example.test/question-bank/book.svg',
    options: [
      { key: 'A', text: '书', isCorrect: true },
      { key: 'B', text: '水', isCorrect: false },
    ],
    explanation: '书 là sách.',
    labels: [label('CH1-901')],
  }),
  question(106, 'IMAGE_MATCH', {
    stem: 'Hình minh hoạ mẫu là một ngọn núi. Chọn từ phù hợp.',
    imageUrl: 'https://assets.example.test/question-bank/mountain.svg',
    options: [
      { key: 'A', text: '人', isCorrect: false },
      { key: 'B', text: '山', isCorrect: true },
    ],
    explanation: '山 là núi.',
    hskLevel: 2,
    source: 'MANUAL',
  }),
  question(107, 'SENTENCE_MATCH', {
    stem: 'Chọn nghĩa phù hợp với 今天我看书。',
    options: [
      { key: 'A', text: 'Hôm nay tôi đọc sách.', isCorrect: true },
      { key: 'B', text: 'Ngày mai tôi uống nước.', isCorrect: false },
    ],
    explanation: '今天 là hôm nay; 看书 là đọc sách.',
    labels: [label('NL1-901')],
  }),
  question(108, 'SENTENCE_MATCH', {
    stem: 'Chọn câu phù hợp với “Bạn tôi ở trường”.',
    options: [
      { key: 'A', text: '我的朋友在学校。', isCorrect: true },
      { key: 'B', text: '我明天去学校。', isCorrect: false },
    ],
    explanation: '我的朋友 là bạn tôi; 在学校 là ở trường.',
    hskLevel: 2,
    labels: [label('NP2-901'), label('NL1-901', 'SECONDARY')],
    status: 'REJECTED',
    code: null,
    source: 'AI',
    reviewReason: 'Ví dụ mẫu cần thêm ngữ cảnh.',
  }),
  question(109, 'FILL_BLANK', {
    stem: 'Điền từ “nước”: 我喝___。',
    options: [],
    correctKey: '水',
    explanation: 'Điền 水 để tạo câu 我喝水。',
  }),
  question(110, 'FILL_BLANK', {
    stem: 'Điền từ chỉ vị trí “ở”: 他___学校学习。',
    options: [],
    correctKey: '在',
    explanation: '在 đứng trước địa điểm 学校.',
    hskLevel: 3,
    labels: [label('NP2-901')],
  }),
  question(111, 'SENTENCE_ORDER', {
    stem: 'Sắp xếp các mảnh thành câu “Hôm nay tôi đọc sách”.',
    options: [
      { key: 'A', text: '我', isCorrect: false },
      { key: 'B', text: '今天', isCorrect: false },
      { key: 'C', text: '看书', isCorrect: false },
    ],
    correctKey: 'BAC',
    explanation: 'Thứ tự mẫu là 今天我看书。',
    hskLevel: 3,
    labels: [label('NP3-901')],
    used: true,
  }),
  question(112, 'SENTENCE_ORDER', {
    stem: 'Sắp xếp các mảnh thành câu “Anh ấy học ở trường”.',
    options: [
      { key: 'A', text: '他', isCorrect: false },
      { key: 'B', text: '在学校', isCorrect: false },
      { key: 'C', text: '学习', isCorrect: false },
    ],
    correctKey: 'ABC',
    explanation: 'Thứ tự mẫu là 他在学校学习。',
    hskLevel: 3,
    labels: [label('NP3-901'), label('TV2-901', 'SECONDARY')],
  }),
]

function sampleSummary(questionId: number) {
  const { id, code, qtype, hskLevel, difficulty, stem, source, status, labels, used } =
    questionSamples.find((entry) => entry.id === questionId)!
  return {
    id,
    code,
    qtype,
    hskLevel,
    difficulty,
    stemSummary: stem.slice(0, 120),
    source,
    status,
    primaryKid: labels.find(({ role }) => role === 'PRIMARY')?.kid ?? null,
    secondaryKids: labels.filter(({ role }) => role === 'SECONDARY').map(({ kid }) => kid),
    used,
  }
}

// Fixed API response examples; the page and view preserve the response order and rows.
export const questionPageSample: QuestionPage = {
  items: [112, 111, 110, 109, 108, 107, 106, 105, 104, 103].map(sampleSummary),
  page: 0,
  size: 10,
  totalElements: questionSamples.length,
}

const questionSecondPageSample: QuestionPage = {
  items: [102, 101].map(sampleSummary),
  page: 1,
  size: 10,
  totalElements: questionSamples.length,
}

function QuestionBankPreview({
  state,
  secondPage = false,
}: {
  state: PreviewState
  secondPage?: boolean
}) {
  const [currentState, setCurrentState] = useState(state)
  const [query, setQuery] = useState<QuestionListQuery>({
    hskLevel: '',
    qtype: '',
    status: '',
    kid: '',
    q: '',
    page: secondPage ? 1 : 0,
  })
  const [searchDraft, setSearchDraft] = useState('')
  const [searchError, setSearchError] = useState<string>()
  const data =
    currentState === 'empty'
      ? { items: [], page: query.page, size: 10, totalElements: 0 }
      : query.page === 1
        ? questionSecondPageSample
        : questionPageSample
  return createElement(QuestionBankListView, {
    data,
    error: new ApiError({
      status: 503,
      errorCode: 'NETWORK_ERROR',
      message: 'Chưa tải được kho câu hỏi. Vui lòng thử lại.',
    }),
    nodes: knowledgeTreeSample.nodes,
    nodesState: 'ready',
    onClearFilters: () => {
      setQuery({ hskLevel: '', qtype: '', status: '', kid: '', q: '', page: 0 })
      setSearchDraft('')
      setSearchError(undefined)
      setCurrentState('ready')
    },
    onFiltersChange: (next) => setQuery({ ...next, page: 0 }),
    onNodesRetry: () => undefined,
    onOpenQuestion: () => undefined,
    onPageChange: (page) => setQuery({ ...query, page }),
    onRetry: () => setCurrentState('ready'),
    onSearchChange: (value) => {
      setSearchDraft(value)
      setSearchError(undefined)
    },
    onSearchSubmit: () => {
      const error = questionSearchError(searchDraft)
      setSearchError(error)
      if (!error) setQuery({ ...query, q: searchDraft.trim(), page: 0 })
    },
    query,
    searchDraft,
    searchError,
    state: currentState,
  })
}

function QuestionEditPreview({
  state,
  sample,
  editor = false,
}: {
  state: PreviewState
  sample: QuestionDetail
  editor?: boolean
}) {
  const [currentState, setCurrentState] = useState(state)
  const [data, setData] = useState(sample)
  const [action, setAction] = useState<'save' | 'archive'>()
  const [body, setBody] = useState<QuestionUpdate>()
  const [notice, setNotice] = useState<string>()
  return createElement(QuestionEditView, {
    action,
    backTo: '/content/questions?page=0&size=10',
    canArchive: editor && data.status !== 'ARCHIVED',
    canEdit: !editor && data.status !== 'ARCHIVED' && data.qtype !== 'ESSAY',
    data,
    error: new ApiError({
      status: 503,
      errorCode: 'NETWORK_ERROR',
      message: 'Chưa tải được câu hỏi. Vui lòng thử lại.',
    }),
    nodes: knowledgeTreeSample.nodes,
    nodesState: 'ready',
    notice,
    onArchive: () => {
      setNotice(undefined)
      setAction('archive')
    },
    onCancelAction: () => setAction(undefined),
    onConfirmAction: () => {
      if (action === 'archive') {
        setData({ ...data, status: 'ARCHIVED' })
        setNotice('Đã lưu trữ câu hỏi.')
      } else if (body) {
        setData({
          ...data,
          ...body,
          labels: [
            label(body.primaryKid),
            ...body.secondaryKids.map((kid) => label(kid, 'SECONDARY')),
          ],
        })
        setNotice('Đã lưu thay đổi.')
      }
      setAction(undefined)
    },
    onDismissError: () => undefined,
    onNodesRetry: () => undefined,
    onRetry: () => setCurrentState('ready'),
    onSave: (next) => {
      setBody(next)
      setNotice(undefined)
      setAction('save')
    },
    pending: false,
    state: currentState,
  })
}

export const previews = [
  {
    group: 'screen',
    id: 'question-bank-samples',
    scr: 'SCR-064',
    title: 'Kho câu hỏi — Dữ liệu mẫu',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) => createElement(QuestionBankPreview, { key: state, state }),
  },
  {
    group: 'screen',
    id: 'question-bank-page-two',
    scr: 'SCR-064',
    title: 'Kho câu hỏi — Trang 2',
    states: ['ready'],
    render: (state) => createElement(QuestionBankPreview, { state, secondPage: true }),
  },
  {
    group: 'screen',
    id: 'question-bank-detail-samples',
    scr: 'SCR-059',
    title: 'Chi tiết câu hỏi — Các loại câu mẫu',
    states: ['loading', 'empty', 'error', 'ready'],
    render: (state) =>
      createElement(QuestionEditPreview, { key: state, state, sample: questionSamples[0]! }),
  },
  ...questionSamples
    .filter((_, index) => index % 2 === 1)
    .map((sample): PreviewEntry => ({
      group: 'screen',
      id: `question-bank-detail-${sample.qtype.toLowerCase()}`,
      scr: 'SCR-059',
      title: `Chi tiết câu hỏi — ${sample.qtype}`,
      states: ['ready'],
      render: (state) => createElement(QuestionEditPreview, { state, sample }),
    })),
  {
    group: 'screen',
    id: 'question-bank-detail-editor',
    scr: 'SCR-059',
    title: 'Chi tiết câu hỏi — Biên tập viên xem và lưu trữ',
    states: ['ready'],
    render: (state) =>
      createElement(QuestionEditPreview, { state, sample: questionSamples[0]!, editor: true }),
  },
  {
    group: 'screen',
    id: 'question-bank-detail-essay',
    scr: 'SCR-059',
    title: 'Chi tiết câu hỏi — Tự luận chỉ đọc',
    states: ['ready'],
    render: (state) =>
      createElement(QuestionEditPreview, {
        state,
        sample: {
          ...questionSamples[0]!,
          qtype: 'ESSAY',
          options: [],
          used: false,
          stem: 'Viết một câu giới thiệu bản thân bằng tiếng Trung.',
          explanation: 'Câu tự luận tự soạn chỉ dùng minh hoạ chế độ chỉ đọc.',
        },
      }),
  },
] satisfies readonly PreviewEntry[]
