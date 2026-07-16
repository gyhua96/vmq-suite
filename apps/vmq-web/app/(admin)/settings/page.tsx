"use client"

import { useState, useEffect } from "react"
import { useI18n } from "@/lib/i18n"
import { vmqApi, SettingsMap } from "@/lib/api"
import { toast } from "sonner"
import { RefreshCw, Save, Upload, Shield, Globe, QrCode, Settings } from "lucide-react"

export default function SettingsPage() {
  const { t } = useI18n()
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [form, setForm] = useState({
    user: "",
    pass: "",
    notifyUrl: "",
    returnUrl: "",
    key: "",
    wxpay: "",
    zfbpay: "",
    close: "",
    payQf: "1",
    callbackAsync: "0",
  })

  async function loadSettings() {
    setLoading(true)
    try {
      const res = await vmqApi.getSettings()
      if (res.code === 1 && res.data) {
        // Merge settings
        setForm((prev) => ({
          ...prev,
          ...res.data,
        }))
      } else {
        toast.error(res.msg || t("refresh"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("refresh"))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadSettings()
  }, [])

  async function handleSave(e: React.FormEvent) {
    e.preventDefault()
    if (saving) return
    setSaving(true)
    try {
      const res = await vmqApi.saveSetting(form)
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadSettings()
      } else {
        toast.error(res.msg || t("saveFailed"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("saveFailed"))
    } finally {
      setSaving(false)
    }
  }

  async function handleFileDecode(e: React.ChangeEvent<HTMLInputElement>, field: "wxpay" | "zfbpay") {
    const file = e.target.files?.[0]
    if (!file) return
    
    const loader = toast.loading(t("loadingOrder"))
    try {
      const res = await vmqApi.decodeQrcodeFile(file)
      toast.dismiss(loader)
      if (res.code === 1 && res.data) {
        setForm((prev) => ({
          ...prev,
          [field]: res.data,
        }))
        toast.success(res.msg || t("success"))
      } else {
        toast.error(res.msg || "Decode failed")
      }
    } catch (err: any) {
      toast.dismiss(loader)
      toast.error(err?.message || "Decode failed")
    }
  }

  function renderQrPreview(value: string) {
    const content = value.trim()

    return (
      <div className="grid grid-cols-1 sm:grid-cols-[8rem_1fr] gap-4 items-start">
        <div className="h-32 w-32 rounded-xl border border-border/50 bg-white p-2 flex items-center justify-center">
          {content ? (
            <img
              src={vmqApi.encodeQrcodeUrl(content)}
              alt="qr code"
              className="h-full w-full object-contain"
            />
          ) : (
            <QrCode className="w-10 h-10 text-subtle" />
          )}
        </div>
        <div>
          <div className="text-xs font-bold text-subtle uppercase tracking-wider mb-2">识别文字</div>
          <div className="min-h-32 max-h-48 overflow-auto text-xs font-mono break-all select-all p-3 border border-border/50 rounded-xl bg-background/50 text-foreground">
            {content || "-"}
          </div>
        </div>
      </div>
    )
  }

  return (
    <form onSubmit={handleSave} className="space-y-6">
      {/* Title & Save */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{t("settings")}</h2>
          <p className="text-sm text-subtle mt-1">{t("settingsSubtitle")}</p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={loadSettings}
            className="flex items-center gap-1.5 px-4 py-2.5 border border-border bg-background/50 rounded-xl text-sm font-semibold hover:bg-muted transition"
          >
            <RefreshCw className="w-4 h-4" />
            <span>{t("refresh")}</span>
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

      {loading ? (
        <div className="py-12 text-center text-foreground font-medium">{t("loadingOrder")}</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Security Credentials */}
          <div className="glass-panel p-6 rounded-2xl space-y-6">
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <Shield className="w-5 h-5 text-primary" />
              <span>{t("adminUser")}</span>
            </h3>
            
            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("user")}</label>
                <input
                  type="text"
                  value={form.user}
                  onChange={(e) => setForm({ ...form, user: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("adminPassword")}</label>
                <input
                  type="password"
                  value={form.pass}
                  onChange={(e) => setForm({ ...form, pass: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("secretKey")}</label>
                <input
                  type="text"
                  value={form.key}
                  onChange={(e) => setForm({ ...form, key: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
            </div>
          </div>

          {/* Callback Settings */}
          <div className="glass-panel p-6 rounded-2xl space-y-6">
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <Globe className="w-5 h-5 text-primary" />
              <span>{t("callbackMode")}</span>
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("notifyUrl")}</label>
                <input
                  type="text"
                  value={form.notifyUrl}
                  onChange={(e) => setForm({ ...form, notifyUrl: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("returnUrl")}</label>
                <input
                  type="text"
                  value={form.returnUrl}
                  onChange={(e) => setForm({ ...form, returnUrl: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("callbackMode")}</label>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setForm({ ...form, callbackAsync: "0" })}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold border transition ${
                      form.callbackAsync === "0"
                        ? "bg-primary text-background border-primary shadow-md shadow-primary/10"
                        : "border-border text-subtle hover:bg-muted"
                    }`}
                  >
                    {t("sync")}
                  </button>
                  <button
                    type="button"
                    onClick={() => setForm({ ...form, callbackAsync: "1" })}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold border transition ${
                      form.callbackAsync === "1"
                        ? "bg-primary text-background border-primary shadow-md shadow-primary/10"
                        : "border-border text-subtle hover:bg-muted"
                    }`}
                  >
                    {t("async")}
                  </button>
                </div>
              </div>
            </div>
          </div>

          {/* Wechat Common QR */}
          <div className="glass-panel p-6 rounded-2xl space-y-6">
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <QrCode className="w-5 h-5 text-positive" />
              <span>{t("wechatCommonQr")}</span>
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("wechatCommonQr")}</label>
                <textarea
                  value={form.wxpay}
                  onChange={(e) => setForm({ ...form, wxpay: e.target.value })}
                  rows={4}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition resize-none"
                />
              </div>
              {renderQrPreview(form.wxpay)}
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("decodeWechatQr")}</label>
                <label className="flex items-center justify-center gap-2 w-full py-2.5 border border-dashed border-border rounded-xl cursor-pointer hover:bg-muted hover:border-primary/50 transition">
                  <Upload className="w-4.5 h-4.5 text-subtle" />
                  <span className="text-xs text-subtle font-semibold">{t("decodeImage")}</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => handleFileDecode(e, "wxpay")}
                    className="hidden"
                  />
                </label>
              </div>
            </div>
          </div>

          {/* Alipay Common QR */}
          <div className="glass-panel p-6 rounded-2xl space-y-6">
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <QrCode className="w-5 h-5 text-primary" />
              <span>{t("alipayCommonQr")}</span>
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("alipayCommonQr")}</label>
                <textarea
                  value={form.zfbpay}
                  onChange={(e) => setForm({ ...form, zfbpay: e.target.value })}
                  rows={4}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition resize-none"
                />
              </div>
              {renderQrPreview(form.zfbpay)}
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("decodeAlipayQr")}</label>
                <label className="flex items-center justify-center gap-2 w-full py-2.5 border border-dashed border-border rounded-xl cursor-pointer hover:bg-muted hover:border-primary/50 transition">
                  <Upload className="w-4.5 h-4.5 text-subtle" />
                  <span className="text-xs text-subtle font-semibold">{t("decodeImage")}</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(e) => handleFileDecode(e, "zfbpay")}
                    className="hidden"
                  />
                </label>
              </div>
            </div>
          </div>

          {/* Timeout & Price Direction */}
          <div className="glass-panel p-6 rounded-2xl space-y-6">
            <h3 className="text-base font-bold text-foreground flex items-center gap-2">
              <Settings className="w-5 h-5 text-primary" />
              <span>{t("timeoutMinutes")}</span>
            </h3>

            <div className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("timeoutMinutes")}</label>
                <input
                  type="text"
                  value={form.close}
                  onChange={(e) => setForm({ ...form, close: e.target.value })}
                  className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-foreground mb-2">{t("priceDirection")}</label>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => setForm({ ...form, payQf: "1" })}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold border transition ${
                      form.payQf === "1"
                        ? "bg-primary text-background border-primary shadow-md shadow-primary/10"
                        : "border-border text-subtle hover:bg-muted"
                    }`}
                  >
                    {t("increase")}
                  </button>
                  <button
                    type="button"
                    onClick={() => setForm({ ...form, payQf: "2" })}
                    className={`flex-1 py-2.5 rounded-xl text-sm font-bold border transition ${
                      form.payQf === "2"
                        ? "bg-primary text-background border-primary shadow-md shadow-primary/10"
                        : "border-border text-subtle hover:bg-muted"
                    }`}
                  >
                    {t("decrease")}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </form>
  )
}
