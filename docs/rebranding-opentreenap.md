# OpenTreeNap: rebranding e lingue

L'applicazione usa l'italiano come lingua predefinita, indipendentemente dalla lingua del browser. Il selettore IT/EN salva la scelta con il normale endpoint Django protetto da CSRF. Il cambio lingua conserva percorso, query e frammento della mappa. I contenuti personalizzati inseriti nel database non vengono tradotti automaticamente.

Logo originale del sito di progetto, palette verde #557f2d / #365d2b, accento azzurro #4b9fbd, sfondo #f6f8f3. Header, footer, login, management e amministrazione condividono il tema. Link al sito: https://opentreenap.altervista.org/. Il blog viene aggiornato separatamente.

## Aggiornamento Docker

Usare il file Compose già utilizzato nell'installazione. Esempio con v4.1:

```sh
git fetch origin
git switch rebrand/opentreenap-it-en
git pull --ff-only
docker compose -f docker-compose.modern-v4.1.yml up -d --build web worker
docker compose -f docker-compose.modern-v4.1.yml exec web python manage.py upgrade_opentreenap_branding
docker compose -f docker-compose.modern-v4.1.yml exec web python manage.py upgrade_opentreenap_branding --apply
```

Il comando prima mostra una simulazione. Aggiorna solamente colori corrispondenti esattamente ai vecchi valori standard e riferimenti a immagini identiche ai loghi originali OpenTreeMap. Conserva loghi e colori personalizzati, file media e contenuti. Non modifica alberi, geometrie, utenti, permessi o pagine personalizzate. Non richiede nuove migrazioni. L'entrypoint compila i cataloghi e gli asset; non copiare gli asset di una precedente build nella nuova immagine.

## Verifiche

Build Webpack di produzione completata; cataloghi gettext validati e compilati; controlli Django senza errori (sei avvisi preesistenti NullBooleanField). Cinque test verificano lingua predefinita, scelta inglese, URL e redirect sicuri, catalogo JavaScript e preservazione della configurazione durante l'aggiornamento dei colori.

```sh
python opentreemap/manage.py test opentreemap.test_rebranding --testrunner django.test.runner.DiscoverRunner
```

La verifica completa della mappa e dei dati richiede l'installazione con PostgreSQL/PostGIS e gli altri servizi. Non è stato effettuato un deployment sulla VPS da questo ambiente.
