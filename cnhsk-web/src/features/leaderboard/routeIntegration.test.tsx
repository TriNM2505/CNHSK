import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { appRouteDefinitions } from '@/app/routeAccess'
import { screenRegistry } from '@/app/screenRegistry'
import { QuizResultView } from '@/features/quiz/components/QuizResultView'

describe('leaderboard route integration', () => {
  it('registers both public SCR routes and exposes the quiz-result topic link', () => {
    expect(appRouteDefinitions).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          access: 'public',
          layout: 'public',
          path: '/leaderboards/topics',
          scr: 'SCR-046',
        }),
        expect.objectContaining({
          access: 'public',
          layout: 'public',
          path: '/leaderboards/games',
          scr: 'SCR-047',
        }),
      ]),
    )
    expect(screenRegistry['SCR-046']).toBeDefined()
    expect(screenRegistry['SCR-047']).toBeDefined()

    render(
      <MemoryRouter>
        <QuizResultView
          result={{
            sessionId: 1,
            topicId: 43,
            topicName: 'Chào hỏi',
            score: 90,
            correctCount: 9,
            wrongCount: 1,
            durationMs: 60000,
            rankingEligible: true,
            suspicious: false,
            items: [],
            firstAttempt: null,
          }}
          pending={false}
          leaderboardAvailable
          onRetry={() => undefined}
        />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: 'Bảng xếp hạng chủ đề' })).toHaveAttribute(
      'href',
      '/leaderboards/topics?topicId=43',
    )
  })
})
