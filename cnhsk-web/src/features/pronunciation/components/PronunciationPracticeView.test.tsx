import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'
import {
  PronunciationPracticeView,
  type PronunciationPracticeViewProps,
} from './PronunciationPracticeView'

const stage: PronunciationStageSummary = {
  activeAnswered: null,
  activeSessionId: null,
  bestAccuracy: null,
  description: null,
  id: 8,
  name: 'Biến điệu',
  passPercent: 80,
  playable: true,
  stageIndex: 8,
  state: 'AVAILABLE',
  targetSounds: ['一', '不'],
  unitCount: 10,
}

function extra(overrides: Partial<PronunciationFinishExtra>): PronunciationFinishExtra {
  return {
    accuracy: 90,
    bestAccuracy: 92.5,
    completedAllStages: false,
    needed: 0,
    nextStageIndex: null,
    noScore: false,
    passed: true,
    passPercent: 80,
    stageIndex: 8,
    unlockedNextStage: false,
    ...overrides,
  }
}

function renderResult(
  finishExtra: PronunciationFinishExtra,
  finalAnswer?: PronunciationPracticeViewProps['finalAnswer'],
) {
  render(
    <MemoryRouter>
      <PronunciationPracticeView
        finalAnswer={finalAnswer}
        finishExtra={finishExtra}
        onRestart={vi.fn()}
        onRetry={vi.fn()}
        stage={stage}
        state="finished"
      />
    </MemoryRouter>,
  )
}

describe('PronunciationPracticeView result', () => {
  it('FR019_hiện tầng vừa mở và nút sang tầng kế', () => {
    renderResult(extra({ nextStageIndex: 4, stageIndex: 3, unlockedNextStage: true }))

    expect(screen.getByText('Đã mở tầng 4')).toBeVisible()
    expect(screen.getByRole('link', { name: /Sang tầng 4/ })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/4',
    )
    expect(screen.getByText('Đã mở tầng 4')).toHaveClass('motion-reduce:animate-none')
    expect(screen.getByTestId('pronunciation-result-icon')).toHaveClass(
      'motion-reduce:animate-none',
    )
  })

  it('FR019_hiện phần trăm còn thiếu theo định dạng vi-VN', () => {
    renderResult(extra({ accuracy: 72.5, needed: 7.5, passed: false }))

    expect(screen.getByText('Còn 7,5% nữa')).toBeVisible()
  })

  it('FR019_hiện hoàn thành toàn bộ lộ trình', () => {
    renderResult(extra({ completedAllStages: true }))

    expect(screen.getByText('Hoàn thành lộ trình phát âm')).toBeVisible()
    expect(screen.queryByRole('link', { name: /Sang tầng/ })).not.toBeInTheDocument()
  })

  it('FR019_hiện noScore khi mọi câu đều không nghe được', () => {
    renderResult(extra({ accuracy: null, bestAccuracy: null, noScore: true, passed: false }))

    expect(screen.getByRole('heading', { name: 'Chưa tính điểm lượt này' })).toBeVisible()
    expect(screen.getAllByText('Chưa có')).toHaveLength(2)
  })

  it('FR019_không báo mở tầng mới khi server chỉ trả nextStageIndex', () => {
    renderResult(extra({ nextStageIndex: 2, unlockedNextStage: false }))

    expect(screen.queryByText('Đã mở tầng 2')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Sang tầng 2/ })).toBeVisible()
  })

  it('FR019_lượt trượt vẫn hiển thị needed từ server dù bestAccuracy đã đạt', () => {
    renderResult(
      extra({ accuracy: 70, bestAccuracy: 90, needed: 10, nextStageIndex: 2, passed: false }),
    )

    expect(screen.getByText('70%')).toBeVisible()
    expect(screen.getByText('90%')).toBeVisible()
    expect(screen.getByText('Còn 10% nữa')).toBeVisible()
    expect(screen.queryByRole('link', { name: /Sang tầng/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/Đã mở tầng/)).not.toBeInTheDocument()
  })

  it('FR019_noScore giữ best cũ và không suy ra kết quả từ best', () => {
    renderResult(
      extra({
        accuracy: null,
        bestAccuracy: 92.5,
        needed: null,
        nextStageIndex: 2,
        noScore: true,
        passed: false,
      }),
    )

    expect(screen.getByText('Chưa có')).toBeVisible()
    expect(screen.getByText('92,5%')).toBeVisible()
    expect(screen.queryByText(/Còn .*% nữa/)).not.toBeInTheDocument()
    expect(screen.queryByText(/Đã mở tầng/)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Sang tầng/ })).not.toBeInTheDocument()
  })

  it.each([true, false])('FR018_câu cuối có phản hồi correct=%s và lời giải server', (correct) => {
    renderResult(extra({}), {
      item: {
        no: 10,
        type: 'PRON_CHOICE',
        skill: 'PRONUNCIATION',
        prompt: { text: 'Nghe và chọn âm' },
        options: [
          { key: 'A', text: 'bā' },
          { key: 'B', text: 'pā' },
        ],
        rules: { hasAudio: true, maxReplays: 3, minDurationMs: 500 },
      },
      result: { no: 10, correct, counted: true, correctAnswer: 'B', explanation: 'Âm p bật hơi.' },
    })

    expect(screen.getByRole('heading', { name: 'Câu cuối' })).toBeVisible()
    expect(screen.getByText(correct ? 'Chính xác' : 'Chưa chính xác')).toBeVisible()
    expect(screen.getByText('Âm p bật hơi.')).toBeVisible()
    if (!correct) expect(screen.getByText(/Đáp án đúng: B/)).toBeVisible()
    expect(screen.getByRole('button', { name: /bā/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /pā/ })).toBeDisabled()
  })

  it('FR018_câu cuối không tính điểm không được hiển thị như câu sai', () => {
    renderResult(extra({ noScore: true, accuracy: null, passed: false }), {
      item: { no: 10, type: 'PRON_CHOICE', skill: 'PRONUNCIATION', prompt: {}, rules: {} },
      result: { no: 10, correct: null, counted: false },
    })

    expect(screen.getByText('Câu này không tính điểm.')).toBeVisible()
    expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
  })

  it('FR018_không có kết quả answer thì không suy ra phản hồi câu cuối từ tổng kết', () => {
    renderResult(extra({ accuracy: 100, bestAccuracy: 100 }))

    expect(screen.queryByRole('heading', { name: 'Câu cuối' })).not.toBeInTheDocument()
    expect(screen.queryByText('Chính xác')).not.toBeInTheDocument()
    expect(screen.queryByText('Chưa chính xác')).not.toBeInTheDocument()
  })
})
