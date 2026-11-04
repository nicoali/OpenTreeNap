# OpenTreeNap — installa il nuovo logo Android e, se richiesto, pubblicalo su GitHub.
# Da Android Studio Terminal: powershell -ExecutionPolicy Bypass -File .\scripts\install-playstore-logo.ps1 -CommitAndPush
param(
    [string]$ZipPath = (Join-Path $env:USERPROFILE 'Downloads\OpenTreeNap_NUOVO_logo_PlayStore_Android.zip'),
    [switch]$CommitAndPush
)
$ErrorActionPreference = 'Stop'
$androidRoot = Split-Path -Parent $PSScriptRoot
$manifest = Join-Path $androidRoot 'app\src\main\AndroidManifest.xml'
$resTarget = Join-Path $androidRoot 'app\src\main\res'
if (-not (Test-Path -LiteralPath $ZipPath -PathType Leaf)) {
    throw "ZIP non trovato: $ZipPath. Scarica il pacchetto e specifica -ZipPath."
}
if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) {
    throw "AndroidManifest.xml non trovato nella cartella: $androidRoot"
}
$tmp = Join-Path $env:TEMP ("otn-icon-" + [guid]::NewGuid().ToString('N'))
$changed = New-Object 'System.Collections.Generic.List[string]'
try {
    New-Item -ItemType Directory -Path $tmp -Force | Out-Null
    Expand-Archive -LiteralPath $ZipPath -DestinationPath $tmp -Force
    $source = Join-Path $tmp 'android\app\src\main\res'
    $expected = @(
        'mipmap-mdpi\ic_launcher.png',
        'mipmap-hdpi\ic_launcher.png',
        'mipmap-xhdpi\ic_launcher.png',
        'mipmap-xxhdpi\ic_launcher.png',
        'mipmap-xxxhdpi\ic_launcher.png',
        'mipmap-anydpi-v26\ic_launcher.xml',
        'mipmap-anydpi-v26\ic_launcher_round.xml',
        'drawable-nodpi\otn_launcher_foreground.png',
        'drawable-nodpi\otn_launcher_background.png'
    )
    foreach ($relative in $expected) {
        if (-not (Test-Path -LiteralPath (Join-Path $source $relative))) {
            throw "Pacchetto incompleto: manca $relative"
        }
    }
    Get-ChildItem -LiteralPath $source -Recurse -File | ForEach-Object {
        $relative = $_.FullName.Substring($source.Length).TrimStart('\', '/')
        $dest = Join-Path $resTarget $relative
        New-Item -ItemType Directory -Path (Split-Path -Parent $dest) -Force | Out-Null
        Copy-Item -LiteralPath $_.FullName -Destination $dest -Force
        $changed.Add(("app/src/main/res/" + ($relative -replace '\\', '/')))
    }
    $xml = [System.IO.File]::ReadAllText($manifest)
    $oldIcon = 'android:icon="@drawable/ic_tree"'
    $newIcon = 'android:icon="@mipmap/ic_launcher"'
    if ($xml.Contains($oldIcon)) {
        $xml = $xml.Replace($oldIcon, $newIcon)
    } elseif (-not $xml.Contains($newIcon)) {
        throw 'Icona del Manifest non riconosciuta. Controlla android:icon.'
    }
    if (-not $xml.Contains('android:roundIcon=')) {
        $xml = $xml.Replace($newIcon, $newIcon + [Environment]::NewLine + '        android:roundIcon="@mipmap/ic_launcher_round"')
    }
    [System.IO.File]::WriteAllText($manifest, $xml, (New-Object System.Text.UTF8Encoding $false))
    $changed.Add('app/src/main/AndroidManifest.xml')
    Write-Host "OK: installati $($changed.Count - 1) file e aggiornato AndroidManifest.xml" -ForegroundColor Green
    Write-Host 'Ora puoi rigenerare il Signed App Bundle (release) con il certificato JKS esistente.'
    if ($CommitAndPush) {
        if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw 'Git non disponibile nel PATH.' }
        $branch = (& git -C $androidRoot branch --show-current).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $branch) { throw 'Git branch non rilevato.' }
        if ($branch -ne 'feature/android-sprint5-measurements') {
            throw "Per sicurezza non pubblico sul branch '$branch'. Passa a feature/android-sprint5-measurements."
        }
        $files = $changed.ToArray() | Sort-Object -Unique
        & git -C $androidRoot add -- $files
        if ($LASTEXITCODE -ne 0) { throw 'git add non riuscito.' }
        & git -C $androidRoot diff --cached --quiet -- $files
        if ($LASTEXITCODE -eq 0) {
            Write-Host 'Nessuna modifica nuova da pubblicare.'
        } else {
            & git -C $androidRoot commit -m 'Brand OpenTreeNap Android launcher with new Play Store logo' -- $files
            if ($LASTEXITCODE -ne 0) { throw 'Commit Git fallito.' }
            & git -C $androidRoot push origin $branch
            if ($LASTEXITCODE -ne 0) { throw 'Push Git fallito. Le modifiche restano locali.' }
            Write-Host "OK: icone pubblicate su GitHub ($branch)." -ForegroundColor Green
        }
    }
} finally {
    if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force }
}