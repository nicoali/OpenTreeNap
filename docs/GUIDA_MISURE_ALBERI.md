# Guida pubblica alle misure degli alberi

Pagina prevista sul sito: `opentreenap.altervista.org`.

## Obiettivo

Spiegare in modo semplice ma rigoroso come rilevare altezza e circonferenza dell'albero, sia con OpenTreeNap su smartphone sia con strumenti manuali.

## Regola fondamentale

Le misure sono opzionali. L'utente può:

1. inserirle manualmente se le ha misurate personalmente;
2. misurarle con gli strumenti guidati dello smartphone;
3. lasciare il campo non misurato / `Da misurare` per un contributore successivo.

Nessun valore viene inventato o stimato automaticamente senza che l'utente scelga esplicitamente un metodo di misura.

## Circonferenza manuale

- usare un metro flessibile o nastro dendrometrico;
- misurare il fusto a 1,30 m da terra, salvo casi particolari previsti dal protocollo;
- mantenere il nastro orizzontale e aderente al tronco;
- non stringere il nastro;
- annotare eventuali anomalie del fusto, biforcazioni o terreno inclinato;
- salvare il valore in centimetri.

## Circonferenza con smartphone

- usare la modalità `Misura circonferenza` quando il dispositivo è compatibile;
- l'app mostra la quota virtuale di 1,30 m;
- l'utente esegue la scansione guidata del tronco;
- OpenTreeNap mostra valore, qualità e margine di errore stimato;
- l'utente può confermare, ripetere oppure lasciare `Da misurare`.

## Altezza manuale

Metodi da documentare nella pagina:

- clinometro / inclinometro;
- telemetro laser, se disponibile;
- metodo trigonometrico con distanza nota e angolo alla cima/base;
- eventuale metodo dell'ombra come tecnica educativa/indicativa, chiarendo i limiti.

## Altezza con smartphone

- modalità AR guidata con punto base e punto cima;
- 3 misure consecutive;
- salvataggio della mediana;
- controllo automatico della dispersione;
- se tracking/qualità sono insufficienti, l'app chiede di ripetere.

## Metadati da salvare

Ogni misura dovrebbe poter registrare:

- valore;
- unità;
- metodo (`manuale`, `smartphone AR`, `clinometro`, `laser`, `importato`);
- data del rilievo;
- utente che ha effettuato la misura;
- qualità / errore stimato quando disponibile;
- stato (`misurato`, `da verificare`, `da misurare`).

## UX app

Per altezza e circonferenza usare sempre le tre scelte:

- `Inserisci misura`;
- `Misura con smartphone`;
- `Non ora / Da misurare`.

Se il dato è già presente, mostrare anche `Sostituisci / Aggiorna misura` con storico del valore precedente.

## Collegamento dalla app

Nella schermata di misura inserire un link `Come si misura?` che apre direttamente questa pagina guida sul sito OpenTreeNap.

## Contenuto editoriale consigliato

La pagina dovrebbe includere:

- illustrazione quota 1,30 m;
- esempio corretto/errato del nastro sul tronco;
- casi particolari (pendenza, contrafforti, biforcazioni);
- esempio clinometro;
- tutorial smartphone passo-passo;
- box `Non sei sicuro? Lascia Da misurare`;
- nota sulla differenza tra circonferenza e DBH.
