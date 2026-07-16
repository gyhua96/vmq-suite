"use client"

import { useEffect, Suspense } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import { useI18n } from "@/lib/i18n"

function PayLegacyContent() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const orderId = searchParams.get("orderId") || ""
  const accessToken = searchParams.get("accessToken") || ""
  const { t } = useI18n()

  useEffect(() => {
    router.replace(`/pay?orderId=${encodeURIComponent(orderId)}&accessToken=${encodeURIComponent(accessToken)}`)
  }, [accessToken, orderId, router])

  return (
    <div className="flex min-height-screen items-center justify-center p-4 min-h-screen">
      <div className="glass-panel w-full max-w-md p-8 rounded-2xl shadow-2xl relative overflow-hidden tech-card text-center">
        <h2 className="text-xl font-bold text-foreground mb-2">{t("redirecting")}</h2>
        <p className="text-sm text-subtle">{t("legacyPayRedirecting")}</p>
      </div>
    </div>
  )
}

export default function PayLegacyPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <PayLegacyContent />
    </Suspense>
  )
}
