import { LuPower, LuShieldCheck, LuLoader } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { VpnConnectionStatus } from "@/types/vpn"
import { cn } from "@/lib/utils"

interface ConnectionButtonProps {
    status: VpnConnectionStatus
    onToggle: () => void
}

export const ConnectionButton = ({ status, onToggle }: ConnectionButtonProps) => {
    const isConnected = status === "connected"
    const isConnecting = status === "connecting"
    const isDisconnecting = status === "disconnecting"

    return (
        <div className="flex flex-col items-center justify-center py-6">
            <div className="relative flex items-center justify-center">
                {/* Outer Glow Ring */}
                <div
                    className={cn(
                        "absolute -inset-4 rounded-full blur-xl transition-all duration-700 opacity-60",
                        isConnected
                            ? "bg-gradient-to-tr from-purple-600 via-fuchsia-500 to-indigo-500 opacity-80 animate-pulse"
                            : isConnecting || isDisconnecting
                            ? "bg-amber-400/50 animate-spin"
                            : "bg-purple-950/40"
                    )}
                />

                {/* Cyber Rotating Ring */}
                <div
                    className={cn(
                        "absolute -inset-1 rounded-full border border-dashed transition-all duration-1000",
                        isConnected
                            ? "border-purple-400/70 animate-spin [animation-duration:12s]"
                            : isConnecting
                            ? "border-amber-400/80 animate-spin [animation-duration:3s]"
                            : "border-purple-900/60"
                    )}
                />

                {/* Main Action Button */}
                <button
                    onClick={onToggle}
                    disabled={isConnecting || isDisconnecting}
                    className={cn(
                        "relative flex h-40 w-40 flex-col items-center justify-center rounded-full border-2 transition-all duration-300 shadow-2xl cursor-pointer active:scale-95 disabled:opacity-80",
                        isConnected
                            ? "border-purple-400 bg-gradient-to-b from-purple-900/90 to-slate-950 text-purple-200 shadow-purple-500/40 glow-hunter-lg"
                            : isConnecting || isDisconnecting
                            ? "border-amber-400 bg-gradient-to-b from-amber-950/80 to-slate-950 text-amber-300 shadow-amber-500/20"
                            : "border-purple-900/80 bg-gradient-to-b from-purple-950/50 to-slate-950 text-purple-300/70 hover:border-purple-500/70 hover:text-purple-300"
                    )}
                >
                    <div className="flex flex-col items-center space-y-2">
                        {isConnecting || isDisconnecting ? (
                            <LuLoader className="h-14 w-14 animate-spin text-amber-400" />
                        ) : isConnected ? (
                            <LuShieldCheck className="h-14 w-14 text-purple-400 drop-shadow-[0_0_12px_rgba(168,85,247,0.9)]" />
                        ) : (
                            <LuPower className="h-14 w-14 transition-transform duration-300 group-hover:scale-110" />
                        )}
                        <Typography.Small className={cn(
                            "font-bold uppercase tracking-widest text-[11px]",
                            isConnected ? "text-purple-300" : isConnecting ? "text-amber-400" : "text-purple-400/80"
                        )}>
                            {isConnected ? "PROTEGIDO" : isConnecting ? "CONECTANDO" : isDisconnecting ? "PARANDO" : "INICIAR"}
                        </Typography.Small>
                    </div>
                </button>
            </div>

            {/* Status description */}
            <div className="mt-6 flex items-center space-x-2">
                <div
                    className={cn(
                        "h-2.5 w-2.5 rounded-full transition-all duration-300",
                        isConnected
                            ? "bg-purple-400 shadow-[0_0_10px_#c084fc]"
                            : isConnecting
                            ? "bg-amber-400 animate-ping"
                            : "bg-purple-900"
                    )}
                />
                <Typography.Muted className="text-xs font-medium uppercase tracking-wider text-purple-200/80">
                    {isConnected
                        ? "Túnel VLESS Ativo • Sem Limite"
                        : isConnecting
                        ? "Estabelecendo Handshake..."
                        : "Desconectado • Pronto para conectar"}
                </Typography.Muted>
            </div>
        </div>
    )
}
