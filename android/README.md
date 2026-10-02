# OpenTreeNap Android — V0.3

V0.3 porta il client Android oltre la sola consultazione della mappa e lo
allinea alle funzioni introdotte nell'ecosistema OpenTreeNap.

## Funzioni V0.3

- caricamento paginato dell'intero inventario OTN, senza richieste da 10.000 record;
- marker e filtro dedicati agli alberi monumentali;
- badge "Albero Monumentale d'Italia";
- scheda albero con nome comune, nome scientifico, indirizzo, DBH, altezza,
  Custom ID/MASAF e identificativi OTN quando disponibili;
- pulsante "Scheda botanica" collegato alle pagine
  `https://opentreenap.altervista.org/specie/{slug}/`;
- login con il proprio account OTN tramite autenticazione Basic abbinata alla
  firma HMAC richiesta dall'API;
- lettura dei permessi dell'istanza (`can_add_tree`, `can_edit_tree`,
  `can_edit_tree_photo`);
- aggiunta di un nuovo albero direttamente dalla mappa;
- scelta specie dall'elenco reale dell'istanza;
- inserimento opzionale di DBH e altezza;
- modifica di specie, DBH e altezza di un albero esistente;
- logout;
- password mantenuta solo in memoria durante la sessione e non salvata su disco.

## Flusso aggiunta albero

1. Accedere dal pulsante Account.
2. Premere il pulsante + sulla mappa.
3. Toccare il punto geografico del nuovo albero.
4. Selezionare la specie dall'elenco OTN.
5. Inserire DBH e altezza se disponibili.
6. Salvare.

L'API OTM applica i permessi dell'account anche lato server.

## Flusso modifica

1. Toccare un albero.
2. Se l'account ha il permesso `can_edit_tree`, compare il pulsante Modifica.
3. Aggiornare specie, DBH o altezza.
4. Salvare e ricaricare la mappa.

## Configurazione locale

Il file `local.properties` resta privato e non deve essere committato.

```properties
MAPS_API_KEY=...
OTM_BASE_URL=https://opentreenap.ddns.net
OTM_INSTANCE=napoli
OTM_ACCESS_KEY=...
OTM_SECRET_KEY=...
```

## Sicurezza prima della pubblicazione

La V0.3 continua a usare il modello legacy OTM previsto per i client mobili:
firma HMAC dell'app + credenziali personali Basic per le operazioni autenticare.
È adatto allo sviluppo e ai test controllati.

Prima della distribuzione pubblica sul Play Store, il secret HMAC non dovrà
rimanere dentro l'APK: la firma va spostata dietro un piccolo servizio backend
OpenTreeNap oppure sostituita con un flusso di autenticazione client-safe.

## Prossimi moduli

- foto alberi;
- registrazione e recupero password;
- ricerca;
- segnalazioni;
- scheda botanica nativa;
- QR;
- percorsi botanici;
- benefici ecosistemici;
- modalità bambini;
- strumenti dedicati a Capodimonte e agli alberi monumentali.
