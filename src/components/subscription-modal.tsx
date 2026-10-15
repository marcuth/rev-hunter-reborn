import * as React from "react"
import { LuX, LuDownload, LuLink, LuCircleAlert } from "react-icons/lu"
import { Typography } from "@/components/ui/typography"
import { Button } from "@/components/ui/button"
import { SubscriptionUrlSchema } from "@/schemas/server"
import { VpnServer } from "@/types/vpn"
import { VpnService } from "@/services/vpn-service"

interface SubscriptionModalProps {
    isOpen: boolean
    onClose: () => void
    onServersImported: (newServers: VpnServer[]) => void
}

export const SubscriptionModal = ({
    isOpen,
    onClose,
    onServersImported
}: SubscriptionModalProps) => {
    const [url, setUrl] = React.useState("")
    const [isLoading, setIsLoading] = React.useState(false)
    const [errorMessage, setErrorMessage] = React.useState<string | null>(null)

    if (!isOpen) return null

    const handleImport = async () => {
        setErrorMessage(null)

        const validation = SubscriptionUrlSchema.safeParse(url.trim())
        if (!validation.success) {
            setErrorMessage("Por favor, insira uma URL válida (ex: https://.../sub ou https://.../servers.json)")
            return
        }

        setIsLoading(true)
        try {
            const servers = await VpnService.fetchSubscriptionList(url.trim())
            onServersImported(servers)
            setUrl("")
            onClose()
        } catch (err: unknown) {
            if (err instanceof Error) {
                setErrorMessage(err.message)
            } else {
                setErrorMessage("Falha ao sincronizar lista de servidores")
            }
        } finally {
            setIsLoading(false)
        }
    }

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-md p-4">
            <div className="w-full max-w-sm rounded-3xl border border-purple-900/60 bg-slate-950 p-6 shadow-2xl">
                {/* Header */}
                <div className="flex items-center justify-between pb-3">
                    <div className="flex items-center space-x-2">
                        <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-500/15 text-purple-400">
                            <LuLink className="h-4 w-4" />
                        </div>
                        <Typography.H3 className="text-base font-bold text-white">
                            Importar Servidores
                        </Typography.H3>
                    </div>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={onClose}
                        className="h-8 w-8 text-purple-400 hover:text-white"
                    >
                        <LuX className="h-4 w-4" />
                    </Button>
                </div>

                <Typography.P className="text-xs text-purple-300/70 leading-relaxed mt-1">
                    Cole uma URL de subscrição padrão V2Ray/VLESS ou um endpoint JSON customizado com a lista de servidores.
                </Typography.P>

                {/* Input Field */}
                <div className="mt-4 space-y-2">
                    <input
                        type="url"
                        placeholder="https://exemplo.com/sub/vless ou /servers.json"
                        value={url}
                        onChange={(e) => {
                            setUrl(e.target.value)
                            setErrorMessage(null)
                        }}
                        className="w-full rounded-xl border border-purple-900/50 bg-purple-950/40 py-3 px-3.5 text-xs text-white placeholder-purple-400/40 focus:border-purple-500 focus:outline-hidden"
                    />

                    {errorMessage && (
                        <div className="flex items-center space-x-1.5 text-red-400 mt-2">
                            <LuCircleAlert className="h-3.5 w-3.5 shrink-0" />
                            <Typography.Small className="text-[11px] text-red-400">
                                {errorMessage}
                            </Typography.Small>
                        </div>
                    )}
                </div>

                {/* Action Buttons */}
                <div className="mt-6 flex space-x-2.5">
                    <Button
                        variant="outline"
                        onClick={onClose}
                        className="flex-1 rounded-xl text-xs h-10 border-purple-900/60 text-purple-200"
                    >
                        <Typography.Small className="text-purple-200 font-semibold">Cancelar</Typography.Small>
                    </Button>
                    <Button
                        variant="hunter"
                        onClick={handleImport}
                        disabled={isLoading || !url.trim()}
                        className="flex-1 rounded-xl text-xs h-10 space-x-1.5"
                    >
                        <LuDownload className="h-4 w-4" />
                        <Typography.Small className="text-white font-bold">
                            {isLoading ? "Buscando..." : "Sincronizar"}
                        </Typography.Small>
                    </Button>
                </div>
            </div>
        </div>
    )
}
