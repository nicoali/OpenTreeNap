# Sprint 3 — Immagini botaniche condivise

Obiettivo: usare una sola associazione specie -> immagine rappresentativa tra OpenTreeNap web e app Android.

## Fonte immagini

Le immagini rappresentative restano ospitate su:

`https://opentreenap.altervista.org/`

Il manifest condiviso è:

`opentreemap/treemap/static/botanical-images.json`

Dopo il deploy OTN viene pubblicato come:

`/static/botanical-images.json`

Il manifest contiene, per ogni specie:

- nome scientifico;
- URL esatto dell'immagine WordPress/Altervista;
- URL della scheda botanica pubblica.

## Gerarchia immagini

1. Foto reale dell'albero caricata in OTN.
2. Immagine rappresentativa della specie dal manifest.
3. Placeholder OpenTreeNap generico.

Questa gerarchia viene usata sia dall'app sia da OTN.

## Prime associazioni

- `Pinus pinea` -> immagine rappresentativa WordPress fornita per la specie.
- `Quercus ilex` -> immagine rappresentativa WordPress fornita per la specie.

Le altre specie verranno aggiunte allo stesso manifest senza modificare il codice Android.

## Android

Versione Sprint 3: `0.5.0`.

- Coil per caricamento/caching immagini HTTPS;
- manifest botanico scaricato da OTN e salvato in cache locale;
- hero image nella scheda albero;
- badge "Foto albero" quando esiste una foto reale OTN;
- badge "Immagine specie" quando viene usato il fallback botanico;
- placeholder OTN se nessuna immagine è disponibile;
- la pagina botanica usa l'URL del manifest quando presente.

## OTN web

Quando un albero non ha foto reali:

- il carosello foto usa l'immagine rappresentativa della specie;
- OpenGraph/social sharing usa la stessa immagine;
- se la specie non è presente nel manifest resta il placeholder storico.

## Aggiornamento automatico da WordPress

La fonte master è ora direttamente `opentreenap.altervista.org`.

Endpoint canonico:

`https://opentreenap.altervista.org/wp-json/opentreenap/v1/botanical-images`

Il plugin WordPress genera il manifest dalla Media Library usando il naming `<specie>-immagine-rappresentativa`.

OTN consuma il manifest remoto con cache resiliente. Android 0.5.1 consuma lo stesso endpoint con ETag e cache locale.

Quindi una nuova immagine rappresentativa diventa disponibile in sito, OTN e app senza nuova APK.

## Test Sprint 3

1. Attivare il plugin WordPress su `opentreenap.altervista.org`.
2. Aprire l'endpoint REST e verificare le specie rilevate.
3. Deploy OTN per attivare il consumo remoto.
4. Aprire `Pinus pinea` e `Quercus ilex` nell'app.
5. Sostituire una rappresentativa su WordPress e verificare l'aggiornamento dopo refresh/cache TTL.
6. Verificare una foto reale OTN: deve avere precedenza sull'immagine specie.


## Verifica endpoint WordPress live

Verificato il 2026-10-02 dopo l'attivazione del plugin su `opentreenap.altervista.org`.

L'endpoint:

`/wp-json/opentreenap/v1/botanical-images`

risponde con schema v2 e rileva automaticamente 15 specie dalla Media Library, tra cui `Pinus pinea`, `Quercus ilex`, `Cedrus libani`, `Celtis australis`, `Magnolia grandiflora` e altre.

È verificata anche la selezione automatica della rappresentativa più recente: per `Pinus pinea` sono presenti più immagini rappresentative e il manifest sceglie quella modificata più recentemente, mantenendo le altre in `gallery`.

Il passaggio WordPress → manifest automatico è quindi operativo.


## Verifica end-to-end Android ↔ WordPress

Test reale del 2026-10-02:

- `Platanus occidentalis`, specie non inserita manualmente nel vecchio manifest statico, viene rilevata dal manifest WordPress live;
- l'app Android 0.5.1 riceve automaticamente la rappresentativa dalla Media Library;
- l'immagine viene mostrata con layout adattivo (fit-center + backdrop della stessa sorgente);
- badge `Immagine specie`, nome comune, nome scientifico, indirizzo, ID e azione botanica restano visibili.

Questo conferma il flusso automatico WordPress → manifest REST → Android senza nuova APK per aggiungere una specie.


## Test foto reale rinviato

Al 2026-10-02 non sono ancora presenti foto reali caricate sugli alberi OTN da usare per una verifica end-to-end.

La priorità è già implementata:

`foto reale albero > immagine rappresentativa specie > placeholder`.

Il test resta esplicitamente aperto e dovrà essere eseguito appena nello Sprint 4 o 5 sarà disponibile almeno un albero con foto reale.
