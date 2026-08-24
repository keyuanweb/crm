import { useEffect, useRef, useState } from 'react'
import { App, Button, Upload } from 'antd'
import { UploadOutlined } from '@ant-design/icons'

interface Props {
  /** 签名完成回调（base64 图片，dataURL 或 null=清除）。 */
  onChange: (base64: string | null) => void
  /** 是否禁用（已签署）。 */
  disabled?: boolean
}

/** 电子签名板（047）：canvas 手绘 + 图片上传两种方式。 */
export default function SignaturePad({ onChange, disabled }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const drawingRef = useRef(false)
  const lastRef = useRef<{ x: number; y: number } | null>(null)
  const [hasInk, setHasInk] = useState(false)
  const { message } = App.useApp()

  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas) return
    const dpr = window.devicePixelRatio || 1
    const rect = canvas.getBoundingClientRect()
    canvas.width = rect.width * dpr
    canvas.height = rect.height * dpr
    const ctx = canvas.getContext('2d')
    if (ctx) {
      ctx.scale(dpr, dpr)
      ctx.lineWidth = 2
      ctx.lineCap = 'round'
      ctx.strokeStyle = '#1f1f1f'
    }
  }, [])

  const getPos = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const rect = canvasRef.current!.getBoundingClientRect()
    return { x: e.clientX - rect.left, y: e.clientY - rect.top }
  }

  const onDown = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (disabled) return
    e.preventDefault()
    drawingRef.current = true
    lastRef.current = getPos(e)
    ;(e.target as HTMLCanvasElement).setPointerCapture(e.pointerId)
  }

  const onMove = (e: React.PointerEvent<HTMLCanvasElement>) => {
    if (!drawingRef.current || disabled) return
    const ctx = canvasRef.current!.getContext('2d')
    const pos = getPos(e)
    if (ctx && lastRef.current) {
      ctx.beginPath()
      ctx.moveTo(lastRef.current.x, lastRef.current.y)
      ctx.lineTo(pos.x, pos.y)
      ctx.stroke()
    }
    lastRef.current = pos
    setHasInk(true)
  }

  const onUp = () => {
    if (!drawingRef.current) return
    drawingRef.current = false
    lastRef.current = null
    emit()
  }

  const emit = () => {
    if (canvasRef.current && hasInk) {
      onChange(canvasRef.current.toDataURL('image/png'))
    }
  }

  const clear = () => {
    const canvas = canvasRef.current
    const ctx = canvas?.getContext('2d')
    if (canvas && ctx) {
      ctx.clearRect(0, 0, canvas.width, canvas.height)
    }
    setHasInk(false)
    onChange(null)
  }

  const handleUpload = (file: File) => {
    if (file.size > 500 * 1024) {
      message.error('签名图片不能超过 500KB')
      return false
    }
    const reader = new FileReader()
    reader.onload = () => {
      const dataUrl = reader.result as string
      // 绘制到 canvas 供预览与统一导出
      const img = new Image()
      img.onload = () => {
        const canvas = canvasRef.current
        const ctx = canvas?.getContext('2d')
        if (canvas && ctx) {
          ctx.clearRect(0, 0, canvas.width, canvas.height)
          const scale = Math.min(canvas.width / img.width, canvas.height / img.height, 1)
          const w = img.width * scale
          const h = img.height * scale
          ctx.drawImage(img, (canvas.width - w) / 2, (canvas.height - h) / 2, w, h)
        }
        setHasInk(true)
        onChange(dataUrl)
      }
      img.src = dataUrl
    }
    reader.readAsDataURL(file)
    return false
  }

  return (
    <div>
      <canvas
        ref={canvasRef}
        style={{
          width: '100%',
          height: 160,
          border: '1px dashed #d9d9d9',
          borderRadius: 6,
          background: '#fafafa',
          touchAction: 'none',
          cursor: disabled ? 'not-allowed' : 'crosshair',
        }}
        onPointerDown={onDown}
        onPointerMove={onMove}
        onPointerUp={onUp}
        onPointerLeave={onUp}
      />
      <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
        <Upload accept="image/*" beforeUpload={handleUpload} showUploadList={false} disabled={disabled}>
          <Button icon={<UploadOutlined />} size="small" disabled={disabled}>
            上传签名图
          </Button>
        </Upload>
        <Button size="small" onClick={clear} disabled={disabled || !hasInk}>
          清除
        </Button>
      </div>
    </div>
  )
}
