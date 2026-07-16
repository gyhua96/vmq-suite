"use client"

import { useState, useEffect } from "react"
import { useParams, useRouter } from "next/navigation"
import { useI18n } from "@/lib/i18n"
import { vmqApi, PayQrcode } from "@/lib/api"
import { toast } from "sonner"
import { Plus, Trash2, ArrowLeft, ArrowRight, Pencil, Save, X, Upload, QrCode } from "lucide-react"

export default function QrcodeListPage() {
  const params = useParams()
  const router = useRouter()
  const kind = (params.kind as string) || "wechat"

  const { t } = useI18n()
  const [loading, setLoading] = useState(true)
  const [qrcodes, setQrcodes] = useState<PayQrcode[]>([])
  const [count, setCount] = useState(0)
  const [page, setPage] = useState(1)
  const [limit] = useState(20)
  const [editing, setEditing] = useState<PayQrcode | null>(null)
  const [editPrice, setEditPrice] = useState("")
  const [editPayUrl, setEditPayUrl] = useState("")
  const [saving, setSaving] = useState(false)

  const isWechat = kind === "wechat"
  const qrType = isWechat ? 1 : 2
  const title = isWechat ? t("wechatFixedQrCodes") : t("alipayFixedQrCodes")

  async function loadQrcodes() {
    setLoading(true)
    try {
      const res = await vmqApi.getPayQrcodes({
        page,
        limit,
        type: qrType,
      })
      if (res.code === 0 && res.data) {
        setQrcodes(res.data)
        setCount(res.count)
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
    loadQrcodes()
  }, [page, kind])

  function startEdit(qr: PayQrcode) {
    setEditing(qr)
    setEditPrice(String(qr.price))
    setEditPayUrl(qr.payUrl || "")
  }

  async function handleEditFileDecode(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return

    const loader = toast.loading(t("loadingOrder"))
    try {
      const res = await vmqApi.decodeQrcodeFile(file)
      toast.dismiss(loader)
      if (res.code === 1 && res.data) {
        setEditPayUrl(res.data)
        toast.success(res.msg || t("success"))
      } else {
        toast.error(res.msg || "Decode failed")
      }
    } catch (err: any) {
      toast.dismiss(loader)
      toast.error(err?.message || "Decode failed")
    }
  }

  async function handleUpdate(e: React.FormEvent) {
    e.preventDefault()
    if (!editing || !editPrice || !editPayUrl.trim()) {
      toast.error("请填写金额和二维码内容")
      return
    }
    setSaving(true)
    try {
      const res = await vmqApi.updatePayQrcode({
        id: editing.id,
        price: editPrice,
        payUrl: editPayUrl,
        type: qrType,
      })
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        setEditing(null)
        loadQrcodes()
      } else {
        toast.error(res.msg || t("saveFailed"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("saveFailed"))
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(id: number) {
    if (!window.confirm(t("deleteQrConfirm"))) return
    try {
      const res = await vmqApi.deletePayQrcode(id)
      if (res.code === 1) {
        toast.success(res.msg || t("success"))
        loadQrcodes()
      } else {
        toast.error(res.msg || t("delete"))
      }
    } catch (err: any) {
      toast.error(err?.message || t("delete"))
    }
  }

  const totalPages = Math.ceil(count / limit) || 1

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-extrabold tracking-tight text-foreground">{title}</h2>
          <p className="text-sm text-subtle mt-1">{t("qrcodeListSubtitle")}</p>
        </div>
        <button
          onClick={() => router.push(`/qrcodes/new/${kind}`)}
          className="flex items-center gap-1.5 px-5 py-2.5 bg-primary text-background font-bold rounded-xl text-sm hover:bg-primary/90 transition shadow-lg shadow-primary/10"
        >
          <Plus className="w-4 h-4" />
          <span>{t("create")}</span>
        </button>
      </div>

      <div className="glass-panel rounded-2xl overflow-hidden overflow-x-auto">
        <table className="w-full border-collapse text-left">
          <thead>
            <tr className="border-b border-border/50 bg-background/30 text-xs font-bold text-subtle uppercase tracking-wider">
              <th className="p-4">{t("qrContent")}</th>
              <th className="p-4">二维码图片</th>
              <th className="p-4">{t("amount")}</th>
              <th className="p-4">{t("type")}</th>
              <th className="p-4 text-right">{t("actions")}</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800 text-sm">
            {loading ? (
              <tr>
                <td colSpan={5} className="p-8 text-center text-foreground font-medium">
                  {t("loadingOrder")}
                </td>
              </tr>
            ) : qrcodes.length === 0 ? (
              <tr>
                <td colSpan={5} className="p-8 text-center text-subtle">
                  {t("noQrcodes")}
                </td>
              </tr>
            ) : (
              qrcodes.map((qr) => (
                <tr key={qr.id} className="hover:bg-muted/30 transition">
                  <td className="p-4 font-mono text-xs break-all max-w-lg select-all text-foreground">
                    {qr.payUrl}
                  </td>
                  <td className="p-4">
                    <div className="h-24 w-24 rounded-xl border border-border/50 bg-white p-2 flex items-center justify-center">
                      {qr.payUrl ? (
                        <img
                          src={vmqApi.encodeQrcodeUrl(qr.payUrl)}
                          alt="qr code"
                          className="h-full w-full object-contain"
                        />
                      ) : (
                        <QrCode className="w-8 h-8 text-subtle" />
                      )}
                    </div>
                  </td>
                  <td className="p-4 font-semibold text-primary">
                    ¥{Number(qr.price).toFixed(2)}
                  </td>
                  <td className="p-4">
                    <span className={`px-2.5 py-1 text-xs font-bold rounded-lg ${isWechat ? "bg-positive/10 text-positive" : "bg-primary/10 text-primary"}`}>
                      {isWechat ? t("wechat") : t("alipay")}
                    </span>
                  </td>
                  <td className="p-4 text-right">
                    <button
                      onClick={() => startEdit(qr)}
                      className="p-1.5 rounded-lg text-primary hover:bg-primary/10 transition"
                      title="编辑"
                    >
                      <Pencil className="w-4.5 h-4.5" />
                    </button>
                    <button
                      onClick={() => handleDelete(qr.id)}
                      className="p-1.5 rounded-lg text-danger hover:bg-danger/10 transition"
                      title={t("delete")}
                    >
                      <Trash2 className="w-4.5 h-4.5" />
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <form onSubmit={handleUpdate} className="glass-panel w-full max-w-3xl rounded-2xl p-6 space-y-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <h3 className="text-xl font-extrabold text-foreground">编辑固定二维码</h3>
                <p className="text-sm text-subtle mt-1">{isWechat ? t("wechat") : t("alipay")}</p>
              </div>
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="p-2 rounded-xl border border-border text-subtle hover:text-foreground hover:bg-muted transition"
                title="取消"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-[1fr_12rem] gap-5">
              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-foreground mb-2">{t("amount")}</label>
                  <input
                    type="text"
                    value={editPrice}
                    onChange={(e) => setEditPrice(e.target.value)}
                    className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-foreground mb-2">识别文字</label>
                  <textarea
                    value={editPayUrl}
                    onChange={(e) => setEditPayUrl(e.target.value)}
                    rows={7}
                    className="w-full px-4 py-2.5 rounded-xl border border-border bg-background/50 text-sm focus:outline-none focus:ring-2 focus:ring-primary/50 text-foreground transition resize-none font-mono"
                  />
                </div>
                <label className="flex items-center justify-center gap-2 w-full py-3 border border-dashed border-border rounded-xl cursor-pointer hover:bg-muted hover:border-primary/50 transition">
                  <Upload className="w-5 h-5 text-subtle" />
                  <span className="text-xs text-subtle font-semibold">{t("decodeImage")}</span>
                  <input
                    type="file"
                    accept="image/*"
                    onChange={handleEditFileDecode}
                    className="hidden"
                  />
                </label>
              </div>

              <div>
                <div className="text-xs font-bold text-subtle uppercase tracking-wider mb-2">二维码图片</div>
                <div className="aspect-square rounded-xl border border-border/50 bg-white p-3 flex items-center justify-center">
                  {editPayUrl.trim() ? (
                    <img
                      src={vmqApi.encodeQrcodeUrl(editPayUrl.trim())}
                      alt="qr code"
                      className="h-full w-full object-contain"
                    />
                  ) : (
                    <QrCode className="w-10 h-10 text-subtle" />
                  )}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                type="button"
                onClick={() => setEditing(null)}
                className="flex items-center gap-1.5 px-4 py-2.5 border border-border bg-background/50 rounded-xl text-sm font-semibold hover:bg-muted transition"
              >
                <X className="w-4 h-4" />
                <span>取消</span>
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
          </form>
        </div>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-between mt-4">
          <span className="text-xs text-subtle">
            Total {count} items | Page {page} of {totalPages}
          </span>
          <div className="flex gap-2">
            <button
              disabled={page <= 1}
              onClick={() => setPage(p => Math.max(1, p - 1))}
              className="p-2 border border-border rounded-xl text-subtle hover:text-foreground hover:bg-muted disabled:opacity-30 transition"
            >
              <ArrowLeft className="w-4.5 h-4.5" />
            </button>
            <button
              disabled={page >= totalPages}
              onClick={() => setPage(p => Math.min(totalPages, p + 1))}
              className="p-2 border border-border rounded-xl text-subtle hover:text-foreground hover:bg-muted disabled:opacity-30 transition"
            >
              <ArrowRight className="w-4.5 h-4.5" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
