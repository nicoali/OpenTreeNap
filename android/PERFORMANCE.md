# Android Sprint 1 — Performance e mappa

Branch di sviluppo: `feature/android-sprint1-performance`

Obiettivo: rendere la mappa immediatamente utilizzabile e mantenere buone prestazioni anche quando l'inventario cresce oltre gli attuali alberi di Napoli.

## Implementato in questa branch

### Cache-first

- cache locale JSON atomica in `filesDir`;
- all'avvio la mappa mostra subito l'ultimo inventario salvato;
- la rete aggiorna i dati in background;
- se il refresh fallisce e la cache esiste, la mappa resta utilizzabile;
- il refresh non cancella più una mappa valida per un errore temporaneo.

### Primo caricamento veloce

- l'app usa `mobile=1` sul normale endpoint `/plots`;
- il server restituisce un inventario leggero costruito con una singola query, senza foto, audit, polygon lookup e metadati di dettaglio per ogni albero;
- pagina Android da 1000 record: l'attuale inventario di Napoli (~400 alberi) arriva in una sola risposta compatta;
- per inventari >1000 record la stessa API continua a paginare;
- senza cache i dati vengono mostrati appena arriva la prima risposta;
- al termine viene salvato lo snapshot completo.

### Clustering

- sostituito il clustering manuale con Google Maps Utility Library;
- `ClusterManager` gestisce camera idle, marker e cluster;
- renderer OpenTreeNap dedicato;
- marker monumentali mantengono il pin oro;
- i cluster che contengono almeno un monumentale hanno anello oro;
- cache dei BitmapDescriptor dei cluster per evitare di ridisegnare la stessa icona;
- tap su un cluster adatta automaticamente la camera ai suoi punti.

### Dettaglio lazy

La card viene mostrata immediatamente con i dati già presenti nell'inventario. Subito dopo il tap viene richiesto in background:

`GET /api/v4/instance/{instance}/plots/{plotId}`

La card viene aggiornata solo se l'utente sta ancora guardando lo stesso albero. Il dettaglio aggiornato viene anche scritto nella cache locale.

## Strategia viewport per inventari grandi

Con gli attuali ~400 alberi è più semplice e robusto mantenere in memoria l'intero inventario leggero.

Quando l'istanza supera indicativamente 2.000–5.000 alberi, passare a una strategia per area visibile:

1. calcolare bounds/centro della camera;
2. usare un endpoint mobile leggero con bbox, oppure estendere l'API OTN con bbox;
3. scaricare soltanto id, coordinate, species id, flag Monumentale e dati minimi;
4. mantenere una cache LRU per celle geografiche;
5. continuare a caricare il dettaglio completo soltanto al tap;
6. invalidare le celle dopo create/edit/delete.

L'endpoint OTM `locations/{lat},{lng}/plots` resta utile per la ricerca di alberi vicini, ma non sostituisce un vero endpoint bbox per una viewport arbitraria.

## Test da eseguire sul telefono

1. Primo avvio dopo install/clear data: verificare il tempo fino alla comparsa del totale e confrontarlo con i ~40 secondi precedenti.
2. Chiudere e riaprire: gli alberi devono apparire quasi subito dalla cache.
3. Durante l'aggiornamento la mappa deve restare utilizzabile.
4. Pan/zoom: niente ricostruzione manuale completa dei marker.
5. Cluster con monumentali: anello oro.
6. Tap albero: card immediata, dettaglio aggiornato in background.
7. Disattivare temporaneamente rete dopo aver creato la cache: la mappa deve continuare a mostrare i dati salvati.
8. Filtri Tutti/Monumentali devono continuare a funzionare.
9. Login, aggiunta e modifica devono restare invariati.

## Criterio di chiusura Sprint 1

Dopo il test su dispositivo, gli elementi corrispondenti in `ROADMAP.md` vengono marcati completati. L'ottimizzazione bbox rimane una fase di scalabilità futura finché l'inventario non la richiede.
