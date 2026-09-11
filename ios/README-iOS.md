# Fufcord für iOS (Beta)

Custom Discord Rich Presence fürs iPhone — nativ in SwiftUI, mit denselben
Funktionen wie die Android-Version: Setup-Assistent, Start/Stop, Presets,
Studio (Editor + Bild-Upload), Live-Vorschau, Credits und Einstellungen.

## Installieren — die einfache Art (empfohlen) ⭐

Fufcord hat eine eigene **AltStore-Quelle** — danach steht die App direkt
in AltStore wie in einem kleinen App Store, inkl. automatischer Updates:

1. **AltStore** einmalig einrichten (kostenlos):
   [AltServer](https://altstore.io) auf PC/Mac → iPhone verbinden →
   mit eigener Apple-ID anmelden → AltStore aufs iPhone übertragen
2. Auf dem iPhone in AltStore: **Settings → Sources → +** und diese URL
   einfügen:
   `https://raw.githubusercontent.com/Fufi1925/Fufcord/main/apps.json`
3. Tab **Browse → Fufcord → GET** → installieren — fertig! 🎉
4. iPhone: Einstellungen → Allgemein → VPN & Geräteverwaltung → Apple-ID
   **vertrauen**

Updates kommen danach automatisch in AltStore — einfach antippen. ✅

## Installieren — manuell (Alternative)

1. Die Datei **Fufcord-iOS-unsigned.ipa** aus dem
   [Release](https://github.com/Fufi1925/Fufcord/releases/latest) laden
   (z. B. per iCloud aufs iPhone)
2. In AltStore: **+** → IPA wählen → installieren
3. Apple-ID vertrauen (siehe oben)

Oder **Sideloadly** (https://sideloadly.io) auf dem PC — iPhone
anschließen, IPA + Apple-ID, Start.

> ⏳ **Wichtig:** Kostenlose Apple-IDs müssen Apps alle **7 Tage**
> aktualisieren (AltStore macht das automatisch im gleichen WLAN).
> Sonst stoppt iOS die App.

## Hintergrund-Modus 🎧

iOS stoppt Apps im Hintergrund schnell. Fufcord spielt deshalb **lautloses
Audio** in Schleife ( dextra als „Hintergrund-Audio" angemeldet) — der Status
bleibt aktiv, solange die App läuft. Das ist:

- **100 % lautlos** (Lautstärke 0, mischt sich mit Musik)
- **sparsam** (kein messbarer Akkuverbrauch)
- in den Einstellungen **abschaltbar** (dann pausiert iOS die App beim
  Minimieren)

Ehrlicher Hinweis: Bei vollem Arbeitsspeicher kann iOS jede App beenden —
dann einfach Fufcord öffnen und neu starten.

## Funktionen

- ✅ Setup-Assistent (Onboarding + Token/App-ID, beides auch automatisch)
- ✅ Start/Stop mit Live-Status, Reconnect & Watchdog
- ✅ 8 Presets mit Auto-Bild-Upload + eigene Presets (Speichern/Import/Export)
- ✅ Studio: alles bearbeiten, Fotos hochladen, Assets verwalten
- ✅ Discord-ähnliche Live-Vorschau (lokal + Server-Bilder)
- ✅ Sicher-Modus (nur Text), Timer-Modi, Party, Buttons, Stream-URL
- ✅ Update-Check (meldet sich nur bei Releases mit echter .ipa)
- ✅ Credits mit Live-Entwickler-Profil

## Selbst bauen

```sh
brew install xcodegen
cd ios && xcodegen generate
open Fufcord.xcodeproj  # mit eigener Apple-ID signieren & aufs iPhone
```

Oder einfach das fertige IPA aus den GitHub-Releases nehmen.
Die CI baut es automatisch bei jedem Push (`ios.yml`, macOS-Runner).
