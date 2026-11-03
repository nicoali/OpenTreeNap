#!/usr/bin/env python3
"""Installa le risorse grafiche OpenTreeNap dall'archivio consegnato in chat.

Uso dalla root del repository:
  python3 android/scripts/apply_playstore_icon.py OpenTreeNap_PlayStore_icone_alpha.zip
Eseguire solo con l'archivio affidabile preparato per il progetto.
"""
from pathlib import Path
import re
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
RES_PREFIX = "android/app/src/main/res/"
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"

def main() -> int:
    if len(sys.argv) != 2:
        print("Uso: python3 android/scripts/apply_playstore_icon.py <pacchetto-icone.zip>")
        return 2
    archive = Path(sys.argv[1]).resolve()
    if not archive.is_file() or not MANIFEST.is_file():
        print("Archivio non trovato oppure non sei nel repository OpenTreeNap.")
        return 2

    copied = 0
    with zipfile.ZipFile(archive) as zip_file:
        for member in zip_file.infolist():
            name = member.filename
            if member.is_dir() or not name.startswith(RES_PREFIX):
                continue
            path = Path(name)
            if ".." in path.parts or path.suffix.lower() not in (".png", ".xml"):
                raise ValueError(f"Percorso non valido nell'archivio: {name}")
            # No unrelated files or code are written from the package.
            if not (name.startswith(RES_PREFIX+"mipmap-") or
                    name.startswith(RES_PREFIX+"drawable-nodpi/")):
                raise ValueError(f"Risorsa inattesa: {name}")
            destination = ROOT / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(zip_file.read(member))
            copied += 1

    if copied != 14:
        raise RuntimeError(f"Attese 14 risorse Android, trovate {copied}. Verifica ZIP.")
    manifest = MANIFEST.read_text(encoding="utf-8")
    manifest_new = re.sub(
        r'android:icon="@(?:drawable/ic_tree|mipmap/ic_launcher)"',
        'android:icon="@mipmap/ic_launcher"',
        manifest, count=1
    )
    if manifest_new == manifest and 'android:icon="@mipmap/ic_launcher"' not in manifest:
        raise RuntimeError("Elemento android:icon non trovato nel manifest.")
    if 'android:roundIcon=' not in manifest_new:
        manifest_new = manifest_new.replace(
            'android:icon="@mipmap/ic_launcher"',
            'android:icon="@mipmap/ic_launcher"\n'
            '        android:roundIcon="@mipmap/ic_launcher_round"', 1
        )
    MANIFEST.write_text(manifest_new, encoding="utf-8")
    print(f"Installate {copied} risorse e aggiornato {MANIFEST.relative_to(ROOT)}.")
    print("Verificare le modifiche con git diff e compilare con Android Studio.")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
