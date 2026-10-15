import * as React from "react"
import { cn } from "@/lib/utils"

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    variant?: "default" | "destructive" | "outline" | "secondary" | "ghost" | "hunter"
    size?: "default" | "sm" | "lg" | "icon"
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
    ({ className, variant = "default", size = "default", ...props }, ref) => {
        const variants = {
            default: "bg-purple-600 text-white hover:bg-purple-500 font-semibold shadow-md shadow-purple-600/30 active:scale-[0.98]",
            hunter: "bg-gradient-to-r from-purple-600 via-fuchsia-500 to-indigo-500 text-white font-bold hover:brightness-110 shadow-lg shadow-purple-600/40 active:scale-[0.98] border border-purple-400/40",
            destructive: "bg-red-500/90 text-white hover:bg-red-500 shadow-md shadow-red-500/20 active:scale-[0.98]",
            outline: "border border-purple-900/60 bg-purple-950/40 text-purple-200 hover:bg-purple-900/50 hover:border-purple-700 active:scale-[0.98]",
            secondary: "bg-slate-800 text-slate-100 hover:bg-slate-700 active:scale-[0.98]",
            ghost: "text-purple-200 hover:bg-purple-900/40 active:scale-[0.98]"
        }

        const sizes = {
            default: "h-11 px-5 py-2.5 text-sm rounded-xl",
            sm: "h-9 px-3 text-xs rounded-lg",
            lg: "h-14 px-8 text-base rounded-2xl",
            icon: "h-10 w-10 rounded-xl flex items-center justify-center"
        }

        return (
            <button
                ref={ref}
                className={cn(
                    "inline-flex items-center justify-center whitespace-nowrap transition-all duration-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-purple-400 disabled:pointer-events-none disabled:opacity-50 select-none cursor-pointer",
                    variants[variant],
                    sizes[size],
                    className
                )}
                {...props}
            />
        )
    }
)

Button.displayName = "Button"
