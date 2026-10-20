import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { SessionSummary } from '@/features/practice/components/SessionSummary'
import type { FinishResult } from '@/features/practice/types'
import { Button } from '@/shared/ui'

describe('SessionSummary', () => {
  it('renders server scores, wrong answers and supplied actions', () => {
    const result: FinishResult = {
      items: [
        { correct: true, no: 1 },
        { correct: false, correctAnswer: 'B', no: 2, yourAnswer: 'A' },
      ],
      maxScore: 2,
      score: 1,
      sessionId: 42,
      status: 'SUBMITTED',
      suspicious: false,
    }
    render(<SessionSummary actions={<Button>Học tiếp</Button>} result={result} />)

    expect(screen.getByText('1/2')).toBeInTheDocument()
    expect(screen.getByText('Câu 2')).toBeInTheDocument()
    expect(screen.getByText('Bạn trả lời: A')).toBeInTheDocument()
    expect(screen.getByText('Đáp án đúng: B')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Học tiếp' })).toBeInTheDocument()
  })

  it('states when a RECORD session has no score', () => {
    render(
      <SessionSummary
        actions={null}
        result={{
          items: [{ correct: null, no: 1 }],
          maxScore: null,
          score: null,
          sessionId: 43,
          status: 'SUBMITTED',
          suspicious: false,
        }}
      />,
    )

    expect(screen.getByText('Không chấm điểm')).toBeInTheDocument()
  })
})
