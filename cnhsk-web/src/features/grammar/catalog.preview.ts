import { createElement, useState } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { GrammarLessonView, type GrammarLessonViewProps } from './components/GrammarLessonView'
import { grammarLessonPreviews, previewGrammarItem } from './components/GrammarLessonView.preview'
import { GrammarListView, type GrammarListViewProps } from './components/GrammarListView'
import { grammarListPreviews } from './components/GrammarListView.preview'

const learnerAuth: AuthContextValue = {
  error: null,
  reloadCurrentUser: async () => undefined,
  setAuthenticatedUser: () => undefined,
  signOut: async () => undefined,
  status: 'authenticated',
  user: previewLearner,
}

function inLearnerLayout(element: ReturnType<typeof createElement>) {
  return createElement(
    AuthContext.Provider,
    { value: learnerAuth },
    createElement(LearnerLayout, null, element),
  )
}

function GrammarListPreview({ props }: { props: GrammarListViewProps }) {
  const [selectedLevel, setSelectedLevel] = useState(props.selectedLevel)
  return createElement(GrammarListView, {
    ...props,
    onLevelChange: setSelectedLevel,
    selectedLevel,
  })
}

const lessonPreviewQuestions = [
  {
    item: previewGrammarItem,
    correctAnswer: 'A',
    explanation: '是……的 nhấn mạnh cách thức đi lại.',
  },
  {
    item: {
      ...previewGrammarItem,
      no: 3,
      prompt: { sentence: '我是昨天来___。' },
      options: [
        { key: 'A', text: '了' },
        { key: 'B', text: '的' },
        { key: 'C', text: '吗' },
      ],
    },
    correctAnswer: 'B',
    explanation: '的 kết thúc cấu trúc nhấn mạnh thời gian 是……的.',
  },
  {
    item: {
      ...previewGrammarItem,
      no: 4,
      prompt: { sentence: '这本书是___买的。' },
      options: [
        { key: 'A', text: '吗' },
        { key: 'B', text: '呢' },
        { key: 'C', text: '我' },
      ],
    },
    correctAnswer: 'C',
    explanation: '我 cho biết người đã thực hiện hành động mua quyển sách.',
  },
]

function GrammarLessonPreview({ props }: { props: GrammarLessonViewProps }) {
  const [stage, setStage] = useState(props.stage ?? 'reading')
  const [questionIndex, setQuestionIndex] = useState(0)
  const [answers, setAnswers] = useState<Record<number, string>>(
    props.result ? { [previewGrammarItem.no]: 'A' } : {},
  )
  const [finishResult, setFinishResult] = useState(props.finishResult)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const question = lessonPreviewQuestions[questionIndex]!
  const selectedAnswer = answers[question.item.no]
  const result =
    selectedAnswer === undefined
      ? undefined
      : {
          ...grammarLessonPreviews.feedback.result,
          no: question.item.no,
          correct: selectedAnswer === question.correctAnswer,
          correctAnswer: question.correctAnswer,
          explanation: question.explanation,
        }

  const complete = (skipped: boolean) => {
    const recorded = lessonPreviewQuestions.filter(({ item }) => answers[item.no] !== undefined)
    const correct = recorded.filter(
      ({ item, correctAnswer }) => answers[item.no] === correctAnswer,
    ).length
    setFinishResult({
      ...grammarLessonPreviews.finished.finishResult,
      score: correct,
      maxScore: lessonPreviewQuestions.length,
      items: recorded.map(({ item, correctAnswer }) => ({
        no: item.no,
        correct: answers[item.no] === correctAnswer,
      })),
      extra: {
        answered: recorded.length,
        correct,
        exercisesTotal: lessonPreviewQuestions.length,
        percent: Math.floor((correct * 100) / lessonPreviewQuestions.length),
        skippedExercises: skipped,
        firstTimeLearned: true,
      },
    })
    setConfirmOpen(false)
    setStage('finished')
  }

  return createElement(GrammarLessonView, {
    ...props,
    stage,
    current: questionIndex + 1,
    total: lessonPreviewQuestions.length,
    item: question.item,
    result,
    finishResult,
    confirmOpen,
    onBegin: () => {
      setQuestionIndex(0)
      setAnswers({})
      setFinishResult(undefined)
      setStage('exercise')
    },
    onAnswer: async (key: string) =>
      setAnswers((current) => ({ ...current, [question.item.no]: key })),
    onNext: () => {
      if (questionIndex + 1 < lessonPreviewQuestions.length)
        setQuestionIndex((current) => current + 1)
      else complete(false)
    },
    onSkipRequest: () => setConfirmOpen(true),
    onSkipCancel: () => setConfirmOpen(false),
    onSkipConfirm: () => complete(true),
  })
}

const listEntries = [
  ['scr-017-ready', 'Ngữ pháp HSK — Danh sách', grammarListPreviews.ready],
  ['scr-017-loading', 'Ngữ pháp HSK — Đang tải', grammarListPreviews.loading],
  ['scr-017-empty', 'Ngữ pháp HSK — Chưa có dữ liệu', grammarListPreviews.empty],
  ['scr-017-error', 'Ngữ pháp HSK — Lỗi tải', grammarListPreviews.error],
] as const

const lessonEntries = [
  ['scr-018-ready', 'Bài ngữ pháp — Nội dung', grammarLessonPreviews.ready],
  ['scr-018-restored', 'Bài ngữ pháp — Có lượt đang dở', grammarLessonPreviews.restored],
  ['scr-018-exercise', 'Bài ngữ pháp — Câu hỏi', grammarLessonPreviews.exercise],
  ['scr-018-feedback', 'Bài ngữ pháp — Lời giải', grammarLessonPreviews.feedback],
  ['scr-018-finished', 'Bài ngữ pháp — Kết quả', grammarLessonPreviews.finished],
  ['scr-018-loading', 'Bài ngữ pháp — Đang tải', grammarLessonPreviews.loading],
  ['scr-018-empty', 'Bài ngữ pháp — Không tìm thấy', grammarLessonPreviews.empty],
  ['scr-018-error', 'Bài ngữ pháp — Lỗi tải', grammarLessonPreviews.error],
] as const

const grammarListEntries = listEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () => inLearnerLayout(createElement(GrammarListPreview, { key: id, props })),
  scr: 'SCR-017',
  title,
})) satisfies readonly PreviewEntry[]

const grammarLessonEntries = lessonEntries.map(([id, title, props]) => ({
  fullFrame: true,
  group: 'screen' as const,
  id,
  render: () => inLearnerLayout(createElement(GrammarLessonPreview, { key: id, props })),
  scr: 'SCR-018',
  title,
})) satisfies readonly PreviewEntry[]

export const previews = [...grammarListEntries, ...grammarLessonEntries]
