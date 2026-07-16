import type { Metadata } from "next"
import { I18nProvider } from "@/lib/i18n"
import { ThemeProvider } from "@/lib/theme"
import { Toaster } from "sonner"
import "./globals.css"

export const metadata: Metadata = {
  title: "VMQ Suite",
  description: "Realtime Payment Console",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="zh" data-theme="dark">
      <body>
        <I18nProvider>
          <ThemeProvider>
            {children}
            <Toaster position="top-right" richColors />
          </ThemeProvider>
        </I18nProvider>
      </body>
    </html>
  )
}
