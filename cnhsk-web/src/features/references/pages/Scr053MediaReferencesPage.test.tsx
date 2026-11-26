import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import Scr053MediaReferencesPage from './Scr053MediaReferencesPage'

describe('SCR-053 retired media references', () => {
  it('sends old video and podcast links to the book catalogue', () => {
    render(
      <MemoryRouter initialEntries={['/references/media?kind=PODCAST']}>
        <Routes>
          <Route path="/references/media" element={<Scr053MediaReferencesPage />} />
          <Route path="/references/books" element={<p>Kệ sách</p>} />
        </Routes>
      </MemoryRouter>,
    )
    expect(screen.getByText('Kệ sách')).toBeVisible()
  })
})
