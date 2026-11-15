import { describe, expect, it } from 'vitest'
import { ApiError } from '@/shared/api'

import { getTopicStepBlockedReason, topicLockedBy, type TopicStep } from './topicStudyFlow'
import type { TopicDetail } from './types'

const topic: TopicDetail = {
  id: 1,
  code: 'SELF_AUTHORED',
  name: 'Chủ đề tự soạn',
  hskLevel: 1,
  status: 'IN_PROGRESS',
  lockedBy: [],
  learnedWords: 1,
  totalWords: 4,
  percent: 25,
  bestTestPercent: null,
  words: [],
  actions: {
    canStudy: true,
    canPracticeRecognition: true,
    canPracticeListening: true,
    canTakeTest: true,
  },
}

describe('topic prerequisite gate', () => {
  it.each<TopicStep>(['study', 'recognition', 'listening', 'test'])(
    'gives prerequisites priority over the old %s reason',
    (step) => {
      expect(
        getTopicStepBlockedReason(
          { ...topic, status: 'LOCKED', lockedBy: [{ id: 2, name: 'Chào hỏi' }] },
          step,
        ),
      ).toBe('Cần đạt 90% bài kiểm tra: Chào hỏi')
    },
  )
  it('reads lockedBy from error details and ignores malformed entries', () => {
    expect(
      topicLockedBy(
        new ApiError({
          status: 422,
          errorCode: 'TOPIC_LOCKED',
          message: 'Khoá',
          details: { lockedBy: [{ id: 2, name: 'Chào hỏi' }, null, { id: -1, name: 'Sai' }] },
        }),
      ),
    ).toEqual([{ id: 2, name: 'Chào hỏi' }])
    expect(
      topicLockedBy(new ApiError({ status: 500, errorCode: 'TOPIC_LOCKED', message: 'Lỗi' })),
    ).toBeUndefined()
    expect(topicLockedBy(null)).toBeUndefined()
  })
})
