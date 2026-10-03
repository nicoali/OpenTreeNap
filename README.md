<p align="center">
  <img src="docs/images/opentreenap-banner.png" alt="OpenTreeNap — modernized OpenTreeMap stack born in Naples, Italy" width="100%">
</p>

# 🌳 OpenTreeNap

**English** | [🇮🇹 Italiano](README_IT.md)

**OpenTreeNap** is a modernized, Docker-based development environment derived from the original **OpenTreeMap** platform, with a focus on collaborative urban tree mapping.

Born in **Naples (Napoli), Italy 🇮🇹**, the project aims to give the historical OpenTreeMap codebase a contemporary technical environment while keeping it useful for experimentation and urban-forestry projects in Naples and beyond.

> **Status:** active modernization and development project.  
> OpenTreeNap is not yet intended as a production-ready public deployment.

---

## 🌋 Why OpenTreeNap?

OpenTreeNap was born from two ideas: preserving and modernizing the historical OpenTreeMap platform, and exploring how open-source urban-forestry tools can support tree mapping in Naples.

The name combines **OpenTreeMap** with **Napoli**.

Naples is the project's home and inspiration, but OpenTreeNap is not intended to be limited to one city. The goal is to keep the platform reusable for other communities, researchers, developers and urban-forestry initiatives.

The current development environment supports:

- interactive tree maps
- trees and planting sites
- tree detail panels
- species data
- OpenStreetMap basemaps
- PostgreSQL/PostGIS spatial data
- Mapnik/Windshaft tile rendering
- PNG tiles and UTFGrid interaction
- Leaflet map interaction
- Django administration
- persistent Docker storage

---

## 🗺️ Current milestone

A real development instance has been created and tested at `/napoli/map/` on a fresh Ubuntu 24.04 VPS.

The complete tree-rendering path has been validated:

**PostgreSQL/PostGIS → OpenTreeMap/Django → OTM Tiler → Windshaft/Mapnik → PNG + UTFGrid → Leaflet → interactive tree markers**

Persisted trees can be rendered on the map and selected to display their details.

The Docker stack has also been validated from a **fresh Ubuntu 24.04 VPS and fresh Git clone with completely new Docker volumes**, including PostgreSQL initialization, Redis, Django startup, Celery, static-file collection, OTM Tiler health checks, Google Maps configuration, manual tree creation and a 638-row Bulk Uploader import.

---

## ✨ OpenTreeNap extensions beyond the historical OTM stack

OpenTreeNap is not only a compatibility port. Development is adding a set of
OpenTreeNap-specific features on top of the historical OpenTreeMap codebase and
its legacy mobile workflow.

**Status legend:** ✅ implemented/tested · 🧪 active prototype or field validation · 🛠️ in development/planned.

### Web, backend and data platform

- ✅ **Italian/English OpenTreeNap rebrand** with a refreshed public-facing UI,
  consistent branding and responsive tree-detail presentation.
- ✅ **Modern Docker deployment** for the historical OTM application, including
  PostgreSQL/PostGIS, Redis, Celery, Gunicorn and the isolated legacy
  Windshaft/Mapnik tiler.
- ✅ **Mobile-optimized API path** with compact paginated tree inventory,
  lazy-loaded full tree details and stable mobile metadata.
- ✅ **Extended tree metadata for mobile clients**, including scalar UDFs,
  latest-update information, author when available and a public detail URL.
- ✅ **Improved Naples address/geocoding workflow**, including OpenStreetMap
  Nominatim integration, handling of approximate matches and preservation of a
  manually entered civic number when the geocoder cannot resolve it exactly.
- ✅ **Monumental-tree support** with dedicated status/metadata and map styling;
  Android includes dedicated marker, badge and filter behaviour.
- ✅ **Shared botanical-image system**: WordPress/Media Library is the source of
  the species-image manifest consumed by both the OTN web interface and the
  Android app, with caching and fallback handling.
- ✅ **Botanical image fallback hierarchy**:
  real tree photo → representative species image → OpenTreeNap placeholder.
- ✅ **Measurement metadata/UDF infrastructure** for method, status, quality,
  survey date, circumference and estimated-error fields.
- ✅ **Python 3 compatibility fixes** in legacy UDF serialization paths required
  by the richer mobile detail API.

### Modern Android client

OpenTreeNap includes a new Android client that replaces the obsolete build and
networking stack of the historical OTM Android application while preserving
API-v4/HMAC compatibility during development.

- ✅ **Fast map startup** with cache-first loading, background refresh and
  paginated inventory retrieval.
- ✅ **Google Maps clustering** with OpenTreeNap rendering and visual treatment
  for clusters containing monumental trees.
- ✅ **Material 3 OpenTreeNap UI**, bottom sheets, guided add/edit flows,
  edge-to-edge layout and inline API/error handling.
- ✅ **OTN account login and real instance permissions** for adding and editing
  trees from the phone.
- ✅ **Complete tree detail actions** including botanical sheet, structured
  data, directions, sharing and locally generated QR code.
- ✅ **Tree details on demand** rather than loading heavy metadata for the whole
  inventory.
- ✅ **Manual dendrometric entry** for height and circumference at 1.30 m, with
  DBH-equivalent derivation where appropriate.
- 🧪 **Smartphone height measurement prototype** using CameraX and orientation
  sensors as a clinometer, three repeated measurements, median, dispersion,
  confirmation before saving and measurement-quality metadata.
- 🧪 **Clinometer calibration/field validation** is currently active; assisted
  measurements are still treated as prototype data until validated against
  known references and professional instruments.
- 🛠️ **ARCore-guided height measurement** is planned to replace manual distance
  entry on compatible devices.
- 🛠️ **ARCore Depth / point-cloud circumference measurement at 1.30 m** is under
  development, with robust section fitting and explicit quality controls.

### Botanical, educational and field-work roadmap

- 🛠️ **Assisted species recognition** from one or more photos, with BioCLIP as
  the planned self-hosted primary model, candidates restricted to the local OTN
  species catalogue and mandatory human confirmation.
- 🛠️ **Scientific botanical sheets** linked across OTN, Android and the public
  OpenTreeNap site.
- 🛠️ **Front/back children's botanical cards** and an educational collection /
  game concept using the same species identity.
- 🛠️ **Dedicated monumental-tree experiences**, including richer MASAF data,
  history, images and thematic routes.
- 🛠️ **Botanical routes, nearby-tree discovery, advanced search and special
  area layers**, including work focused on Capodimonte.
- 🛠️ **Expanded ecosystem-benefit presentation**, with evaluation of modern
  i-Tree-compatible workflows for the Naples inventory.
- 🛠️ **Public-release hardening** for the Android client: client-safe
  authentication, removal of embedded HMAC secrets, rate limiting, offline
  behaviour, crash reporting and Play Store release preparation.

Features marked as prototype or planned are deliberately distinguished from
validated functionality. In particular, smartphone dendrometric measurements
will not be presented as authoritative until field-validation targets have
been met.

---

## 📘 Installation guide

The complete, VPS-tested Italian installation procedure is available in **[INSTALL_IT.md](INSTALL_IT.md)**. It covers Ubuntu 24.04, Docker, environment configuration, Google Maps, instance creation and the Bulk Uploader.

---

## 🧱 Docker architecture

| Service | Purpose |
|---|---|
| `web` | OpenTreeMap Django application |
| `db` | PostgreSQL 14 + PostGIS |
| `redis` | Cache and broker services |
| `tiler` | Historical OTM Windshaft/Mapnik tile renderer |
| `db-tiler-init` | Configures the restricted PostgreSQL account used by the tiler |
| `worker` | Celery worker for background tasks and imports; starts with the standard stack |

The historical tiler intentionally runs in an isolated legacy Node.js environment because its Windshaft/Mapnik dependency chain is not directly compatible with current Node.js releases.

---

## ⚙️ Technology stack

OpenTreeNap currently uses:

- Python 3.10
- Django 3.2
- PostgreSQL 14
- PostGIS 3.x
- Redis 6
- Celery 5
- Gunicorn
- Node.js 14 for the main frontend
- Webpack 1
- Node.js 6 for the legacy OTM Tiler
- Windshaft
- Mapnik
- Leaflet
- Docker Compose

---

## 🚀 Quick start

Clone the repository:

    git clone https://github.com/nicoali/OpenTreeNap.git
    cd OpenTreeNap

Create the local environment file:

    cp .env.modern-v4.1.example .env.modern-v4.1

Edit `.env.modern-v4.1` and replace placeholder values with your local configuration and secrets.

The real `.env.modern-v4.1` is excluded from Git and must never be committed.

Check Docker:

    chmod +x modern-v4.1.sh
    ./modern-v4.1.sh doctor

Build:

    ./modern-v4.1.sh build

Start OpenTreeNap:

    ./modern-v4.1.sh up

Check the containers:

    ./modern-v4.1.sh status

Run the backend health check:

    ./modern-v4.1.sh smoke

A healthy backend returns a response similar to:

    {"status": "ok", "database": "ok"}

---

## 👤 Django administrator

Create a local administrator with:

    ./modern-v4.1.sh superuser

---

## 🗺️ OTM Tiler

The historical OpenTreeMap renderer is integrated directly into the repository under `otm-tiler/`.

For local development the browser-visible tile endpoint and host port are configured with:

    OTM_TILE_HOST=//localhost:4000
    OTM_TILER_HTTP_PORT=4000

`OTM_TILER_HTTP_PORT` controls the host port published by Docker, while the tiler continues to listen on port 4000 inside the container.

The tiler uses a dedicated restricted PostgreSQL account through:

    OTM_TILER_DB_USER
    OTM_TILER_DB_PASSWORD

Database configuration is handled by:

    docker/configure-tiler-db.sh

Real database passwords must never be committed.

---

## 🧰 Useful commands

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

### ⚠️ Destructive reset

The command:

    ./modern-v4.1.sh reset

deletes the v4.1 Docker volumes and their persisted development data.

**Do not use `reset` as a normal restart command.**

---

## 📚 Modernization documentation

Technical migration notes are retained in:

- `MODERNIZATION.md`
- `MODERN_V2_NOTES.md`
- `MODERN_V3_NOTES.md`
- `MODERN_V4_1_NOTES.md`

---

## 🚧 Current limitations

OpenTreeNap remains an active modernization project.

Areas still requiring additional work or validation include:

- production deployment configuration
- broader application workflow testing
- Celery/background-task validation
- remaining Django deprecations
- some historical geocoding integrations
- external Google Maps configuration where used
- additional frontend/browser compatibility testing

The currently working development environment should not yet be considered a completed production migration.

---

## 🔐 Security

Never commit:

- `.env.modern-v4.1`
- database passwords
- Django secret keys
- API keys
- private SSH keys
- external service credentials

Only example configuration without real credentials should be versioned.

---

## 🌳 OpenTreeMap attribution

OpenTreeNap is based on and derived from **OpenTreeMap**, the open-source collaborative platform for tree inventory, ecosystem-services calculations, urban forestry analysis and community engagement.

The original OpenTreeMap code and copyright notices remain attributed to their respective authors, including **Azavea, Inc.**

OpenTreeNap does not claim authorship of the original OpenTreeMap codebase.

This repository contains modernization, compatibility and integration work built on top of the original project.

---

## 📜 License

The original licensing information is preserved in [`LICENSE`](LICENSE).

The OpenTreeMap codebase in this repository retains its applicable open-source licensing terms, including the **GNU Affero General Public License v3 (AGPLv3)** contained in the repository.

Refer to the full `LICENSE` file for the applicable terms and copyright notices.

---

## 🏙️ OpenTreeNap

**Open trees. Open data. Napoli.**

From Naples to anywhere: modernizing OpenTreeMap while preserving its open-source roots.
