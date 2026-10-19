import type { ApiError } from '@/shared/api'

import { formatTopicDateTime } from './formatters'
import type { TopicDetail } from './types'

export type TopicStep = 'study' | 'recognition' | 'listening' | 'test'

export const TOPIC_STEP_PATHS: Record<TopicStep, string> = {
  study: 'study',
  recognition: 'practice/recognition',
  listening: 'practice/listening',
  test: 'test',
}

export function parseTopicStep(splat: string | undefined): TopicStep | undefined {
  return (Object.entries(TOPIC_STEP_PATHS).find(([, path]) => path === splat)?.[0] ?? undefined) as
    TopicStep | undefined
}

export function topicStepPath(topicId: number, step: TopicStep) {
  return `/learn/topics/${topicId}/${TOPIC_STEP_PATHS[step]}`
}

export function isTopicNotFound(error: ApiError | undefined) {
  return error?.status === 404 && error.errorCode === 'TOPIC_NOT_FOUND'
}

export function getTopicStepBlockedReason(topic: TopicDetail, step: TopicStep) {
  if (step === 'study')
    return topic.actions.canStudy ? undefined : 'Bạn đã học hết từ mới của chủ đề này.'
  if (step === 'recognition') {
    return topic.actions.canPracticeRecognition
      ? undefined
      : 'Hãy học từ mới trước khi luyện nhận diện.'
  }
  if (step === 'listening') {
    return topic.actions.canPracticeListening ? undefined : 'Chủ đề chưa có âm thanh.'
  }
  if (topic.actions.canTakeTest) return undefined

  if (topic.actions.testBlockedReason === 'NOT_ENOUGH_LEARNED') {
    const remaining = Math.max(
      (topic.actions.requiredWords ?? topic.learnedWords) - topic.learnedWords,
      0,
    )
    return `Học thêm ${remaining} từ để mở bài kiểm tra.`
  }
  if (topic.actions.testBlockedReason === 'TOO_FEW_WORDS') {
    return 'Chủ đề cần ít nhất 4 từ để mở bài kiểm tra.'
  }
  if (topic.actions.testBlockedReason === 'RETRY_TOO_SOON' && topic.actions.retryAvailableAt) {
    return `Bạn có thể kiểm tra lại từ ${formatTopicDateTime(topic.actions.retryAvailableAt)}.`
  }
  return 'Bài kiểm tra hiện chưa khả dụng.'
}

export function practiceErrorMessage(error: ApiError) {
  if (error.errorCode === 'SESSION_EXPIRED') {
    return 'Lượt học đã hết hạn. Hãy bắt đầu lượt mới.'
  }
  if (error.errorCode === 'SESSION_NOT_FOUND' || error.errorCode === 'ATTEMPT_NOT_OWNED') {
    return 'Không thể tiếp tục lượt học này. Hãy bắt đầu lại.'
  }
  if (error.status === 429 && error.errorCode === 'RATE_LIMITED') {
    const seconds = error.extra.retryAfterSeconds
    return typeof seconds === 'number' && Number.isFinite(seconds)
      ? `Bạn thao tác quá nhanh. Vui lòng thử lại sau ${seconds} giây.`
      : 'Bạn thao tác quá nhanh. Vui lòng chờ rồi thử lại.'
  }
  return error.message
}
