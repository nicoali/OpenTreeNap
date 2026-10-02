# Sprint 5 — Misurazioni dendrometriche da smartphone

## Obiettivo

Rendere lo smartphone uno strumento di rilievo sul campo per due misure prioritarie:

- altezza dell'albero;
- circonferenza del fusto a 1,30 m da terra, con DBH equivalente quando utile.

La funzione deve produrre una **stima misurata e verificabile**, non un valore generato dall'AI.

## Standard di riferimento

Per la circonferenza degli alberi monumentali seguiamo la convenzione MASAF: misura del fusto a **1,30 m da terra / dal colletto nei casi previsti**.

## Metodo altezza — AR guidata

Metodo primario sui dispositivi ARCore:

1. L'utente marca la base del tronco da distanza ravvicinata.
2. OpenTreeNap crea un anchor AR stabile sulla base.
3. L'utente arretra fino a una distanza adatta mantenendo il tracking.
4. Punta il reticolo alla cima.
5. Dalla posa della camera, dall'anchor della base e dall'asse verticale/gravitazionale calcoliamo l'intersezione geometrica con la verticale del tronco.
6. Ripetiamo la misura 3 volte.
7. Salviamo mediana, dispersione e qualità del tracking.

Questo evita di dipendere solo dal GPS e non richiede un oggetto di altezza nota.

### Fallback

Su dispositivi senza ARCore/Depth:

- clinometro tramite sensori di orientamento;
- distanza inserita o misurata con metodo esterno;
- risultato esplicitamente etichettato come misura assistita/fallback.

## Metodo circonferenza — scansione 3D a 1,30 m

Metodo primario sui dispositivi con ARCore Depth:

1. L'utente marca la base del tronco.
2. L'app visualizza una fascia virtuale a 1,30 m.
3. L'utente muove lentamente il telefono intorno al tronco acquisendo depth/point cloud.
4. Selezioniamo i punti appartenenti alla sezione del fusto alla quota di 1,30 m.
5. Fit robusto della sezione (cerchio o ellisse, con rimozione outlier).
6. Calcolo diretto della circonferenza della sezione.
7. DBH equivalente derivato dalla circonferenza solo come valore secondario.

### Fallback circonferenza

- misura assistita dei due bordi del tronco con depth e stima del diametro;
- conversione in circonferenza solo se la sezione è sufficientemente regolare;
- in caso di tronchi irregolari/policormici l'app richiede misura manuale o nuova scansione.

## Qualità e affidabilità

Ogni misura salva anche:

- metodo;
- data/ora;
- modello dispositivo;
- supporto ARCore/Depth;
- numero di ripetizioni;
- dispersione delle ripetizioni;
- livello di confidenza/qualità;
- eventuale errore stimato.

Una misura con tracking scarso o ripetizioni incoerenti non può essere salvata come misura affidabile: l'app chiede di ripetere.

## Piano di validazione prima dell'uso ufficiale

### Altezza

- almeno 30 alberi campione;
- confronto con clinometro professionale o laser rangefinder;
- target di progetto: errore medio assoluto <= 0,5 m e/o <= 5%.

### Circonferenza

- almeno 50 alberi campione;
- confronto con nastro dendrometrico a 1,30 m;
- target di progetto: errore medio assoluto <= 2 cm e MAPE <= 5–8%.

I target sono criteri interni di accettazione: vanno verificati sui nostri dispositivi e sugli alberi urbani di Napoli prima di presentare la misura come affidabile.

## Integrazione OpenTreeNap

Campi previsti:

- `height` OTN per altezza confermata;
- `diameter` / DBH equivalente quando appropriato;
- UDF `Circonferenza 1,30 m`;
- UDF `Metodo misura altezza`;
- UDF `Metodo misura circonferenza`;
- UDF `Qualità misura`;
- UDF `Errore stimato`;
- UDF `Data rilievo smartphone`.

## Fasi Sprint 5

### 5A — Prototipo altezza
- compatibilità ARCore;
- anchor base;
- reticolo cima;
- calcolo geometrico;
- 3 misure + mediana;
- salvataggio solo dopo conferma.

### 5B — Prototipo circonferenza
- quota guidata 1,30 m;
- acquisizione depth/point cloud;
- fit sezione;
- circonferenza + DBH equivalente;
- controlli di qualità.

### 5C — Validazione sul campo
- dataset di riferimento;
- confronto con nastro/clinometro o laser;
- calcolo MAE, MAPE e outlier;
- calibrazione per famiglie di dispositivi se necessaria.

### 5D — Integrazione definitiva
- pulsanti `Misura altezza` e `Misura circonferenza`;
- metadata di qualità;
- storico rilievi;
- scrittura OTN con permessi reali;
- UX per ripetere/rifiutare misure poco affidabili.

## Nota dispositivi

ARCore Depth non è disponibile su tutti i telefoni. La feature deve degradare in modo trasparente e non deve mai inventare precisione che il dispositivo non può fornire.
