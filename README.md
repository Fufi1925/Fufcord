# ALEKS 📱 — Custom Discord Rich Presence für Termux

Wie **Vencord Custom Rich Presence** — aber fürs **Handy** (Termux / Android).
Setze deine **komplett eigene Rich Presence**: Name, Bilder, 2 Zeilen Text, Buttons, Zeit — alles frei einstellbar.

![Termux](https://img.shields.io/badge/Termux-Android-green) ![Python](https://img.shields.io/badge/Python-3.8+-blue) ![Discord](https://img.shields.io/badge/Discord-Rich_Presence-5865F2)

---

## ✨ Features

- 🎮 **Eigener Name** — z. B. Minecraft, Spotify, VS Code, alles was du willst
- 📝 **Typen** — Spielt / Streamt / Hört / Schaut / Tritt an / Custom
- 📄 **Details + State** — 2 Textzeilen wie bei Vencord
- 🖼️ **Großes + kleines Bild** — eigene Uploads aus dem Developer Portal
- 🔘 **2 Buttons** mit eigenem Link
- ⏱️ **Zeitstempel** — „Verstrichen: 12:34"
- 🟢 **Online-Status** — online / idle / dnd / invisible
- 📦 **Presets** — z. B. Gaming, Coding, Musik per Klick wechseln
- 🔄 **Auto-Reconnect** — bleibt von allein online
- 📱 **Nur 1 Abhängigkeit** — super leicht, perfekt fürs Handy

---

## 🚀 Installation auf dem Handy (Termux)

### 1. Termux installieren
- **F-Droid** laden → dort **Termux** installieren
- ⚠️ **NICHT** die Play-Store-Version (veraltet!)

### 2. Repo clonen

```bash
pkg update -y && pkg install git -y
git clone https://github.com/DEINNAME/aleks.git
cd aleks
```

> `DEINNAME` durch deinen GitHub-Namen ersetzen, nachdem du das Repo hochgeladen hast (siehe unten ⬇️).

### 3. Setup ausführen

```bash
bash setup.sh
```

### 4. Starten

```bash
bash start.sh
```

---

## 🔑 Einrichtung (einmalig, 5 Minuten)

### Schritt 1: Discord-App erstellen (für Bilder + App-ID)

1. Öffne https://discord.com/developers/applications
2. **New Application** → Name wählen (z. B. `Aleks`) → Create
3. **Application ID kopieren** (wird in Aleks gebraucht)
4. Links auf **Rich Presence** → **Art Assets** → **Add Image(s)**
5. Bild hochladen (mind. 512×512) → Namen merken, z. B. `logo`
6. 💡 Mehrere Bilder = mehr Auswahl (z. B. `minecraft`, `spotify`, `vscode`)

### Schritt 2: Token holen

1. Am **PC**: Discord im **Browser** öffnen (discord.com)
2. **F12** drücken → Reiter **Application** (Anwendung)
3. Links **Local Storage** → `https://discord.com`
4. Eintrag **token** → Wert kopieren

> ⚠️ **WARNUNG:** Der Token ist wie dein Passwort!
> Niemals teilen, niemals auf GitHub laden, niemandem schicken.
> Wer deinen Token hat, hat deinen Account.

### Schritt 3: In Aleks eintragen

1. `bash start.sh` → Menü **Punkt 2** (Token & Application ID)
2. Token einfügen + Application ID einfügen → speichern
3. Menü **Punkt 3** → Presence einstellen wie bei Vencord:
   - Name, Typ, Details, State
   - Großes Bild = Asset-Name (z. B. `logo`)
   - Buttons mit Link
4. Menü **Punkt 1** → **RPC starten** 🚀

Fertig! Dein Profil zeigt jetzt deine eigene Rich Presence. 🎉

---

## 📱 Termux-Tipps

| Problem | Lösung |
|---|---|
| Display aus → RPC stoppt | Vor dem Start `termux-wake-lock` eingeben |
| Beenden | `Lautstärke-Leiser + C` |
| Update holen | `cd ~/aleks && git pull` |
| Neustart | `bash start.sh` |
| Token ändern | Menü → Punkt 2 |

### Im Hintergrund laufen lassen
```bash
termux-wake-lock   # Handy schläft nicht ein
cd ~/aleks
bash start.sh
```
Dann einfach Termux minimieren — die Presence bleibt online. ✅

---

## 📦 Presets

Im Menü unter **Punkt 4**:
- Aktuelle Einstellung als Preset speichern (z. B. `gaming`, `chill`)
- Per Nummer laden — ideal zum Wechseln
- Mitgeliefert: `gaming`, `coding`, `musik`

---

## ⬆️ Eigenes Repo auf GitHub hochladen

Damit du (und andere) es per `git clone` laden kannst:

```bash
cd ~/aleks   # oder wo dein Ordner liegt
git init
git add .
git commit -m "Aleks RPC 🎉"
```

Dann auf https://github.com/new:
1. Repository-Name: `aleks`
2. Public → **Create repository**
3. Befehle von GitHub kopieren, z. B.:

```bash
git remote add origin https://github.com/DEINNAME/aleks.git
git branch -M main
git push -u origin main
```

Danach kann jeder clonen mit:
```bash
git clone https://github.com/DEINNAME/aleks.git
```

> 🔒 `config.json` (mit deinem Token) wird durch `.gitignore` **automatisch NICHT** hochgeladen. Sicher! ✅

---

## 📁 Projekt-Struktur

```
aleks/
├── main.py              # Hauptprogramm (Menü + RPC)
├── config.example.json  # Beispiel-Config
├── config.json          # Deine Config (wird automatisch erstellt, privat!)
├── requirements.txt     # Abhängigkeiten (nur websockets)
├── setup.sh             # Einmaliges Setup für Termux
├── start.sh             # Start-Skript
├── presets/             # Gespeicherte Presets
│   ├── gaming.json
│   ├── coding.json
│   └── musik.json
├── .gitignore           # Schützt deinen Token
└── README.md            # Diese Anleitung
```

---

## ⚠️ Hinweis / Disclaimer

- Dieses Tool nutzt deinen **User-Token** (Selfbot-Prinzip), damit es auch auf dem Handy ohne PC/Vencord funktioniert.
- Das verstößt gegen die **Discord-Nutzungsbedingungen** — eine Sperrung ist theoretisch möglich.
- Nutzung auf **eigene Gefahr**. Token niemals teilen!
- Nur für **private / Bildungszwecke**.

---

## ❓ Probleme?

**„Token ungültig":**
→ Neuen Token holen (Discord-Passwort geändert? Dann ist der alte Token tot) → Menü Punkt 2

**„Bilder werden nicht angezeigt":**
→ Asset-Name muss **exakt** stimmen (klein geschrieben, keine Leerzeichen)
→ Bilder brauchen nach Upload paar Minuten
→ Application ID muss zur App mit den Bildern gehören

**„Buttons fehlen":**
→ Max. 2 Buttons, Link muss mit `https://` anfangen
→ Manche Discord-Versionen zeigen Buttons nur im Profil, nicht im Mini-Popup

**„Verbindung bricht ab":**
→ WLAN prüfen, `termux-wake-lock` nutzen, einfach neu starten (Auto-Reconnect ist eingebaut)

---

Made with ❤️ für Termux — **Aleks RPC**
