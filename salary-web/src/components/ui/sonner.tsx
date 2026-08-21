"use client"

import { Toaster as Sonner, type ToasterProps } from "sonner"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

/**
 * Salary OS: the generated component resolved its theme with next-themes, which
 * is not our theme system — ours is a cookie plus an .app-light/.app-dark class
 * (src/lib/theme.ts). Without a next-themes provider it fell back to
 * prefers-color-scheme, so a reader who chose Dark on a light OS got light
 * toasts over a dark app.
 *
 * Instead `theme` is pinned and the colours come from our own tokens below,
 * which already switch with the class on <html>. One less dependency, and the
 * toast can no longer disagree with the page it sits on.
 */
const Toaster = ({ ...props }: ToasterProps) => {
  return (
    <Sonner
      theme="light"
      className="toaster group"
      icons={{
        success: <CircleCheckIcon className="size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--success-bg": "var(--popover)",
          "--success-text": "var(--positive)",
          "--success-border": "var(--border)",
          "--warning-bg": "var(--popover)",
          "--warning-text": "var(--attention)",
          "--warning-border": "var(--border)",
          "--error-bg": "var(--popover)",
          "--error-text": "var(--critical)",
          "--error-border": "var(--border)",
          "--border-radius": "var(--radius)",
        } as React.CSSProperties
      }
      toastOptions={{ classNames: { toast: "cn-toast" } }}
      {...props}
    />
  )
}

export { Toaster }
