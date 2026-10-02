# Android Sprint 2 — UI/UX professionale OpenTreeNap

Branch di sviluppo: `feature/android-sprint2-ui`

Obiettivo: sostituire l'aspetto da prototipo Android con un'interfaccia coerente con OpenTreeNap, mantenendo la mappa come elemento principale.

## Implementato

### Material 3 e palette OTN

- tema Material 3;
- palette OpenTreeNap: verde, verde scuro, navy, crema, oro e superfici neutre;
- barre di sistema trasparenti con edge-to-edge;
- gestione degli inset mantenuta per header, filtri, controlli e schede;
- pulsanti e superfici con angoli/spaziature coerenti.

### Scheda albero

La vecchia card statica è ora un vero `BottomSheetBehavior`:

- si apre dal basso al tap sull'albero;
- può essere trascinata e chiusa;
- quando viene chiusa il marker selezionato torna allo stato normale;
- i controlli mappa ricompaiono automaticamente;
- la mappa riceve padding dinamico in base all'altezza reale della scheda;
- pulsanti Material per Scheda botanica e Modifica;
- safe-area inferiore dedicata per evitare sovrapposizioni con la barra di navigazione Android.

### Account e login

I dialog Android standard sono stati sostituiti da bottom sheet OTN:

- login con campi Material;
- password visibility toggle;
- supporto autofill;
- errori inline senza popup grezzi;
- account panel con nome, username, email e permessi effettivi;
- logout dal pannello account.

### Aggiunta / modifica albero

Il flow di aggiunta è guidato:

1. Passo 1: selezione del punto sulla mappa.
2. Passo 2: bottom sheet con specie, DBH e altezza.

L'editor:

- usa una ricerca/selezione specie Material;
- precompila i dati quando si modifica un albero;
- mostra la posizione selezionata;
- visualizza errori inline;
- mantiene i permessi OTN già presenti.

### Messaggi ed errori

- messaggi brevi tramite Snackbar con colori OTN;
- errori API importanti tramite bottom sheet dedicato;
- eliminati i principali AlertDialog standard.

## Test su dispositivo

1. Avvio mappa: prestazioni Sprint 1 invariate.
2. Tap albero: bottom sheet fluido e marker selezionato.
3. Swipe verso il basso: scheda chiusa, marker ripristinato, controlli mappa visibili.
4. Scheda botanica: link ancora funzionante.
5. Account non autenticato: apertura login OTN.
6. Login valido: pannello account con permessi.
7. Login errato: errore inline, sheet resta aperto.
8. Aggiungi albero: messaggio "Passo 1 di 2", tap mappa, editor "Passo 2 di 2".
9. Ricerca specie: dropdown filtrabile.
10. Modifica albero: specie/DBH/altezza precompilati.
11. Tastiera: nessuna sovrapposizione con i campi o i pulsanti.
12. Status bar e navigation bar: nessuna sovrapposizione.
13. Filtri Tutti/Monumentali ancora funzionanti.
14. Rotazione/panoramica/zoom non devono lasciare sheet o controlli in stato incoerente.

## Chiusura Sprint 2

Le checkbox della roadmap verranno marcate completate dopo verifica visiva e funzionale su dispositivo reale.


## Verifica finale

Test visivo/funzionale su dispositivo reale del 2026-10-02:

- bottom sheet albero stabile e leggibile;
- pulsanti "Scheda botanica" e "Modifica" allineati, monoriga e completamente visibili sopra la navigation bar;
- palette OTN coerente tra verde, navy, crema e oro;
- badge Monumentale distinto e leggibile;
- dati albero strutturati con ID/DBH/altezza quando disponibili;
- editor specie più pulito;
- stato header mantiene inventario e account;
- cluster e marker mantengono lo stile OTN;
- login/account bottom sheet verificati.

Lo Sprint 2 è considerato completato.
