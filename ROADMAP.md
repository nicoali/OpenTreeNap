# OpenTreeNap Roadmap

Questa roadmap raccoglie il lavoro approvato per portare OpenTreeNap da prototipo funzionante a piattaforma completa e coerente tra:

- istanza OpenTreeMap / OTN;
- app Android OpenTreeNap;
- sito pubblico `opentreenap.altervista.org`;
- schede botaniche;
- alberi monumentali;
- contributi degli utenti autenticati.

Ultimo aggiornamento: 2026-10-02.

## Stato attuale

### Completato

- [x] Rebrand OpenTreeNap IT/EN.
- [x] Mappa OTN Napoli operativa.
- [x] App Android V0.3 con Google Maps.
- [x] Clustering alberi.
- [x] Caricamento paginato inventario.
- [x] Filtro alberi monumentali.
- [x] Marker dedicati.
- [x] Scheda albero base.
- [x] Login con account OTN.
- [x] Lettura permessi account.
- [x] Modifica base albero.
- [x] Aggiunta albero da app.
- [x] Collegamento alle schede botaniche del sito.
- [x] Correzione validazione HMAC UTC lato server.
- [x] UI edge-to-edge con gestione inset di sistema.

---

# Sprint 1 — Performance e mappa

Obiettivo: rendere l'app veloce anche con migliaia di alberi.

**Sprint completato e verificato su dispositivo (2026-10-02).** Cold start senza cache: ~3 s per 684 alberi; secondo avvio quasi immediato grazie alla cache locale. Piano tecnico e test: [android/PERFORMANCE.md](android/PERFORMANCE.md).

- [x] Mostrare rapidamente dati già disponibili/cached all'avvio.
- [x] Aggiornamento dati in background senza bloccare la UI.
- [x] Evitare refresh completo della mappa ad ogni piccola interazione.
- [x] Migrare dal clustering artigianale a Google Maps Utility Library / ClusterManager.
- [x] Renderer cluster personalizzato OpenTreeNap.
- [x] Differenziare cluster normali e monumentali.
- [x] Caricare il dettaglio completo di un albero solo al tap.
- [x] Preparare una strategia di caricamento per area visibile / bounding box.
- [x] Valutare cache locale strutturata (JSON atomica ora; Room/DB rimandato a inventari molto più grandi).
- [x] Stato di caricamento discreto e non invasivo.

# Sprint 2 — UI/UX professionale OTN

Obiettivo: eliminare i dialog Android standard e dare all'app un'identità coerente.

**Sprint completato e verificato su dispositivo (2026-10-02).** Material 3, bottom sheet albero, login/account OTN, editor guidato add/edit, palette OTN, Snackbar/error sheet ed edge-to-edge sono stati verificati visivamente e funzionalmente. Checklist: [android/UI_SPRINT2.md](android/UI_SPRINT2.md).

- [x] Bottom sheet professionale per la scheda albero.
- [x] Bottom sheet/account panel per login, profilo e logout.
- [x] Schermata/modale professionale per modifica albero.
- [x] Flow guidato per aggiunta nuovo albero.
- [x] Palette coerente OTN verde / navy / crema / oro.
- [x] Migliorare tipografia, spaziature e pulsanti.
- [x] Gestione definitiva edge-to-edge e system insets.
- [x] Stati vuoti, loading e errori nello stile OpenTreeNap.
- [x] Migliorare accessibilità e dimensioni touch target.

# Sprint 3 — Immagini botaniche condivise

Obiettivo: usare una sola fonte di immagini/specie in app e OTN.

**Sprint 3 — flusso WordPress→Android verificato:** il manifest REST automatico è attivo e Android 0.5.1 ha caricato automaticamente la rappresentativa di `Platanus occidentalis` dalla Media Library senza manifest statico né nuova APK. Resta solo la verifica priorità foto reale OTN prima della chiusura definitiva. Dettagli: [android/BOTANICAL_IMAGES.md](android/BOTANICAL_IMAGES.md).

- [x] Definire un manifest condiviso specie -> immagine botanica.
- [x] Associare nome scientifico, URL immagine e pagina botanica tramite manifest WordPress; `species_id` resta risolto lato OTN/app.
- [x] Usare come fallback l'immagine botanica rappresentativa della specie se l'albero non ha foto.
- [x] Usare foto reale dell'albero quando disponibile.
- [x] Fallback finale con icona OpenTreeNap generica.
- [x] Integrare la stessa immagine di default nella scheda OTN web.
- [x] Integrare la stessa immagine nella scheda Android.
- [x] Precaricamento e cache immagini.
- [x] WebP ottimizzati e responsive tramite Media Library/WordPress e rendering adattivo nell'app.
- [x] Gestire versionamento/invalidazione cache immagini con timestamp URL, ETag e TTL.

# Sprint 4 — Scheda albero completa

Obiettivo: trasformare il dettaglio albero in una vera scheda informativa.

- [ ] Foto.
- [ ] Nome comune.
- [ ] Nome scientifico.
- [ ] Indirizzo.
- [ ] DBH / diametro.
- [ ] Altezza.
- [ ] Custom ID / MASAF quando presente.
- [ ] ID OTN.
- [ ] Stato monumentale.
- [ ] Dati UDF rilevanti.
- [ ] Ultimo aggiornamento.
- [ ] Autore/utente dell'ultima modifica, quando disponibile.
- [ ] Pulsante Scheda botanica.
- [ ] Pulsante Modifica.
- [ ] Pulsante Foto.
- [ ] Condivisione.
- [ ] Indicazioni stradali.
- [ ] QR dedicato.

# Sprint 5 — Contributi utenti

Obiettivo: consentire contributi controllati dal telefono.

- [ ] Login persistente sicuro.
- [ ] Logout.
- [ ] Recupero password.
- [ ] Registrazione account.
- [ ] Aggiunta nuovo albero.
- [ ] Modifica albero esistente.
- [ ] Upload foto.
- [ ] Gestione permessi reali OTN.
- [ ] Segnalazione errori/danni.
- [ ] Storico modifiche utente.
- [ ] Conferma prima delle modifiche distruttive.
- [ ] Gestione eventuali pending edits / moderazione.

# Sprint 6 — Alberi monumentali

Obiettivo: valorizzare il patrimonio monumentale di Napoli.

- [ ] Pin dedicato oro/monumentale.
- [ ] Badge "Albero Monumentale d'Italia".
- [ ] Filtro dedicato.
- [ ] Campi MASAF e identificativi ufficiali.
- [ ] Scheda monumentale estesa.
- [ ] Foto/storia/curiosità.
- [ ] Percorso monumentali.
- [ ] Condivisione dedicata.
- [ ] Eventuale layer separato sulla mappa.

# Sprint 7 — Schede botaniche

Obiettivo: unificare contenuti scientifici e divulgativi.

- [ ] Scheda botanica seria/scientifica.
- [ ] Fronte/retro per versione bambini.
- [ ] Collegamento tra OTN, Android e sito.
- [ ] Tassonomia coerente.
- [ ] Immagine botanica standard per specie.
- [ ] Dati morfologici.
- [ ] Habitat e distribuzione.
- [ ] Fioritura/fruttificazione.
- [ ] Curiosità.
- [ ] Stato/uso urbano.
- [ ] Eventuale gioco collezione/figurine.

# Sprint 8 — Ricerca e navigazione

- [ ] Ricerca specie.
- [ ] Ricerca indirizzo.
- [ ] Ricerca ID albero.
- [ ] Ricerca Custom ID / MASAF.
- [ ] Alberi vicini.
- [ ] Distanza dall'utente.
- [ ] Filtri avanzati.
- [ ] Percorsi botanici.
- [ ] Itinerari.
- [ ] Preferiti.
- [ ] Alberi visitati.

# Sprint 9 — Benefici ecosistemici

- [ ] Valutare integrazione i-Tree.
- [ ] CO2.
- [ ] Ombreggiamento.
- [ ] Acqua intercettata.
- [ ] Qualità dell'aria.
- [ ] Valore ambientale.
- [ ] Presentazione divulgativa semplice.
- [ ] Dettaglio tecnico opzionale.

# Sprint 10 — Capodimonte e aree speciali

- [ ] Layer Capodimonte.
- [ ] Alberi storici/centenari.
- [ ] Percorsi botanici tematici.
- [ ] Contenuti dedicati.
- [ ] Punti di interesse.
- [ ] Modalità visita guidata.

# Sprint 11 — Sicurezza e pubblicazione

- [ ] Rimuovere il secret HMAC dall'APK pubblico.
- [ ] Mobile API/proxy server-side OpenTreeNap.
- [ ] Token/sessione client-safe.
- [ ] Rate limiting.
- [ ] Logging API.
- [ ] Crash reporting.
- [ ] Gestione offline.
- [ ] Privacy policy.
- [ ] Preparazione Play Store.
- [ ] Firma release.
- [ ] Versionamento stabile.

# Priorità operativa

1. Performance mappa e caricamento.
2. UI/UX professionale.
3. Immagini botaniche condivise.
4. Scheda albero completa.
5. Add/edit/foto con account.
6. Monumentali.
7. Ricerca e percorsi.
8. Benefici ecosistemici.
9. Modalità bambini e contenuti speciali.
10. Hardening sicurezza e pubblicazione.

## Regola di sviluppo

Ogni sprint deve:

1. rimanere compatibile con l'istanza `napoli`;
2. rispettare i permessi OTN;
3. non compromettere i dati esistenti;
4. essere testato prima su branch dedicata;
5. essere approvato visivamente/funzionalmente prima del merge;
6. aggiornare questa roadmap marcando gli elementi completati.
