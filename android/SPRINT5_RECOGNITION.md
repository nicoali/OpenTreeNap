# Sprint 5 — Contributi utenti e riconoscimento assistito

## Scelta motore di riconoscimento

Primario: **BioCLIP v1** self-hosted.

Motivazioni:

- open source / licenza MIT;
- nessun costo per identificazione;
- nessuna API key esterna;
- modello più leggero di BioCLIP 2 (circa 602 MB contro ~1.7 GB per BioCLIP 2);
- adatto a classificazione zero-shot/few-shot di organismi;
- possiamo limitare le candidate alle specie realmente presenti nell'istanza OTN Napoli.

Il modello non sarà inserito nell'APK. Girerà lato server in un servizio separato.

## Architettura prevista

Android
→ invio 1–3 foto
→ endpoint OpenTreeNap
→ servizio di riconoscimento BioCLIP
→ ranking solo sulle specie dell'istanza Napoli
→ top 3 candidate
→ conferma manuale utente
→ eventuale salvataggio/modifica specie tramite API OTN esistente

Nessuna modifica automatica della specie.

## Ottimizzazioni

- pre-calcolo embeddings testuali delle specie OTN Napoli;
- opzionale embedding delle immagini rappresentative WordPress;
- fusione score testo + immagine di riferimento;
- filtro geografico/istanza;
- cache risultati;
- immagini ridimensionate prima dell'inferenza;
- possibilità futura di export ONNX / quantizzazione CPU.

## Provider secondario opzionale

Pl@ntNet può essere mantenuto come fallback opzionale, non obbligatorio.
Il piano gratuito pubblico offre un numero limitato di identificazioni al giorno.
La chiave, se usata, deve restare esclusivamente lato server.

## Perché non iNaturalist come motore principale

iNaturalist pubblica modelli piccoli open source, ma il classificatore completo non è distribuito pubblicamente. Per OpenTreeNap è più utile un modello aperto con candidate controllate dall'inventario locale.

## UX proposta

Pulsante: **Riconosci albero**

1. Scatta foto / scegli dalla galleria.
2. Suggerimento: foto intera + foglia/corteccia se possibile.
3. Analisi.
4. Risultati:
   - specie 1 + confidenza;
   - specie 2 + confidenza;
   - specie 3 + confidenza.
5. Pulsanti:
   - Conferma;
   - Confronta;
   - Nessuna corrisponde.
6. Solo dopo conferma manuale l'utente può salvare la specie.

## Regola scientifica

La percentuale mostrata è una confidenza del modello, non una certezza botanica.
La UI deve esplicitamente presentarla come suggerimento assistito.

## Fasi Sprint 5

### 5A — Fondazione
- branch dedicata;
- endpoint/provider abstraction;
- UI Riconosci albero;
- foto fotocamera/galleria;
- risultati mock/contract.

### 5B — Motore locale
- container recognizer;
- BioCLIP v1;
- embeddings specie OTN;
- top 3;
- timeout e limiti file.

### 5C — Conferma e contributi
- conferma manuale;
- modifica specie;
- upload foto;
- gestione permessi;
- audit/storico.

### 5D — Rifinitura
- confronto immagini;
- feedback corretto/non corretto;
- eventuale fallback Pl@ntNet;
- metriche accuratezza sul set Napoli.
