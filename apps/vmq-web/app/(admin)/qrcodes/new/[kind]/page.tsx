"use client"

import { useState } from "react"
import { useParams, useRouter } from "next/navigation"
import { useI18n } from "@/lib/i18n"
import { vmqApi } from "@/lib/api"
import { toast } from "sonner"
import { ArrowLeft, Save, Upload, QrCode } from "lucide-react"

export default function QrcodeCreatePage() {
  const params = useParams()
  const router = useRouter()
  const kind = (params.kind as string) || "wechat"
  
  const { t } = useI18n()
  const [saving, setSaving] = useState(false)
  const [price, setPrice] = useState("")
  const [payUrl, setPayUrl] = useState("")

  const isWechat = kind === "wechat"
  const qrType = isWechat ? 1 : 2

  async function handleFileDecode(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    
    const loader = toast.loading(t("loadingOrder"))
    try {
      const res = await vmqApi.decodeQrcodeFile(file)
      toast.dismiss(loader)
      if (res.code === 1 && res.data) {
        setPayUrl(res.data)
        toast.success(res.msg || t("success"))
      } else {
        toast.error(res.msg || "Decode failed")
      }
    } catch (err: any) {
      toast.dismiss(loader)
      toast.error(err?.message || "Decode failed")
    }
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    if (!price || !payUrl) {
      toast.error("Please fill in all fields")
      return
    }
    setSaving(true)
    try {
      const res = await vmqApi.addPayQrcode({
        price,
        payUrl,
        type: qrType,
      })
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        router.push(`/qrcodes/${kind}`)
      } else {
        toast.error(res.msg || t("saveFailed"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("saveFailed"))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={handleSave} className="space-y-6">
      {/* Title & Actions */}
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{t("createQrCode")}</h2>
          <p className="text-sm text-subtle mt-1">{t("qrcodeCreateSubtitle")}</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => router.back()}
            className="flex items-center gap-1.5 px-4 py-2.5 border border-border bg-background/50 rounded-xl text-sm font-semibold hover:bg-muted transition"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>{t("back")}</span>
          </button>
          <button
            type="submit"
            disabled={saving}
            className="flex items-center gap-1.5 px-5 py-2.5 bg-primary text-background font-bold rounded-xl text-sm hover:bg-primary/90 transition disabled:opacity-50"
          >
            <Save className="w-4 h-4" />
            <span>{saving ? t("loadingOrder") : t("save")}</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Creation Form */}
        <div className="glass-panel p-6 rounded-2xl lg:col-span-2 space-y-5">
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("payType")}</label>
            <span className={`inline-block px-3 py-1.5 text-xs font-bold rounded-xl ${isWechat ? "bg-positive text-background" : "bg-primary text-background"}`}>
              {isWechat ? t("wechat") : t("alipay")}
            </span>
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("amount")}</label>
            <input
              type="text"
              value={price}
              onChange={(e) => setPrice(e.target.value)}
              placeholder="e.g. 1.00"
              className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("qrContent")}</label>
            <textarea
              value={payUrl}
              onChange={(e) => setPayUrl(e.target.value)}
              rows={7}
              placeholder="qrcode content..."
              className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition resize-none font-mono"
              required
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-foreground mb-2">{t("decodeImage")}</label>
            <label className="flex items-center justify-center gap-2 w-full py-3.5 border border-dashed border-border rounded-xl cursor-pointer hover:bg-muted hover:border-primary/50 transition">
              <Upload className="w-5 h-5 text-subtle" />
              <span className="text-xs text-subtle font-semibold">{t("decodeImage")}</span>
              <input
                type="file"
                accept="image/*"
                onChange={handleFileDecode}
                className="hidden"
              />
            </label>
          </div>
        </div>

        {/* QR Preview Panel */}
        <div className="glass-panel p-6 rounded-2xl space-y-4">
          <div className="text-xs font-bold text-subtle uppercase tracking-wider">二维码图片</div>
          <div className="aspect-square rounded-xl border border-border/50 bg-white p-3 flex items-center justify-center">
            {payUrl.trim() ? (
              <img
                src={vmqApi.encodeQrcodeUrl(payUrl.trim())}
                alt="qr code"
                className="h-full w-full object-contain"
              />
            ) : (
              <QrCode className="w-10 h-10 text-subtle" />
            )}
          </div>
          <div className="text-xs font-bold text-subtle uppercase tracking-wider">识别文字</div>
          <div className="text-xs font-mono break-all select-all p-3 border border-border/50 rounded-xl bg-background/50 text-foreground min-h-[10rem]">
            {payUrl || "-"}
          </div>
        </div>
      </div>
    </form>
  )
}
