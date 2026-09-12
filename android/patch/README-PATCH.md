# ⚡ Fufcord Patch (Beta)

Fufcord **direkt in Discord**: Nach dem Patchen hast du in Discord unter
**Einstellungen → Fufcord** eine eigene Seite — Token, Texte, Bilder,
Status, Start/Stop und Auto-Start mit Discord. Wie Vencord, nur für
deinen Custom-Status. 💜

## Was du brauchst

1. **Discord** (normal aus dem Play Store)
2. **Fufcord Patch** (`patch-debug.apk` aus dem
   [Release](https://github.com/Fufi1925/Fufcord/releases/latest)) —
   einfach installieren & wieder vergessen (nur Anleitung drin)
3. **LSPatch Manager** ([GitHub-Releases](https://github.com/LSPosed/LSPatch/releases)) —
   das Werkzeug, das alles zusammenbaut. Kein Root nötig!

## Patchen in 5 Minuten

1. LSPatch Manager öffnen → **+ / Patchen** → **Discord** auswählen
2. Bei **Module einbetten**: **Fufcord Patch** anhaken
3. **Patchen** antippen → warten → **Installieren**
   (Hinweis „App nicht installiert"? → Originales Discord zuerst
   deinstallieren, dann gepatchtes installieren. Chats & Login bleiben
   erhalten — alles liegt auf Discords Servern.)
4. Gepatchtes Discord öffnen — es erscheint: **„⚡ Fufcord geladen!"** 🎉
5. Discord → **Einstellungen → Fufcord**:
   - **✨ Token automatisch holen** (oder einfügen)
   - Texte/Bilder eintragen → **▶ Start** — fertig!

Danach startet dein Status automatisch mit Discord (abschaltbar).

## Fragen & Probleme

- **„Fufcord geladen!" erscheint nicht?** → In LSPatch prüfen, ob das
  Modul wirklich eingebettet ist. Andere Discord-Version testen
  (Stable empfohlen).
- **Kein Fufcord-Bereich in den Einstellungen?** → Discord ganz schließen
  und neu öffnen. Manche Discord-Versionen bauen Einstellungen um —
  bitte Version melden (Discord → Einstellungen → ganz unten)!
- **Status geht nicht an?** → Token prüfen (✨-Button), Internet prüfen.
  Bilder brauchen zusätzlich eine App-ID (siehe Fufcord-Hauptapp).
- **Discord stürzt ab?** → Gepatchtes Discord deinstallieren, originales
  aus dem Play Store neu installieren. Dann bitte melden, was passiert ist.
- **Zurück zum Original?** → Gepatchtes Discord deinstallieren, originales
  installieren. Fertig — keine Spuren.

## ⚠️ Ehrliche Hinweise

- Gemoddete Discord-Apps verstoßen gegen die Discord-Regeln. Bans wegen
  Client-Mods sind **sehr selten**, aber das Risiko ist nicht null.
  Die normale Fufcord-App (ohne Patch) ist die vorsichtigere Wahl.
- Nach jedem **Discord-Update aus dem Play Store** ist der Patch weg —
  einfach erneut patchen (Einstellungen bleiben erhalten).
- Beta: Wenn etwas spinnt, sag Bescheid — dann wird es repariert. 🛠️

## Für Entwickler

- `js/fufcord.js` — das in Discord injizierte Skript (Plain JS, kein JSX).
  Mit `hermesc` nach `src/main/assets/fufcord.hbc` kompilieren (plus `.js`
  als Fallback daneben legen).
- `FufHook.kt` — Xposed-Einstieg: hängt sich an Discords RN-Loader
  (`ReactInstance$loadJSBundle$1` bzw. `CatalystInstanceImpl`) und lädt
  das Skript per `loadScriptFromFile` nach.
- Technik angelehnt an [Revenge](https://github.com/revenge-mod/revenge-xposed)
  (Loader) und [revenge-bundle](https://github.com/revenge-mod/revenge-bundle)
  (Metro-Finder, Setting-Renderer-Patch). Danke an die Teams! 💜
