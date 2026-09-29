<p align="center">
  <img src="docs/images/opentreenap-banner.png" alt="OpenTreeNap — progetto OpenTreeMap modernizzato nato a Napoli" width="100%">
</p>

# 🌳 OpenTreeNap

[🇬🇧 English](README.md) | **Italiano**

**OpenTreeNap** è un progetto open source nato a Napoli per riportare in vita e modernizzare la storica piattaforma **OpenTreeMap**, rendendola più semplice da avviare oggi con Docker e più adatta a nuovi esperimenti di mappatura del verde urbano.

L'idea parte da una convinzione semplice: **gli alberi in città non sono arredo, sono infrastruttura viva**.

Fanno ombra, contribuiscono a mitigare il calore urbano, migliorano la qualità e la vivibilità degli spazi pubblici, offrono habitat e continuità ecologica e possono avere un ruolo importante anche nel benessere quotidiano delle persone. In una città densa, calda e complessa come Napoli, conoscere meglio il patrimonio arboreo significa anche avere più strumenti per capirlo, monitorarlo e proteggerlo.

OpenTreeNap nasce anche dal mio percorso personale: **sono laureato in Scienze Naturali** e ho sempre avuto interesse per l'ambiente, la biodiversità, il territorio e il rapporto tra natura e città. Questo progetto mette insieme quella formazione con la mia passione per la tecnologia e per i dati aperti.

> **Stato del progetto:** modernizzazione e sviluppo attivi.  
> OpenTreeNap è già utilizzabile come ambiente di sviluppo, ma non è ancora da considerare un deployment pubblico pronto per la produzione.

---

## 🌋 Perché OpenTreeNap?

Il nome unisce **OpenTreeMap** e **Napoli**.

OpenTreeNap nasce con un'identità napoletana molto chiara, ma non vuole essere un progetto chiuso dentro i confini della città. Napoli è il punto di partenza, il laboratorio e l'ispirazione; l'obiettivo è mantenere il software riutilizzabile anche da altre comunità, ricercatori, sviluppatori, associazioni e progetti dedicati alla forestazione urbana.

La piattaforma può diventare uno strumento per:

- censire e localizzare alberi e siti di impianto
- organizzare informazioni sulle specie
- visualizzare il patrimonio arboreo su una mappa interattiva
- raccogliere dati utili per analisi e monitoraggio
- rendere più accessibili le informazioni sul verde urbano
- favorire progetti collaborativi e open data
- sperimentare nuovi strumenti digitali per ambiente e territorio

---

## 🌳 Perché gli alberi urbani contano

In città gli alberi svolgono funzioni che vanno ben oltre l'aspetto estetico.

La loro ombra può ridurre l'esposizione diretta al sole sulle superfici urbane; la vegetazione contribuisce a creare microclimi più favorevoli e, attraverso l'evapotraspirazione, può aiutare a mitigare le condizioni di caldo intenso.

Gli alberi possono inoltre:

- offrire rifugio e risorse alla biodiversità urbana
- migliorare la qualità degli spazi pubblici
- contribuire alla gestione delle acque meteoriche
- aumentare il comfort di strade, piazze e percorsi pedonali
- favorire il contatto quotidiano con elementi naturali
- rendere più riconoscibile e più piacevole il paesaggio urbano

Ma per gestire meglio il verde serve prima di tutto **conoscerlo**.

Sapere dove si trovano gli alberi, a quale specie appartengono, in quali condizioni sono e come sono distribuiti sul territorio permette di trasformare una semplice mappa in uno strumento utile per osservazione, ricerca, manutenzione e pianificazione.

È qui che entra in gioco OpenTreeNap.

---

## 🗺️ Cosa funziona oggi

È stata creata e testata una vera istanza di sviluppo all'indirizzo:

`/napoli-test/map/`

Il percorso completo dei dati è stato verificato:

**PostgreSQL/PostGIS → OpenTreeMap/Django → OTM Tiler → Windshaft/Mapnik → PNG + UTFGrid → Leaflet → marker interattivi degli alberi**

In pratica, un albero salvato nel database può essere:

1. memorizzato in PostgreSQL/PostGIS
2. elaborato dall'applicazione Django
3. renderizzato dal sistema di tile
4. mostrato sulla mappa
5. selezionato per visualizzarne i dettagli

Lo stack Docker è stato inoltre validato partendo da un **clone Git completamente nuovo e da volumi Docker vuoti**, verificando correttamente:

- inizializzazione PostgreSQL/PostGIS
- avvio di Redis
- configurazione dell'account dedicato al tiler
- avvio di Django
- raccolta dei file statici
- avvio di Gunicorn
- health check del backend
- health check database/cache di OTM Tiler

---

## ✨ Funzionalità già disponibili

L'ambiente di sviluppo attuale supporta:

- mappe interattive degli alberi
- alberi e siti di impianto
- schede di dettaglio
- dati sulle specie
- mappe di base OpenStreetMap
- dati geografici PostgreSQL/PostGIS
- rendering con Mapnik/Windshaft
- tile PNG
- interazione UTFGrid
- frontend cartografico Leaflet
- amministrazione Django
- storage persistente tramite Docker

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

Il tiler storico viene eseguito intenzionalmente in un ambiente Node.js legacy isolato, perché la sua catena di dipendenze Windshaft/Mapnik non è direttamente compatibile con le versioni moderne di Node.js.

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

Apri `.env.modern-v4.1` e sostituisci i valori di esempio con la tua configurazione locale.

Il file reale `.env.modern-v4.1` è escluso da Git e **non deve mai essere pubblicato nel repository**.

Controlla che Docker sia pronto:

    chmod +x modern-v4.1.sh
    ./modern-v4.1.sh doctor

Esegui la build:

    ./modern-v4.1.sh build

Avvia OpenTreeNap:

    ./modern-v4.1.sh up

Controlla lo stato dei container:

    ./modern-v4.1.sh status

Verifica il backend:

    ./modern-v4.1.sh smoke

Un backend correttamente avviato restituisce una risposta simile a:

    {"status": "ok", "database": "ok"}

---

## 👤 Amministratore Django

Per creare un amministratore locale:

    ./modern-v4.1.sh superuser

---

## 🗺️ OTM Tiler

Il renderer storico di OpenTreeMap è integrato direttamente nel repository nella directory:

`otm-tiler/`

Per lo sviluppo locale, l'endpoint delle tile visibile dal browser e la porta pubblicata sull'host sono configurati con:

    OTM_TILE_HOST=//localhost:4000
    OTM_TILER_HTTP_PORT=4000

`OTM_TILER_HTTP_PORT` controlla la porta pubblicata da Docker sull'host, mentre il tiler continua ad ascoltare sulla porta 4000 all'interno del container.

Il tiler utilizza un account PostgreSQL dedicato e con privilegi limitati:

    OTM_TILER_DB_USER
    OTM_TILER_DB_PASSWORD

La configurazione del database viene gestita da:

    docker/configure-tiler-db.sh

Le password reali non devono mai essere inserite nel repository.

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

elimina i volumi Docker v4.1 e i dati persistenti contenuti al loro interno.

**Non usare `reset` come normale comando di riavvio.**

---

## 📚 Documentazione della modernizzazione

Le note tecniche della migrazione sono conservate in:

- `MODERNIZATION.md`
- `MODERN_V2_NOTES.md`
- `MODERN_V3_NOTES.md`
- `MODERN_V4_1_NOTES.md`

Questi file raccontano le diverse fasi con cui il vecchio stack è stato progressivamente adattato all'ambiente attuale.

---

## 🚧 Cosa resta da fare

OpenTreeNap è ancora un progetto in evoluzione.

Tra le aree da approfondire ci sono:

- configurazione per un vero deployment di produzione
- test più ampi dei flussi applicativi
- validazione completa di Celery e dei task in background
- aggiornamento delle parti Django ancora deprecate
- revisione di alcune vecchie integrazioni di geocoding
- configurazione dei servizi esterni eventualmente utilizzati
- ulteriori test frontend e browser

L'obiettivo, però, non è semplicemente “far partire un vecchio software”.

L'obiettivo è costruire una base moderna e comprensibile su cui continuare a lavorare, senza perdere il valore storico e funzionale di OpenTreeMap.

---

## 🔐 Sicurezza

Non pubblicare mai nel repository:

- `.env.modern-v4.1`
- password del database
- Django secret key
- API key
- chiavi SSH private
- credenziali di servizi esterni

Nel versionamento devono comparire soltanto configurazioni di esempio prive di credenziali reali.

---

## 🌳 Da OpenTreeMap a OpenTreeNap

OpenTreeNap è basato e derivato da **OpenTreeMap**, la piattaforma open source collaborativa dedicata all'inventario degli alberi, ai servizi ecosistemici, alla forestazione urbana e al coinvolgimento delle comunità.

Il progetto non rivendica la paternità del codice originale.

Il codice storico e le relative note di copyright restano attribuiti ai rispettivi autori, inclusa **Azavea, Inc.**

OpenTreeNap aggiunge a quella base il lavoro di:

- modernizzazione
- compatibilità
- containerizzazione
- integrazione
- test su stack contemporaneo
- adattamento a nuovi esperimenti e casi d'uso

---

## 📜 Licenza

Le informazioni originali sulla licenza sono conservate in [`LICENSE`](LICENSE).

Il codice OpenTreeMap presente nel repository mantiene i termini di licenza open source applicabili, inclusa la **GNU Affero General Public License v3 (AGPLv3)** presente nel progetto.

Per i termini completi e le note di copyright fai riferimento al file `LICENSE`.

---

## 🏙️ Da Napoli, con radici aperte

OpenTreeNap nasce a Napoli, ma l'idea è più ampia.

Una città può essere letta anche attraverso i suoi alberi: dove sono, quali specie ospita, dove manca ombra, dove esistono spazi da valorizzare, come cambia il verde nel tempo.

Se questi dati sono aperti, leggibili e condivisibili, possono diventare uno strumento utile non solo per chi sviluppa software, ma anche per chi studia il territorio, per associazioni, scuole, cittadini e comunità locali.

**Verde urbano. Dati aperti. Napoli.**

Da Napoli verso qualsiasi città, mantenendo vive le radici open source di OpenTreeMap.
