import { describe, expect, it } from 'vitest'

describe('exam preview catalog', () => {
  it('có đúng ba đề tự soạn cho HSK 1–3', async () => {
    const { examPreviews } = await import('./catalog.preview')

    expect(examPreviews.map(({ hskLevel }) => hskLevel)).toEqual([1, 2, 3])
  })

  it('có lượt placement cho đủ ba khối và không lộ đáp án trước khi nộp', async () => {
    const { placementStepPreviews } = await import('./catalog.preview')

    expect(placementStepPreviews.map(({ session }) => session?.meta?.block)).toEqual([1, 2, 3])
    expect(placementStepPreviews.flatMap(({ session }) => session?.items ?? [])).not.toContainEqual(
      expect.objectContaining({ correctKey: expect.anything() }),
    )
  })

  it('có mẫu kết quả đề, KID và sáu miền placement', async () => {
    const { examResultPreview, placementResultPreview, previews } =
      await import('./catalog.preview')

    expect(examResultPreview.wrongByKid).toEqual([])
    expect(placementResultPreview.domains).toHaveLength(6)
    expect(previews).toEqual([])
  })
})
