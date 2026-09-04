import { useEffect, useState } from 'react'

/**
 * 全屏 API Hook
 * @returns [isFullscreen, toggleFullscreen, enterFullscreen, exitFullscreen]
 */
export function useFullscreen() {
  const [isFullscreen, setIsFullscreen] = useState(!!document.fullscreenElement)

  const enterFullscreen = () => {
    const element = document.documentElement
    if (element.requestFullscreen) {
      void element.requestFullscreen()
    } else if ((element as any).webkitRequestFullscreen) {
      void (element as any).webkitRequestFullscreen()
    }
  }

  const exitFullscreen = () => {
    if (document.exitFullscreen) {
      void document.exitFullscreen()
    } else if ((document as any).webkitExitFullscreen) {
      void (document as any).webkitExitFullscreen()
    }
  }

  const toggleFullscreen = () => {
    if (isFullscreen) {
      exitFullscreen()
    } else {
      enterFullscreen()
    }
  }

  useEffect(() => {
    const handleFullscreenChange = () => {
      setIsFullscreen(!!document.fullscreenElement)
    }

    document.addEventListener('fullscreenchange', handleFullscreenChange)
    document.addEventListener('webkitfullscreenchange', handleFullscreenChange)

    return () => {
      document.removeEventListener('fullscreenchange', handleFullscreenChange)
      document.removeEventListener('webkitfullscreenchange', handleFullscreenChange)
    }
  }, [])

  return { isFullscreen, toggleFullscreen, enterFullscreen, exitFullscreen }
}
