import { onMounted, onUnmounted, ref } from 'vue'

/** Breakpoint D-SHELL-NAV : sous 768 px la nav démarre repliée. */
export const SHELL_NAV_NARROW_MQ = '(max-width: 767.98px)'

function readNarrow(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return window.matchMedia(SHELL_NAV_NARROW_MQ).matches
}

/**
 * État ouvert/replié de la navigation du shell (D-SHELL-NAV).
 * Pas de mémorisation : un franchissement du breakpoint remet le défaut du nouveau côté.
 */
export function useShellNavCollapse() {
  const isNarrow = ref(readNarrow())
  const navOpen = ref(!isNarrow.value)

  let media: MediaQueryList | null = null

  function applyViewport(narrow: boolean) {
    isNarrow.value = narrow
    navOpen.value = !narrow
  }

  function onMediaChange(event: MediaQueryListEvent) {
    applyViewport(event.matches)
  }

  function toggleNav() {
    navOpen.value = !navOpen.value
  }

  function closeNav() {
    navOpen.value = false
  }

  function openNav() {
    navOpen.value = true
  }

  onMounted(() => {
    media = window.matchMedia(SHELL_NAV_NARROW_MQ)
    applyViewport(media.matches)
    media.addEventListener('change', onMediaChange)
  })

  onUnmounted(() => {
    media?.removeEventListener('change', onMediaChange)
    media = null
  })

  return { isNarrow, navOpen, toggleNav, closeNav, openNav }
}
