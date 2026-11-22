import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// A loaded machine can need more than the default second to render a lazily loaded screen.
// Passing queries still resolve immediately; only a real failure waits this long.
configure({ asyncUtilTimeout: 5000 })

const NativeRequest = globalThis.Request
globalThis.Request = class extends NativeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    if (!init?.signal) {
      super(input, init)
      return
    }

    // React Router và jsdom tạo AbortSignal ở hai realm khác nhau trong Vitest.
    // Request thử nghiệm không gửi mạng nên bỏ signal để vẫn kiểm được điều hướng.
    super(input, { ...init, signal: undefined })
  }
}

afterEach(() => {
  cleanup()
})
