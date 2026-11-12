import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { getMyRecommendations } from '../api/mastery'
import { getReviewSummary } from '../api/reviews'
import type { Recommendation, ReviewSummary } from '../types'
import Scr010LearningDashboardPage from './Scr010LearningDashboardPage'

vi.mock('../api/mastery', () => ({ getMyRecommendations: vi.fn() }))
vi.mock('../api/reviews', () => ({ getReviewSummary: vi.fn() }))

const getMyRecommendationsMock = vi.mocked(getMyRecommendations)
const getReviewSummaryMock = vi.mocked(getReviewSummary)

const recommendationFixtures: Recommendation[] = Array.from({ length: 6 }, (_, index) => ({
  kid: `NA1-${String(index + 1).padStart(3, '0')}`,
  nameVi: `Nút kiểm thử ${index + 1}`,
  kind: 'NEW',
  reason: `Lý do kiểm thử ${index + 1}`,
  value: null,
}))

const reviewSummaryFixture: ReviewSummary = {
  dueCount: 2,
  nextDueAt: '2026-11-12T00:00:00Z',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <Scr010LearningDashboardPage />
    </MemoryRouter>,
  )
}

function setSuccessfulResponses(
  recommendations = recommendationFixtures,
  reviewSummary = reviewSummaryFixture,
) {
  getMyRecommendationsMock.mockResolvedValue(recommendations)
  getReviewSummaryMock.mockResolvedValue(reviewSummary)
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('SCR-010 Learning Dashboard (FR-025, AT-25)', () => {
  it('shows at most five recommendations with their reasons and targeted practice links', async () => {
    setSuccessfulResponses()

    renderPage()

    const recommendations = await screen.findByRole('region', { name: 'Gợi ý hôm nay' })
    expect(within(recommendations).getAllByRole('link', { name: 'Luyện' })).toHaveLength(5)
    expect(within(recommendations).getByText('Nút kiểm thử 1')).toBeVisible()
    expect(within(recommendations).getByText('Lý do kiểm thử 1')).toBeVisible()
    expect(within(recommendations).queryByText('Nút kiểm thử 6')).not.toBeInTheDocument()
    expect(within(recommendations).queryByText('NEW')).not.toBeInTheDocument()
    expect(within(recommendations).getAllByRole('link', { name: 'Luyện' })[0]).toHaveAttribute(
      'href',
      '/learn/practice/targeted?kid=NA1-001',
    )
    expect(screen.queryByText(/chuỗi ngày|lượt|credit/i)).not.toBeInTheDocument()
  })

  it('keeps review data visible while recommendations are loading', async () => {
    getMyRecommendationsMock.mockReturnValue(new Promise(() => undefined))
    getReviewSummaryMock.mockResolvedValue(reviewSummaryFixture)

    renderPage()

    const recommendations = screen.getByRole('region', { name: 'Gợi ý hôm nay' })
    expect(await within(recommendations).findByRole('status', { name: 'Đang tải' })).toBeVisible()
    expect(await screen.findByText('2 mục đến hạn')).toBeVisible()
  })

  it('keeps recommendations visible while review summary is loading', async () => {
    getMyRecommendationsMock.mockResolvedValue(recommendationFixtures)
    getReviewSummaryMock.mockReturnValue(new Promise(() => undefined))

    renderPage()

    expect(await screen.findByText('Nút kiểm thử 1')).toBeVisible()
    const review = screen.getByRole('region', { name: 'Ôn tập' })
    expect(await within(review).findByRole('status', { name: 'Đang tải' })).toBeVisible()
  })

  it('retries only recommendations when that section fails', async () => {
    getMyRecommendationsMock
      .mockRejectedValueOnce(new Error('recommendations unavailable'))
      .mockResolvedValue(recommendationFixtures)
    getReviewSummaryMock.mockResolvedValue(reviewSummaryFixture)

    renderPage()

    const recommendations = screen.getByRole('region', { name: 'Gợi ý hôm nay' })
    fireEvent.click(await within(recommendations).findByRole('button', { name: 'Thử lại' }))

    await waitFor(() => expect(getMyRecommendationsMock).toHaveBeenCalledTimes(2))
    expect(getReviewSummaryMock).toHaveBeenCalledTimes(1)
    expect(await within(recommendations).findByText('Nút kiểm thử 1')).toBeVisible()
  })

  it('retries only review summary when that section fails', async () => {
    getMyRecommendationsMock.mockResolvedValue(recommendationFixtures)
    getReviewSummaryMock
      .mockRejectedValueOnce(new Error('review summary unavailable'))
      .mockResolvedValue(reviewSummaryFixture)

    renderPage()

    const review = screen.getByRole('region', { name: 'Ôn tập' })
    fireEvent.click(await within(review).findByRole('button', { name: 'Thử lại' }))

    await waitFor(() => expect(getReviewSummaryMock).toHaveBeenCalledTimes(2))
    expect(getMyRecommendationsMock).toHaveBeenCalledTimes(1)
    expect(await within(review).findByText('2 mục đến hạn')).toBeVisible()
  })

  it('shows the approved empty recommendation text and no upcoming review action', async () => {
    setSuccessfulResponses([], { dueCount: 0, nextDueAt: null })

    renderPage()

    expect(await screen.findByText('Hôm nay chưa có gợi ý phù hợp')).toBeVisible()
    expect(await screen.findByText('0 mục đến hạn')).toBeVisible()
    expect(screen.getByText('Chưa có lịch ôn tiếp theo')).toBeVisible()
    expect(screen.queryByRole('link', { name: 'Ôn trước' })).not.toBeInTheDocument()
  })

  it('links due reviews to SCR-013 without ahead mode', async () => {
    setSuccessfulResponses(recommendationFixtures, { dueCount: 3, nextDueAt: null })

    renderPage()

    expect(await screen.findByText('3 mục đến hạn')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Ôn ngay' })).toHaveAttribute('href', '/learn/review')
    expect(screen.queryByRole('link', { name: 'Ôn trước' })).not.toBeInTheDocument()
  })

  it('formats the next due time in Vietnam time and opens SCR-013 in ahead mode', async () => {
    setSuccessfulResponses(recommendationFixtures, {
      dueCount: 0,
      nextDueAt: '2026-11-12T00:00:00Z',
    })

    renderPage()

    expect(await screen.findByText('0 mục đến hạn')).toBeVisible()
    expect(screen.getByText('Lần ôn tiếp theo: 07:00 12 thg 11, 2026')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Ôn trước' })).toHaveAttribute(
      'href',
      '/learn/review?ahead=true',
    )
  })
})
