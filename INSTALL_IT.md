# Installare OpenTreeNap su Ubuntu 24.04

Questa guida installa **OpenTreeNap modern-v4.1** su un VPS Ubuntu nuovo.

La procedura è stata collaudata dall'inizio alla fine su un VPS pulito con **Ubuntu Server 24.04 LTS x86_64, 2 vCPU, 4 GB RAM, 2 GB swap e 40 GB disco**, arrivando fino alla creazione dell'istanza, Google Maps, inserimento manuale e importazione CSV tramite Bulk Uploader/Celery.

> **Importante:** la procedura è stata collaudata anche in assetto production con `OTM_DEBUG=0`, hostname No-IP, Caddy come reverse proxy, HTTPS automatico, Tiler pubblicato tramite `/tiles/`, media persistenti serviti da Caddy, cookie `Secure` e porte Docker applicative limitate a `127.0.0.1`. Restano comunque necessari backup automatici, aggiornamenti di sicurezza e manutenzione/hardening continuo.

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

Per il deployment HTTPS configura inoltre:

```dotenv
OTM_DEBUG=0
OTM_TRUST_PROXY_HEADERS=1
OTM_ALLOWED_HOSTS=<IP_PUBBLICO_VPS>,TUO_HOSTNAME,localhost,127.0.0.1
OTM_HTTP_PORT=8000
OTM_TILER_HTTP_PORT=4000
OTM_TILE_HOST=//TUO_HOSTNAME/tiles
OTM_MEDIA_ROOT=/tmp/otm/media
OTM_MEDIA_HOST_PATH=/srv/opentreenap/media
```

`OTM_TILE_HOST` è visibile al browser e deve usare lo stesso hostname HTTPS pubblicato da Caddy. `OTM_TRUST_PROXY_HEADERS=1` permette a Django di riconoscere correttamente HTTPS dietro il reverse proxy. `OTM_MEDIA_HOST_PATH` rende persistenti sul VPS gli upload che Django vede in `/tmp/otm/media`.

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

## 14. Firewall e porte applicative

La configurazione Compose corrente pubblica Web e Tiler solo sul loopback del VPS:

```text
127.0.0.1:8000
127.0.0.1:4000
```

Non aprire pubblicamente le porte `8000`, `4000`, PostgreSQL `5432` o Redis `6379`. L'accesso Internet deve passare da Caddy sulle sole porte HTTP/HTTPS.

Se UFW è attivo, conserva SSH e abilita:

```bash
ufw allow 80/tcp comment 'Caddy HTTP'
ufw allow 443/tcp comment 'Caddy HTTPS'
ufw status numbered
```

Verifica i binding locali:

```bash
ss -lntp | grep -E ':(8000|4000)\b'
```

Non devono comparire `0.0.0.0:8000` o `0.0.0.0:4000`.

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

6. dopo aver configurato HTTPS aggiungi anche:

```text
https://TUO_HOSTNAME/*
```

Esempio del deployment collaudato:

```text
https://opentreenap.ddns.net/*
```

7. in **API restrictions** limita la chiave a **Maps JavaScript API**.

Quando HTTPS è stato verificato completamente, il vecchio referrer HTTP con IP e porta `8000` può essere rimosso.

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

## 16. No-IP, Caddy e HTTPS

Il deployment collaudato usa un hostname DNS che punta all'IPv4 pubblico del VPS.

Esempio:

```text
opentreenap.ddns.net -> <IP_PUBBLICO_VPS>
```

Verifica la risoluzione:

```bash
getent ahostsv4 TUO_HOSTNAME
```

### Installare Caddy

Su Ubuntu 24.04:

```bash
apt install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' \
  | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg

curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' \
  | tee /etc/apt/sources.list.d/caddy-stable.list

chmod o+r /usr/share/keyrings/caddy-stable-archive-keyring.gpg
chmod o+r /etc/apt/sources.list.d/caddy-stable.list

apt update
apt install -y caddy
```

Verifica:

```bash
caddy version
systemctl is-active caddy
systemctl is-enabled caddy
```

### Firewall HTTP/HTTPS

```bash
ufw allow 80/tcp comment 'Caddy HTTP'
ufw allow 443/tcp comment 'Caddy HTTPS'
```

### Configurare Caddy

Il Tiler viene pubblicato sotto `/tiles/`, gli upload sotto `/media/`, mentre tutto il resto viene inoltrato a Django:

```caddy
TUO_HOSTNAME {
    handle_path /media/* {
        root * /srv/opentreenap/media
        file_server
    }

    handle_path /tiles/* {
        reverse_proxy 127.0.0.1:4000
    }

    handle {
        reverse_proxy 127.0.0.1:8000
    }
}
```

Sostituisci `TUO_HOSTNAME` con il dominio reale, quindi:

```bash
caddy validate --config /etc/caddy/Caddyfile
systemctl reload caddy
```

Caddy gestisce automaticamente il certificato TLS quando DNS, porte 80/443 e hostname sono configurati correttamente.

### Preparare i media persistenti

Prima di ricreare Web/Worker prepara la directory host:

```bash
mkdir -p /srv/opentreenap/media
chown root:root /srv/opentreenap/media
chmod 755 /srv/opentreenap
chmod 755 /srv/opentreenap/media
```

Con `OTM_MEDIA_HOST_PATH=/srv/opentreenap/media`, Compose monta questa directory come `/tmp/otm/media` sia nel container Web sia nel Worker. Caddy la serve in sola lettura dal punto di vista HTTP tramite `/media/*`; non viene esposto il resto del filesystem.

Dopo la modifica dell'ambiente ricrea Web e Worker:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  up -d --force-recreate --no-deps web worker
```

Puoi verificare il mount creando temporaneamente un file in `/tmp/otm/media` dal container e controllando che compaia in `/srv/opentreenap/media` sul VPS.

### Aggiornare OpenTreeNap

In `.env.modern-v4.1` aggiungi l'hostname a `OTM_ALLOWED_HOSTS`.

Esempio:

```dotenv
OTM_ALLOWED_HOSTS=<IP_PUBBLICO_VPS>,TUO_HOSTNAME,localhost,127.0.0.1
```

Imposta inoltre:

```dotenv
OTM_TILE_HOST=//TUO_HOSTNAME/tiles
```

Non usare più `//<IP_PUBBLICO_VPS>:4000` per il deployment HTTPS.

Ricrea il container web affinché rilegga l'ambiente:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  up -d --force-recreate --no-deps web
```

Verifica:

```bash
docker compose \
  -f docker-compose.modern-v4.1.yml \
  --env-file .env.modern-v4.1 \
  exec -T web python manage.py shell <<'PY'
from django.conf import settings
print("TILE_HOST:", settings.TILE_HOST)
PY
```

Il risultato deve essere equivalente a:

```text
TILE_HOST: //TUO_HOSTNAME/tiles
```

### Verificare HTTPS e Tiler

```bash
curl -I https://TUO_HOSTNAME/napoli/map/
curl -sS https://TUO_HOSTNAME/tiles/health-check
```

La pagina deve rispondere HTTP 200 e il Tiler deve riportare database e cache `ok`.

Apri quindi:

```text
https://TUO_HOSTNAME/napoli/map/
```

e verifica che la base Google Maps e i punti degli alberi siano entrambi visibili.

### Isolare le porte Docker

La configurazione Compose corrente pubblica Web e Tiler esclusivamente sul loopback del VPS:

```text
127.0.0.1:8000
127.0.0.1:4000
```

Verifica:

```bash
ss -lntp | grep -E ':(8000|4000)\b'
```

Non devono comparire binding `0.0.0.0:8000` o `0.0.0.0:4000`.

Se durante il collaudo iniziale erano state aggiunte regole UFW per queste porte, rimuovile:

```bash
ufw delete allow 8000/tcp
ufw delete allow 4000/tcp
ufw status numbered
```

Il firewall pubblico deve consentire almeno SSH, HTTP 80 e HTTPS 443, ma non deve essere usato come unico meccanismo per nascondere le porte pubblicate da Docker.

Da un computer esterno verifica:

```powershell
Test-NetConnection TUO_HOSTNAME -Port 443
Test-NetConnection TUO_HOSTNAME -Port 8000
Test-NetConnection TUO_HOSTNAME -Port 4000
```

Risultato atteso:

```text
443  -> True
8000 -> False
4000 -> False
```

Il deployment collaudato usa quindi questa catena:

```text
Internet
   |
   +-- 80/443
        |
      Caddy
        |
        +-- /media/* --> /srv/opentreenap/media (Caddy file server)
        |
        +-- /tiles/* --> 127.0.0.1:4000 (Tiler)
        |
        +-- tutto il resto --> 127.0.0.1:8000 (Django)
```

PostgreSQL e Redis restano interni e non devono essere esposti pubblicamente.

## 17. Test di inserimento manuale

Apri:

```text
https://TUO_HOSTNAME/napoli/map/
```

Accedi con il superuser, usa **Add a Tree**, seleziona un punto dentro l'istanza e salva.

Questo verifica autenticazione, permessi, scrittura PostgreSQL, aggiornamento mappa e tiler.

## 18. Bulk Uploader CSV

La pagina corretta è:

```text
/<nome-istanza>/management/bulk-uploader/
```

Esempio:

```text
https://TUO_HOSTNAME/napoli/management/bulk-uploader/
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

## 19. Comandi utili

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

## 20. Reset: attenzione

```bash
./modern-v4.1.sh reset
```

È **distruttivo**: elimina i volumi Docker v4.1 e i dati persistenti. L'helper richiede di digitare `RESET`.

Per fermare normalmente lo stack usa:

```bash
./modern-v4.1.sh down
```

## 21. Problemi noti

- Django è ancora 3.2 e il codice storico contiene alcuni `NullBooleanField` deprecati.
- Il worker Celery gira attualmente come root nel container e può mostrare il relativo warning.
- Il deployment pubblico collaudato usa `OTM_DEBUG=0`; non riattivare DEBUG su Internet salvo diagnostica temporanea e controllata.
- Google Maps può mostrare un warning sul caricamento senza `loading=async`; non blocca la mappa.
- Il frontend storico può mostrare warning JavaScript non bloccanti.
- OTM Tiler usa intenzionalmente una catena Node/Windshaft/Mapnik legacy isolata.

## 22. Sicurezza

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

Reverse proxy Caddy, HTTPS, `OTM_DEBUG=0`, cookie sicuri, statici WhiteNoise, media persistenti e isolamento delle porte applicative sono stati collaudati. Per un esercizio production continuativo restano essenziali backup automatici verificati, aggiornamenti di sicurezza, monitoraggio e ulteriore hardening applicativo.

## 23. Checklist finale

L'installazione production di base è riuscita quando:

- `doctor` passa;
- la build termina;
- db/redis/tiler/web sono healthy;
- worker risponde `pong`;
- db-tiler-init termina con codice 0;
- `/healthz/` restituisce database `ok`;
- superuser e istanza vengono creati;
- `/<istanza>/map/` funziona dal browser tramite HTTPS;
- Caddy serve correttamente il certificato TLS;
- Django gira con `OTM_DEBUG=0` e riconosce HTTPS dietro Caddy;
- i cookie di sessione e CSRF sono marcati `Secure`;
- gli statici vengono serviti correttamente con WhiteNoise;
- gli upload persistono in `/srv/opentreenap/media` e sono raggiungibili tramite `/media/`;
- il Tiler funziona tramite `/tiles/`;
- le porte 8000 e 4000 ascoltano solo su `127.0.0.1`;
- dall'esterno 443 è raggiungibile mentre 8000 e 4000 non lo sono;
- Google Maps non restituisce errori billing/referrer;
- un albero può essere aggiunto manualmente;
- Bulk Uploader raggiunge `Verification Complete`;
- un CSV valido viene aggiunto alla Tree Map tramite Celery;
- dati importati e mappa risultano coerenti.

A questo punto lo stack fondamentale OpenTreeNap è operativo.
