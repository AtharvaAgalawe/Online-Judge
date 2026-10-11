import { useEffect, useState } from 'react'

function formatRemaining(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const pad = (value: number) => String(value).padStart(2, '0')
  return days > 0
    ? `${days}d ${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
    : `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

export function Countdown({ target, label }: { target: string; label: string }) {
  const [remaining, setRemaining] = useState(() => Date.parse(target) - Date.now())

  useEffect(() => {
    const timer = window.setInterval(() => setRemaining(Date.parse(target) - Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [target])

  return (
    <span className="contest-countdown">
      {label}: {formatRemaining(remaining)}
    </span>
  )
}
