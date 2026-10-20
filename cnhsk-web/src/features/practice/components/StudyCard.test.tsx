import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { StudyCard } from '@/features/practice/components/StudyCard'
import type { ServedItemView } from '@/features/practice/types'

function studyItem(audioUrl?: string | null, sinoViet?: string | null): ServedItemView {
  return {
    no: 1,
    prompt: {
      audioUrl,
      examples: [
        {
          cn: '我们学习中文。',
          pinyin: 'wo3men5 xue2xi2 zhong1wen2',
          vi: 'Chúng tôi học tiếng Trung.',
        },
      ],
      meaning: 'chúng tôi, chúng ta',
      pinyin: 'wo3men5',
      sinoViet,
      text: '我们',
    },
    rules: { maxReplays: 3 },
    skill: 'VOCAB',
    type: 'STUDY_CARD',
  }
}

describe('StudyCard', () => {
  it('hides empty Sino-Vietnamese and audio controls when audio is absent', () => {
    render(
      <StudyCard
        item={studyItem(null, '  ')}
        onAudioFailed={vi.fn()}
        onContinue={vi.fn()}
        onKnown={vi.fn()}
      />,
    )

    expect(screen.queryByRole('button', { name: 'Nghe từ' })).not.toBeInTheDocument()
    expect(screen.getByText('我们学习中文。')).toHaveAttribute('lang', 'zh-CN')
    expect(screen.getByText('chúng tôi, chúng ta')).toBeInTheDocument()
  })

  it('renders both learning actions and audio when available', async () => {
    const user = userEvent.setup()
    const onContinue = vi.fn()
    const onKnown = vi.fn()
    render(
      <StudyCard
        item={studyItem('/audio/we.mp3', 'NGÃ MÔN')}
        onAudioFailed={vi.fn()}
        onContinue={onContinue}
        onKnown={onKnown}
      />,
    )

    expect(screen.getByText('NGÃ MÔN')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Nghe từ' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Tiếp' }))
    await user.click(screen.getByRole('button', { name: 'Tôi đã biết từ này' }))
    expect(onContinue).toHaveBeenCalledOnce()
    expect(onKnown).toHaveBeenCalledOnce()
  })
})
