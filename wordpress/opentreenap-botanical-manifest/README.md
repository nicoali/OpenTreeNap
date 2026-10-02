# OpenTreeNap Botanical Manifest — WordPress

Questo plugin rende `opentreenap.altervista.org` la fonte unica delle immagini botaniche usate da:

- sito botanico;
- OpenTreeNap / OTN web;
- app Android.

## Endpoint

Dopo l'attivazione:

`/wp-json/opentreenap/v1/botanical-images`

Il manifest viene generato direttamente dalla Media Library.

## Convenzione file

### Immagine rappresentativa

Nome obbligatorio:

`<specie>-immagine-rappresentativa.webp`

Sono ammessi suffissi numerici:

`pinus-pinea-immagine-rappresentativa-1.webp`

`quercus-ilex-immagine-rappresentativa-2.webp`

Se esistono più immagini rappresentative della stessa specie, viene scelta automaticamente quella modificata più di recente.

### Galleria futura

Sono già riconosciuti:

- `<specie>-foglia.webp`
- `<specie>-frutto.webp`
- `<specie>-fiore.webp`
- `<specie>-corteccia.webp`
- `<specie>-portamento.webp`
- `<specie>-galleria-1.webp`

Questi file compaiono nel campo `gallery` del manifest.

## Nome scientifico

Per le normali specie binomiali viene ricavato automaticamente dal nome file:

`quercus-ilex` -> `Quercus ilex`

Per ibridi, sottospecie, cultivar o casi particolari è disponibile nella Media Library il campo:

`OTN nome scientifico`

## Aggiornare una foto

Per sostituire la rappresentativa:

1. caricare una nuova immagine con lo stesso prefisso specie e `-immagine-rappresentativa`;
2. il plugin sceglie automaticamente la più recente;
3. il manifest cambia versione;
4. app Android e OTN aggiornano il catalogo senza nuova APK.

Gli URL includono un parametro `?v=<timestamp>` per invalidare le cache immagine quando il file viene sostituito.

## Cache

- WordPress REST: 5 minuti;
- OTN: cache resiliente 5 minuti con stale fallback;
- Android: cache locale + ETag HTTP;
- Coil: cache immagine basata sull'URL versionato.

## Installazione

Opzione plugin:

1. copiare la cartella `wordpress/opentreenap-botanical-manifest/` in `wp-content/plugins/`;
2. attivare **OpenTreeNap Botanical Manifest**.

In alternativa il contenuto PHP può essere inserito in un plugin custom/snippet, mantenendo il codice identico.

## Test

Aprire:

`https://opentreenap.altervista.org/wp-json/opentreenap/v1/botanical-images`

e verificare che il JSON contenga almeno le specie per cui esiste una immagine con naming `-immagine-rappresentativa`.
