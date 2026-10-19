import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { AudioButton } from '@/features/practice/components/AudioButton'

const playMock = vi.spyOn(HTMLMediaElement.prototype, 'play')

afterEach(() => playMock.mockReset())

describe('AudioButton', () => {
  it('does not autoplay and locks after three total plays', async () => {
    const user = userEvent.setup()
    const onReplayCountChange = vi.fn()
    playMock.mockResolvedValue(undefined)
    render(
      <AudioButton
        audioUrl="/audio/ni-hao.mp3"
        maxReplays={3}
        onAudioFailed={vi.fn()}
        onReplayCountChange={onReplayCountChange}
      />,
    )

    expect(playMock).not.toHaveBeenCalled()
    const playButton = screen.getByRole('button', { name: 'Nghe từ' })
    await user.click(playButton)
    await user.click(playButton)
    await user.click(playButton)

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Đã hết lượt nghe' })).toBeDisabled(),
    )
    expect(playMock).toHaveBeenCalledTimes(3)
    expect(onReplayCountChange).toHaveBeenLastCalledWith(3)
    expect(screen.getByText('Đã nghe 3/3 lần')).toBeInTheDocument()
  })

  it('reports zero plays when the learner cannot hear without playing audio', async () => {
    const user = userEvent.setup()
    const onAudioFailed = vi.fn()
    render(
      <AudioButton audioUrl="/audio/ni-hao.mp3" maxReplays={3} onAudioFailed={onAudioFailed} />,
    )

    await user.click(screen.getByRole('button', { name: 'Không nghe được' }))
    expect(onAudioFailed).toHaveBeenCalledWith({ audioFailed: true, replayCount: 0 })
    expect(playMock).not.toHaveBeenCalled()
  })

  it('shows a safe instruction when playback fails', async () => {
    const user = userEvent.setup()
    playMock.mockRejectedValue(new Error('media detail'))
    render(<AudioButton audioUrl="/audio/missing.mp3" maxReplays={3} onAudioFailed={vi.fn()} />)

    await user.click(screen.getByRole('button', { name: 'Nghe từ' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Không tải được âm thanh. Bạn có thể bấm “Không nghe được” để tiếp tục.',
    )
  })

  it('starts from initialReplayCount and only allows the remaining plays', async () => {
    const user = userEvent.setup()
    playMock.mockResolvedValue(undefined)
    render(
      <AudioButton
        audioUrl="/audio/ni-hao.mp3"
        initialReplayCount={2}
        maxReplays={3}
        onAudioFailed={vi.fn()}
      />,
    )

    expect(screen.getByText('Đã nghe 2/3 lần')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'Nghe từ' }))

    expect(await screen.findByRole('button', { name: 'Đã hết lượt nghe' })).toBeDisabled()
  })
})
