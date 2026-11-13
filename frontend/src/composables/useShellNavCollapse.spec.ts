import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import { SHELL_NAV_NARROW_MQ, useShellNavCollapse } from './useShellNavCollapse'

type Listener = (event: MediaQueryListEvent) => void

function mockMatchMedia(matches: boolean) {
  const listeners = new Set<Listener>()
  const mql = {
    matches,
    media: SHELL_NAV_NARROW_MQ,
    onchange: null,
    addEventListener: (_event: string, listener: Listener) => {
      listeners.add(listener)
    },
    removeEventListener: (_event: string, listener: Listener) => {
      listeners.delete(listener)
    },
    addListener: () => undefined,
    removeListener: () => undefined,
    dispatchEvent: () => true,
  }
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => mql),
  )
  return {
    setMatches(next: boolean) {
      mql.matches = next
      const event = { matches: next, media: SHELL_NAV_NARROW_MQ } as MediaQueryListEvent
      listeners.forEach((listener) => listener(event))
    },
  }
}

const Probe = defineComponent({
  setup() {
    return useShellNavCollapse()
  },
  template:
    '<div data-testid="probe" :data-narrow="isNarrow" :data-open="navOpen">{{ isNarrow }}-{{ navOpen }}</div>',
})

describe('useShellNavCollapse (D-SHELL-NAV)', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('starts collapsed when the viewport is narrow', () => {
    mockMatchMedia(true)
    const wrapper = mount(Probe)
    expect(wrapper.text()).toBe('true-false')
  })

  it('starts open when the viewport is wide', () => {
    mockMatchMedia(false)
    const wrapper = mount(Probe)
    expect(wrapper.text()).toBe('false-true')
  })

  it('toggles open and closed', async () => {
    mockMatchMedia(false)
    const wrapper = mount(Probe)
    const vm = wrapper.vm as unknown as ReturnType<typeof useShellNavCollapse>
    vm.toggleNav()
    await nextTick()
    expect(wrapper.text()).toBe('false-false')
    vm.toggleNav()
    await nextTick()
    expect(wrapper.text()).toBe('false-true')
  })

  it('resets to the default of the new side when the breakpoint is crossed', async () => {
    const media = mockMatchMedia(false)
    const wrapper = mount(Probe)
    const vm = wrapper.vm as unknown as ReturnType<typeof useShellNavCollapse>
    vm.toggleNav()
    await nextTick()
    expect(wrapper.text()).toBe('false-false')

    media.setMatches(true)
    await nextTick()
    expect(wrapper.text()).toBe('true-false')

    media.setMatches(false)
    await nextTick()
    expect(wrapper.text()).toBe('false-true')
  })

  it('closeNav and openNav set the state explicitly', async () => {
    mockMatchMedia(true)
    const wrapper = mount(Probe)
    const vm = wrapper.vm as unknown as ReturnType<typeof useShellNavCollapse>
    vm.openNav()
    await nextTick()
    expect(wrapper.text()).toBe('true-true')
    vm.closeNav()
    await nextTick()
    expect(wrapper.text()).toBe('true-false')
  })
})
