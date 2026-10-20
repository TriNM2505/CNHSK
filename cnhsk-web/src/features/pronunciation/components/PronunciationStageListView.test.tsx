import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'

import type { PronunciationStageSummary } from '../types'
import { PronunciationStageListView } from './PronunciationStageListView'

function stage(
  stageIndex: number,
  overrides: Partial<PronunciationStageSummary> = {},
): PronunciationStageSummary {
  return {
    activeAnswered: null,
    activeSessionId: null,
    bestAccuracy: null,
    description: `Mô tả tầng ${stageIndex}`,
    id: 100 + stageIndex,
    name: `Tầng mẫu ${stageIndex}`,
    passPercent: 80,
    playable: true,
    stageIndex,
    state: stageIndex === 1 ? 'AVAILABLE' : 'LOCKED',
    targetSounds: [`âm-${stageIndex}`],
    unitCount: 10,
    ...overrides,
  }
}

function renderView(stages: PronunciationStageSummary[]) {
  render(
    <MemoryRouter>
      <PronunciationStageListView onRetry={vi.fn()} stages={stages} state="ready" />
    </MemoryRouter>,
  )
}

describe('PronunciationStageListView', () => {
  it('hiện đủ ba trạng thái và lý do khoá bằng ngưỡng của tầng trước', () => {
    renderView([
      stage(1, { bestAccuracy: 87.5, passPercent: 82.5, state: 'COMPLETED' }),
      stage(2, { state: 'AVAILABLE' }),
      stage(3, { state: 'LOCKED' }),
    ])

    expect(screen.getByText('Hoàn thành')).toBeVisible()
    expect(screen.getByText('Đang học')).toBeVisible()
    expect(screen.getByText('Khoá')).toBeVisible()
    expect(screen.getByText('Hoàn thành tầng 2 với ≥ 80%')).toBeVisible()
    expect(screen.getByText('87,5%')).toBeVisible()
  })

  it('hiện Tiếp tục (x/y) khi tầng có lượt đang làm', () => {
    renderView([stage(1, { activeAnswered: 4, activeSessionId: 901 })])

    expect(screen.getByRole('link', { name: 'Tiếp tục (4/10)' })).toHaveAttribute(
      'href',
      '/learn/pronunciation/stages/1',
    )
  })

  it('khoá nút và hiện Đang cập nhật khi tầng chưa có bài', () => {
    renderView([stage(1, { playable: false, unitCount: 0 })])

    expect(screen.getByRole('button', { name: 'Đang cập nhật' })).toBeDisabled()
  })
})
