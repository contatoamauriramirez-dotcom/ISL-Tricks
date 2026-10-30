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

## Atualização — 29/10/2026
- **Corrigido:** as famílias de Grinds (Soul, Acid, Makio etc.) não abriam ao toque. O botão gerava um `onclick` com aspas quebradas (`JSON.stringify` dentro de atributo HTML), causando erro de JavaScript. Agora usa `data-cat` + `openFamily()`.
- **Novo — níveis de dificuldade:** cada manobra é classificada em Iniciante, Intermediário, Avançado ou Expert. Filtro por nível na aba Grinds; dentro de cada família as variações aparecem agrupadas do nível mais fácil para o mais difícil.
  - A regra fica no bloco "Níveis de dificuldade" do HTML: `FAMILY_BASE` (nota 1–3 por família), `LEVEL_MODS` (peso de Alley-oop, Truespin, 360 Out, Blind, 450 etc.) e `LEVEL_OVERRIDES` para forçar o nível de uma manobra específica.
- **Novo — desafios semanais:** botão "🏆 Desafios da semana" na aba Grinds. São 4 desafios por nível (2 de aprender, 1 de switch, 1 de consistência), que mudam toda segunda-feira e são iguais para todos os usuários (sorteio com semente da semana ISO). Pontos: 10/20/35/50 por desafio conforme o nível. O nível sugerido é o primeiro em que o usuário domina menos de 60% das manobras. Ao concluir, a manobra (ou o switch) é marcada no progresso. Dados em `localStorage` (`isl-weekly-challenges`).

## Atualização — ilustrações de encaixe
- Cada manobra mostra uma ilustração (SVG gerado no próprio app) do patinador com camiseta ISL, com o encaixe de cada pé destacado em vermelho, etiquetas FRENTE/TRÁS e um mapa "visto de cima". O usuário alterna entre Corrimão e Caixote, e a escolha fica salva.
- Abaixo da ilustração aparece o painel "Encaixe dos pés", com a descrição de cada pé e o que a variação (Alley-oop, 360 Out etc.) muda.
- As posições ficam na tabela `GRIND_POSES` (bloco "Ilustrações de encaixe" do HTML). Para corrigir uma família, troque o contato de `f` (pé da frente) ou `b` (pé de trás): groove, soul, neg, top, frame, stub, wheels ou lift. Também existem as opções `rev`, `crossed`, `cross` e `back`.
- Famílias marcadas com `check:true` ainda precisam ser confirmadas pela equipe ISL.
- A foto do usuário (📷) agora aparece abaixo da ilustração, em vez de substituí-la. A imagem de referência do Tea Kettle foi mantida.
- Revisão dos encaixes com base nas descrições do site Book of Grinds (bookofgrinds.com). Os desenhos continuam sendo ilustrações originais do app; nenhuma imagem do site foi copiada. Ainda a confirmar: Makio Freestyle, Christ Fish, Topside X-grind, Fs/Bs Torque, Fs/Bs Fastslide, Fs/Bs Tabernacle, Fs/Bs Pudslide, Fs/Bs Cowboy e Barrel Wheel.

## Atualização — novo layout (tema 2026)
- Visual repaginado: fundo escuro tipo asfalto, vermelho ISL como cor de ação, superfícies sem bordas, cantos arredondados e botões em pílula.
- Tipografia: Archivo, embutida no app (funciona sem internet). Títulos em versão expandida.
- Progresso exibido como um "rail", com o cursor deslizando conforme a evolução.
- Navegação inferior com ícones de linha; o nome da aba Home aparece como "Início" (o código interno continua usando "Home").
- O tema fica no último bloco `<style>` ("ISL BLADE · Tema 2026"), com as cores e os raios em variáveis CSS no `:root`.
- Tema v2: fundo preto, cartões grandes arredondados, imagem de destaque na aba Grinds (arte ISL do Tea Kettle) com indicadores sobrepostos, cartões de estatística com ícone em círculo, abas em texto grande (Manobras / Desafios), listas agrupadas com ícones e setas no Perfil, e navegação inferior com o ícone ativo preenchido em vermelho. As cores ficam em `:root`, no bloco "Tema 2026 v2".
- Ícone do app com o logo ISL (adaptativo para Android 8+, mais as versões quadrada e redonda). Gradle wrapper adicionado (`gradlew`). Veja `COMO_GERAR_O_APK.md` e o workflow `.github/workflows/build-apk.yml`.
