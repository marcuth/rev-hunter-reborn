import * as React from "react"
import { cn } from "@/lib/utils"

export interface BadgeProps extends React.HTMLAttributes<HTMLDivElement> {
    variant?: "default" | "secondary" | "destructive" | "outline" | "success" | "warning"
}

export const Badge = ({ className, variant = "default", ...props }: BadgeProps) => {
    const variants = {
        default: "border-transparent bg-purple-500/20 text-purple-300 border border-purple-500/35",
        secondary: "border-transparent bg-purple-950/60 text-purple-200 border border-purple-900/40",
        destructive: "border-transparent bg-red-500/20 text-red-400 border border-red-500/30",
        outline: "text-purple-300 border border-purple-800/60",
        success: "border-transparent bg-purple-500/20 text-purple-300 border border-purple-500/40 shadow-xs shadow-purple-500/25",
        warning: "border-transparent bg-amber-500/20 text-amber-300 border border-amber-500/40"
    }

    return (
        <div
            className={cn(
                "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-hidden",
                variants[variant],
                className
            )}
            {...props}
        />
    )
}
