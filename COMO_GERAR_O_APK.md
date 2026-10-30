# Como gerar o APK do ISL BLADE

## Opção 1 — Android Studio (no seu computador)
1. Instale o Android Studio (gratuito): https://developer.android.com/studio
2. Abra a pasta `01_PROJETO_ANDROID_STUDIO` (File > Open).
3. Aguarde o "Gradle Sync" terminar (na primeira vez, ele baixa o SDK e as bibliotecas).
4. Menu **Build > Build App Bundle(s) / APK(s) > Build APK(s)**.
5. Clique em **locate** no aviso que aparece. O arquivo é `app/build/outputs/apk/debug/app-debug.apk`.
6. Envie o APK para o celular (WhatsApp, Drive, cabo) e abra. Se o Android pedir, permita "instalar apps desconhecidos".

## Opção 2 — GitHub (sem instalar nada)
1. Crie um repositório no GitHub e envie esta pasta inteira (inclusive a pasta oculta `.github`).
2. A aba **Actions** roda o processo "Gerar APK" automaticamente a cada envio.
3. Ao terminar (cerca de 5 minutos), abra a execução e baixe o artefato **ISL-BLADE-apk**.

## Observações
- Esse é um APK de teste (debug), assinado com uma chave de desenvolvimento. Serve para instalar e testar, não para publicar na Play Store.
- Login com Google: no Firebase (projeto `isl-blade`), cadastre a impressão digital SHA-1 da chave usada no build. Sem isso, o login não funciona; o restante do app funciona normalmente.
- Para publicar na Play Store, gere um AAB assinado (Build > Generate Signed App Bundle) com uma chave sua e guarde essa chave em lugar seguro.
- O ícone fica em `app/src/main/res/mipmap-*`. O `ic_launcher-playstore.png` (512×512) é o ícone para a página da Play Store.
