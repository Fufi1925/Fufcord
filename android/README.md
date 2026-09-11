# 📱 Fufcord Android-App (APK)

Echte Android-App — **kein Termux nötig**: läuft im Hintergrund, Bilder-Upload direkt in die Discord-App, 1:1-Vorschau, Setup-Assistent, Presets, KI-JSON, Doktor.

**Aktuell: v1.07 „Clear"** — komplett neues Design (Glass/Clear-Look, Animationen, Onboarding, eigener Einstellungs-Screen).

## 📥 APK installieren

**Option A — Fertige APK von GitHub:**
1. Im Repo oben auf **Actions** → neuster Lauf „Android APK bauen" → ganz unten **Artifacts → Fufcord-APK** laden (GitHub-Login nötig)
2. Oder bei **Releases** schauen (falls vorhanden)
3. APK auf dem Handy öffnen → **Installieren** (ggf. „Unbekannte Apps zulassen" für den Browser)

**Option B — Selbst bauen (Android Studio):**
1. Android Studio installieren
2. `File → Open` → Ordner **`android/`** wählen
3. Warten bis Gradle fertig ist → ▶️ Run (baut + installiert automatisch)

**Option C — Selbst bauen (Kommandozeile):**
```bash
cd android
gradle :app:assembleDebug
# APK liegt dann in: app/build/outputs/apk/debug/app-debug.apk
```
(Braucht JDK 17 + Android SDK mit API 34.)

## ✨ Features

- 🚀 **START/STOP** — Rich Presence läuft als Hintergrund-Service weiter (auch wenn die App zu ist)
- 🧙 **Setup-Assistent** — Token + App-ID mit Live-Test beim ersten Start
- 🎨 **Editor** — alle Felder wie bei Vencord
- 🖼️ **Bilder-Upload** — Bild aus Galerie wählen → direkt in die Discord-App hochladen (kein Developer-Portal nötig!)
- 👁️ **1:1-Vorschau** — sieht aus wie die Discord-Aktivitätskarte
- 📦 **8 Presets** mit Icons + eigene Presets speichern
- 📤📥 **KI-JSON** — Export per Teilen-Menü, Import per Einfügen
- 🔍 **Doktor** — prüft Token/App/Bilder bei Discord + Auto-Reparatur
- 🛡️ **Sicher-Modus** — Nur-Text (geht immer)
- 🔄 **Autostart** nach Handy-Neustart (optional)
- 🔒 **Token verschlüsselt** gespeichert (Android Keystore)

## 🔧 Projekt-Infos

- Sprache: **Kotlin**, UI: **Material3 (Views)**, minSdk **26** (Android 8+)
- Netzwerk: **OkHttp** (Gateway-WebSocket + Discord-API), JSON: **org.json**
- Package: `com.fufcord.app`
