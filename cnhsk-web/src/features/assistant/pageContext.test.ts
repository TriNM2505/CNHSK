import { describe, expect, it } from 'vitest'

import { assistantPageContext } from './pageContext'

describe('assistant screen context', () => {
  it.each([
    ['/account/credits', 'SCR-030'],
    ['/learn/topics/12/study', 'SCR-012'],
    ['/learn/topics/12/practice/recognition', 'SCR-012'],
    ['/learn/topics/12/practice/listening', 'SCR-012'],
  ])('FR013_reads the registered screen for %s', (pathname, scr) => {
    expect(assistantPageContext({ pathname })).toEqual({ pageContext: scr, hidden: false })
  })

  it.each([
    '/learn/exams/sessions/123',
    '/learn/exams/sessions/123/',
    '/learn/topics/12/test',
    '/learn/topics/12/test/',
    '/community/quizzes/43/play',
    '/community/quizzes/43/play/',
  ])('FR014_hides the assistant at %s', (pathname) => {
    expect(assistantPageContext({ pathname }).hidden).toBe(true)
  })

  it.each([
    ['/community/quizzes', 'SCR-043'],
    ['/community/quizzes/attempts/101', 'SCR-045'],
  ])('keeps the assistant and trusted screen at %s', (pathname, pageContext) => {
    expect(assistantPageContext({ pathname })).toEqual({ pageContext, hidden: false })
  })
  it('FR013_omits context when no registered screen matches', () => {
    expect(assistantPageContext({ pathname: '/not-a-screen' })).toEqual({
      pageContext: undefined,
      hidden: false,
    })
  })
})
