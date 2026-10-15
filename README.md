# 🛸 Rev Hunter Reborn (Mod / Re-implementation)

<div align="center">
  <img src="public/icon.png" width="128" height="128" alt="Rev Hunter Logo" />
  <br />
  <strong>Re-implementação moderna, aberta e ultra-leve do cliente Rev Hunter VPN</strong>
  <br />
  <em>Desenvolvido com Tauri v2, React 19, Tailwind CSS 4, Shadcn/UI e Rust</em>
  <br /><br />

  [![Tauri v2](https://img.shields.io/badge/Tauri-v2-blue?style=flat-square&logo=tauri)](https://tauri.app/)
  [![React 19](https://img.shields.io/badge/React-19-61dafb?style=flat-square&logo=react)](https://react.dev/)
  [![Vite 8](https://img.shields.io/badge/Vite-8-646cff?style=flat-square&logo=vite)](https://vite.dev/)
  [![Tailwind CSS 4](https://img.shields.io/badge/TailwindCSS-v4-38bdf8?style=flat-square&logo=tailwindcss)](https://tailwindcss.com/)
  [![Rust](https://img.shields.io/badge/Rust-2024-dea584?style=flat-square&logo=rust)](https://www.rust-lang.org/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-purple?style=flat-square)](LICENSE)
</div>

---

## ⚡ Visão Geral

O **Rev Hunter Reborn** foi projetado com o *"espírito de mod"* sobre a engenharia reversa do app original: mantém a identidade visual marcante (Cyber Dark Purple) e a compatibilidade com o protocolo **V2Ray / VLESS**, mas elimina completamente todas as barreiras e limitações impostas pela versão comercial.

### 🔥 Comparativo: Original vs. Reborn

| Recurso | Rev Hunter Original (APK) | Rev Hunter Reborn (Mod) |
|---|---|---|
| **Limite de Tempo** | 5 min iniciais + 2h por anúncio assistido | **Ilimitado (Conexão Contínua 24/7)** |
| **Monetização / Ads** | Appodeal, AdMob, ByteDance, VPAID | **Zero Anúncios / Zero Rastreadores** |
| **Login Google** | Obrigatório em diversos fluxos | **Desnecessário / Totalmente Local** |
| **Tamanho do App** | ~150 MB (APK inchado com SDKs) | **Ultra-leve (~15 MB no Android)** |
| **Servidores** | Fixos do provedor com restrições | **Híbrido: Presets + Subscrições VLESS / JSON** |
| **Ping & Latência** | Checagem básica | **TCP Ping Nativo em Rust com auto-sort** |

---

## 🏗️ Arquitetura do Sistema

```mermaid
graph TD
    subgraph Frontend [React 19 + Shadcn/UI + Tailwind 4]
        UI[Painel Mobile-First] --> Drawer[Seletor de Modos / Servidores]
        UI --> Stats[Telemetria de Tráfego em Tempo Real]
        UI --> SubModal[Importador de Subscrição / JSON]
    end

    subgraph Core [Backend Rust - Tauri v2]
        TauriIPC[Tauri IPC / Handlers] --> Ping[TCP Handshake Direto com Host:Porta]
        TauriIPC --> Tunnel[Gerenciador do Ciclo de Conexão]
    end

    subgraph Network [Camada de Rede]
        Tunnel --> Engine[Túnel VLESS / Xray / SOCKS5]
        Engine <--> RemoteServer[Servidor VPS / Nós Remotos]
    end

    Drawer <-->|invoke: ping_tcp| TauriIPC
    Stats <-->|invoke: stats| TauriIPC
```

---

## 🚀 Como Executar

### Pré-requisitos
* [Node.js](https://nodejs.org/) (versão 20+)
* [Rust](https://www.rust-lang.org/) & Cargo
* Dependências do Tauri para o seu sistema operacional

### 1. Clonar e Instalar Dependências
```bash
git clone https://github.com/marcuth/rev-hunter-client.git
cd rev-hunter-client
npm install
```

### 2. Modo Desenvolvimento

```bash
# Executar no navegador (apenas frontend Vite)
npm run dev

# Executar a aplicação desktop nativa (Tauri + Rust)
npm run tauri dev
```

---

## 📱 Compilando para Android (.APK)

O projeto está pronto para empacotamento Android através do Tauri v2:

```bash
# 1. Inicializa o template Android nativo (apenas na 1ª vez)
npm run tauri android init

# 2. Executa em emulador Android ou celular conectado via USB/ADB
npm run tauri android dev

# 3. Gera o arquivo final .apk assinado para distribuição
npm run tauri android build
```

O arquivo gerado estará localizado em:
`src-tauri/gen/android/app/build/outputs/apk/release/`

---

## 📁 Estrutura de Pastas

```text
rev-hunter-client/
├── public/
│   └── icon.png                  # Logo 3D oficial do Rev Hunter
├── src/
│   ├── components/
│   │   ├── ui/                   # Componentes base Shadcn (button, card, badge, typography)
│   │   ├── connection-button.tsx # Botão central de conexão com efeitos neon
│   │   ├── server-card.tsx       # Card interativo do servidor ativo com badge de ping
│   │   ├── server-list-drawer.tsx# Gaveta de seleção e busca de servidores
│   │   ├── traffic-stats.tsx     # Métricas de velocidade e cronômetro ilimitado
│   │   └── subscription-modal.tsx# Modal para importar links de subscrição
│   ├── schemas/
│   │   └── server.ts             # Validações estritas com Zod v4
│   ├── services/
│   │   └── vpn-service.ts        # Serviço de rede, subscrições e IPC com Rust
│   ├── types/
│   │   └── vpn.ts                # Interfaces TypeScript globais
│   ├── App.tsx                   # Layout principal
│   └── index.css                 # Tokens do tema Hunter Purple e Tailwind 4
└── src-tauri/
    ├── icons/                    # Conjunto completo de ícones para Windows, Android e macOS
    ├── src/
    │   ├── lib.rs                # Comandos Rust nativos (TCP ping, setup de ícone, túnel)
    │   └── main.rs
    ├── Cargo.toml
    └── tauri.conf.json           # Configuração do aplicativo
```

---

## 📜 Licença

Distribuído sob a licença **MIT**. Veja `LICENSE` para mais detalhes.
