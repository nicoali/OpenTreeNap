# Android Sprint 4 — Scheda albero completa

Branch Android: `feature/android-sprint4-tree-detail`

Backend: `rebrand/opentreenap-it-en`

Versione Android: `0.6.0`

## Implementazione corrente

### Metadata OTN

Il dettaglio API espone un blocco stabile `mobile_meta` con:

- `updated_at`;
- `updated_by`;
- UDF scalari di albero;
- UDF scalari del sito/plot;
- URL pubblico della scheda OTN.

Il payload compatto della mappa non cambia: questi dati vengono caricati solo al tap dell'albero.

### Scheda Android

La scheda principale mantiene il layout compatto e aggiunge:

- ultimo aggiornamento e autore, quando disponibili;
- azione **Dati**;
- azione **Vai** per aprire le indicazioni;
- azione **Condividi**;
- azione **QR**.

### Dati completi

Il pannello **Dati** mostra:

- stato normale/monumentale;
- indirizzo;
- ID sito OTN;
- ID albero OTN;
- Custom ID / MASAF quando presente;
- DBH;
- altezza;
- ultimo aggiornamento/autore;
- UDF rilevanti disponibili.

Le UDF dell'albero hanno precedenza sulle UDF del plot con lo stesso nome. `Monumentale` non viene duplicato perché è già rappresentato dal badge/stato.

### Condivisione e navigazione

- **Vai** usa Google Maps Navigation quando disponibile, con fallback geo URI;
- **Condividi** usa il link pubblico OTN `/<istanza>/features/<plot_id>/`;
- **QR** genera localmente un QR code dello stesso link pubblico, senza servizi esterni.

## Test su dispositivo

1. Aprire un albero normale.
2. Verificare che la scheda resti leggibile con la nuova riga di azioni.
3. Attendere il dettaglio lazy e verificare ultimo aggiornamento/autore.
4. Aprire **Dati** e controllare ID, DBH/altezza e UDF.
5. Testare un monumentale e verificare assenza di duplicazione del campo Monumentale.
6. Toccare **Vai** e verificare apertura navigazione alla coordinata corretta.
7. Toccare **Condividi** e controllare il link OTN.
8. Toccare **QR** e verificare che il codice apra la stessa scheda pubblica.
9. Verificare layout sia loggato sia non loggato.
10. Verificare che caricamento mappa Sprint 1 resti invariato.

## Nota foto reale

Resta aperto il test della priorità:

`foto reale OTN > immagine rappresentativa specie > placeholder`

da eseguire appena sarà disponibile almeno una foto reale.
