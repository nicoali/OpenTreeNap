# OpenTreeNap — logo Android e Google Play

Il launcher della versione Android usa le risorse `mipmap-*` per le diverse densità e `mipmap-anydpi-v26` per le icone adattive (Android 8+). Il logo precedente `@drawable/ic_tree` viene sostituito da `@mipmap/ic_launcher` al momento dell'installazione delle nuove risorse.

## Installare il nuovo logo da Android Studio (Windows)

1. Scarica il pacchetto **OpenTreeNap_NUOVO_logo_PlayStore_Android.zip** fornito nel progetto e mettilo in `Download` (o indica un percorso alternativo).
2. In Android Studio, fai `Git → Pull` sul branch `feature/android-sprint5-measurements` per ricevere lo script di installazione.
3. Apri **Terminal** nella cartella `C:\opentreenap\android` ed esegui:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-playstore-logo.ps1 -CommitAndPush
```

Se il file ZIP è in un'altra cartella:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-playstore-logo.ps1 -ZipPath "C:\percorso\OpenTreeNap_NUOVO_logo_PlayStore_Android.zip" -CommitAndPush
```

Lo script:
- verifica che l'archivio contenga le icone richieste;
- copia i PNG e gli XML in `app/src/main/res` senza eliminare le altre risorse;
- modifica `AndroidManifest.xml` per usare `@mipmap/ic_launcher` e `@mipmap/ic_launcher_round`;
- crea un commit Git contenente **solo** le nuove icone e il Manifest e prova a inviarlo su GitHub.

Senza `-CommitAndPush` modifica soltanto il progetto locale. Se il push è rifiutato per autenticazione, i file rimangono sul computer e puoi usare il menu Git di Android Studio.

## Compilare

Esegui `Build → Clean Project`, poi `Build → Generate Signed App Bundle / APK → Android App Bundle → release`. Utilizza **lo stesso certificato upload JKS** già creato; non generare un nuovo certificato.

Il logo 512×512 destinato alla scheda Google Play si carica separatamente nella Play Console; non sostituisce l'icona launcher.

## Sicurezza

Non pubblicare `local.properties`, credenziali HMAC, password, `.jks` o certificati privati. Il solo aggiornamento dell'icona non risolve la presenza delle chiavi legacy `OTM_ACCESS_KEY` / `OTM_SECRET_KEY` nella configurazione Gradle: sistemare il modello di autenticazione **prima di distribuire l'AAB a tester esterni o al pubblico**.
