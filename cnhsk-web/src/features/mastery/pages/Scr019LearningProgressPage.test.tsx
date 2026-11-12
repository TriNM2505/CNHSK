import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getKnowledgeTree } from '@/features/question-bank/api/knowledge'
import type { KnowledgeNode, KnowledgeTree } from '@/features/question-bank/types'

import { getMyMastery, getMyMasteryNode } from '../api/mastery'
import type { MasteryNode, MasteryNodeDetail, MasteryTree } from '../types'
import Scr019LearningProgressPage from './Scr019LearningProgressPage'

vi.mock('@/features/question-bank/api/knowledge', () => ({ getKnowledgeTree: vi.fn() }))
vi.mock('../api/mastery', () => ({ getMyMastery: vi.fn(), getMyMasteryNode: vi.fn() }))

const getKnowledgeTreeMock = vi.mocked(getKnowledgeTree)
const getMyMasteryMock = vi.mocked(getMyMastery)
const getMyMasteryNodeMock = vi.mocked(getMyMasteryNode)

const testKnowledgeNodes: KnowledgeNode[] = [
  ['NA', 1],
  ['CH', 1],
  ['TV', 2],
  ['NP', 2],
  ['NL', 3],
  ['VB', 3],
].map(([domain, hskLevel]) => ({
  kid: `${domain}${hskLevel}-101`,
  domain: domain as KnowledgeNode['domain'],
  hskLevel: hskLevel as number,
  nameVi: `Nút ${domain}`,
  nameZh: `练习${domain}`,
  prereqKids: domain === 'CH' ? ['NA1-101'] : [],
  supportKids: [],
}))

const testKnowledgeTree: KnowledgeTree = { nodes: testKnowledgeNodes }

const testMasteryNodes: MasteryNode[] = [
  ['NA', 'notyet', null, 0, false],
  ['CH', 'insufficient', 100, 1, false],
  ['TV', 'review', 34.1, 3, true],
  ['NP', 'consolidate', 67.4, 3, true],
  ['NL', 'extend', 100, 3, false],
  ['VB', 'inferred', null, 0, false],
].map(([domain, state, value, evidenceCount, frontier]) => {
  const knowledgeNode = testKnowledgeNodes.find((node) => node.domain === domain)!
  return {
    kid: knowledgeNode.kid,
    state: state as MasteryNode['state'],
    value: value as number | null,
    confidence: state === 'notyet' || state === 'inferred' ? 0 : 1.25,
    evidenceCount: evidenceCount as number,
    frontier: frontier as boolean,
    dueAt: null,
  }
})

const testDetail: MasteryNodeDetail = {
  ...testMasteryNodes[2]!,
  prereqs: [{ kid: testKnowledgeNodes[0]!.kid, nameVi: testKnowledgeNodes[0]!.nameVi, state: 'notyet' }],
  evidence: [
    {
      answeredAt: '2026-11-09T10:00:00Z',
      source: 'TOPIC_TEST',
      context: 'QUIZ',
      form: 'MCQ4',
      role: 'PRIMARY',
      score: 100,
      weight: 0.6,
    },
    {
      answeredAt: '2026-11-08T10:00:00Z',
      source: 'MOCK_EXAM',
      context: 'EXAM',
      form: 'OPEN',
      role: 'PRIMARY',
      score: 0,
      weight: 0.5,
    },
    {
      answeredAt: '2026-11-07T10:00:00Z',
      source: 'GRADED_PRACTICE',
      context: 'GRADED',
      form: 'MCQ3',
      role: 'SECONDARY',
      score: 100,
      weight: 0.6,
    },
    {
      answeredAt: '2026-11-06T10:00:00Z',
      source: 'PRACTICE',
      context: 'PRACTICE',
      form: 'BINARY',
      role: 'ERROR',
      score: 0,
      weight: 0.25,
    },
  ],
  approvedQuestionCount: 2,
}

const newLearnerMastery: MasteryTree = {
  nodes: testMasteryNodes.map((node) => ({
    ...node,
    state: 'notyet',
    value: null,
    confidence: 0,
    evidenceCount: 0,
  })),
}

function renderPage() {
  return render(
    <MemoryRouter>
      <Scr019LearningProgressPage />
    </MemoryRouter>,
  )
}

function setSuccessfulResponses(
  tree: KnowledgeTree = testKnowledgeTree,
  mastery: MasteryTree = { nodes: testMasteryNodes },
) {
  getKnowledgeTreeMock.mockResolvedValue(tree)
  getMyMasteryMock.mockResolvedValue(mastery)
}

beforeEach(() => {
  vi.clearAllMocks()
  getMyMasteryNodeMock.mockResolvedValue(testDetail)
})

describe('SCR-019 Learning Progress (FR-024, AT-24)', () => {
  it('renders six state colors with text labels, frontier border, domain codes, and HSK 1–3 only', async () => {
    const levelFour: KnowledgeTree = {
      nodes: [
        ...testKnowledgeNodes,
        { ...testKnowledgeNodes[0]!, kid: 'NA4-999', hskLevel: 4, nameVi: 'Không hiển thị' },
      ],
    }
    setSuccessfulResponses(levelFour)

    const { container } = renderPage()
    expect(await screen.findByText('Vững ≥ 80')).toBeVisible()

    const stateLabels = [
      ['notyet', 'Chưa học', 'bg-canvas'],
      ['insufficient', 'Chưa đủ minh chứng', 'bg-danger-bg'],
      ['review', 'Cần ôn', 'bg-danger-bg'],
      ['consolidate', 'Củng cố', 'bg-warning-bg'],
      ['extend', 'Vững', 'bg-success-bg'],
      ['inferred', 'Suy ra vững', 'bg-brand-50'],
    ] as const
    for (const [state, label, colorClass] of stateLabels) {
      const nodeButton = container.querySelector<HTMLButtonElement>(`button[data-state="${state}"]`)
      expect(nodeButton).not.toBeNull()
      expect(nodeButton).toHaveTextContent(label)
      expect(nodeButton).toHaveClass(colorClass)
    }
    for (const domain of ['NA', 'CH', 'TV', 'NP', 'NL', 'VB']) {
      expect(screen.getByRole('columnheader', { name: domain })).toBeVisible()
    }
    expect(screen.getByText('Vững ≥ 80')).toBeVisible()
    expect(screen.queryByText('Không hiển thị')).not.toBeInTheDocument()

    const frontier = container.querySelector<HTMLButtonElement>('[data-frontier="true"]')
    expect(frontier).not.toBeNull()
    expect(frontier).toHaveClass('border-2', 'border-brand-600')
    expect(frontier).toHaveClass('focus-visible:ring-2', 'focus-visible:ring-focus')
    expect(frontier).toHaveAttribute('data-frontier', 'true')
    expect(getKnowledgeTreeMock).toHaveBeenCalledTimes(1)
    expect(getKnowledgeTreeMock).toHaveBeenCalledWith(undefined, expect.any(AbortSignal))
  })

  it('opens detail on click and preserves API evidence order and the targeted practice link', async () => {
    const detail = testDetail
    const knowledgeNode = testKnowledgeNodes.find(({ kid }) => kid === detail.kid)!
    getMyMasteryNodeMock.mockResolvedValue(detail)
    setSuccessfulResponses()

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: new RegExp(knowledgeNode.kid) }))

    expect(await screen.findByTestId('mastery-detail')).toBeVisible()
    const detailPanel = screen.getByTestId('mastery-detail')
    expect(detailPanel).toHaveTextContent(detail.value === null ? '—' : `${detail.value}/100`)
    expect(
      within(detailPanel).getByText(
        new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 3 }).format(detail.confidence),
      ),
    ).toBeVisible()
    expect(detailPanel).toHaveTextContent(String(detail.evidenceCount))
    expect(within(detailPanel).getByText(detail.prereqs[0]!.nameVi)).toBeVisible()
    expect(screen.getByRole('link', { name: 'Luyện nút này' })).toHaveAttribute(
      'href',
      `/learn/practice/targeted?kid=${encodeURIComponent(detail.kid)}`,
    )

    const evidenceRows = within(detailPanel)
      .getByRole('list', { name: 'Danh sách minh chứng' })
      .querySelectorAll('li')
    const labels = [
      'TOPIC_TEST · Bài kiểm tra · Trắc nghiệm 4 lựa chọn · Chính',
      'MOCK_EXAM · Bài thi · Trả lời mở · Chính',
      'GRADED_PRACTICE · Bài có chấm điểm · Trắc nghiệm 3 lựa chọn · Phụ',
      'PRACTICE · Luyện tập · Đúng/Sai · Lỗi thường gặp',
    ]
    detail.evidence.forEach((item, index) => {
      expect(evidenceRows[index]?.textContent).toContain(
        labels[index],
      )
      expect(evidenceRows[index]?.querySelector('time')).toHaveAttribute(
        'dateTime',
        item.answeredAt,
      )
    })
    expect(evidenceRows[0]?.textContent).toContain('17:00 9 thg 11, 2026')
    expect(evidenceRows[1]?.textContent).toContain('17:00 8 thg 11, 2026')
  })

  it('shows the practice action disabled with an explanation when no approved questions exist', async () => {
    const detail: MasteryNodeDetail = { ...testDetail, approvedQuestionCount: 0 }
    getMyMasteryNodeMock.mockResolvedValue(detail)
    setSuccessfulResponses()

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /TV2-101/ }))

    expect(await screen.findByRole('button', { name: 'Luyện nút này' })).toBeDisabled()
    expect(screen.getByText('Chưa có câu luyện cho nút này')).toBeVisible()
  })

  it('shows the exact new learner notice while retaining the gray grid and exam link', async () => {
    setSuccessfulResponses(testKnowledgeTree, newLearnerMastery)

    renderPage()

    expect(
      await screen.findByText(
        'Bạn chưa có minh chứng — hãy làm bài kiểm tra trình độ hoặc luyện một chủ đề',
      ),
    ).toBeVisible()
    expect(screen.getByRole('link', { name: 'Làm bài kiểm tra trình độ' })).toHaveAttribute(
      'href',
      '/learn/exams',
    )
    expect(screen.getAllByRole('button').some((button) => button.dataset.state === 'notyet')).toBe(
      true,
    )
  })

  it('shows empty state for a tree without HSK 1–3 nodes instead of the new learner notice', async () => {
    setSuccessfulResponses({ nodes: [] }, { nodes: [] })

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Chưa có dữ liệu tiến độ' })).toBeVisible()
    expect(
      screen.queryByText(
        'Bạn chưa có minh chứng — hãy làm bài kiểm tra trình độ hoặc luyện một chủ đề',
      ),
    ).not.toBeInTheDocument()
  })

  it('fills a KID missing from the mastery response as notyet', async () => {
    const masteryWithoutNa: MasteryTree = {
      nodes: testMasteryNodes.filter(({ kid }) => kid !== 'NA1-101'),
    }
    setSuccessfulResponses(testKnowledgeTree, masteryWithoutNa)

    const { container } = renderPage()

    const naNode = await waitFor(() => {
      const node = container.querySelector<HTMLButtonElement>('button[data-kid="NA1-101"]')
      expect(node).not.toBeNull()
      return node!
    })
    expect(naNode).toHaveAttribute('data-state', 'notyet')
    expect(naNode).toHaveTextContent('Chưa học')
  })

  it('shows a loading skeleton while the APIs are pending', async () => {
    getKnowledgeTreeMock.mockReturnValue(new Promise(() => undefined))
    getMyMasteryMock.mockReturnValue(new Promise(() => undefined))

    renderPage()

    expect(await screen.findByRole('status', { name: 'Đang tải' })).toBeVisible()
  })

  it('shows a retry action on API error and reloads both requests when clicked', async () => {
    getKnowledgeTreeMock.mockRejectedValueOnce(new Error('fixture failure')).mockResolvedValue(
      testKnowledgeTree,
    )
    getMyMasteryMock.mockResolvedValue({ nodes: testMasteryNodes })

    renderPage()

    const retry = await screen.findByRole('button', { name: 'Thử lại' })
    fireEvent.click(retry)
    await waitFor(() => expect(getKnowledgeTreeMock).toHaveBeenCalledTimes(2))
    expect(await screen.findByText('Vững ≥ 80')).toBeVisible()
  })

  it('aborts the previous detail request when another KID is selected', async () => {
    const signals: AbortSignal[] = []
    getMyMasteryNodeMock.mockImplementation(
      (kid, signal) =>
        new Promise((resolve) => {
          if (signal) signals.push(signal)
          if (kid === 'NP2-101') {
            resolve({ ...testDetail, kid })
          }
        }),
    )
    setSuccessfulResponses()

    renderPage()
    fireEvent.click(await screen.findByRole('button', { name: /NA1-101/ }))
    await waitFor(() => expect(getMyMasteryNodeMock).toHaveBeenCalledTimes(1))
    fireEvent.click(screen.getByRole('button', { name: /NP2-101/ }))

    await waitFor(() => expect(signals[0]?.aborted).toBe(true))
    expect(await screen.findByTestId('mastery-detail')).toBeVisible()
  })
})
