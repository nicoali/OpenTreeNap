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

## Aggiornamento futuro

Quando viene pubblicata una nuova immagine rappresentativa sul sito:

1. aggiungere/aggiornare una sola voce in `botanical-images.json`;
2. effettuare il deploy OTN;
3. app Android e OTN useranno automaticamente la nuova associazione.

Non è necessario pubblicare una nuova APK per aggiungere una specie al manifest.

## Test Sprint 3

1. Deploy OTN con manifest e template aggiornati.
2. Aprire un `Pinus pinea` senza foto reale: deve apparire l'immagine rappresentativa.
3. Aprire un `Quercus ilex` senza foto reale: stessa verifica.
4. Aprire un albero con foto reale OTN: la foto reale deve avere precedenza.
5. Aprire una specie non ancora nel manifest: placeholder generico.
6. Verificare lo stesso comportamento nella pagina dettaglio OTN.
7. Riavviare l'app: manifest e immagini già viste devono beneficiare della cache.


## Verifica parziale su dispositivo

Test reale del 2026-10-02:

- `Quercus ilex` senza foto reale carica correttamente l'immagine rappresentativa WordPress;
- badge `Immagine specie` visibile;
- dopo la correzione del re-layout, immagine, nome comune, nome scientifico, indirizzo, ID e azioni restano tutti visibili;
- quando l'utente non è autenticato, `Modifica` resta correttamente nascosto e `Scheda botanica` occupa tutta la larghezza;
- il comportamento con foto reale OTN e il popolamento completo del manifest restano da verificare.


## Verifica resa hero

Test visivo su dispositivo del 2026-10-02:

- la stessa immagine rappresentativa di `Quercus ilex` usata dal sito viene mostrata anche nell'app;
- foreground in fit-center: chioma e tronco restano interamente visibili;
- backdrop ricavato dalla stessa identica immagine, senza introdurre una seconda sorgente;
- nessuna deformazione dell'immagine;
- badge `Immagine specie` leggibile e non invasivo;
- il resto della scheda (nome, specie, indirizzo, ID, azioni) rimane visibile.

Questa presentazione viene considerata il layout di riferimento per le immagini botaniche nell'app.
