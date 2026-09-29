# 🌳 OpenTreeNap

[🇬🇧 English](README.md) | **Italiano**

**OpenTreeNap** è un ambiente di sviluppo modernizzato e basato su Docker, derivato dalla piattaforma originale **OpenTreeMap** e dedicato alla mappatura collaborativa degli alberi urbani.

Nato a **Napoli, Italia 🇮🇹**, il progetto vuole dare allo storico codice di OpenTreeMap un ambiente tecnico contemporaneo, mantenendolo utile per sperimentazioni e progetti di forestazione urbana a Napoli e non solo.

> **Stato del progetto:** modernizzazione e sviluppo attivi.  
> OpenTreeNap non è ancora pensato come deployment pubblico pronto per la produzione.

---

## 🌋 Perché OpenTreeNap?

OpenTreeNap nasce da due idee: preservare e modernizzare la storica piattaforma OpenTreeMap e sperimentare come strumenti open source per la forestazione urbana possano supportare la mappatura degli alberi a Napoli.

Il nome unisce **OpenTreeMap** e **Napoli**.

Napoli è la casa e l'ispirazione del progetto, ma OpenTreeNap non vuole essere limitato a una sola città. L'obiettivo è mantenere la piattaforma riutilizzabile da altre comunità, ricercatori, sviluppatori e iniziative dedicate al verde urbano.

L'ambiente di sviluppo attuale supporta:

- mappe interattive degli alberi
- alberi e siti di impianto
- pannelli con i dettagli degli alberi
- dati sulle specie
- mappe di base OpenStreetMap
- dati geografici PostgreSQL/PostGIS
- rendering delle tile con Mapnik/Windshaft
- tile PNG e interazione UTFGrid
- interazione con la mappa tramite Leaflet
- amministrazione Django
- archiviazione persistente tramite Docker

---

## 🗺️ Traguardo attuale

È stata creata e testata una vera istanza di sviluppo all'indirizzo `/napoli-test/map/`.

È stato validato l'intero percorso di rendering degli alberi:

**PostgreSQL/PostGIS → OpenTreeMap/Django → OTM Tiler → Windshaft/Mapnik → PNG + UTFGrid → Leaflet → marker interattivi degli alberi**

Gli alberi salvati in modo persistente possono essere visualizzati sulla mappa e selezionati per mostrarne i dettagli.

Lo stack Docker è stato inoltre validato partendo da un **clone Git completamente nuovo e da volumi Docker completamente vuoti**, verificando inizializzazione di PostgreSQL, Redis, avvio di Django, raccolta dei file statici e health check del database/cache di OTM Tiler.

---

## 🧱 Architettura Docker

| Servizio | Funzione |
|---|---|
| `web` | Applicazione OpenTreeMap basata su Django |
| `db` | PostgreSQL 14 + PostGIS |
| `redis` | Cache e broker |
| `tiler` | Renderer storico OTM basato su Windshaft/Mapnik |
| `db-tiler-init` | Configura l'account PostgreSQL con privilegi limitati usato dal tiler |
| `worker` | Worker Celery opzionale tramite profilo `tasks` |

Il tiler storico viene intenzionalmente eseguito in un ambiente Node.js legacy isolato, perché la sua catena di dipendenze Windshaft/Mapnik non è direttamente compatibile con le versioni moderne di Node.js.

---

## ⚙️ Stack tecnologico

OpenTreeNap utilizza attualmente:

- Python 3.10
- Django 3.2
- PostgreSQL 14
- PostGIS 3.x
- Redis 6
- Celery 5
- Gunicorn
- Node.js 14 per il frontend principale
- Webpack 1
- Node.js 6 per OTM Tiler legacy
- Windshaft
- Mapnik
- Leaflet
- Docker Compose

---

## 🚀 Avvio rapido

Clona il repository:

    git clone https://github.com/nicoali/OpenTreeNap.git
    cd OpenTreeNap

Crea il file di configurazione locale:

    cp .env.modern-v4.1.example .env.modern-v4.1

Modifica `.env.modern-v4.1` sostituendo i valori segnaposto con la configurazione e i segreti locali.

Il vero file `.env.modern-v4.1` è escluso da Git e non deve mai essere inserito nel repository.

Controlla Docker:

    chmod +x modern-v4.1.sh
    ./modern-v4.1.sh doctor

Esegui la build:

    ./modern-v4.1.sh build

Avvia OpenTreeNap:

    ./modern-v4.1.sh up

Controlla i container:

    ./modern-v4.1.sh status

Esegui l'health check del backend:

    ./modern-v4.1.sh smoke

Un backend correttamente avviato restituisce una risposta simile a:

    {"status": "ok", "database": "ok"}

---

## 👤 Amministratore Django

Crea un amministratore locale con:

    ./modern-v4.1.sh superuser

---

## 🗺️ OTM Tiler

Il renderer storico di OpenTreeMap è integrato direttamente nel repository nella directory `otm-tiler/`.

Per lo sviluppo locale, endpoint delle tile visibile dal browser e porta pubblicata sull'host sono configurati con:

    OTM_TILE_HOST=//localhost:4000
    OTM_TILER_HTTP_PORT=4000

`OTM_TILER_HTTP_PORT` controlla la porta pubblicata da Docker sull'host, mentre il tiler continua ad ascoltare sulla porta 4000 all'interno del container.

Il tiler utilizza un account PostgreSQL dedicato e con privilegi limitati tramite:

    OTM_TILER_DB_USER
    OTM_TILER_DB_PASSWORD

La configurazione del database è gestita da:

    docker/configure-tiler-db.sh

Le password reali del database non devono mai essere inserite nel repository.

---

## 🧰 Comandi utili

    ./modern-v4.1.sh doctor
    ./modern-v4.1.sh build
    ./modern-v4.1.sh up
    ./modern-v4.1.sh logs
    ./modern-v4.1.sh status
    ./modern-v4.1.sh check
    ./modern-v4.1.sh smoke
    ./modern-v4.1.sh superuser
    ./modern-v4.1.sh worker-up
    ./modern-v4.1.sh down

### ⚠️ Reset distruttivo

Il comando:

    ./modern-v4.1.sh reset

elimina i volumi Docker v4.1 e i dati di sviluppo persistenti contenuti al loro interno.

**Non usare `reset` come normale comando di riavvio.**

---

## 📚 Documentazione della modernizzazione

Le note tecniche della migrazione sono conservate in:

- `MODERNIZATION.md`
- `MODERN_V2_NOTES.md`
- `MODERN_V3_NOTES.md`
- `MODERN_V4_1_NOTES.md`

---

## 🚧 Limitazioni attuali

OpenTreeNap rimane un progetto di modernizzazione attivo.

Le aree che richiedono ancora ulteriore lavoro o validazione includono:

- configurazione per il deployment in produzione
- test più ampi dei flussi applicativi
- validazione di Celery e delle attività in background
- deprecazioni Django ancora presenti
- alcune integrazioni storiche di geocoding
- configurazione esterna di Google Maps dove utilizzata
- ulteriori test di compatibilità frontend/browser

L'ambiente di sviluppo attualmente funzionante non deve ancora essere considerato una migrazione di produzione completata.

---

## 🔐 Sicurezza

Non inserire mai nel repository:

- `.env.modern-v4.1`
- password del database
- secret key di Django
- API key
- chiavi SSH private
- credenziali di servizi esterni

Nel versionamento devono essere presenti soltanto configurazioni di esempio prive di credenziali reali.

---

## 🌳 Attribuzione OpenTreeMap

OpenTreeNap è basato e derivato da **OpenTreeMap**, la piattaforma open source collaborativa per l'inventario degli alberi, il calcolo dei servizi ecosistemici, l'analisi della forestazione urbana e il coinvolgimento delle comunità.

Il codice originale di OpenTreeMap e le relative note di copyright restano attribuiti ai rispettivi autori, inclusa **Azavea, Inc.**

OpenTreeNap non rivendica la paternità del codice originale di OpenTreeMap.

Questo repository contiene il lavoro di modernizzazione, compatibilità e integrazione realizzato sopra il progetto originale.

---

## 📜 Licenza

Le informazioni originali sulla licenza sono conservate in [`LICENSE`](LICENSE).

Il codice OpenTreeMap contenuto in questo repository mantiene i termini di licenza open source applicabili, inclusa la **GNU Affero General Public License v3 (AGPLv3)** presente nel repository.

Consulta il file `LICENSE` completo per i termini applicabili e le note di copyright.

---

## 🏙️ OpenTreeNap

**Alberi aperti. Dati aperti. Napoli.**

Da Napoli verso qualsiasi città: modernizzare OpenTreeMap preservandone le radici open source.
