# Istanza Milano: censimento 31 marzo 2024

Il comando `import_milano` crea una seconda istanza nello stesso OTN, con slug
`milano`, unità visibili cm/m, amministratore esplicito, accesso pubblico in sola
lettura e ambito geografico ricavato dai punti. L'ambito è un rettangolo di
visualizzazione, non il confine amministrativo. Napoli non viene modificata.

## Dati e merge

Fonte principale: ZIP ufficiale `ds2484_alberi_20240331_geojson.zip` fornito
dall'utente, **247.779 alberi**, data di riferimento **31/03/2024**. Non è il
censimento 2025. Non è un elenco di tutti gli alberi esistenti nella città oltre
quelli censiti in questo dataset.

Fonte storica: `verde_Milano.zip/alberi.xlsx`, 247.233 alberi. L'anno 2020 deriva
dai metadati dei file, non da una data di rilievo verificata. I CSV storici hanno
numeri con separatori decimali perduti e non sono usati come fonte numerica.

Gli ID non sono stabili: tutte le 132.796 corrispondenze per ID hanno posizioni
discordanti oltre 2 m. Il merge cerca punti a distanza <=2 m con lo stesso nome
botanico (spazi/case/segno × normalizzati, cultivar storico separato). Accetta
solo un candidato e solo se anche l'abbinamento inverso è univoco. La prossimità
non prova l'identità nel tempo: un albero sostituito nella stessa posizione con
la stessa specie può risultare abbinato. Gli arricchimenti mantengono quindi
sempre l'etichetta **archivio storico**.

Risultato: **183.968 abbinamenti spaziali univoci**; 33.769 casi con tassonomia
da rivedere; 7.233 ambiguità spaziali; 319 ambiguità inverse; 22.490 senza
abbinamento verificato. I 63.265 record storici non abbinati restano nel file
storico, senza aggiungerli all'inventario attuale. Nessuna misura 2024 viene
sovrascritta con quella storica. Non vengono importate fotografie placeholder.

Il pacchetto contiene:

- `data/milano.otn.jsonl`: dati, provenienza, misure, arricchimenti e impronte;
  formato accettato dal nuovo comando OTN.
- `data/milano.normalized.csv`: copia tabellare in unità metriche; **non** è un
  template direttamente accettato dall'importatore web standard.
- `data/legacy.original.jsonl`: tutti i record originali dell'Excel storico,
  con valori decimali conservati come stringhe e geometria EWKB originale.
- `data/merge_audit.csv`: abbinamenti, distanze e conflitti sugli identificativi.
- `data/taxonomy_review.csv`: combinazioni botaniche originali e conteggi.
- `data/report.json`: conteggi, regole, hash di input/output.

334 etichette botaniche nel confronto originario non equivalgono a 334 specie
validate. Le combinazioni genere/specie/varietà richiedono revisione botanica;
le specie non determinate rimangono a livello di genere o senza assegnazione.
I codici `MILANO_LOCAL_*` conservano questa distinzione senza inventare un
abbinamento i-Tree. L'attivazione dei benefici richiede una mappatura e un
modello/area di calcolo adatti a Milano. Le vecchie stime restano snapshot
non validati, senza entrare nei benefici OTN correnti.

`diam_tronc` è importato in cm; `h_m` e `diam_chiom` in m. Il comando usa la
conversione OTN per le unità di database, normalmente pollici/piedi.
Il diametro della chioma è un campo personalizzato `Diametro chioma m`;
non viene confuso con `canopy_height`. `data_ini` resta un valore della fonte
originale: non viene interpretato automaticamente come data di piantagione.
Zero, valori mancanti e non validi diventano null, con originali conservati.
Sono mancanti/non validi 579 diametri, 799 altezze e 2.404 diametri chioma;
tre altezze oltre 60 m sono segnalate per revisione e conservate.

## Ricostruzione del pacchetto

Dal repository:

```bash
python3 scripts/prepare_milano.py \
  --modern /percorso/ds2484_alberi_20240331_geojson.zip \
  --legacy /percorso/verde_Milano.zip \
  --output /percorso/milano/data
python3 -m unittest discover -s scripts -p test_prepare_milano.py -v
```

## Caricamento sul VPS

Le istruzioni assumono il repository `/opt/OpenTreeNap`, il Compose esistente
`docker-compose.modern-v4.1.yml`, e un amministratore OTN già presente.
Prima del caricamento usare una copia di staging del database per verificare
il comando Django; l'ambiente di preparazione non dispone di Django/PostGIS
né di una connessione amministrativa al VPS. Test sul database e pubblicazione
live **non sono stati eseguiti**.

Portare nel checkout del server i file della PR e ricostruire le immagini web
e worker secondo la procedura di deploy abituale. Evitare di sostituire
modifiche locali o cambiare branch su un checkout non pulito. Estrarre il
pacchetto e copiare i dati nel container:

```bash
cd /opt/OpenTreeNap
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  build web worker
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  up -d web worker
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  exec -T web mkdir -p /tmp/milano
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  cp /percorso/milano-otn-2024/data/milano.otn.jsonl web:/tmp/milano/milano.otn.jsonl
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  cp /percorso/milano-otn-2024/data/report.json web:/tmp/milano/report.json
```

Verifica senza modifiche (sostituire `NOME_ADMIN`):

```bash
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml \
  exec -T web python manage.py import_milano /tmp/milano/milano.otn.jsonl \
  --report /tmp/milano/report.json --user NOME_ADMIN
```

Ripetere aggiungendo `--apply` per creare l'istanza privata e caricare tutti i
record. L'importazione conserva audit e convalide native, quindi può essere
lunga: commit ogni 100 alberi, progressi sul terminale, ripresa rilanciando lo
stesso comando. Una singola transazione fallita viene annullata; i batch già
salvati restano. Gli ID esterni già importati con la stessa impronta vengono
saltati, preservando eventuali successive modifiche manuali. Se la fonte cambia,
il comando si ferma: non è un aggiornamento automatico del censimento.

Dopo controlli su mappa, unità, permessi, campioni di specie/chioma e conteggi,
rilanciare con **`--apply --publish`**. Gli stessi record vengono saltati e
l'istanza completa diventa pubblica a `/milano/map/`. Il lock PostgreSQL
impedisce due importazioni contemporanee con questo comando. Durante un primo
import incompleto Milano resta privata. Nessuna cancellazione massiva viene
eseguita. L'API custom `/public-api/napoli/trees/` continua a riguardare Napoli;
Milano usa le rotte e le tile native multi-istanza di OTN.

## Verifiche svolte

Cinque test unitari: identificativi riassegnati, ambiguità in entrambe le
direzioni, tassonomia/posizioni discordanti, decimali/null e impronta/ordine
coordinate. Verifica completa dei 247.779 record e dei conteggi/abbinamenti
prima della consegna; compilazione Python del comando. Le verifiche Django
e PostGIS restano da svolgere sul server/staging, non sono simulate dai test
della trasformazione.
