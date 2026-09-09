# 🛵 Rota Livre — Otimizador de Rotas (grátis)

App web (PWA) parecido com o **Rota Pró**: você adiciona os endereços das entregas,
ele calcula a **melhor ordem** pra fazer o menor caminho, e você navega parada por
parada no **Google Maps** ou no **Waze**. Sem cadastro, sem mensalidade, sem chave de API.

## ✨ O que ele faz

- **Adicionar paradas** por endereço, por `latitude,longitude` ou colando um link do Google Maps
- **Colar uma lista** de vários endereços de uma vez (um por linha)
- **Ponto de partida** = sua localização atual (GPS)
- **Otimizar a rota** (vizinho mais próximo + 2-opt) pra reduzir a distância
- **Navegar** cada parada no **Google Maps** ou **Waze**, ou abrir a **rota completa** no Google Maps
- **Marcar como entregue**, reordenar, adicionar observação por parada
- **Funciona offline** e **instala na tela inicial** como um app de verdade
- Seus dados ficam **só no seu celular** (localStorage) — nada é enviado pra ninguém

> A busca de endereço usa o **OpenStreetMap / Nominatim** (grátis). Ele tem limite de
> ~1 busca por segundo — por isso a lista em massa vai adicionando uma de cada vez.

## 📲 Como rodar no celular

### Opção 1 — Publicar no GitHub Pages (recomendado, é de graça)

1. No GitHub, entre em **Settings → Pages**.
2. Em **Source**, escolha **Deploy from a branch**.
3. Selecione a branch onde está este código e a pasta **/ (root)**, salve.
4. Aguarde ~1 min. O link vai aparecer, algo como:
   `https://SEU-USUARIO.github.io/n8n-automation-portfolio/rota-livre/`
5. Abra esse link no navegador do celular → menu do navegador → **"Adicionar à tela inicial"**.

Precisa ser um endereço **https** (o GitHub Pages já é) pra liberar o GPS e a busca de endereços.

### Opção 2 — Testar rápido pelo computador

```bash
cd rota-livre
python3 -m http.server 8000
# abra http://localhost:8000 no navegador
```

Para testar no celular pela mesma rede Wi-Fi, use o IP do computador
(ex.: `http://192.168.0.10:8000`). O GPS pode pedir https em alguns navegadores.

## 🕹️ Como usar

1. Toque no chip **📍 localização** no topo pra definir de onde você sai.
2. Adicione as paradas (aba **Uma parada** ou **Colar lista**).
3. Toque em **⚡ Otimizar** — ele reordena as paradas na melhor sequência.
4. Toque em **Google Maps** / **Waze** em cada parada, ou em **🗺️ Rota** pra abrir tudo.
5. Marque **✅ Entregue** conforme for concluindo.

> Toque no ícone 🛵 (canto superior) pra abrir o menu: limpar tudo, instalar o app
> ou carregar uma **rota de exemplo**.

## 🧱 Estrutura

| Arquivo | Função |
|---|---|
| `index.html` | O app inteiro (interface + lógica) |
| `manifest.webmanifest` | Metadados do PWA (nome, ícone, cor) |
| `sw.js` | Service worker (funcionar offline) |
| `icon.svg` / `icon-maskable.svg` | Ícones do app |

## ⚠️ Observações

- O Google Maps abre uma rota com até ~10 paradas por vez pela URL. Acima disso,
  faça em blocos (marque as entregues e otimize de novo).
- O Waze navega uma parada por vez (limitação do próprio Waze via link).
- A otimização usa distância em linha reta (haversine) — ótima pra ordenar as paradas,
  mas o trajeto real quem calcula é o Google Maps/Waze.
