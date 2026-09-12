import { useEffect, useState } from 'react'

/** Safari 的旧前缀全屏 API（DOM lib 未收录，故补声明而不是用 any 抹平）。 */
type WebkitFullscreenElement = Element & { webkitRequestFullscreen?: () => void }
type WebkitFullscreenDocument = Document & { webkitExitFullscreen?: () => void }

/**
 * 全屏 API Hook
 * @returns [isFullscreen, toggleFullscreen, enterFullscreen, exitFullscreen]
 */
export function useFullscreen() {
  const [isFullscreen, setIsFullscreen] = useState(!!document.fullscreenElement)

  const enterFullscreen = () => {
    const element = document.documentElement as WebkitFullscreenElement
    if (element.requestFullscreen) {
      void element.requestFullscreen()
    } else if (element.webkitRequestFullscreen) {
      void element.webkitRequestFullscreen()
    }
  }

  const exitFullscreen = () => {
    const doc = document as WebkitFullscreenDocument
    if (doc.exitFullscreen) {
      void doc.exitFullscreen()
    } else if (doc.webkitExitFullscreen) {
      void doc.webkitExitFullscreen()
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
