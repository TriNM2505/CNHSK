import { describe, expect, it } from 'vitest'

import { assistantPageContext } from './pageContext'

describe('assistant screen context', () => {
  it.each([
    ['/account/credits', 'SCR-030'],
    ['/learn/topics/12/study', 'SCR-012'],
    ['/learn/topics/12/practice/recognition', 'SCR-012'],
    ['/learn/topics/12/practice/listening', 'SCR-012'],
    ['/contests', 'SCR-048'],
    ['/contests/2101', 'SCR-049'],
    ['/contests/2101/results', 'SCR-051'],
    ['/community/quizzes', 'SCR-043'],
    ['/community/quizzes/attempts/101', 'SCR-045'],
  ])('FR013_reads the registered screen for %s', (pathname, scr) => {
    expect(assistantPageContext({ pathname })).toEqual({ pageContext: scr })
  })

  it.each([
    ['/learn/exams/sessions/123', 'SCR-021'],
    ['/community/quizzes/43/play', 'SCR-044'],
    ['/contests/2101/attempt', 'SCR-050'],
  ])('keeps the assistant on test screens such as %s', (pathname, scr) => {
    expect(assistantPageContext({ pathname })).toEqual({ pageContext: scr })
  })

  it('FR013_omits context when no registered screen matches', () => {
    expect(assistantPageContext({ pathname: '/not-a-screen' })).toEqual({ pageContext: undefined })
  })
})
