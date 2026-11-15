import type {} from '@testing-library/jest-dom/vitest'
import * as matchers from '@testing-library/jest-dom/matchers'
import { cleanup } from '@testing-library/react'
import { afterEach, expect } from 'vitest'

// Extend the test runner's expect even when dependencies live behind a junction.
expect.extend(matchers)

const NativeRequest = globalThis.Request
globalThis.Request = class extends NativeRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    // Memory-router requests never send network traffic. Match the web app's
    // jsdom/Node AbortSignal realm compatibility without changing production code.
    super(input, init?.signal ? { ...init, signal: undefined } : init)
  }
}

afterEach(cleanup)
