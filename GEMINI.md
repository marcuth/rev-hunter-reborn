# 🛸 Rev Hunter Reborn - Diretrizes do Projeto & Instruções de IA

Este arquivo serve como contexto de alto nível e guia operacional para agentes de IA (Antigravity/Gemini) e desenvolvedores trabalhando no repositório **Rev Hunter Reborn**.

---

## 📌 Visão Geral do Projeto

O **Rev Hunter Reborn** é uma re-implementação moderna, de código aberto e ultra-leve do cliente de VPN Rev Hunter, desenvolvida com base em engenharia reversa do APK original. O projeto mantém a identidade visual marcante (Cyber Dark Purple) e suporte ao protocolo **V2Ray / VLESS / SOCKS5**, mas remove 100% dos limites de tempo, anúncios invasivos e dependência de serviços externos proprietários.

---

## 🛠️ Stack Tecnológica

| Camada | Tecnologia | Detalhes |
|---|---|---|
| **Frontend** | React 19 + TypeScript + Vite 8 | UI mobile-first reativa |
| **Estilização** | Tailwind CSS v4 + Lucide Icons | Estilo Cyber Dark Purple |
| **Core Desktop & IPC** | Tauri v2 (Rust 2024 Edition) | IPC assíncrono e handlers de ping/túnel |
| **VPN Engine** | Rust `tun2proxy` + Tokio | Roteamento de pacotes TUN (camada 3) para proxy SOCKS5/HTTP |
| **Camada Nativa Android** | Kotlin + Android `VpnService` | Criação de interface `tun0`, permissão nativa do SO e Foreground Service |

---

## ⚙️ Regras do Ambiente de Execução

1. **Sistema Operacional do Desenvolvedor**: Windows 10 (PowerShell).
2. **Separador de Comandos no Shell**:
   - **NUNCA use `&&`** para encadear comandos no terminal.
   - **SEMPRE use `;`** para separar múltiplos comandos (ex: `cmd1; cmd2`).
3. **Paths e Diretórios**:
   - O projeto reside em `c:\Users\Marcuth\Downloads\Rev+Hunter+-+VPN_47_APKPure\rev-hunter-client`.
   - Modificações devem sempre ser restritas aos limites do repositório.

---

## 🏗️ Arquitetura de Tunelamento VPN

```
[ Frontend React / UI ]
       │  (Tauri IPC invoke: 'start_vpn_tunnel')
       ▼
[ Core Tauri em Rust (lib.rs) ]
       │  (Plugin IPC: 'startVpn')
       ▼
[ VpnPlugin.kt (Kotlin) ] ──> VpnService.prepare(activity) [Diálogo do Android]
       │
       ▼ (startForegroundService)
[ HunterVpnService.kt ]
       ├── Criação da interface TUN: builder.establish() -> fd
       ├── addDisallowedApplication(packageName) [Evita loop de roteamento]
       └── ServiceCompat.startForeground (FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
       │
       ▼ (retorna o 'fd' do TUN de volta para o Rust)
[ tun2proxy::general_run_async(tun_fd, token) ]
       └── Envia o tráfego do túnel para o servidor proxy remoto
```

---

## 📦 Comandos Essenciais

### Desenvolvimento Frontend
```powershell
npm run dev
```

### Build do Frontend
```powershell
npm run build
```

### Build do APK Android (Local)
Para evitar disputas de travas de arquivo no cache global do Gradle no Windows, sempre utilize um diretório de cache dedicado:
```powershell
$env:GRADLE_USER_HOME = "$pwd\src-tauri\gen\android\.gradle_home"; npx tauri android build --target aarch64 --apk
```

O APK assinado será gerado em:
- `src-tauri\gen\android\app\build\outputs\apk\universal\release\app-universal-release.apk`

---

## 🚀 Integração Contínua (CI/CD)

O repositório possui uma action configurada em `.github/workflows/build-release.yml` para compilar e assinar automaticamente o APK `aarch64` sob demanda via `workflow_dispatch`:
- Instala apenas o target `aarch64-linux-android`.
- Assina o APK gerado com chaves RSA via `apksigner` com suporte a schemes v1, v2 e v3.
- Publica o binário na aba de **Releases** do GitHub.

---

## 🛡️ Boas Práticas & Regras Importantes

1. **Android 14+ Compatibilidade**:
   - Todo Foreground Service com notificação contínua no Android 14 (`targetSdk 34+`) precisa ter `foregroundServiceType` declarado no `AndroidManifest.xml` e ser chamado com `ServiceCompat.startForeground` informando o mesmo tipo.
2. **Assinatura de APK**:
   - O Android rejeita a instalação de APKs com status `unsigned` informando que o *"pacote parece ser inválido"*. Todo APK de release deve passar por assinatura (v1 + v2).
3. **Concorrência em File Descriptors**:
   - O descritor da interface TUN criado pelo `VpnService` deve ser manipulado exclusivamente pelo motor de tunelamento (`tun2proxy` em Rust).
