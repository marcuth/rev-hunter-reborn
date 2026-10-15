import * as React from "react"
import { cn } from "@/lib/utils"

interface TypographyProps extends React.HTMLAttributes<HTMLElement> {
    children: React.ReactNode
    className?: string
}

const H1 = ({ children, className, ...props }: TypographyProps) => (
    <h1
        className={cn(
            "scroll-m-20 text-3xl font-extrabold tracking-tight lg:text-4xl text-foreground",
            className
        )}
        {...props}
    >
        {children}
    </h1>
)

const H2 = ({ children, className, ...props }: TypographyProps) => (
    <h2
        className={cn(
            "scroll-m-20 text-2xl font-bold tracking-tight text-foreground",
            className
        )}
        {...props}
    >
        {children}
    </h2>
)

const H3 = ({ children, className, ...props }: TypographyProps) => (
    <h3
        className={cn(
            "scroll-m-20 text-xl font-semibold tracking-tight text-foreground",
            className
        )}
        {...props}
    >
        {children}
    </h3>
)

const H4 = ({ children, className, ...props }: TypographyProps) => (
    <h4
        className={cn(
            "scroll-m-20 text-lg font-semibold tracking-tight text-foreground",
            className
        )}
        {...props}
    >
        {children}
    </h4>
)

const P = ({ children, className, ...props }: TypographyProps) => (
    <p
        className={cn("leading-7 text-foreground/90", className)}
        {...props}
    >
        {children}
    </p>
)

const Lead = ({ children, className, ...props }: TypographyProps) => (
    <p
        className={cn("text-xl text-muted-foreground", className)}
        {...props}
    >
        {children}
    </p>
)

const Large = ({ children, className, ...props }: TypographyProps) => (
    <div
        className={cn("text-lg font-semibold text-foreground", className)}
        {...props}
    >
        {children}
    </div>
)

const Small = ({ children, className, ...props }: TypographyProps) => (
    <small
        className={cn("text-sm font-medium leading-none text-muted-foreground", className)}
        {...props}
    >
        {children}
    </small>
)

const Muted = ({ children, className, ...props }: TypographyProps) => (
    <span
        className={cn("text-sm text-muted-foreground", className)}
        {...props}
    >
        {children}
    </span>
)

export const Typography = {
    H1,
    H2,
    H3,
    H4,
    P,
    Lead,
    Large,
    Small,
    Muted
}
