import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { PreviewPage } from '@/app/preview/PreviewPage'

function LocationProbe() {
  const location = useLocation()

  return <output data-testid="location">{`${location.pathname}${location.search}`}</output>
}

function renderPreview(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <PreviewPage />
      <LocationProbe />
    </MemoryRouter>,
  )
}

describe('PreviewPage', () => {
  it('stores the default view, state and width in the URL', async () => {
    renderPreview('/__preview')

    await waitFor(() =>
      expect(screen.getByTestId('location')).toHaveTextContent(
        '/__preview?view=component-button&state=ready&w=1280',
      ),
    )
    expect(screen.getByTitle('Xem trước Button')).toHaveAttribute(
      'src',
      '/__preview/frame/component-button/ready',
    )
    expect(screen.getByTitle('Xem trước Button')).toHaveStyle({ width: '1280px' })
  })

  it('restores a shared selection and updates its width in the URL', async () => {
    const user = userEvent.setup()
    renderPreview('/__preview?view=component-status-badge&state=ready&w=768')

    expect(screen.getByTitle('Xem trước StatusBadge')).toHaveStyle({ width: '768px' })

    await user.click(screen.getByRole('button', { name: '390 px' }))

    expect(screen.getByTitle('Xem trước StatusBadge')).toHaveStyle({ width: '390px' })
    expect(screen.getByTestId('location')).toHaveTextContent(
      '/__preview?view=component-status-badge&state=ready&w=390',
    )
  })
})
