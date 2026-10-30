# ISL BLADE — Pacote para continuidade do desenvolvimento no Cloud

## Estrutura
- `01_PROJETO_ANDROID_STUDIO/`: projeto Android (WebView, Java, Gradle, Firebase e recursos locais).
- `02_VERSAO_WEB/ISL_BLADE.html`: versão HTML para abrir e modificar em editor web.
- `03_DOCUMENTACAO/`: contexto, instruções e pendências.

## Estado do aplicativo
- Abas na ordem: Home, Grinds, Pistas, Eventos e Perfil.
- Home: notícias/artigos; editor local de conteúdo de teste.
- Grinds: famílias e variações em ordem alfabética, Fs/Bs, marcações Normal e Switch.
- B.L.A.D.E.: modo de jogo com participantes e letras.
- Pistas: a versão mais recente substitui o mapa embutido por abertura externa, para evitar travamentos no WebView.
- Eventos e perfil: funcionalidades locais existentes.
- Login Google: ponte nativa em `MainActivity.java` e configuração Firebase em `app/google-services.json`.
- Firestore: dependência e regras iniciais presentes, mas perfis/curtidas NÃO estão integrados ponta a ponta à nuvem.

## Como continuar no Cloud
1. Envie o ZIP completo ao ambiente de desenvolvimento Cloud, se ele aceitar projetos compactados.
2. Para editar só a interface, comece em `02_VERSAO_WEB/ISL_BLADE.html`.
3. Para gerar um APK, importe a pasta `01_PROJETO_ANDROID_STUDIO` no Android Studio e faça o sync do Gradle.
4. O projeto de origem NÃO contém `gradlew.bat` nem o wrapper JAR. Será necessário configurar o Gradle localmente ou gerar um wrapper compatível.
5. Para publicar, gere AAB assinado, configure SHA da chave de publicação no Firebase e valide permissões, política de privacidade e fluxos de login.

## Pendências importantes
- Verificar o comportamento de Grinds e Pistas em Android real; correções anteriores não foram validadas em dispositivo.
- Testar compilação Java/Gradle e login Google; o projeto não foi compilado aqui.
- Implementar persistência real no Firestore para artigos, curtidas e perfis, com regras administrativas adequadas.
- Garantir sincronização de progresso entre dispositivos e migração dos dados locais.
- Revisar o código e permissões do WebView antes de produção.

## Firebase
Projeto: `isl-blade` | Pacote Android: `com.itu.isl`.
O arquivo `google-services.json` é de configuração cliente, não é uma chave de administrador. Não compartilhe chaves privadas ou arquivos de conta de serviço.
