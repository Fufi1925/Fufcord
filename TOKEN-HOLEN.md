# 🎫 Discord User-Token NUR mit dem Handy holen (Firefox-Methode)

Kein PC? Kein Problem. Du brauchst nur **Firefox** + eine kostenlose Erweiterung. Dauert ca. **5 Minuten**.

## Schritt 1: Firefox installieren

- Aus dem **Play Store**: **Firefox** laden (kostenlos, von Mozilla)

## Schritt 2: Violentmonkey installieren

1. In Firefox diese Seite öffnen:
   **https://addons.mozilla.org/firefox/addon/violentmonkey/**
2. **„Zu Firefox hinzufügen"** → **Hinzufügen**
3. Falls Firefox nach Berechtigungen fragt: **erlauben**

> Alternative: **Tampermonkey** geht genauso (https://addons.mozilla.org/firefox/addon/tampermonkey/)

## Schritt 3: Helper-Script einfügen

1. Firefox-Menü **☰ → Add-ons → Violentmonkey** antippen (Dashboard öffnet sich)
2. Auf **＋** tippen → **Neues Userscript**
3. Die Vorlage **komplett löschen**
4. Code aus der Datei **`token-helper.user.js`** (in diesem Repo) einfügen
5. **Speichern** (💾 / Haken) — Script muss **aktiviert** sein ✅

## Schritt 4: Bei Discord einloggen

1. Öffnen: **discord.com/login**
2. Firefox-Menü **☰ → „Desktopwebsite"** ✅ aktivieren (wichtig!)
3. Normal mit E-Mail + Passwort einloggen — du musst deine Chats sehen

## Schritt 5: Token kopieren

1. Nach dem Neuladen erscheint oben kurz grün **„Fufcord Helper aktiv ✅ – Token bereit!"**
2. Unten rechts **🎫 Token anzeigen** antippen → Token erscheint **direkt auf der Seite** (kein Popup)
3. **📋 Kopieren** antippen (oder Text lang drücken → kopieren)
4. In Fufcord einfügen: `bash start.sh` → Menü **Punkt 2** → Token einfügen → speichern
5. Menü **Punkt 1** → RPC starten 🚀

## ❓ Probleme?

| Problem | Lösung |
|---|---|
| Button reagiert nicht / kein Fenster | Script auf **v1.2+** aktualisieren (ohne Popups): alten Code komplett ersetzen → speichern → Tab neu laden |
| „Kein Token" obwohl eingeloggt | Script auf **v1.3+** aktualisieren (läuft direkt in der Seite, `@inject-into page`) → speichern → neu laden |
| Immer noch kein Token | 🛡️-Symbol in Adressleiste → **Tracking-Schutz für discord.com AUS** → neu laden. Hilft das nicht: Diagnose-Zeile aus dem Fenster abschreiben/screenshotten |
| Button erscheint nicht | **Tab NEU LADEN** (↻ / runterziehen) — Script greift erst nach Reload! |
| Kein grüner „Helper aktiv ✅" nach Reload | Script läuft nicht → Dashboard prüfen: Script in Liste? Schalter AN? Gespeichert? |
| Berechtigung fehlt | Firefox ☰ → Add-ons → Violentmonkey → Zugriff auf Websites **erlauben** |
| Button erscheint nicht | Seite neu laden, prüfen ob Script aktiviert ist, nur auf **discord.com** (nicht in der Discord-App!) |
| „Kein Token gefunden" | Nicht eingeloggt? → Erst einloggen, dann Button drücken |
| Discord will App öffnen | Im Browser bleiben, **Desktopwebsite** aktivieren |
| „Token ungültig" in Fufcord | Token ohne Leerzeichen kopieren, ggf. neu kopieren |

## ⚠️ Sicherheit

- 🔴 **Token = Passwort!** Niemals teilen, in keine Screenshots, auf keine fremde Website.
- ✅ Das Script läuft **nur auf discord.com** und sendet **nichts** irgendwohin — du kannst den kurzen Code in `token-helper.user.js` selbst prüfen.
- 🚫 Keine „Token Finder"-Apps/Websites benutzen — fast immer Betrug.
- 🔄 Falls der Token mal geleakt ist: **Discord-Passwort ändern** → alter Token wird sofort ungültig.
