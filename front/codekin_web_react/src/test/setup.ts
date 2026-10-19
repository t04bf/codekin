import '@testing-library/jest-dom/vitest'
import { afterEach, vi } from 'vitest'
import { cleanup } from '@testing-library/react'
afterEach(cleanup)

// Node 25 exposes experimental Web Storage; use the browser storage from the test DOM.
const dom = (globalThis as typeof globalThis & { jsdom?: { window: Window } }).jsdom
if (dom) vi.stubGlobal('localStorage', dom.window.localStorage)
