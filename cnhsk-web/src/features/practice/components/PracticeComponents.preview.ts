import type { AnswerResult, FinishResult, ServedItemView } from '@/features/practice/types'

export const pinyinInputPreview = {
  hint: 'lời chào',
  prompt: '你好',
}

export const sessionProgressPreview = [
  { current: 1, total: 10 },
  { current: 7, total: 10 },
  { current: 10, total: 10 },
] as const

export const choiceItemPreview: ServedItemView = {
  no: 3,
  options: [
    { key: 'A', text: 'Chúng tôi cùng nhau học tiếng Trung vào mỗi buổi sáng trước khi đi làm.' },
    { key: 'B', text: 'Họ đang chuẩn bị một bữa tối rất đặc biệt cho cả gia đình.' },
    { key: 'C', text: 'Các bạn muốn đi đến thư viện trung tâm bằng xe buýt.' },
    { key: 'D', text: 'Tôi đã hoàn thành bài tập ngày hôm qua.' },
  ],
  prompt: { hint: 'Chọn nghĩa phù hợp nhất', text: '我们一起学习中文' },
  rules: { hasAudio: false, minDurationMs: 800 },
  skill: 'VOCAB',
  type: 'MEANING_CHOICE',
}

export const clozeItemPreview: ServedItemView = {
  no: 4,
  options: [
    { key: 'A', text: '朋友' },
    { key: 'B', text: '学校' },
    { key: 'C', text: '老师' },
    { key: 'D', text: '中国' },
  ],
  prompt: { hint: 'Chúng tôi là bạn', sentence: '我们是＿＿' },
  rules: { hasAudio: false, minDurationMs: 800 },
  skill: 'READING',
  type: 'CLOZE_CHOICE',
}

export const audioChoiceItemPreview: ServedItemView = {
  no: 5,
  options: [
    { key: 'A', text: '你好' },
    { key: 'B', text: '谢谢' },
    { key: 'C', text: '再见' },
    { key: 'D', text: '学习' },
  ],
  prompt: { audioUrl: '/audio/preview-ni-hao.mp3' },
  rules: { hasAudio: true, maxReplays: 3, minDurationMs: 800 },
  skill: 'LISTENING',
  type: 'AUDIO_WORD_CHOICE',
}

export const studyItemWithAudioPreview: ServedItemView = {
  no: 1,
  prompt: {
    audioUrl: '/audio/preview-xue-xi.mp3',
    examples: [
      {
        cn: '我们每天一起学习中文，也会认真练习听力和汉字书写。',
        pinyin: 'wo3men5 mei3tian1 yi4qi3 xue2xi2 zhong1wen2',
        vi: 'Mỗi ngày chúng tôi cùng học tiếng Trung.',
      },
    ],
    meaning: 'học tập; tiếp thu kiến thức hoặc kỹ năng mới',
    pinyin: 'xue2xi2',
    sinoViet: 'HỌC TẬP',
    text: '学习',
  },
  rules: { hasAudio: true, maxReplays: 3, minDurationMs: 1000 },
  skill: 'VOCAB',
  type: 'STUDY_CARD',
}

export const studyItemWithoutAudioPreview: ServedItemView = {
  ...studyItemWithAudioPreview,
  no: 2,
  prompt: {
    ...studyItemWithAudioPreview.prompt,
    audioUrl: null,
    sinoViet: '',
    text: '汉字',
  },
  rules: { hasAudio: false, minDurationMs: 1000 },
}

export const finishResultPreview: FinishResult = {
  items: [
    { correct: true, correctAnswer: 'A', no: 1, yourAnswer: 'A' },
    { correct: false, correctAnswer: '朋友', no: 2, yourAnswer: '学校' },
    { correct: false, correctAnswer: 'xue2xi2', no: 3, yourAnswer: 'xue2xi1' },
  ],
  maxScore: 3,
  percent: 33,
  score: 1,
  sessionId: 42,
  status: 'SUBMITTED',
  suspicious: false,
}

export function choiceResultPreview(answer: string): AnswerResult {
  return {
    correct: answer === 'A',
    correctAnswer: 'A',
    explanation: '我们 nghĩa là chúng tôi hoặc chúng ta.',
    no: choiceItemPreview.no,
  }
}
