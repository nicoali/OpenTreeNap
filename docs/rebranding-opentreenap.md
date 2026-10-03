# OpenTreeNap: rebranding e lingue

L'applicazione usa l'italiano come lingua predefinita, indipendentemente dalla lingua del browser. Il selettore IT/EN salva la scelta con il normale endpoint Django protetto da CSRF. Il cambio lingua conserva percorso, query e frammento della mappa. I contenuti personalizzati inseriti nel database non vengono tradotti automaticamente.

Logo originale del sito di progetto, palette verde #557f2d / #365d2b, accento azzurro #4b9fbd, sfondo #f6f8f3. Header, footer, login, management e amministrazione condividono il tema. Link al sito: https://opentreenap.altervista.org/. Il blog viene aggiornato separatamente.

## Aggiornamento Docker

Usare il file Compose già utilizzato nell'installazione. Esempio con v4.1:

```sh
git fetch origin
git switch rebrand/opentreenap-it-en
git pull --ff-only
docker compose -f docker-compose.modern-v4.1.yml --env-file .env.modern-v4.1 up -d --build web worker
docker compose -f docker-compose.modern-v4.1.yml --env-file .env.modern-v4.1 exec web python manage.py upgrade_opentreenap_branding
docker compose -f docker-compose.modern-v4.1.yml --env-file .env.modern-v4.1 exec web python manage.py upgrade_opentreenap_branding --apply
```

Il comando prima mostra una simulazione. Aggiorna solamente colori corrispondenti esattamente ai vecchi valori standard e riferimenti a immagini identiche ai loghi originali OpenTreeMap. Conserva loghi e colori personalizzati, file media e contenuti. Non modifica alberi, geometrie, utenti, permessi o pagine personalizzate. Non richiede nuove migrazioni. L'entrypoint compila i cataloghi e gli asset; non copiare gli asset di una precedente build nella nuova immagine.

## Verifiche

Build Webpack di produzione completata; cataloghi gettext validati e compilati; controlli Django senza errori (sei avvisi preesistenti NullBooleanField). Dieci test verificano lingua predefinita, scelta inglese, URL e redirect sicuri, catalogo JavaScript e preservazione della configurazione durante l'aggiornamento dei colori. Coprono anche etichette e plurali italiani e cache distinta per lingua e filtro.

```sh
python opentreemap/manage.py test opentreemap.test_rebranding --testrunner django.test.runner.DiscoverRunner
```

La verifica completa della mappa e dei dati richiede l'installazione con PostgreSQL/PostGIS e gli altri servizi. Non è stato effettuato un deployment sulla VPS da questo ambiente.

## Rifinitura dell’interfaccia

Pulsanti rettangolari con angoli arrotondati, altezze coerenti e gerarchia primaria/secondaria/distruttiva. Ridotti gli spazi iniziali della scheda albero; titoli, barra risultati, avvisi informativi e progressi usano verdi coerenti. Corretti Edit, il placeholder specie, il pulsante commenti e le etichette degli interventi di cura. Gli ETag dei frammenti dipendono da lingua, URL completo e revisione dell’interfaccia, per evitare risposte obsolete dopo cambio lingua o filtro. I nomi comuni delle specie restano i dati importati.

Sulla VPS, già su questo ramo, eseguire `git pull --ff-only` e ricostruire web e worker usando lo stesso file Compose e `--env-file .env.modern-v4.1` dell’installazione. Non occorre ripetere il comando di aggiornamento dei colori per queste rifiniture.

## Spazio della mappa e pagine informative

Su desktop, header ridotto da 153 a 109 px, barra risultati da 42 a 36 px, mappa da top 196 a 145 px: 51 px di altezza recuperati. Sidebar da 350 a 320 px: 30 px di larghezza recuperati. Geometria aggiornata anche per embed; comportamento mobile della mappa mantenuto. Le pagine FAQ/Informazioni/Risorse non ereditano ricerca, esportazione e barra risultati. Schede albero con sfondo neutro e colori dei pulsanti corretti anche rispetto alla specificità delle regole legacy.

## Navigazione compatta e FAQ

La navigazione desktop passa da 45 a 38 px; la ricerca da 64 a 55 px e la barra risultati da 36 a 34 px. Il bordo superiore della mappa scende da 145 a 127 px: altri 18 px disponibili in altezza. La vista incorporata mantiene allineate le stesse fasce; il layout mobile non è stato compresso. Le pagine informative mostrano un header senza i controlli della mappa. La FAQ usa una larghezza di lettura più contenuta e coppie domanda/risposta con spaziatura e bordi coerenti, anche quando il testo proviene dal database.

Il footer usa la stessa palette della navigazione e mostra GitHub (`nicoali`), Instagram e Facebook (`opentreenap`) con icone accessibili. Gli indirizzi Instagram e Facebook sono predisposti per i profili da creare: verificare che appartengano al progetto prima di promuoverli come canali ufficiali.

## Bilanciamento della mappa

Dopo la verifica sulla VPS, la barra dei risultati e il footer usano superfici chiare con testi verdi, mentre la navigazione conserva il verde scuro. Il pannello laterale della mappa ha fondo bianco e intestazione chiara. Il pulsante Cerca resta verde ma occupa 24 px di altezza e si allinea a destra sotto Avanzato/Resetta, senza sovrapporsi alla barra dei risultati. Aggiungi un Albero è ridotto a 26 px; Esporta diventa secondario con bordo. Questi cambiamenti sono limitati al desktop, lasciando invariati i controlli mobile.
