import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { PronunciationFinishExtra, PronunciationStageSummary } from '../types'
import { PronunciationPracticeView } from './PronunciationPracticeView'

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

function renderResult(finishExtra: PronunciationFinishExtra) {
  render(
    <MemoryRouter>
      <PronunciationPracticeView
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
  it('hiện tầng vừa mở và nút sang tầng kế', () => {
    renderResult(extra({ nextStageIndex: 4, stageIndex: 3, unlockedNextStage: true }))

    expect(screen.getByText('Đã mở tầng 4')).toBeVisible()
    expect(screen.getByRole('link', { name: /Sang tầng 4/ })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/4',
    )
  })

  it('hiện phần trăm còn thiếu theo định dạng vi-VN', () => {
    renderResult(extra({ accuracy: 72.5, needed: 7.5, passed: false }))

    expect(screen.getByText('Còn 7,5% nữa')).toBeVisible()
  })

  it('hiện hoàn thành toàn bộ lộ trình', () => {
    renderResult(extra({ completedAllStages: true }))

    expect(screen.getByText('Hoàn thành lộ trình phát âm')).toBeVisible()
  })

  it('hiện noScore khi mọi câu đều không nghe được', () => {
    renderResult(extra({ accuracy: null, bestAccuracy: null, noScore: true, passed: false }))

    expect(screen.getByRole('heading', { name: 'Chưa tính điểm lượt này' })).toBeVisible()
    expect(screen.getAllByText('Chưa có')).toHaveLength(2)
  })
})
