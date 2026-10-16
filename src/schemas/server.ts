import { z } from "zod"

export const ServerSchema = z.object({
    id: z.string().min(1),
    name: z.string().min(1),
    host: z.string().min(1),
    port: z.number().int().min(1).max(65535),
    protocol: z.enum(["vless", "vmess", "trojan", "socks5", "ssh"]),
    uuid: z.string().optional(),
    encryption: z.string().optional(),
    flow: z.string().optional(),
    tls: z.boolean().optional(),
    countryCode: z.string().optional(),
    flag: z.string().optional(),
    user: z.string().optional(),
    pass: z.string().optional()
})

export const ServerListSchema = z.array(ServerSchema)

export const SubscriptionUrlSchema = z.string().url("URL de subscrição inválida")
