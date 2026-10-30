import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import { AuthContext, type AuthContextValue } from '@/shared/auth'
import { LearnerLayout } from '@/shared/layout'
import { previewLearner } from '@/shared/layout/Layout.preview'

import { CharacterListView } from './components/CharacterListView'
import { CharacterPracticeView } from './components/CharacterPracticeView'
import { RecognitionView } from './components/RecognitionView'
import { recognitionPreviews } from './components/RecognitionView.preview'
import { WritingChallengeView } from './components/WritingChallengeView'
import {
  characterListPreviews,
  characterPracticePreviews,
  writingChallengePreviews,
} from './components/WritingViews.preview'

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

const recognitionEntries = [
  ['scr-015-setup', 'Nhận diện chữ — Chọn chế độ', recognitionPreviews.setup],
  ['scr-015-no-characters', 'Nhận diện chữ — Chưa có chữ', recognitionPreviews.noCharacters],
  ['scr-015-question', 'Nhận diện chữ — Nhìn chữ chọn nghĩa', recognitionPreviews.question],
  ['scr-015-feedback', 'Nhận diện chữ — Phản hồi', recognitionPreviews.feedback],
  ['scr-015-audio', 'Nhận diện chữ — Nghe chọn chữ', recognitionPreviews.audio],
  ['scr-015-finished', 'Nhận diện chữ — Kết quả', recognitionPreviews.finished],
  ['scr-015-loading', 'Nhận diện chữ — Đang tải', recognitionPreviews.loading],
  ['scr-015-error', 'Nhận diện chữ — Lỗi tải', recognitionPreviews.error],
] as const

function screen(id: string, title: string, scr: string, render: () => ReturnType<typeof createElement>) {
  return { fullFrame: true, group: 'screen' as const, id, render, scr, title }
}

export const previews = [
  ...recognitionEntries.map(([id, title, props]) =>
    screen(id, title, 'SCR-015', () => inLearnerLayout(createElement(RecognitionView, props))),
  ),
  ...(
    [
      ['scr-014-list', 'Luyện viết — Danh sách chữ', characterListPreviews.ready],
      ['scr-014-list-empty', 'Luyện viết — Không tìm thấy', characterListPreviews.empty],
      ['scr-014-list-loading', 'Luyện viết — Đang tải', characterListPreviews.loading],
      ['scr-014-list-error', 'Luyện viết — Lỗi tải', characterListPreviews.error],
    ] as const
  ).map(([id, title, props]) =>
    screen(id, title, 'SCR-014', () => inLearnerLayout(createElement(CharacterListView, props))),
  ),
  ...(
    [
      ['scr-014-stroke', 'Luyện viết — Viết theo nét', characterPracticePreviews.stroke],
      ['scr-014-recall', 'Luyện viết — Nhớ rồi viết', characterPracticePreviews.recall],
      ['scr-014-no-stroke', 'Luyện viết — Chưa có dữ liệu nét', characterPracticePreviews.noStrokeData],
    ] as const
  ).map(([id, title, props]) =>
    screen(id, title, 'SCR-014', () => inLearnerLayout(createElement(CharacterPracticeView, props))),
  ),
  ...(
    [
      ['scr-014-challenge-intro', 'Thử thách viết — Giới thiệu', writingChallengePreviews.intro],
      ['scr-014-challenge-not-enough', 'Thử thách viết — Chưa đủ chữ', writingChallengePreviews.notEnough],
      ['scr-014-challenge-ready', 'Thử thách viết — Đang làm', writingChallengePreviews.ready],
    ] as const
  ).map(([id, title, props]) =>
    screen(id, title, 'SCR-014', () => inLearnerLayout(createElement(WritingChallengeView, props))),
  ),
] satisfies readonly PreviewEntry[]
