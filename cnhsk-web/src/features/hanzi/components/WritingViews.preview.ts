import type { ServedItemView } from '@/features/practice'

import type { CharacterDetail, CharacterSummary } from '../types'
import type { CharacterListViewProps } from './CharacterListView'
import type { CharacterPracticeViewProps } from './CharacterPracticeView'
import type { WritingChallengeViewProps } from './WritingChallengeView'

const noop = () => undefined

const characters: CharacterSummary[] = [
  { hasAudio: true, hskLevel: 1, id: 11, meaning: 'tốt; hay; khoẻ', pinyin: 'hǎo', strokeCount: 6, text: '好' },
  { hasAudio: true, hskLevel: 1, id: 12, meaning: 'mẹ', pinyin: 'mā', strokeCount: 6, text: '妈' },
  { hasAudio: false, hskLevel: 1, id: 13, meaning: 'nhà; gia đình', pinyin: 'jiā', strokeCount: 10, text: '家' },
  { hasAudio: false, hskLevel: 2, id: 14, meaning: 'sách', pinyin: 'shū', strokeCount: null, text: '书' },
]

const listBase: CharacterListViewProps = {
  characters,
  onHskLevelChange: noop,
  onPageChange: noop,
  onRetry: noop,
  onSearch: noop,
  page: 1,
  q: '',
  size: 20,
  state: 'ready',
  total: 4,
}

function detail(writing: Partial<CharacterDetail['writing']> = {}): CharacterDetail {
  return {
    audioUrl: '/audio/hanzi/hao.mp3',
    hasAudio: true,
    hskLevel: 1,
    id: 11,
    meaning: 'tốt; hay; khoẻ',
    pinyin: 'hǎo',
    radical: '女',
    sinoViet: 'hảo',
    strokeCount: 6,
    text: '好',
    writing: { dictationAvailable: true, recallUnlocked: false, strokeAvailable: true, ...writing },
  }
}

const challengeItem: ServedItemView = {
  no: 1,
  prompt: { meaning: 'mẹ', pinyin: 'mā', strokeCount: 6, text: '妈' },
  rules: {},
  skill: 'WRITING',
  type: 'WRITING_RESULT',
}

export const characterListPreviews = {
  empty: { ...listBase, characters: [], q: 'xyz', state: 'empty', total: 0 },
  error: { ...listBase, error: { message: 'Không kết nối được máy chủ.' }, state: 'error' },
  loading: { ...listBase, state: 'loading' },
  ready: listBase,
} satisfies Record<string, CharacterListViewProps>

export const characterPracticePreviews = {
  noStrokeData: {
    character: { ...detail({ dictationAvailable: false, strokeAvailable: false }), strokeCount: null },
    onRetry: noop,
    state: 'ready',
  },
  recall: {
    character: detail({ recallUnlocked: true }),
    mode: 'RECALL',
    onModeChange: noop,
    onRetry: noop,
    state: 'ready',
  },
  stroke: { character: detail(), mode: 'STROKE', onModeChange: noop, onRetry: noop, state: 'ready' },
} satisfies Record<string, CharacterPracticeViewProps>

export const writingChallengePreviews = {
  intro: { onStart: noop, state: 'intro' },
  notEnough: { notEnough: { have: 4, need: 10 }, onStart: noop, state: 'intro' },
  ready: { current: 3, item: challengeItem, mistakes: 1, remainingSeconds: 142, state: 'ready', total: 10 },
} satisfies Record<string, WritingChallengeViewProps>
