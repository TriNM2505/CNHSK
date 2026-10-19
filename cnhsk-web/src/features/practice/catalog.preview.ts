import { createElement } from 'react'

import type { PreviewEntry } from '@/app/preview/types'
import {
  AudioButtonGallery,
  ChoiceQuestionGallery,
  PinyinInputGallery,
  SessionProgressGallery,
  SessionSummaryGallery,
  StudyCardGallery,
} from '@/app/preview/practiceGalleries'

export const previews = [
  {
    group: 'component',
    id: 'practice-choice-question',
    render: () => createElement(ChoiceQuestionGallery),
    title: 'ChoiceQuestion',
  },
  {
    group: 'component',
    id: 'practice-pinyin-input',
    render: () => createElement(PinyinInputGallery),
    title: 'PinyinInput',
  },
  {
    group: 'component',
    id: 'practice-audio-button',
    render: () => createElement(AudioButtonGallery),
    title: 'AudioButton',
  },
  {
    group: 'component',
    id: 'practice-study-card',
    render: () => createElement(StudyCardGallery),
    title: 'StudyCard',
  },
  {
    group: 'component',
    id: 'practice-session-progress',
    render: () => createElement(SessionProgressGallery),
    title: 'SessionProgress',
  },
  {
    group: 'component',
    id: 'practice-session-summary',
    render: () => createElement(SessionSummaryGallery),
    title: 'SessionSummary',
  },
] satisfies readonly PreviewEntry[]
