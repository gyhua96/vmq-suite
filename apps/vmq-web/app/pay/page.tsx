"use client"

import { useState, useEffect, useRef, Suspense } from "react"
import { useSearchParams } from "next/navigation"
import { useI18n } from "@/lib/i18n"
import { useTheme } from "@/lib/theme"
import { vmqApi, CreateOrderRes } from "@/lib/api"
import { toast } from "sonner"

function PayPageContent() {
  const searchParams = useSearchParams()
  const orderId = searchParams.get("orderId") || ""
  const accessToken = searchParams.get("accessToken") || ""
  
  const { language, t, setLanguage } = useI18n()
  const { theme, toggleTheme } = useTheme()
  
  const [loading, setLoading] = useState(true)
  const [order, setOrder] = useState<CreateOrderRes | null>(null)
  const [countdownText, setCountdownText] = useState("")
  
  const timerRef = useRef<number | null>(null)
  const pollRef = useRef<number | null>(null)

  useEffect(() => {
    if (!orderId) {
      setLoading(false)
      return
    }
    
    async function loadOrder() {
      setLoading(true)
      try {
      const res = await vmqApi.getOrder(orderId, accessToken)
        if (res.code === 1 && res.data) {
          setOrder(res.data)
          startCountdown(res.data)
          startPoll()
        } else {
          toast.error(res.msg || t("orderNotFound"))
        }
      } catch (err: any) {
        toast.error(err?.message || t("orderNotFound"))
      } finally {
        setLoading(false)
      }
    }
    
    loadOrder()
    
    return () => {
      stopCountdown()
      stopPoll()
    }
  }, [orderId, accessToken])

  function startCountdown(ord: CreateOrderRes) {
    stopCountdown()
    const deadline = ord.date + ord.timeOut * 60_000
    
    const tick = () => {
      const left = deadline - Date.now()
      if (left <= 0) {
        setCountdownText(t("orderExpired"))
        stopCountdown()
        stopPoll()
        return
      }
      const sec = Math.floor(left / 1000)
      const minutesText = Math.floor(sec / 60) + t("minutes")
      const secondsText = (sec % 60) + t("seconds")
      setCountdownText(`${minutesText} ${secondsText} ${t("remaining")}`)
    }
    
    tick()
    timerRef.current = window.setInterval(tick, 1000)
  }

  function stopCountdown() {
    if (timerRef.current) {
      window.clearInterval(timerRef.current)
      timerRef.current = null
    }
  }

  function startPoll() {
    stopPoll()
    pollRef.current = window.setInterval(async () => {
      try {
      const res = await vmqApi.checkOrder(orderId, accessToken)
        if (res.code === 1 && res.data) {
          stopPoll()
          stopCountdown()
          window.location.href = res.data
        }
      } catch (e) {
        // ignore network error during poll
      }
    }, 2000)
  }

  function stopPoll() {
    if (pollRef.current) {
      window.clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  const qrcodeSrc = order?.payUrl ? vmqApi.encodeQrcodeUrl(order.payUrl) : ""

  return (
    <div className="flex min-height-screen items-center justify-center p-4 min-h-screen">
      <div className="glass-panel w-full max-w-md p-8 rounded-2xl shadow-2xl relative overflow-hidden tech-card text-center">
        <div className="flex justify-between items-start mb-6 text-left relative z-10">
          <div>
            <h2 className="text-xl font-bold tracking-tight text-foreground">{t("scanToPay")}</h2>
            <div className="text-sm text-subtle mt-1">{t("paySubtitle")}</div>
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => setLanguage(language === "zh" ? "en" : "zh")}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold text-foreground hover:bg-muted transition duration-200"
            >
              {language === "zh" ? "EN" : "ZH"}
            </button>
            <button
              onClick={toggleTheme}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold text-foreground hover:bg-muted transition duration-200"
            >
              {theme === "dark" ? t("light") : t("dark")}
            </button>
          </div>
        </div>

        {loading ? (
          <div className="py-12 text-foreground font-medium">{t("loadingOrder")}</div>
        ) : order ? (
          <div className="space-y-6 relative z-10">
            <div className="text-3xl font-extrabold text-primary tracking-tight">
              ￥{order.reallyPrice.toFixed(2)}
            </div>
            
            {qrcodeSrc && (
              <div className="inline-block p-4 bg-white rounded-2xl shadow-inner">
                <img src={qrcodeSrc} alt="payment qr code" className="w-56 h-56 object-contain" />
              </div>
            )}

            <div className="border-t border-b border-border/50 py-4 space-y-2 text-sm text-left">
              <div className="flex justify-between">
                <span className="text-subtle">{t("orderId")}</span>
                <span className="font-mono text-foreground select-all">{order.orderId}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-subtle">{t("status")}</span>
                <span className="px-2.5 py-1 text-xs font-bold rounded-lg bg-warning/10 text-warning">
                  {t("pending")}
                </span>
              </div>
            </div>

            {countdownText && (
              <div className="text-sm font-semibold text-subtle animate-pulse">
                {countdownText}
              </div>
            )}
          </div>
        ) : (
          <div className="py-12 text-subtle">{t("orderNotFound")}</div>
        )}
      </div>
    </div>
  )
}

export default function PayPage() {
  return (
    <Suspense fallback={<div className="flex min-h-screen items-center justify-center text-foreground font-medium">Loading...</div>}>
      <PayPageContent />
    </Suspense>
  )
}
