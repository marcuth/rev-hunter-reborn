import * as React from "react"
import { cn } from "@/lib/utils"

interface LinkProps extends React.AnchorHTMLAttributes<HTMLAnchorElement> {
    href: string
    children: React.ReactNode
    className?: string
}

export const Link = ({ href, children, className, ...props }: LinkProps) => {
    const isExternal = href.startsWith("http://") || href.startsWith("https://") || href.startsWith("//")

    return (
        <a
            href={href}
            className={cn(
                "font-medium text-purple-400 underline underline-offset-4 hover:text-purple-300 transition-colors cursor-pointer",
                className
            )}
            {...(isExternal ? { target: "_blank", rel: "noopener noreferrer" } : {})}
            {...props}
        >
            {children}
        </a>
    )
}
