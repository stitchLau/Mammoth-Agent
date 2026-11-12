import { describe, expect, it } from 'vitest'
import { clampPage, pageCount, pageSlice } from './pagination'

describe('pagination helpers', () => {
  it('computes page counts', () => {
    expect(pageCount(0, 10)).toBe(1)
    expect(pageCount(1, 10)).toBe(1)
    expect(pageCount(10, 10)).toBe(1)
    expect(pageCount(11, 10)).toBe(2)
    expect(pageCount(95, 10)).toBe(10)
  })

  it('clamps out-of-range pages', () => {
    expect(clampPage(0, 11, 10)).toBe(1)
    expect(clampPage(5, 11, 10)).toBe(2)
    expect(clampPage(-3, 11, 10)).toBe(1)
  })

  it('slices items for the requested page', () => {
    const items = Array.from({ length: 25 }, (_, index) => index + 1)
    expect(pageSlice(items, 1, 10)).toEqual([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    expect(pageSlice(items, 3, 10)).toEqual([21, 22, 23, 24, 25])
    expect(pageSlice(items, 99, 10)).toEqual([21, 22, 23, 24, 25])
    expect(pageSlice([], 1, 10)).toEqual([])
  })
})
