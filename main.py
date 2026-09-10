#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
FUFCORD — Custom Discord Rich Presence für Termux (Handy)
Wie Vencord: eigene Rich Presence komplett selbst setzen.

Funktionen:
  • Eigener Name, Typ (Spielt / Streamt / Hört / Schaut / Antritt / Custom)
  • Details + State (2 Zeilen wie Vencord)
  • Großes + kleines Bild (aus Developer-Portal)
  • 2 Buttons mit Link
  • Zeitstempel (Verstrichene Zeit)
  • Online-Status (online / idle / dnd / invisible)
  • Presets speichern & laden
  • Hält Handy-kompatibel: nur 1 Abhängigkeit (websockets)

Start:  python main.py
Setup:  bash setup.sh

HINWEIS: Nutzt deinen User-Token (Selfbot-Prinzip). Das verstößt gegen
die Discord-ToS — Account-Sperrung ist theoretisch möglich. Nutzung auf
eigene Gefahr, keinen Token weitergeben!
"""

import asyncio
import json
import os
import re
import sys
import time

try:
    import websockets
except ImportError:
    print("Fehlt: websockets — bitte 'pip install -r requirements.txt' ausführen.")
    print("Oder: bash setup.sh")
    sys.exit(1)

# ---------------------------------------------------------------- Datei-Pfade
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")
EXAMPLE_PATH = os.path.join(BASE_DIR, "config.example.json")
PRESETS_DIR = os.path.join(BASE_DIR, "presets")

GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

VERSION = "2.2"

# ---------------------------------------------------------------- Farben (Termux-safe ANSI)
C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_DIM = "\033[2m"
C_GREEN = "\033[92m"
C_CYAN = "\033[96m"
C_YELLOW = "\033[93m"
C_RED = "\033[91m"
C_MAGENTA = "\033[95m"
C_BLUE = "\033[94m"

ACTIVITY_TYPES = {
    0: "Spielt",
    1: "Streamt",
    2: "Hört",
    3: "Schaut zu",
    4: "Benutzerdefiniert",
    5: "Tritt an in",
}

STATUS_LABELS = {
    "online": "🟢 Online",
    "idle": "🌙 Abwesend",
    "dnd": "⛔ Bitte nicht stören",
    "invisible": "⚫ Unsichtbar",
}

DEFAULT_CONFIG = {
    "token": "",
    "application_id": "",
    "status": "online",
    "activity": {
        "name": "Fufcord",
        "type": 0,
        "details": "Custom Rich Presence wie Vencord",
        "state": "läuft auf Termux 📱",
        "large_image": "logo",
        "large_text": "Fufcord RPC",
        "small_image": "",
        "small_text": "",
        "stream_url": "https://twitch.tv/deinname",
        "buttons": [{"label": "Mein Server", "url": "https://discord.gg/deinlink"}],
        "use_timestamp": True,
        "party_current": 0,
        "party_max": 0,
    },
}


# ================================================================= Helpers
def clear():
    os.system("clear" if os.name != "nt" else "cls")


def banner():
    print(f"""{C_MAGENTA}{C_BOLD}
███████╗ ██╗   ██╗ ███████╗ ██████╗ ██████╗ ██████╗ ██████╗
██╔════╝ ██║   ██║ ██╔════╝ ██╔════╝ ██╔═══██╗ ██╔══██╗ ██╔══██╗
█████╗ ██║   ██║ █████╗ ██║ ██║   ██║ ██████╔╝ ██║  ██║
██╔══╝ ██║   ██║ ██╔══╝ ██║ ██║   ██║ ██╔══██╗ ██║  ██║
██║ ╚██████╔╝ ██║ ╚██████╗ ╚██████╔╝ ██║  ██║ ██████╔╝
╚═╝ ╚═════╝ ╚═╝ ╚═════╝ ╚═════╝ ╚═╝  ╚═╝ ╚═════╝{C_RESET}
  {C_CYAN}Custom Discord Rich Presence für Termux 📱 {C_DIM}v{VERSION}{C_RESET}
  {C_DIM}Wie Vencord — aber fürs Handy{C_RESET}
""")


def pause():
    input(f"\n{C_DIM}Enter drücken um fortzufahren...{C_RESET}")


def ask(prompt, default=""):
    """Eingabe mit Default-Wert (Enter = behalten)."""
    if default != "":
        zeig = str(default)
        if len(zeig) > 60:
            zeig = zeig[:57] + "..."
        val = input(f"{C_CYAN}?{C_RESET} {prompt} {C_DIM}[{zeig}]{C_RESET}: ").strip()
        return val if val else str(default)
    return input(f"{C_CYAN}?{C_RESET} {prompt}: ").strip()


def ask_yes_no(prompt, default=True):
    hinweis = "J/n" if default else "j/N"
    val = input(f"{C_CYAN}?{C_RESET} {prompt} {C_DIM}[{hinweis}]{C_RESET}: ").strip().lower()
    if not val:
        return default
    return val in ("j", "ja", "y", "yes", "1", "true")


def mask_token(token: str) -> str:
    if not token or len(token) < 8:
        return "(nicht gesetzt)"
    return "***..." + token[-4:]


# ================================================================= Config
def load_config():
    """Lädt config.json oder erstellt sie aus dem Beispiel."""
    os.makedirs(PRESETS_DIR, exist_ok=True)
    if not os.path.exists(CONFIG_PATH):
        if os.path.exists(EXAMPLE_PATH):
            with open(EXAMPLE_PATH, "r", encoding="utf-8") as f:
                cfg = json.load(f)
        else:
            cfg = json.loads(json.dumps(DEFAULT_CONFIG))
        save_config(cfg)
        return cfg
    try:
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    except (json.JSONDecodeError, OSError):
        print(f"{C_RED}config.json ist kaputt — lege neue an.{C_RESET}")
        cfg = json.loads(json.dumps(DEFAULT_CONFIG))
        save_config(cfg)
        return cfg
    # Fehlende Keys ergänzen (Update-sicher)
    merged = json.loads(json.dumps(DEFAULT_CONFIG))
    merged.update({k: v for k, v in cfg.items() if k != "activity"})
    if isinstance(cfg.get("activity"), dict):
        merged["activity"].update(cfg["activity"])
    return merged


def save_config(cfg):
    with open(CONFIG_PATH, "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


def list_presets():
    os.makedirs(PRESETS_DIR, exist_ok=True)
    return sorted([f[:-5] for f in os.listdir(PRESETS_DIR) if f.endswith(".json")])


# ================================================================= Activity bauen
def valid_app_id(cfg):
    """Prüft ob eine echte Application ID gesetzt ist (lange Zahl)."""
    app_id = (cfg.get("application_id") or "").strip()
    if not app_id or app_id.startswith("DEINE"):
        return ""
    if not app_id.isdigit() or len(app_id) < 15:
        return ""
    return app_id


def build_activity(cfg, start_timestamp=None):
    """Baut das Discord-Activity-Objekt (wie Vencord es sendet)."""
    a = cfg.get("activity", {})
    act_type = int(a.get("type", 0))
    now_ms = int(time.time() * 1000)

    activity = {
        "name": a.get("name", "Fufcord") or "Fufcord",
        "type": act_type,
        "created_at": now_ms,
    }

    # App-ID nur wenn gültig — sonst lässt Discord ggf. die ganze Presence fallen!
    app_id = valid_app_id(cfg)
    if app_id:
        activity["application_id"] = app_id

    if a.get("details"):
        activity["details"] = a["details"][:128]
    if a.get("state"):
        activity["state"] = a["state"][:128]

    # Bilder NUR mit gültiger App-ID (sonst ignoriert Discord alles)
    if app_id:
        assets = {}
        if a.get("large_image"):
            assets["large_image"] = a["large_image"][:256]
        if a.get("large_text"):
            assets["large_text"] = a["large_text"][:128]
        if a.get("small_image"):
            assets["small_image"] = a["small_image"][:256]
        if a.get("small_text"):
            assets["small_text"] = a["small_text"][:128]
        if assets:
            activity["assets"] = assets

    # Stream-URL (nur bei Typ 1 nötig)
    if act_type == 1:
        url = a.get("stream_url") or "https://twitch.tv/"
        activity["url"] = url

    # Buttons NUR mit gültiger App-ID (max 2)
    if app_id:
        buttons = []
        for b in (a.get("buttons") or [])[:2]:
            label = (b.get("label") or "").strip()
            url = (b.get("url") or "").strip()
            if label and url:
                buttons.append({"label": label[:32], "url": url})
        if buttons:
            activity["buttons"] = buttons

    # Zeitstempel
    if a.get("use_timestamp"):
        activity["timestamps"] = {"start": start_timestamp or int(time.time() * 1000)}

    # Party (optional)
    try:
        pc, pm = int(a.get("party_current") or 0), int(a.get("party_max") or 0)
        if pc > 0 and pm > 0:
            activity["party"] = {"id": "fufcord-party", "size": [pc, pm]}
    except (ValueError, TypeError):
        pass

    return activity


def build_presence(cfg, start_timestamp=None):
    return {
        "since": int(time.time() * 1000),
        "activities": [build_activity(cfg, start_timestamp)],
        "status": cfg.get("status", "online") or "online",
        "afk": False,
    }


EMOJI_RE = re.compile("[\\U0001F000-\\U0001FAFF\\u2600-\\u27BF\\u2B00-\\u2BFF\\uFE0F\\u200D]", flags=re.UNICODE)


def strip_emoji(text):
    return EMOJI_RE.sub("", text)


def sanitize_activity(raw):
    """Bereinigt ein Activity-Dict (Preset/Import) — falsche Werte killen die Presence!
    Gibt (clean_dict, warnungen) zurück."""
    warns = []
    if not isinstance(raw, dict):
        return json.loads(json.dumps(DEFAULT_CONFIG["activity"])), ["Ungültiges Format — Standard genommen."]
    clean = json.loads(json.dumps(DEFAULT_CONFIG["activity"]))
    try:
        t = int(raw.get("type", 0))
        clean["type"] = t if t in ACTIVITY_TYPES else 0
        if clean["type"] != t:
            warns.append("Typ ungültig → 'Spielt' gesetzt.")
    except (ValueError, TypeError):
        clean["type"] = 0
        warns.append("Typ ungültig → 'Spielt' gesetzt.")
    for key in ("name", "details", "state", "large_image", "large_text",
                "small_image", "small_text", "stream_url"):
        val = raw.get(key, "")
        clean[key] = str(val)[:256] if val is not None else ""
    if len(str(raw.get("details", "") or "")) > 128 or len(str(raw.get("state", "") or "")) > 128:
        warns.append("Details/State zu lang → gekürzt.")
    # Buttons: max 2, nur http(s)-Links, KEINE Emojis (killen die Anzeige!)
    btns = []
    if isinstance(raw.get("buttons"), list):
        for i, b in enumerate(raw["buttons"][:2], 1):
            if not isinstance(b, dict):
                continue
            label = str(b.get("label", "")).strip()
            url = str(b.get("url", "")).strip()
            if EMOJI_RE.search(label):
                label = strip_emoji(label).strip()
                warns.append(f"Button {i}: Emojis entfernt (blockieren die Anzeige!).")
            label = label[:32]
            if not label:
                warns.append(f"Button {i}: leer nach Bereinigung → entfernt.")
                continue
            if not (url.startswith("https://") or url.startswith("http://")):
                warns.append(f"Button {i}: Link ungültig ('{url[:30]}') → entfernt.")
                continue
            btns.append({"label": label, "url": url})
        if len(raw["buttons"]) > 2:
            warns.append("Mehr als 2 Buttons → nur erste 2 behalten.")
    clean["buttons"] = btns
    clean["use_timestamp"] = bool(raw.get("use_timestamp", True))
    try:
        clean["party_current"] = max(0, int(raw.get("party_current", 0) or 0))
        clean["party_max"] = max(0, int(raw.get("party_max", 0) or 0))
    except (ValueError, TypeError):
        clean["party_current"] = 0
        clean["party_max"] = 0
    if not clean["name"].strip():
        clean["name"] = "Fufcord"
        warns.append("Name leer → 'Fufcord' gesetzt.")
    return clean, warns


def extract_json_block(text):
    """Holt den ersten {...}-Block aus Text (ignoriert ``` und KI-Gelaber)."""
    text = text.replace("```json", "").replace("```", "")
    start = text.find("{")
    if start == -1:
        return None
    depth = 0
    in_str = False
    esc = False
    for i in range(start, len(text)):
        ch = text[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return text[start:i + 1]
    return None


def show_preview(cfg):
    """Zeigt eine Discord-ähnliche Vorschau."""
    a = cfg.get("activity", {})
    typ = ACTIVITY_TYPES.get(int(a.get("type", 0)), "?")
    status = STATUS_LABELS.get(cfg.get("status", "online"), cfg.get("status"))
    print(f"\n  {C_DIM}┌─ Vorschau ─────────────────────────{C_RESET}")
    print(f"  {C_DIM}│{C_RESET} {status}")
    print(f"  {C_DIM}│{C_RESET} {C_BOLD}{typ} {a.get('name', '')}{C_RESET}")
    if a.get("details"):
        print(f"  {C_DIM}│{C_RESET} {a['details']}")
    if a.get("state"):
        print(f"  {C_DIM}│{C_RESET} {a['state']}")
    if a.get("large_image"):
        img = a["large_image"]
        print(f"  {C_DIM}│{C_RESET} 🖼️  Groß: {img}" + (f" ({a['large_text']})" if a.get("large_text") else ""))
    if a.get("small_image"):
        print(f"  {C_DIM}│{C_RESET} 🔹 Klein: {a['small_image']}")
    for b in (a.get("buttons") or [])[:2]:
        if b.get("label"):
            print(f"  {C_DIM}│{C_RESET} {C_BLUE}[ {b['label']} ]{C_RESET} → {b.get('url', '')}")
    if a.get("use_timestamp"):
        print(f"  {C_DIM}│{C_RESET} ⏱️  Verstrichen: läuft ab Start")
    print(f"  {C_DIM}└────────────────────────────────────{C_RESET}\n")


# ================================================================= Editor (Vencord-Stil)
def edit_presence(cfg):
    a = cfg["activity"]
    while True:
        clear()
        banner()
        print(f"{C_BOLD}── Presence bearbeiten (Vencord-Stil) ──{C_RESET}")
        show_preview(cfg)
        print(f"  {C_YELLOW}1{C_RESET}  Name          : {a.get('name')}")
        print(f"  {C_YELLOW}2{C_RESET}  Typ           : {ACTIVITY_TYPES.get(int(a.get('type', 0)))} ({a.get('type')})")
        print(f"  {C_YELLOW}3{C_RESET}  Details (Zeile 1): {a.get('details')}")
        print(f"  {C_YELLOW}4{C_RESET}  State (Zeile 2)  : {a.get('state')}")
        print(f"  {C_YELLOW}5{C_RESET}  Großes Bild   : {a.get('large_image')}  | Text: {a.get('large_text')}")
        print(f"  {C_YELLOW}6{C_RESET}  Kleines Bild  : {a.get('small_image')}  | Text: {a.get('small_text')}")
        print(f"  {C_YELLOW}7{C_RESET}  Buttons       : {len(a.get('buttons') or [])} gesetzt")
        print(f"  {C_YELLOW}8{C_RESET}  Zeitstempel   : {'an ✅' if a.get('use_timestamp') else 'aus ❌'}")
        print(f"  {C_YELLOW}9{C_RESET}  Online-Status : {STATUS_LABELS.get(cfg.get('status'), cfg.get('status'))}")
        print(f"  {C_YELLOW}10{C_RESET} Stream-URL    : {a.get('stream_url')}")
        print(f"  {C_YELLOW}11{C_RESET} Party         : {a.get('party_current')}/{a.get('party_max')}")
        print(f"\n  {C_GREEN}S{C_RESET} Speichern & zurück")
        print(f"  {C_RED}X{C_RESET} Abbrechen (ohne speichern)")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()

        if w == "1":
            a["name"] = ask("Aktivitäts-Name (z.B. Minecraft, Spotify, Visual Studio Code)", a.get("name", "Fufcord"))
        elif w == "2":
            print("\n  Typen: 0=Spielt  1=Streamt  2=Hört  3=Schaut zu  4=Custom  5=Tritt an")
            try:
                t = int(ask("Typ-Nummer", str(a.get("type", 0))))
                a["type"] = t if t in ACTIVITY_TYPES else 0
            except ValueError:
                print(f"{C_RED}Ungültig, bleibe bei {a.get('type')}{C_RESET}")
                time.sleep(1)
        elif w == "3":
            a["details"] = ask("Details — erste Zeile unter dem Namen", a.get("details", ""))
        elif w == "4":
            a["state"] = ask("State — zweite Zeile", a.get("state", ""))
        elif w == "5":
            print(f"\n{C_DIM}Tipp: Asset-Name aus dem Developer Portal (z.B. 'logo').")
            print(f"Bild-URLs gehen NICHT — nur hochgeladene Assets. Leer = kein Bild.{C_RESET}")
            a["large_image"] = ask("Großes Bild (Asset-Name)", a.get("large_image", ""))
            if a["large_image"]:
                a["large_text"] = ask("Hover-Text großes Bild", a.get("large_text", ""))
        elif w == "6":
            a["small_image"] = ask("Kleines Bild (Asset-Name, leer = keins)", a.get("small_image", ""))
            if a["small_image"]:
                a["small_text"] = ask("Hover-Text kleines Bild", a.get("small_text", ""))
        elif w == "7":
            print(f"\n{C_DIM}Max. 2 Buttons. URL muss mit http(s):// anfangen.{C_RESET}")
            btns = []
            for i in range(1, 3):
                alt = (a.get("buttons") or [])
                alt_l = alt[i - 1].get("label", "") if len(alt) >= i else ""
                alt_u = alt[i - 1].get("url", "") if len(alt) >= i else ""
                label = ask(f"Button {i} Text (leer = überspringen)", alt_l)
                if not label:
                    continue
                url = ask(f"Button {i} Link", alt_u or "https://")
                if not url.startswith("http"):
                    print(f"{C_RED}Link muss mit http(s):// anfangen — Button übersprungen.{C_RESET}")
                    continue
                btns.append({"label": label, "url": url})
            a["buttons"] = btns
        elif w == "8":
            a["use_timestamp"] = ask_yes_no("Verstrichene Zeit anzeigen?", bool(a.get("use_timestamp", True)))
        elif w == "9":
            print("\n  online / idle / dnd / invisible")
            s = ask("Online-Status", cfg.get("status", "online")).strip().lower()
            cfg["status"] = s if s in STATUS_LABELS else "online"
        elif w == "10":
            a["stream_url"] = ask("Stream-URL (nur wichtig bei Typ 'Streamt')", a.get("stream_url", ""))
        elif w == "11":
            try:
                a["party_current"] = int(ask("Party aktuell (0 = aus)", str(a.get("party_current", 0))))
                a["party_max"] = int(ask("Party maximal (0 = aus)", str(a.get("party_max", 0))))
            except ValueError:
                print(f"{C_RED}Bitte Zahlen eingeben.{C_RESET}")
                time.sleep(1)
        elif w == "s":
            save_config(cfg)
            print(f"{C_GREEN}✅ Gespeichert!{C_RESET}")
            time.sleep(1)
            return
        elif w == "x":
            return


def setup_token_appid(cfg):
    clear()
    banner()
    print(f"{C_BOLD}── Token & Application ID ──{C_RESET}\n")
    print(f"  Aktueller Token : {mask_token(cfg.get('token', ''))}")
    print(f"  Aktuelle App-ID : {cfg.get('application_id') or '(nicht gesetzt)'}\n")
    print(f"  {C_DIM}1) App erstellen: https://discord.com/developers/applications")
    print(f"     → New Application → Name wählen → Application ID kopieren")
    print(f"     → Menü 'Rich Presence' → Art Assets → Bilder hochladen")
    print(f"     Asset-Namen (z.B. 'logo') dann oben bei Bildern eintragen.{C_RESET}\n")
    print(f"  {C_YELLOW}⚠️  Token = dein Passwort! Niemals teilen, niemals auf GitHub laden.{C_RESET}")
    print(f"  {C_DIM}Token bekommst du z.B. über Discord im Browser (F12 → Application →")
    print(f"  Local Storage → discord.com → token). Oder Google: 'discord token finden'.{C_RESET}\n")

    token = ask("User-Token einfügen (Enter = behalten)", "")
    if token:
        cfg["token"] = token.strip().strip('"').strip("'")
    appid = ask("Application ID", cfg.get("application_id", ""))
    if appid:
        cfg["application_id"] = appid.strip()
    save_config(cfg)
    print(f"\n{C_GREEN}✅ Gespeichert!{C_RESET}")
    pause()


# ================================================================= Presets
def presets_menu(cfg):
    while True:
        clear()
        banner()
        print(f"{C_BOLD}── Presets ──{C_RESET}\n")
        presets = list_presets()
        if presets:
            for i, p in enumerate(presets, 1):
                print(f"  {C_YELLOW}{i}{C_RESET}  {p}")
        else:
            print(f"  {C_DIM}(noch keine Presets — speichere dein erstes!) {C_RESET}")
        print(f"\n  {C_GREEN}S{C_RESET} Aktuelle Presence als Preset speichern")
        print(f"  {C_GREEN}L{C_RESET} Preset laden (Nummer eingeben)")
        print(f"  {C_RED}D{C_RESET} Preset löschen (z.B. 'd 2')")
        print(f"  {C_RED}X{C_RESET} Zurück")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()

        if w == "s":
            name = ask("Preset-Name (z.B. gaming, chill, coding)")
            name = "".join(c for c in name if c.isalnum() or c in ("-", "_", " ")).strip().replace(" ", "-").lower()
            if not name:
                print(f"{C_RED}Ungültiger Name.{C_RESET}")
                time.sleep(1)
                continue
            path = os.path.join(PRESETS_DIR, name + ".json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"status": cfg.get("status"), "application_id": cfg.get("application_id"),
                           "activity": cfg.get("activity")}, f, ensure_ascii=False, indent=2)
            print(f"{C_GREEN}✅ Preset '{name}' gespeichert!{C_RESET}")
            time.sleep(1)
        elif w == "x":
            return
        elif w.startswith("d"):
            try:
                idx = int(w[1:].strip()) - 1
                presets = list_presets()
                target = presets[idx]
                os.remove(os.path.join(PRESETS_DIR, target + ".json"))
                print(f"{C_GREEN}✅ '{target}' gelöscht.{C_RESET}")
            except (ValueError, IndexError, OSError):
                print(f"{C_RED}Ungültig. Beispiel: d 1{C_RESET}")
            time.sleep(1)
        else:
            try:
                idx = int(w) - 1
                presets = list_presets()
                target = presets[idx]
                with open(os.path.join(PRESETS_DIR, target + ".json"), "r", encoding="utf-8") as f:
                    data = json.load(f)
                cfg["activity"], _w = sanitize_activity(data.get("activity", cfg["activity"]))
                cfg["status"] = data.get("status", cfg.get("status"))
                if data.get("application_id"):
                    cfg["application_id"] = data["application_id"]
                save_config(cfg)
                print(f"{C_GREEN}✅ Preset '{target}' geladen!{C_RESET}")
            except (ValueError, IndexError, OSError):
                print(f"{C_RED}Ungültige Auswahl.{C_RESET}")
            time.sleep(1)


# ================================================================= Gateway (Verbindung zu Discord)
async def run_rpc(cfg):
    token = (cfg.get("token") or "").strip()
    if not token or token.startswith("DEIN"):
        print(f"\n{C_RED}❌ Kein Token gesetzt! Erst Menü → Punkt 2 (Token & App-ID).{C_RESET}")
        pause()
        return

    app_id = valid_app_id(cfg)
    act = cfg.get("activity", {})
    wants_rich = bool(act.get("large_image") or act.get("small_image") or act.get("buttons"))
    if not app_id and wants_rich:
        print(f"\n{C_YELLOW}⚠️  Keine gültige Application ID — Bilder & Buttons werden weggelassen (nur Text).")
        print(f"   Für volle Rich Presence: App-ID in Menü → Punkt 2 eintragen.{C_RESET}\n")

    start_ts = int(time.time() * 1000)
    show_preview(cfg)
    print(f"{C_GREEN}Verbinde mit Discord...{C_RESET}")
    print(f"{C_DIM}(Zum Beenden: Lautstärke-Leiser + C, oder Strg+C){C_RESET}\n")

    backoff = 5
    while True:
        try:
            async with websockets.connect(GATEWAY_URL, max_size=10 * 1024 * 1024) as ws:
                seq = None
                heartbeat_task = None
                username = "?"

                async def heartbeat(interval_ms):
                    nonlocal seq
                    try:
                        while True:
                            await asyncio.sleep(interval_ms / 1000)
                            await ws.send(json.dumps({"op": 1, "d": seq}))
                    except (asyncio.CancelledError, Exception):
                        pass

                try:
                    async for raw in ws:
                        try:
                            msg = json.loads(raw)
                        except json.JSONDecodeError:
                            continue
                        op = msg.get("op")
                        if msg.get("s") is not None:
                            seq = msg["s"]

                        if op == 10:  # Hello
                            interval = msg["d"]["heartbeat_interval"]
                            heartbeat_task = asyncio.create_task(heartbeat(interval))
                            # Identify — Handy-Device damit es wie Mobile aussieht
                            identify = {
                                "op": 2,
                                "d": {
                                    "token": token,
                                    "intents": 0,
                                    "properties": {
                                        "os": "Android",
                                        "browser": "Discord Android",
                                        "device": "Android",
                                    },
                                    "presence": build_presence(cfg, start_ts),
                                },
                            }
                            await ws.send(json.dumps(identify))

                        elif op == 11:  # Heartbeat ACK
                            pass

                        elif op == 0:  # Dispatch
                            t = msg.get("t")
                            if t == "READY":
                                username = msg["d"]["user"].get("username", "?")
                                backoff = 5
                                # Presence nach READY nochmal explizit senden (wichtig!)
                                try:
                                    await ws.send(json.dumps({"op": 3, "d": build_presence(cfg, start_ts)}))
                                except Exception:
                                    pass
                                print(f"{C_GREEN}✅ Online als {C_BOLD}{username}{C_RESET}{C_GREEN}! Rich Presence läuft.{C_RESET}")
                                print(f"{C_DIM}   Handy-Display kann aus — Termux läuft weiter (Wakelock empfohlen).{C_RESET}")
                                print(f"{C_DIM}   Prüfen: Server-Mitgliederliste / 2. Account / Firefox — eigenes Handy-Profil zeigt es oft nicht!{C_RESET}\n")
                            elif t == "RESUMED":
                                print(f"{C_GREEN}✅ Verbindung wiederhergestellt.{C_RESET}")
                                try:
                                    await ws.send(json.dumps({"op": 3, "d": build_presence(cfg, start_ts)}))
                                except Exception:
                                    pass

                        elif op == 7:  # Reconnect
                            print(f"{C_YELLOW}↻ Discord verlangt Reconnect...{C_RESET}")
                            break

                        elif op == 9:  # Invalid Session
                            print(f"{C_YELLOW}↻ Session ungültig — neu verbinden...{C_RESET}")
                            await asyncio.sleep(3)
                            break

                finally:
                    if heartbeat_task:
                        heartbeat_task.cancel()

        except websockets.exceptions.ConnectionClosed as e:
            print(f"{C_RED}✖ Verbindung geschlossen ({e.code}). Neuversuch in {backoff}s...{C_RESET}")
        except Exception as e:
            err = str(e)
            if "401" in err or "Authentication failed" in err or "4004" in err:
                print(f"\n{C_RED}❌ TOKEN UNGÜLTIG! Bitte neuen Token in Menü → Punkt 2 eintragen.{C_RESET}")
                pause()
                return
            print(f"{C_RED}✖ Fehler: {err[:150]} — Neuversuch in {backoff}s...{C_RESET}")

        await asyncio.sleep(backoff)
        backoff = min(backoff * 2, 60)


def start_rpc(cfg):
    try:
        asyncio.run(run_rpc(cfg))
    except KeyboardInterrupt:
        print(f"\n\n{C_YELLOW}👋 RPC gestoppt. Bis bald!{C_RESET}")
        time.sleep(1)


def start_test_mode(cfg):
    """Minimal-Test: nur Text, keine Bilder/Buttons/App-ID — zum Eingrenzen."""
    test_cfg = json.loads(json.dumps(cfg))
    test_cfg["application_id"] = ""
    test_cfg["status"] = "online"
    test_cfg["activity"] = {
        "name": "Fufcord Test",
        "type": 0,
        "details": "Wenn du das siehst, geht alles ✅",
        "state": "Test läuft ...",
        "large_image": "",
        "large_text": "",
        "small_image": "",
        "small_text": "",
        "stream_url": "",
        "buttons": [],
        "use_timestamp": True,
        "party_current": 0,
        "party_max": 0,
    }
    print(f"\n{C_MAGENTA}{C_BOLD}🧪 TEST-MODUS — minimale Presence (nur Text, ohne Bilder/Buttons).{C_RESET}")
    print(f"{C_DIM}   So prüfen: Server-Mitgliederliste / 2. Account / Firefox-discord.com{C_RESET}")
    print(f"{C_DIM}   Eigenes Handy-Profil zeigt die Activity oft NICHT — das ist normal!{C_RESET}\n")
    start_rpc(test_cfg)


def do_update():
    """Git-Update direkt aus der App (git pull)."""
    import subprocess
    clear()
    banner()
    print(f"{C_BOLD}── Update ──{C_RESET}\n")
    print(f"{C_DIM}Hole neueste Version von GitHub...{C_RESET}\n")
    try:
        r = subprocess.run(["git", "pull"], cwd=BASE_DIR, capture_output=True, text=True, timeout=90)
        out = ((r.stdout or "") + "\n" + (r.stderr or "")).strip()
        print((out[:1500] if out else "(keine Ausgabe)") + "\n")
        if "Already up to date" in out or "bereits aktuell" in out:
            print(f"{C_GREEN}✅ Du bist aktuell (v{VERSION}).{C_RESET}")
        elif r.returncode == 0:
            print(f"{C_GREEN}✅ Update geladen! Wichtig: App einmal neu starten:")
            print(f"   Punkt 0 (Beenden) → dann: bash start.sh{C_RESET}")
        else:
            print(f"{C_RED}⚠️  Update unklar — versuch manuell: cd ~/Fufcord && git pull{C_RESET}")
    except FileNotFoundError:
        print(f"{C_RED}❌ git nicht gefunden. In Termux: pkg install git{C_RESET}")
    except Exception as e:
        print(f"{C_RED}❌ Update fehlgeschlagen: {e}{C_RESET}")
        print(f"{C_DIM}Manuell: cd ~/Fufcord && git pull{C_RESET}")
    pause()


def export_json(cfg):
    """Punkt 9: Presence als JSON anzeigen + speichern (für KI)."""
    clear()
    banner()
    print(f"{C_BOLD}── 📤 Presence als JSON (für KI) ──{C_RESET}\n")
    data = {
        "status": cfg.get("status", "online"),
        "application_id": cfg.get("application_id", ""),
        "activity": cfg.get("activity", {}),
    }
    code = json.dumps(data, ensure_ascii=False, indent=2)
    path = os.path.join(BASE_DIR, "meine-presence.json")
    try:
        with open(path, "w", encoding="utf-8") as f:
            f.write(code)
        print(f"{C_GREEN}✅ Gespeichert als: meine-presence.json{C_RESET}")
    except OSError as e:
        print(f"{C_RED}⚠️  Speichern ging nicht: {e}{C_RESET}")
    print(f"\n{C_YELLOW}── Code zum Kopieren (lang drücken → kopieren) ──{C_RESET}\n")
    print(code)
    print(f"\n{C_YELLOW}── 🤖 So geht's mit KI ──{C_RESET}")
    print(f"  1. Code oben kopieren + einer KI schicken (ChatGPT, Claude, Gemini...)")
    print(f"  2. Dazu schreiben, z.B.:")
    print(f'     {C_CYAN}"Erstelle mir eine Discord Rich Presence im gleichen JSON-Format.')
    print(f'     Ich will: [z.B. Elden Ring, mystisch, mit Party 2/4].')
    print(f'     Antworte NUR mit dem JSON-Code, ohne Erklärung."{C_RESET}')
    print(f"  3. Antwort der KI kopieren → Fufcord Punkt 10 → einfügen → fertig! 🚀")
    print(f"\n{C_DIM}Hinweis: Kein Token im Export — sicher zu teilen.{C_RESET}")
    pause()


def import_json(cfg):
    """Punkt 10: JSON-Code einfügen (z.B. von KI) → wird deine Presence."""
    clear()
    banner()
    print(f"{C_BOLD}── 📥 JSON einfügen (z.B. von KI) ──{C_RESET}\n")
    print(f"  Füge jetzt den JSON-Code ein (lang drücken → Einfügen).")
    print(f"  Danach eine {C_YELLOW}LEERE Zeile{C_RESET} (2x Enter) zum Fertigstellen.")
    print(f"  Abbrechen: einfach direkt Enter auf leerer Zeile.\n")
    lines = []
    while True:
        try:
            line = input()
        except EOFError:
            break
        if line.strip() == "":
            if lines:
                break
            print(f"{C_DIM}Abgebrochen.{C_RESET}")
            time.sleep(1)
            return
        lines.append(line)
    block = extract_json_block("\n".join(lines))
    if not block:
        print(f"\n{C_RED}❌ Kein JSON gefunden! Code prüfen und nochmal versuchen.{C_RESET}")
        pause()
        return
    try:
        data = json.loads(block)
    except json.JSONDecodeError as e:
        print(f"\n{C_RED}❌ JSON fehlerhaft: {e}{C_RESET}")
        print(f"{C_DIM}Tipp: Der KI sagen 'Antworte NUR mit JSON, ohne Erklärung'.{C_RESET}")
        pause()
        return
    if not isinstance(data, dict):
        print(f"\n{C_RED}❌ Das ist kein gültiges Presence-Objekt.{C_RESET}")
        pause()
        return
    if isinstance(data.get("activity"), dict):
        activity_raw = data["activity"]
        new_status = str(data.get("status", cfg.get("status", "online"))).lower()
        new_appid = str(data.get("application_id", "") or "").strip()
    elif "name" in data:
        activity_raw = data
        new_status = cfg.get("status", "online")
        new_appid = ""
    else:
        print(f"\n{C_RED}❌ Kein 'activity'/'name' gefunden — falsches Format.{C_RESET}")
        pause()
        return
    cfg["activity"], sanitize_warns = sanitize_activity(activity_raw)
    if new_status in STATUS_LABELS:
        cfg["status"] = new_status
    if new_appid.isdigit() and len(new_appid) >= 15:
        cfg["application_id"] = new_appid
    save_config(cfg)
    clear()
    banner()
    print(f"{C_GREEN}{C_BOLD}✅ Importiert! Deine neue Presence:{C_RESET}")
    if sanitize_warns:
        print(f"{C_YELLOW}── Automatisch repariert:{C_RESET}")
        for wmsg in sanitize_warns:
            print(f"  {C_YELLOW}•{C_RESET} {wmsg}")
    show_preview(cfg)
    print(f"  {C_DIM}App-ID: {(cfg.get('application_id') or '(keine — Bilder/Buttons brauchen eine!)')}{C_RESET}")
    name = ask("Als Preset speichern? Name eingeben (Enter = nein)")
    if name.strip():
        pname = "".join(c for c in name if c.isalnum() or c in ("-", "_", " ")).strip().replace(" ", "-").lower()
        if pname:
            path = os.path.join(PRESETS_DIR, pname + ".json")
            with open(path, "w", encoding="utf-8") as f:
                json.dump({"status": cfg.get("status"), "application_id": cfg.get("application_id"),
                           "activity": cfg.get("activity")}, f, ensure_ascii=False, indent=2)
            print(f"{C_GREEN}✅ Preset '{pname}' gespeichert!{C_RESET}")
    print(f"\n{C_GREEN}🚀 Fertig! Jetzt Punkt 1 zum Starten.{C_RESET}")
    pause()


def check_presence(cfg):
    """Punkt 11: Presence prüfen, Probleme finden, reparieren + Notfall-Test."""
    while True:
        clear()
        banner()
        print(f"{C_BOLD}── 🔍 Presence prüfen & reparieren ──{C_RESET}\n")
        a = cfg.get("activity", {})
        raw_app = (cfg.get("application_id") or "").strip()
        app_id = valid_app_id(cfg)
        probs = []

        if raw_app and not app_id:
            probs.append(("🔴", f"App-ID ungültig ('{raw_app[:24]}') → muss eine lange Zahl sein! Bilder/Buttons werden weggelassen."))
        elif not raw_app and (a.get("large_image") or a.get("small_image") or a.get("buttons")):
            probs.append(("🟡", "Bilder/Buttons gesetzt, aber KEINE App-ID → werden weggelassen (nur Text sichtbar)."))
        elif app_id:
            probs.append(("🟢", f"App-ID Format OK ({app_id[:6]}...)."))

        for key, label in (("large_image", "Großes Bild"), ("small_image", "Kleines Bild")):
            v = (a.get(key) or "").strip()
            if v:
                if re.fullmatch(r"[a-z0-9_]{1,32}", v):
                    probs.append(("🟢", f"{label} '{v}': Format OK — existiert es auch in deiner App? (Developer Portal → Art Assets!)"))
                else:
                    probs.append(("🔴", f"{label} '{v}': ungültiger Name! Nur Kleinbuchstaben, Zahlen, _ (max 32)."))

        btns = a.get("buttons") or []
        if btns and not app_id:
            probs.append(("🟡", f"{len(btns)} Button(s) gesetzt, aber ohne App-ID werden sie weggelassen."))
        for i, b in enumerate(btns, 1):
            if not isinstance(b, dict):
                probs.append(("🔴", f"Button {i}: kaputtes Format → wird entfernt."))
                continue
            label = str(b.get("label", ""))
            url = str(b.get("url", ""))
            if EMOJI_RE.search(label):
                probs.append(("🔴", f"Button {i} ('{label[:20]}'): enthält Emojis → BLOCKIERT die Anzeige! Entfernen!"))
            if len(label) > 32:
                probs.append(("🟡", f"Button {i}: Text zu lang ({len(label)} statt max 32) → wird gekürzt."))
            if not (url.startswith("https://") or url.startswith("http://")):
                probs.append(("🔴", f"Button {i}: Link ungültig ('{url[:30]}') → muss mit http(s):// anfangen."))
        if not btns:
            probs.append(("🟢", "Keine Buttons — unproblematisch."))

        if len(a.get("details", "") or "") > 128 or len(a.get("state", "") or "") > 128:
            probs.append(("🟡", "Details/State zu lang → wird automatisch gekürzt."))
        try:
            t = int(a.get("type", 0))
            if t not in ACTIVITY_TYPES:
                probs.append(("🔴", f"Typ {t} ungültig → wird 'Spielt'."))
        except (ValueError, TypeError):
            probs.append(("🔴", "Typ ungültig → wird 'Spielt'."))

        for sym, txt in probs:
            print(f"  {sym} {txt}")
        print(f"\n  {C_DIM}Wichtig: Bild-Namen müssen in DEINER App hochgeladen sein")
        print(f"  (discord.com/developers → deine App → Rich Presence → Art Assets).{C_RESET}\n")
        print(f"  {C_GREEN}1{C_RESET}  🔧 Auto-Reparatur (jetzt speichern)")
        print(f"  {C_YELLOW}2{C_RESET}  🆘 Notfall-Test: nur Text starten (Bilder/Buttons weg)")
        print(f"  {C_RED}X{C_RESET}  Zurück")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()
        if w == "1":
            cfg["activity"], warns = sanitize_activity(a)
            save_config(cfg)
            print(f"\n{C_GREEN}✅ Repariert & gespeichert!{C_RESET}")
            for wmsg in warns:
                print(f"  {C_YELLOW}•{C_RESET} {wmsg}")
            print(f"\n{C_DIM}Jetzt testen: Menü → Punkt 1. Falls immer noch nichts → Option 2 (Notfall-Test).{C_RESET}")
            pause()
            return
        elif w == "2":
            em = json.loads(json.dumps(cfg))
            em["activity"]["large_image"] = ""
            em["activity"]["large_text"] = ""
            em["activity"]["small_image"] = ""
            em["activity"]["small_text"] = ""
            em["activity"]["buttons"] = []
            print(f"\n{C_MAGENTA}{C_BOLD}🆘 NOTFALL-TEST: nur dein Text, keine Bilder/Buttons.{C_RESET}")
            print(f"{C_DIM}   Wird DAS angezeigt → lag es an Bild/Button/App-ID!{C_RESET}\n")
            start_rpc(em)
            return
        elif w == "x":
            return


# ================================================================= Anleitung
def show_help():
    clear()
    banner()
    print(f"""{C_BOLD}── Kurzanleitung ──{C_RESET}

{C_YELLOW}1. Discord-App erstellen (für Bilder + App-ID):{C_RESET}
   • https://discord.com/developers/applications öffnen
   • "New Application" → Namen wählen (z.B. Fufcord)
   • Application ID kopieren → in Fufcord Menü Punkt 2 eintragen
   • Links "Rich Presence" → "Art Assets" → "Add Image(s)"
   • Bild hochladen, Namen merken (z.B. logo)
   • Diesen Namen in Fufcord bei "Großes Bild" eintragen

{C_YELLOW}2. Token holen:{C_RESET}
   • Am PC: Discord im Browser öffnen → F12 → Reiter "Application"
   • Links "Local Storage" → https://discord.com → "token" kopieren
   • {C_RED}Niemals teilen! Wie ein Passwort behandeln.{C_RESET}

{C_YELLOW}3. In Termux:{C_RESET}
   • Menü Punkt 2: Token + App-ID eintragen
   • Menü Punkt 3: Presence wie bei Vencord einstellen
   • Menü Punkt 1: RPC starten — fertig! 🚀

{C_YELLOW}4. Termux-Tipps:{C_RESET}
   • Display aus? → 'termux-wake-lock' eingeben, damit es weiterläuft
   • Beenden: Lautstärke-Leiser + C
   • Update: 'cd ~/Fufcord && git pull'
   • Neustart: 'bash start.sh'

{C_YELLOW}5. Nichts zu sehen in Discord?{C_RESET}
   • NICHT im eigenen Handy-Profil prüfen (zeigt es oft nicht!)
   • Stattdessen: Server-Mitgliederliste / 2. Account / Firefox
   • Discord: Einstellungen → Privatsphäre → Aktivitätsstatus AN
   • Menü Punkt 7 = Test-Modus (minimal, zum Eingrenzen)
""")
    pause()


# ================================================================= Hauptmenü
def main():
    cfg = load_config()
    # Erster Start ohne Token → direkt zum Setup
    if not (cfg.get("token") or "").strip():
        clear()
        banner()
        print(f"{C_YELLOW}👋 Willkommen bei Fufcord! Zuerst richten wir Token & App-ID ein.{C_RESET}\n")
        pause()
        setup_token_appid(cfg)

    while True:
        clear()
        banner()
        print(f"  Token : {mask_token(cfg.get('token', ''))}   |   App-ID: {(cfg.get('application_id') or '(keine)')[:12]}")
        print(f"  Status: {STATUS_LABELS.get(cfg.get('status'), cfg.get('status'))}")
        print(f"  📦 {len(list_presets())} Presets installiert   |   v{VERSION}\n")
        print(f"  {C_GREEN}{C_BOLD}1{C_RESET}  🚀 RPC starten")
        print(f"  {C_YELLOW}2{C_RESET}  🔑 Token & Application ID")
        print(f"  {C_YELLOW}3{C_RESET}  🎨 Presence bearbeiten (Vencord-Stil)")
        print(f"  {C_YELLOW}4{C_RESET}  📦 Presets")
        print(f"  {C_YELLOW}5{C_RESET}  👁️  Vorschau anzeigen")
        print(f"  {C_YELLOW}6{C_RESET}  📖 Anleitung")
        print(f"  {C_MAGENTA}7{C_RESET}  🧪 Test-Modus (minimal, ohne Bilder)")
        print(f"  {C_CYAN}8{C_RESET}  🔄 Update laden (git pull)")
        print(f"  {C_YELLOW}9{C_RESET}  📤 Presence als JSON (für KI)")
        print(f"  {C_YELLOW}10{C_RESET} 📥 JSON einfügen (von KI)")
        print(f"  {C_YELLOW}11{C_RESET} 🔍 Prüfen & Reparieren (zeigt nix an?)")
        print(f"  {C_RED}0{C_RESET}  Beenden")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip()

        if w == "1":
            start_rpc(cfg)
        elif w == "2":
            setup_token_appid(cfg)
        elif w == "3":
            edit_presence(cfg)
        elif w == "4":
            presets_menu(cfg)
        elif w == "5":
            clear()
            banner()
            show_preview(cfg)
            pause()
        elif w == "6":
            show_help()
        elif w == "7":
            start_test_mode(cfg)
        elif w == "8":
            do_update()
        elif w == "9":
            export_json(cfg)
        elif w == "10":
            import_json(cfg)
        elif w == "11":
            check_presence(cfg)
        elif w == "0":
            print(f"\n{C_CYAN}👋 Ciao!{C_RESET}")
            break


if __name__ == "__main__":
    main()
