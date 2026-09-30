# Installare OpenTreeNap su Ubuntu 24.04

Questa guida installa **OpenTreeNap modern-v4.1** su un VPS Ubuntu nuovo.

La procedura è stata collaudata dall'inizio alla fine su un VPS pulito con **Ubuntu Server 24.04 LTS x86_64, 2 vCPU, 4 GB RAM, 2 GB swap e 40 GB disco**, arrivando fino alla creazione dell'istanza, Google Maps, inserimento manuale e importazione CSV tramite Bulk Uploader/Celery.

> **Importante:** modern-v4.1 è ancora un deployment di sviluppo/bridge. Questa guida valida lo stack applicativo, ma non sostituisce un futuro deployment production con reverse proxy, HTTPS, hardening, backup e `OTM_DEBUG=0`.

## 1. Requisiti

Configurazione consigliata:

| Risorsa | Valore |
|---|---:|
| OS | Ubuntu Server 24.04 LTS x86_64 |
| CPU | 2 vCPU |
| RAM | 4 GB |
| Swap | 2 GB |
| Disco | 40 GB |
| Rete | IPv4 pubblico |

Servono accesso SSH, Git, Docker Engine e Docker Compose v2+.

Gli esempi assumono una shell `root`. Con un utente normale usa `sudo` dove necessario.

## 2. Aggiornare Ubuntu

```bash
apt update
apt full-upgrade -y
```

Se il provider propone di sostituire un proprio file modificato, per esempio `/etc/cloud/cloud.cfg`, valuta le personalizzazioni del provider prima di sovrascriverlo.

Se viene richiesto:

```bash
reboot
```

Dopo il riavvio:

```bash
cat /etc/os-release
uname -m
free -h
df -h /
```

L'architettura prevista è `x86_64`.

## 3. Swap

Controlla prima:

```bash
free -h
swapon --show
```

Con 4 GB RAM sono consigliati circa 2 GB di swap durante build e test.

**Non cancellare alla cieca la swap configurata dal provider.** Se è già adeguata, passa oltre.

Nel VPS usato per il collaudo esisteva `/swap` da 512 MB; è stata portata a 2 GB con:

```bash
swapoff /swap
rm -f /swap
fallocate -l 2G /swap
chmod 600 /swap
mkswap /swap
swapon /swap
```

Verifica:

```bash
free -h
swapon --show
grep -n swap /etc/fstab
```

Se `/etc/fstab` contiene già la riga per `/swap`, non aggiungerne una seconda.

## 4. Docker dal repository ufficiale

```bash
apt update
apt install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
```

Crea il repository:

```bash
cat > /etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: noble
Components: stable
Architectures: amd64
Signed-By: /etc/apt/keyrings/docker.asc
EOF

apt update
```

Installa Docker:

```bash
apt install -y \
  docker-ce \
  docker-ce-cli \
  containerd.io \
  docker-buildx-plugin \
  docker-compose-plugin
```

Verifica:

```bash
docker --version
docker compose version
docker buildx version
systemctl is-active docker
systemctl is-enabled docker
docker run --rm hello-world
```

Il test `hello-world` deve riuscire.

## 5. Clone del repository

```bash
cd /opt
git clone https://github.com/nicoali/OpenTreeNap.git
cd /opt/OpenTreeNap
git status -sb
```

## 6. Configurare .env.modern-v4.1

```bash
cp .env.modern-v4.1.example .env.modern-v4.1
chmod 600 .env.modern-v4.1
```

Genera password/secret casuali:

```bash
openssl rand -hex 24
openssl rand -hex 48
openssl rand -hex 24
```

Usa i tre valori rispettivamente per:

```dotenv
OTM_DB_PASSWORD=<PASSWORD_DB>
OTM_SECRET_KEY=<SECRET_DJANGO>
OTM_TILER_DB_PASSWORD=<PASSWORD_TILER>
```

Non pubblicare questi valori.

Per il primo collaudo remoto configura inoltre:

```dotenv
OTM_DEBUG=1
OTM_ALLOWED_HOSTS=<IP_PUBBLICO_VPS>,localhost,127.0.0.1
OTM_HTTP_PORT=8000
OTM_TILER_HTTP_PORT=4000
OTM_TILE_HOST=//<IP_PUBBLICO_VPS>:4000
```

`OTM_TILE_HOST` è visibile al browser: su un VPS remoto non deve puntare a `localhost`.

Non condividere mai l'intero file `.env.modern-v4.1`.

## 7. Doctor e configurazione Compose

```bash
chmod +x modern-v4.1.sh
./modern-v4.1.sh doctor
```

Elenca i servizi:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  config --services
```

Devono comparire `db`, `redis`, `db-tiler-init`, `tiler`, `web` e `worker`.

## 8. Build

```bash
./modern-v4.1.sh build
```

La prima build può richiedere alcuni minuti.

Controlla poi lo spazio:

```bash
df -h /
docker system df
```

Non usare automaticamente `docker system prune` su un server che contiene dati utili.

## 9. Avvio completo

```bash
./modern-v4.1.sh up
./modern-v4.1.sh status
```

Stato atteso:

- `db`: healthy
- `redis`: healthy
- `tiler`: healthy
- `web`: healthy
- `worker`: running
- `db-tiler-init`: può risultare `Exited (0)`; è normale, perché è un job di inizializzazione.

Health Django:

```bash
./modern-v4.1.sh smoke
```

Risposta attesa:

```json
{"status": "ok", "database": "ok"}
```

Controllo Django:

```bash
./modern-v4.1.sh check
```

Nello stack attuale possono apparire sei warning `fields.W903` relativi a `NullBooleanField`: sono deprecazioni note del codice storico sotto Django 3.2, non un fallimento dell'installazione.

## 10. Celery

Il worker parte automaticamente con lo stack.

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T worker \
  celery -A opentreemap inspect ping
```

Atteso:

```text
-> celery@...: OK
    pong
1 node online.
```

Celery usa Redis come broker e result backend; è necessario anche per il Bulk Uploader.

## 11. Creare il superuser

```bash
./modern-v4.1.sh superuser
```

Conserva lo username scelto: servirà per creare l'istanza.

## 12. Creare la prima istanza

Esempio Napoli:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T web \
  python manage.py create_instance \
  "OpenTreeNap Napoli" \
  --user TUO_USERNAME \
  --url_name napoli \
  --center=14.2681,40.8518
```

`--center` usa l'ordine **longitudine,latitudine**.

Controlla:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T web \
  python manage.py shell -c "
from treemap.instance import Instance
from treemap.models import InstanceUser
for i in Instance.objects.all():
    print('id=',i.id,'| name=',i.name,'| url_name=',i.url_name,'| public=',i.is_public)
    for iu in InstanceUser.objects.filter(instance=i):
        print('  user=',iu.user.username,'| admin=',iu.admin,'| role=',iu.role.name if iu.role else None)
"
```

L'URL `/napoli/` reindirizza normalmente a `/napoli/map/`.

## 13. Test locale Web e Tiler

```bash
curl -sS -L \
  -o /tmp/opentreenap-map.html \
  -w "HTTP=%{http_code} REDIRECTS=%{num_redirects} SIZE=%{size_download}\n" \
  http://127.0.0.1:8000/napoli/
```

Deve terminare con HTTP 200.

Tiler:

```bash
curl -sS http://127.0.0.1:4000/health-check
```

La risposta deve indicare `ok: true` per database e cache.

Porte:

```bash
ss -lntp | grep -E ':(8000|4000)\b'
```

## 14. Firewall per il collaudo

Se UFW è attivo e consente solo SSH:

```bash
ufw allow 8000/tcp comment 'OpenTreeNap web'
ufw allow 4000/tcp comment 'OpenTreeNap tiler'
ufw status numbered
```

**Non aprire PostgreSQL 5432 né Redis 6379.**

Se il provider ha un firewall/security group separato, può essere necessario consentire 8000/TCP e 4000/TCP anche lì.

Dal PC:

```text
http://<IP_PUBBLICO_VPS>:8000/napoli/
```

Queste porte dirette servono al collaudo development/bridge. Un deployment pubblico definitivo dovrà preferire reverse proxy e HTTPS.

## 15. Google Maps

OpenTreeNap legge:

```dotenv
GOOGLE_MAPS_KEY=
```

Nel progetto Google Cloud:

1. abilita **Maps JavaScript API**;
2. collega il **Billing**;
3. crea una API key;
4. in **Application restrictions** scegli **Websites / HTTP referrers**;
5. autorizza per il test:

```text
http://<IP_PUBBLICO_VPS>:8000/*
```

6. in **API restrictions** limita la chiave a **Maps JavaScript API**.

Non scegliere `IP addresses`: Maps JavaScript API viene eseguita nel browser e la restrizione corretta è il referrer web.

### Inserimento sicuro della chiave

Non mettere la chiave direttamente in un comando salvato nella shell history.

```bash
cd /opt/OpenTreeNap
read -rsp "Incolla Google Maps API key UNA SOLA VOLTA e premi INVIO: " GOOGLE_MAPS_KEY
echo
export GOOGLE_MAPS_KEY
```

Salvala:

```bash
python3 - <<'PY'
import os
from pathlib import Path

path = Path(".env.modern-v4.1")
key = os.environ.get("GOOGLE_MAPS_KEY", "").strip()
if not key:
    raise SystemExit("ERRORE: chiave vuota")

lines = path.read_text().splitlines()
out = []
found = False
for line in lines:
    if line.startswith("GOOGLE_MAPS_KEY="):
        out.append("GOOGLE_MAPS_KEY=" + key)
        found = True
    else:
        out.append(line)
if not found:
    out.append("GOOGLE_MAPS_KEY=" + key)

path.write_text("\n".join(out) + "\n")
print("OK: GOOGLE_MAPS_KEY salvata")
print("Lunghezza:", len(key))
PY

unset GOOGLE_MAPS_KEY
chmod 600 .env.modern-v4.1
```

Ricrea `web`, perché Docker deve rileggere `env_file`:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  up -d --force-recreate --no-deps web
```

Verifica senza stampare la chiave:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T web \
  python manage.py shell -c "
from django.conf import settings
k=getattr(settings,'GOOGLE_MAPS_API_KEY',None)
print('Chiave caricata:',bool(k))
print('Lunghezza:',len(k) if k else 0)
"
```

Se la mappa mostra `For development purposes only`, apri F12 → Console e leggi solo il nome dell'errore, senza condividere la riga `js?key=...`.

Errori verificati durante il collaudo:

- `BillingNotEnabledMapError`: billing non attivo/collegato;
- `RefererNotAllowedMapError`: referrer del sito non autorizzato.

Se una API key viene pubblicata accidentalmente, revocala/ruotala.

## 16. Test di inserimento manuale

Apri:

```text
http://<IP_PUBBLICO_VPS>:8000/napoli/map/
```

Accedi con il superuser, usa **Add a Tree**, seleziona un punto dentro l'istanza e salva.

Questo verifica autenticazione, permessi, scrittura PostgreSQL, aggiornamento mappa e tiler.

## 17. Bulk Uploader CSV

La pagina corretta è:

```text
/<nome-istanza>/management/bulk-uploader/
```

Esempio:

```text
http://<IP_PUBBLICO_VPS>:8000/napoli/management/bulk-uploader/
```

### Template Tree: 21 colonne

```text
Point X
Point Y
Street Address
City
Postal Code
Planting Site Width
Planting Site Length
Planting Site Id
Custom Id
Tree Id
Tree Present
Genus
Species
Cultivar
Other Part Of Name
Common Name
Diameter
Tree Height
Canopy Height
Date Planted
Date Removed
```

Per nuovi record:

- `Point X` = longitudine;
- `Point Y` = latitudine;
- `Planting Site Id` = vuoto;
- `Tree Id` = vuoto;
- `Custom Id` = eventuale ID del dataset sorgente.

`Planting Site Id` e `Tree Id` sono ID interni OpenTreeNap/OpenTreeMap e vengono assegnati dalla piattaforma.

Un sito senza albero usa:

```text
Tree Present=False
```

Se è noto solo il genere, non inventare la specie:

```csv
Genus,Species
Magnolia,
```

### Workflow

1. scarica il template Tree dal Bulk Uploader;
2. prepara il CSV;
3. prova inizialmente un piccolo campione;
4. carica il file;
5. attendi **Verification Complete**;
6. apri **View/Vista**;
7. controlla `Ready to Add`, `Errors`, `Warnings`;
8. se tutto è corretto, premi **Add to Tree Map** una sola volta;
9. attendi Celery;
10. controlla mappa e database.

### Collaudo reale

Nel test completo è stato caricato un CSV da 638 righe.

Prima del commit:

```text
Ready to Add: 638
Errors: 0
Warnings: 0
```

Dopo il commit il database conteneva:

```text
Planting site importati: 638
Alberi importati: 637
Planting site senza albero: 1
Custom Id distinti: 638
```

Sono stati verificati anche un record senza albero e record tassonomici con solo il genere valorizzato.

## 18. Comandi utili

```bash
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
```

Health tiler:

```bash
curl -sS http://127.0.0.1:4000/health-check
```

Spazio:

```bash
df -h /
docker system df
```

## 19. Reset: attenzione

```bash
./modern-v4.1.sh reset
```

È **distruttivo**: elimina i volumi Docker v4.1 e i dati persistenti. L'helper richiede di digitare `RESET`.

Per fermare normalmente lo stack usa:

```bash
./modern-v4.1.sh down
```

## 20. Problemi noti

- Django è ancora 3.2 e il codice storico contiene alcuni `NullBooleanField` deprecati.
- Il worker Celery gira attualmente come root nel container e può mostrare il relativo warning.
- `OTM_DEBUG=1` è adatto al collaudo, non a un deployment pubblico definitivo.
- Google Maps può mostrare un warning sul caricamento senza `loading=async`; non blocca la mappa.
- Il frontend storico può mostrare warning JavaScript non bloccanti.
- OTM Tiler usa intenzionalmente una catena Node/Windshaft/Mapnik legacy isolata.

## 21. Sicurezza

Non pubblicare mai:

- `.env.modern-v4.1`;
- password database/tiler;
- `OTM_SECRET_KEY`;
- Google Maps API key;
- password utenti;
- token GitHub;
- chiavi SSH private.

Mantieni:

```bash
chmod 600 .env.modern-v4.1
```

PostgreSQL e Redis non devono essere esposti pubblicamente.

Prima di considerare OpenTreeNap production-ready servono ancora almeno reverse proxy, HTTPS, hardening, backup e una configurazione `OTM_DEBUG=0` collaudata.

## 22. Checklist finale

L'installazione development/bridge è riuscita quando:

- `doctor` passa;
- la build termina;
- db/redis/tiler/web sono healthy;
- worker risponde `pong`;
- db-tiler-init termina con codice 0;
- `/healthz/` restituisce database `ok`;
- superuser e istanza vengono creati;
- `/<istanza>/map/` funziona dal browser;
- Google Maps non restituisce errori billing/referrer;
- un albero può essere aggiunto manualmente;
- Bulk Uploader raggiunge `Verification Complete`;
- un CSV valido viene aggiunto alla Tree Map tramite Celery;
- dati importati e mappa risultano coerenti.

A questo punto lo stack fondamentale OpenTreeNap è operativo.
