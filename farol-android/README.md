# 🚦 Farol — vale a corrida?

App Android gratuito, parecido com o **Rota Pro**: ele lê a oferta de corrida na
tela do **Uber Motorista** e da **99 Motorista** e mostra na hora, por cima do app,
se ela vale a pena — com semáforo **verde / amarelo / vermelho**.

Sem mensalidade, sem cadastro, sem anúncio. Tudo fica no seu celular.

## O que ele mostra

Quando a oferta aparece, um cartão sobe no topo da tela:

```
┌────────────────────────────────────────────────┐  ← borda = veredito geral
│ [Uber]  12,4 km · 22 min                       │
│                                                 │
│ R$/Km      R$/Hora     Nota       Lucro %      │
│ ▌2,00      ▌68         ▌4,92      ▌49          │
└────────────────────────────────────────────────┘
   ↑ vermelho  ↑ amarelo  ↑ verde    ↑ cinza
```

Cada métrica tem **a sua própria barra colorida**. Isso é o ponto: você bate o
olho e sabe *qual* número está puxando a corrida pra baixo, em vez de só receber
um veredito fechado. No exemplo acima o R$/km está ruim, mas o R$/hora está
razoável — é uma corrida curta e bem paga por minuto.

| Métrica | O que é | Fica verde quando |
|---|---|---|
| **R$/Km** | Valor dividido pela distância total | Bate a sua meta de R$/km |
| **R$/Hora** | Valor projetado por hora | Bate a sua meta de R$/hora |
| **Nota** | Nota do passageiro | Igual ou acima da sua mínima |
| **Lucro %** | Quanto sobra do valor depois do combustível | 65% ou mais |

Cores de cada barra: 🟢 bateu a meta · 🟡 chegou perto (a partir de 85% dela)
· 🔴 ficou longe · ⚪ cinza quando não há dado ou você não configurou aquela meta.

A **borda do cartão** dá o veredito geral, e uma linha de alerta aparece embaixo
quando o endereço bate com uma palavra de risco que você cadastrou.

## ⚠️ O que ele NÃO faz

**Não aceita corrida por você.** O card é declarado como "não tocável" no Android —
ele nunca cobre nem intercepta o botão de aceitar. Quem decide é você.

Isso é de propósito: automatizar o aceite viola os termos do Uber e da 99, pode
te render banimento e é perigoso enquanto se dirige.

## 📲 Como instalar

O APK é compilado de graça pelo GitHub Actions. Você não precisa instalar nada no
computador.

1. Vá em **Actions → Farol — build do APK** e espere o build ficar verde
   (roda sozinho a cada push nessa pasta).
2. Baixe o APK. Duas opções:
   - **Releases → `farol-latest` → `farol.apk`** — link direto, mais fácil de abrir no celular
   - ou **Actions → o build → Artifacts → `farol-apk`** (vem zipado)
3. Abra o `farol.apk` no celular e confirme a instalação.
   O Android vai pedir pra **permitir instalação de fontes desconhecidas** — aceite.
4. Abra o Farol e conceda as duas permissões da tela inicial:
   - **Leitura de tela** (Acessibilidade → Apps instalados → Farol → ativar)
   - **Sobrepor outros apps**

> Já tem uma versão instalada? Desinstale antes de atualizar — cada build é
> assinado com uma chave de debug diferente, então o Android recusa a atualização
> por cima.

## ⚙️ Configurando

Abra o app e ajuste:

- **Mínimo por km** e **mínimo por hora** — suas metas. Comece pelo que você já
  considera aceitável hoje e vá calibrando.
- **Nota mínima do passageiro** — deixe vazio pra ignorar.
- **Contar o trecho até o passageiro** — ligado por padrão. Esse km você roda e
  gasta combustível sem receber, então entra na conta.
- **Consumo e preço do litro** — pra calcular o líquido.
- **Locais de risco** — palavras separadas por vírgula. Se aparecerem no endereço
  da oferta, o card acende alerta.

Use o botão **"Testar com uma oferta de exemplo"** pra ver se as suas metas
estão fazendo sentido antes de sair rodando.

## 🔧 Se ele não estiver lendo a oferta

O Uber e a 99 mudam o layout da tela de vez em quando, e aí a leitura pode falhar.
Pra resolver:

1. Ligue o **Modo aprendizado** na tela do Farol.
2. Abra o app de corrida e espere uma oferta aparecer.
3. Volte ao Farol e toque em **"Ver última leitura da tela"**.

Esse texto é exatamente o que o app conseguiu ler. Com ele dá pra ajustar os
padrões em `OfferParser.kt`.

## 🧱 Como funciona por dentro

| Arquivo | Função |
|---|---|
| `RideAccessibilityService.kt` | Lê os textos da tela via `AccessibilityService` e desenha o card |
| `OfferParser.kt` | Extrai valor, km, minutos e nota do texto lido |
| `OfferEvaluator.kt` | Calcula R$/km, R$/h, combustível e decide a cor |
| `Settings.kt` | Suas metas, guardadas no aparelho |
| `MainActivity.kt` | Tela de configuração |

A leitura é feita com dois cuidados de performance: as mudanças de tela são
agrupadas num intervalo de 250 ms, e uma oferta já avaliada não é recalculada
enquanto continuar igual na tela.

Os testes em `app/src/test/` cobrem o parser e o avaliador, e rodam no CI antes
de cada APK ser gerado.

## 🔒 Privacidade

- Nenhuma permissão de internet. O app **não tem** como enviar nada pra lugar nenhum.
- As suas metas ficam em `SharedPreferences`, só no aparelho.
- O modo aprendizado guarda a última leitura localmente, e só enquanto ligado.

## ⚖️ Aviso

Ferramenta de apoio à decisão, de uso pessoal. Ela lê a sua própria tela pra te
dar a conta que o aplicativo não mostra. Apps de sobreposição podem, em tese, ser
detectados pelas plataformas — use por sua conta.
