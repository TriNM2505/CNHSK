import { describe, expect, it } from 'vitest'
import { renderToString } from 'react-dom/server'
import { MemoryRouter } from 'react-router-dom'
import App from './App'

describe('six game entry routes', () => {
  it.each([
    ['/play/char-rain', 'Mưa Chữ'],
    ['/play/pinyin-match', 'Ghép Pinyin'],
    ['/play/radical-match', 'Ghép Bộ Thủ'],
    ['/play/char-catch', 'Bắt Chữ'],
    ['/arcade/brush', 'Thần Bút'],
    ['/arcade/dart', 'Phi Đao'],
  ])('%s renders %s instead of a blank screen', (path, title) => {
    const html = renderToString(<MemoryRouter initialEntries={[path]}><App /></MemoryRouter>)
    expect(html).toContain(title)
    expect(html).toContain(path.startsWith('/play/') ? 'Bắt đầu chơi' : 'Vào trận')
  })
})
