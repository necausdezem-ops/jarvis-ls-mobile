# J.A.R.V.I.S. LS Mobile

Projeto Android nativo para o assistente J.A.R.V.I.S. Mobile, com interface sci-fi, comandos locais, voz e integração experimental com Gemini.

## Gerar APK automaticamente
Cada push para `main) ou execução manual dispara GitHub Actions. Abra **Actions → Build J.A.R.V.I.S. Mobile APK → run concluído → Artifacts** e baixe `jarvis-ls-mobile-debug-apk`. O arquivo é um APK de depuração, adequado para testes.

## Desenvolvimento local
- Android Studio
- JDK 17
- Android SDK Platform 35

Abra o repositório no Android Studio e execute a configuração `app`.

## Estado atual
- UI Compose com HUD animado
- Conversa por texto
- Reconhecimento de voz do Android e síntese de fala
- Comandos locais básicos (hora, bateria e abrir configurações)
- Configuração opcional da chave Gemini mantida somente em memória
- Workflow de compilação automática do APK

O modo local é um conjunto limitado de comandos, não um LLM offline. O reconhecimento de fala pode depender de serviços do dispositivo/rede. Não publique chaves pessoais em repositórios nem distribua APKs com segredos embutidos.
