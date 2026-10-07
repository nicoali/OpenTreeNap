# Home del progetto

La pagina `/` presenta OpenTreeNap e le istanze Napoli e Milano. La mappa
d'Italia usa un contorno geografico SVG derivato dai dati Natural Earth
1:50m, di pubblico dominio; la sorgente è indicata nel template. I
segnaposto e le schede aprono direttamente `/napoli/map/` e `/milano/map/`.
La navigazione funziona senza JavaScript e senza servizi cartografici
esterni. I testi sono disponibili in italiano e inglese.

Una sola query recupera l'esistenza e `is_public` delle due istanze,
senza leggere o contare gli alberi. La home mostra **Mappa pubblica** o
**Accesso riservato** secondo la configurazione esistente. Se un'istanza
non esiste ancora, mostra **In preparazione** e non genera un link
cliccabile. I permessi sono verificati dalle normali route delle istanze;
la home non pubblica istanze e non cambia l'accesso ai dati.

Il foglio di stile è limitato alla home tramite `#otn-home`. Header e
footer riutilizzano il tema, con footer visibile anche su mobile. Un blocco
viewport consente lo zoom nella nuova home. I template delle mappe
continuano a usare la configurazione precedente.

## Applicazione sulla VPS

**Completare l'importazione Milano prima di ricreare il container web.**
Un import lanciato con `docker compose exec web` termina se il suo
container viene ricreato, anche se è stato avviato con `nohup`.

Applicare il commit della home al branch attualmente usato sulla VPS
tramite cherry-pick. Non cambiare branch e non sovrascrivere le modifiche
locali del tiler. Poi, dalla directory `/opt/OpenTreeNap`:

```sh
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml up -d --build web
docker compose --env-file .env.modern-v4.1 -f docker-compose.modern-v4.1.yml logs --tail=80 web
```

L'entrypoint compila i cataloghi e raccoglie anche il nuovo CSS. Questa
modifica non richiede migrazioni aggiuntive, un'importazione o una modifica
alle istanze. Controllare `/`, i due link, il selettore IT/EN e il layout
su desktop e mobile dopo l'avvio. Milano rimane riservata finché non viene
pubblicata mediante il flusso di importazione già previsto.

## Verifica eseguita

Rendering dei template effettivi con il motore Django 3.2 in italiano e
inglese; controllati i casi istanza pubblica, riservata e assente con righe
DB simulate. Catalogo gettext validato con `msgfmt --check`, compilazione
Python e `git diff --check` completati. La verifica non ha usato il
database della VPS. La verifica visiva con Chromium non è stata possibile
in questo ambiente: il browser locale non è disponibile e il browser
remoto non può raggiungere il server di anteprima locale. Deployment e
verifica visiva sulla VPS restano da eseguire.
