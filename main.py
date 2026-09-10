#!/usr/bin/env python3
# Fufcord — https://github.com/Fufi1925/Fufcord
# Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
# Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).

# -*- coding: utf-8 -*-
"""
FUFCORD v3.0 — Custom Discord Rich Presence für Termux (Handy)
Wie Vencord: eigene Rich Presence komplett selbst setzen.

  • Eigener Name, Typ, Details, State, Bilder, Buttons, Zeit, Party
  • Presets, Test-Modus, KI-Export/Import (JSON)
  • 🩺 Doktor: prüft Token/App/Bilder direkt bei Discord + Auto-Reparatur
  • 🛡️ Sicher-Modus: Nur-Text-Presence, die immer angezeigt wird
  • 🔧 Auto-Fix: Fehler werden beim Start automatisch erkannt & repariert
  • Nur 1 Abhängigkeit (websockets) — läuft auf jedem Handy

Start:  python main.py  (oder: bash start.sh)

HINWEIS: Nutzt deinen User-Token (Selfbot-Prinzip). Verstößt gegen die
Discord-ToS — Sperrung theoretisch möglich. Nutzung auf eigene Gefahr!
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

# ------------------------------------------------------------------ Konstanten
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "config.json")
EXAMPLE_PATH = os.path.join(BASE_DIR, "config.example.json")
PRESETS_DIR = os.path.join(BASE_DIR, "presets")

GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"
API_BASE = "https://discord.com/api/v9"
VERSION = "3.0"

C_RESET = "\033[0m"
C_BOLD = "\033[1m"
C_DIM = "\033[2m"
C_GREEN = "\033[92m"
C_CYAN = "\033[96m"
C_YELLOW = "\033[93m"
C_RED = "\033[91m"
C_MAGENTA = "\033[95m"
C_BLUE = "\033[94m"

ACTIVITY_TYPES = {0: "Spielt", 1: "Streamt", 2: "Hört", 3: "Schaut zu",
                  4: "Benutzerdefiniert", 5: "Tritt an in"}
STATUS_LABELS = {"online": "🟢 Online", "idle": "🌙 Abwesend",
                 "dnd": "⛔ Bitte nicht stören", "invisible": "⚫ Unsichtbar"}

DEFAULT_CONFIG = {
    "token": "",
    "application_id": "",
    "status": "online",
    "safe_mode": False,
    "activity": {
        "name": "Fufcord", "type": 0,
        "details": "Custom Rich Presence wie Vencord",
        "state": "läuft auf Termux 📱",
        "large_image": "logo", "large_text": "Fufcord RPC",
        "small_image": "", "small_text": "",
        "stream_url": "https://twitch.tv/deinname",
        "buttons": [{"label": "Mein Server", "url": "https://discord.gg/deinlink"}],
        "use_timestamp": True, "party_current": 0, "party_max": 0,
    },
}

EMOJI_RE = re.compile("[\U0001F000-\U0001FAFF\u2600-\u27BF\u2B00-\u2BFF\uFE0F\u200D]",
                      flags=re.UNICODE)


# =================================================================== UI-Basis
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
""".rstrip())


def pause():
    input(f"\n{C_DIM}Enter drücken um fortzufahren...{C_RESET}")


def ask(prompt, default=""):
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


def safe_type_num(a) -> int:
    try:
        t = int((a or {}).get("type", 0))
        return t if t in ACTIVITY_TYPES else 0
    except (ValueError, TypeError):
        return 0


def strip_emoji(text) -> str:
    return EMOJI_RE.sub("", str(text or ""))


# ===================================================================== Config
def load_config():
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


# ================================================== Validieren & Reparieren
def valid_app_id(cfg) -> str:
    """Echte App-ID? (lange Zahl) — sonst ''."""
    app_id = (cfg.get("application_id") or "").strip()
    if not app_id or app_id.startswith("DEINE"):
        return ""
    if not app_id.isdigit() or len(app_id) < 15:
        return ""
    return app_id


def sanitize_activity(raw):
    """Bereinigt ein Activity-Dict — falsche Werte killen die Presence!
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
    text = (text or "").replace("```json", "").replace("```", "")
    start = text.find("{")
    if start == -1:
        return None
    depth, in_str, esc = 0, False, False
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


# ======================================================== Activity-Gebäude
def build_activity(cfg, start_timestamp=None, safe=False):
    a = cfg.get("activity", {})
    act_type = safe_type_num(a)
    now_ms = int(time.time() * 1000)
    activity = {"name": a.get("name", "Fufcord") or "Fufcord",
                "type": act_type, "created_at": now_ms}
    if safe:  # Sicher-Modus: NUR Text — geht immer
        if a.get("details"):
            activity["details"] = a["details"][:128]
        if a.get("state"):
            activity["state"] = a["state"][:128]
        if a.get("use_timestamp"):
            activity["timestamps"] = {"start": start_timestamp or now_ms}
        return activity
    app_id = valid_app_id(cfg)
    if app_id:
        activity["application_id"] = app_id
    if a.get("details"):
        activity["details"] = a["details"][:128]
    if a.get("state"):
        activity["state"] = a["state"][:128]
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
    if act_type == 1:
        activity["url"] = a.get("stream_url") or "https://twitch.tv/"
    if app_id:
        buttons = []
        for b in (a.get("buttons") or [])[:2]:
            label = (b.get("label") or "").strip()
            url = (b.get("url") or "").strip()
            if label and url:
                buttons.append({"label": label[:32], "url": url})
        if buttons:
            activity["buttons"] = buttons
    if a.get("use_timestamp"):
        activity["timestamps"] = {"start": start_timestamp or now_ms}
    try:
        pc, pm = int(a.get("party_current") or 0), int(a.get("party_max") or 0)
        if pc > 0 and pm > 0:
            activity["party"] = {"id": "fufcord-party", "size": [pc, pm]}
    except (ValueError, TypeError):
        pass
    return activity


def build_presence(cfg, start_timestamp=None, safe=False):
    return {"since": int(time.time() * 1000),
            "activities": [build_activity(cfg, start_timestamp, safe)],
            "status": cfg.get("status", "online") or "online",
            "afk": False}


# ============================================================== Dashboard
def show_dashboard(cfg):
    tok = (cfg.get("token") or "").strip()
    token_ok = bool(tok) and not tok.startswith("DEIN")
    app = valid_app_id(cfg)
    a = cfg.get("activity", {})
    n_btn = len(a.get("buttons") or [])
    n_img = (1 if a.get("large_image") else 0) + (1 if a.get("small_image") else 0)
    safe = cfg.get("safe_mode", False)

    tok_s = f"✅ gesetzt ({mask_token(tok)})" if token_ok else f"{C_RED}❌ fehlt! → Punkt 2{C_RESET}"
    app_s = f"✅ {app[:6]}..." if app else f"{C_YELLOW}⚠️ keine (nur Text){C_RESET}"
    img_s = f"✅ {n_img} gesetzt" if n_img else "—"
    btn_s = f"✅ {n_btn} gesetzt" if n_btn else "—"
    safe_s = f"{C_GREEN}🛡️ AN (nur Text){C_RESET}" if safe else "AUS (voll)"

    print(f"  {C_DIM}┌─ Status ─────────────────────────{C_RESET}")
    print(f"  {C_DIM}│{C_RESET} 🔑 Token   : {tok_s}")
    print(f"  {C_DIM}│{C_RESET} 🆔 App-ID   : {app_s}")
    print(f"  {C_DIM}│{C_RESET} 🖼️ Bilder   : {img_s}   🔘 Buttons: {btn_s}")
    print(f"  {C_DIM}│{C_RESET} 🛡️ Sicher   : {safe_s}  📦 Presets: {len(list_presets())}")
    print(f"  {C_DIM}└────────────────────────────────{C_RESET}")


def show_preview(cfg, safe=False):
    a = cfg.get("activity", {})
    typ = ACTIVITY_TYPES.get(safe_type_num(a), "?")
    status = STATUS_LABELS.get(cfg.get("status", "online"), cfg.get("status"))
    eff_images = (not safe) and bool(valid_app_id(cfg))
    print(f"\n  {C_DIM}┌─ Discord-Vorschau ───────────────{C_RESET}")
    print(f"  {C_DIM}│{C_RESET} {status}")
    print(f"  {C_DIM}│{C_RESET} {C_BOLD}{typ} {a.get('name', '')}{C_RESET}")
    if a.get("details"):
        print(f"  {C_DIM}│{C_RESET} {a['details']}")
    if a.get("state"):
        print(f"  {C_DIM}│{C_RESET} {a['state']}")
    if eff_images and a.get("large_image"):
        print(f"  {C_DIM}│{C_RESET} 🖼️ [{a['large_image']}]" + (f" {a['large_text']}" if a.get("large_text") else ""))
    if eff_images and a.get("small_image"):
        print(f"  {C_DIM}│{C_RESET} 🔹 [{a['small_image']}]")
    if eff_images:
        for b in (a.get("buttons") or [])[:2]:
            if isinstance(b, dict) and b.get("label"):
                print(f"  {C_DIM}│{C_RESET} {C_BLUE}[ {b['label']} ]{C_RESET}")
    elif a.get("buttons") or a.get("large_image"):
        print(f"  {C_DIM}│{C_RESET} {C_YELLOW}⚠️ Bilder/Buttons aktiv, aber ohne App-ID unsichtbar!{C_RESET}")
    if a.get("use_timestamp"):
        print(f"  {C_DIM}│{C_RESET} ⏱️ Verstrichen: läuft ab Start")
    if safe:
        print(f"  {C_DIM}│{C_RESET} {C_GREEN}🛡️ Sicher-Modus: nur Text{C_RESET}")
    print(f"  {C_DIM}└────────────────────────────────{C_RESET}\n")


# =================================================================== Editor
def edit_presence(cfg):
    a = cfg["activity"]
    while True:
        clear()
        banner()
        print(f"\n{C_BOLD}── 🎨 Presence bearbeiten ──{C_RESET}")
        show_preview(cfg)
        print(f"  {C_YELLOW}1{C_RESET}  Name           : {a.get('name')}")
        print(f"  {C_YELLOW}2{C_RESET}  Typ            : {ACTIVITY_TYPES.get(safe_type_num(a))} ({safe_type_num(a)})")
        print(f"  {C_YELLOW}3{C_RESET}  Details (Z.1)  : {a.get('details')}")
        print(f"  {C_YELLOW}4{C_RESET}  State (Z.2)    : {a.get('state')}")
        print(f"  {C_YELLOW}5{C_RESET}  Großes Bild    : {a.get('large_image')}  | Text: {a.get('large_text')}")
        print(f"  {C_YELLOW}6{C_RESET}  Kleines Bild   : {a.get('small_image')}  | Text: {a.get('small_text')}")
        print(f"  {C_YELLOW}7{C_RESET}  Buttons        : {len(a.get('buttons') or [])} gesetzt")
        print(f"  {C_YELLOW}8{C_RESET}  Zeitstempel    : {'an ✅' if a.get('use_timestamp') else 'aus ❌'}")
        print(f"  {C_YELLOW}9{C_RESET}  Online-Status  : {STATUS_LABELS.get(cfg.get('status'), cfg.get('status'))}")
        print(f"  {C_YELLOW}10{C_RESET} Stream-URL     : {a.get('stream_url')}")
        print(f"  {C_YELLOW}11{C_RESET} Party          : {a.get('party_current')}/{a.get('party_max')}")
        print(f"\n  {C_GREEN}S{C_RESET} Speichern & zurück      {C_RED}X{C_RESET} Abbrechen")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()

        if w == "1":
            a["name"] = ask("Aktivitäts-Name (z.B. Minecraft, Spotify)", a.get("name", "Fufcord"))
        elif w == "2":
            print("\n  Typen: 0=Spielt  1=Streamt  2=Hört  3=Schaut zu  4=Custom  5=Tritt an")
            try:
                t = int(ask("Typ-Nummer", str(safe_type_num(a))))
                a["type"] = t if t in ACTIVITY_TYPES else 0
            except ValueError:
                print(f"{C_RED}Ungültig!{C_RESET}")
                time.sleep(1)
        elif w == "3":
            a["details"] = ask("Details — erste Zeile", a.get("details", ""))
        elif w == "4":
            a["state"] = ask("State — zweite Zeile", a.get("state", ""))
        elif w == "5":
            print(f"\n{C_DIM}Asset-Name aus dem Developer Portal (z.B. 'logo').")
            print(f"Bild-URLs gehen NICHT — nur hochgeladene Assets.{C_RESET}")
            a["large_image"] = ask("Großes Bild (Asset-Name, leer = keins)", a.get("large_image", ""))
            if a["large_image"]:
                a["large_text"] = ask("Hover-Text", a.get("large_text", ""))
        elif w == "6":
            a["small_image"] = ask("Kleines Bild (Asset-Name, leer = keins)", a.get("small_image", ""))
            if a["small_image"]:
                a["small_text"] = ask("Hover-Text", a.get("small_text", ""))
        elif w == "7":
            print(f"\n{C_DIM}Max. 2 Buttons, Link mit http(s):// — KEINE Emojis im Text!{C_RESET}")
            btns = []
            for i in range(1, 3):
                alt = (a.get("buttons") or [])
                alt_l = alt[i - 1].get("label", "") if len(alt) >= i else ""
                alt_u = alt[i - 1].get("url", "") if len(alt) >= i else ""
                label = ask(f"Button {i} Text (leer = überspringen)", alt_l)
                if not label:
                    continue
                if EMOJI_RE.search(label):
                    print(f"{C_YELLOW}⚠️ Emojis entfernt (blockieren die Anzeige!).{C_RESET}")
                    label = strip_emoji(label).strip()
                    if not label:
                        continue
                url = ask(f"Button {i} Link", alt_u or "https://")
                if not (url.startswith("https://") or url.startswith("http://")):
                    print(f"{C_RED}Link muss mit http(s):// anfangen — übersprungen.{C_RESET}")
                    continue
                btns.append({"label": label[:32], "url": url})
            a["buttons"] = btns
        elif w == "8":
            a["use_timestamp"] = ask_yes_no("Verstrichene Zeit anzeigen?", bool(a.get("use_timestamp", True)))
        elif w == "9":
            print("\n  online / idle / dnd / invisible")
            s_ = ask("Online-Status", cfg.get("status", "online")).strip().lower()
            cfg["status"] = s_ if s_ in STATUS_LABELS else "online"
        elif w == "10":
            a["stream_url"] = ask("Stream-URL (nur bei Typ 'Streamt')", a.get("stream_url", ""))
        elif w == "11":
            try:
                a["party_current"] = int(ask("Party aktuell (0 = aus)", str(a.get("party_current", 0))))
                a["party_max"] = int(ask("Party maximal (0 = aus)", str(a.get("party_max", 0))))
            except ValueError:
                print(f"{C_RED}Bitte Zahlen eingeben.{C_RESET}")
                time.sleep(1)
        elif w == "s":
            cfg["activity"], warns = sanitize_activity(a)
            save_config(cfg)
            print(f"{C_GREEN}✅ Gespeichert!{C_RESET}")
            for wmsg in warns:
                print(f"  {C_YELLOW}• Auto-Fix: {wmsg}{C_RESET}")
            time.sleep(1.2)
            return
        elif w == "x":
            return


def setup_token_appid(cfg):
    clear()
    banner()
    print(f"\n{C_BOLD}── 🔑 Token & Application ID ──{C_RESET}\n")
    print(f"  Aktueller Token : {mask_token(cfg.get('token', ''))}")
    print(f"  Aktuelle App-ID : {cfg.get('application_id') or '(nicht gesetzt)'}\n")
    print(f"  {C_DIM}App erstellen: discord.com/developers/applications → New Application")
    print(f"  → Application ID kopieren → Rich Presence → Art Assets → Bilder hochladen.{C_RESET}\n")
    print(f"  {C_YELLOW}⚠️ Token = Passwort! Niemals teilen!{C_RESET}")
    print(f"  {C_DIM}Token nur mit Handy holen? → Datei TOKEN-HOLEN.md (Firefox-Methode).{C_RESET}\n")
    token = ask("User-Token einfügen (Enter = behalten)", "")
    if token:
        cfg["token"] = token.strip().strip('"').strip("'").replace(" ", "")
    appid = ask("Application ID (Enter = behalten)", cfg.get("application_id", ""))
    cfg["application_id"] = (appid or "").strip()
    save_config(cfg)
    print(f"\n{C_GREEN}✅ Gespeichert!{C_RESET}")
    pause()


# =================================================================== Presets
def presets_menu(cfg):
    while True:
        clear()
        banner()
        print(f"\n{C_BOLD}── 📦 Presets ──{C_RESET}\n")
        presets = list_presets()
        if presets:
            for i, p in enumerate(presets, 1):
                print(f"  {C_YELLOW}{i}{C_RESET}  {p}")
        else:
            print(f"  {C_DIM}(noch keine — speichere dein erstes!){C_RESET}")
        print(f"\n  {C_GREEN}S{C_RESET} Speichern (aktuelle Presence)   {C_GREEN}L/Nummer{C_RESET} Laden")
        print(f"  {C_RED}D+N{C_RESET} Löschen (z.B. 'd 2')          {C_RED}X{C_RESET} Zurück")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()

        if w == "s":
            name = ask("Preset-Name (z.B. gaming, chill)")
            name = "".join(c for c in name if c.isalnum() or c in ("-", "_", " ")).strip().replace(" ", "-").lower()
            if not name:
                print(f"{C_RED}Ungültiger Name.{C_RESET}")
                time.sleep(1)
                continue
            with open(os.path.join(PRESETS_DIR, name + ".json"), "w", encoding="utf-8") as f:
                json.dump({"status": cfg.get("status"), "application_id": cfg.get("application_id"),
                           "activity": cfg.get("activity")}, f, ensure_ascii=False, indent=2)
            print(f"{C_GREEN}✅ Preset '{name}' gespeichert!{C_RESET}")
            time.sleep(1)
        elif w == "x":
            return
        elif w.startswith("d"):
            try:
                target = list_presets()[int(w[1:].strip()) - 1]
                os.remove(os.path.join(PRESETS_DIR, target + ".json"))
                print(f"{C_GREEN}✅ '{target}' gelöscht.{C_RESET}")
            except (ValueError, IndexError, OSError):
                print(f"{C_RED}Ungültig. Beispiel: d 1{C_RESET}")
            time.sleep(1)
        else:
            try:
                target = list_presets()[int(w.lstrip("l")) - 1]
                with open(os.path.join(PRESETS_DIR, target + ".json"), "r", encoding="utf-8") as f:
                    data = json.load(f)
                cfg["activity"], warns = sanitize_activity(data.get("activity", cfg["activity"]))
                cfg["status"] = data.get("status", cfg.get("status"))
                if data.get("application_id"):
                    cfg["application_id"] = data["application_id"]
                save_config(cfg)
                print(f"{C_GREEN}✅ Preset '{target}' geladen!{C_RESET}")
                for wmsg in warns:
                    print(f"  {C_YELLOW}• Auto-Fix: {wmsg}{C_RESET}")
            except (ValueError, IndexError, OSError):
                print(f"{C_RED}Ungültige Auswahl.{C_RESET}")
            time.sleep(1.2)


# ============================================================== JSON (KI)
def export_json(cfg):
    clear()
    banner()
    print(f"\n{C_BOLD}── 📤 Presence als JSON (für KI) ──{C_RESET}\n")
    code = json.dumps({"status": cfg.get("status", "online"),
                       "application_id": cfg.get("application_id", ""),
                       "activity": cfg.get("activity", {})}, ensure_ascii=False, indent=2)
    try:
        with open(os.path.join(BASE_DIR, "meine-presence.json"), "w", encoding="utf-8") as f:
            f.write(code)
        print(f"{C_GREEN}✅ Gespeichert als: meine-presence.json{C_RESET}")
    except OSError as e:
        print(f"{C_RED}⚠️ Speichern ging nicht: {e}{C_RESET}")
    print(f"\n{C_YELLOW}── Code zum Kopieren (lang drücken) ──{C_RESET}\n")
    print(code)
    print(f"\n{C_YELLOW}── 🤖 So geht's mit KI ──{C_RESET}")
    print("  1. Code kopieren + einer KI schicken (ChatGPT, Claude, Gemini...)")
    print("  2. Dazu schreiben, z.B.:")
    print(f'     {C_CYAN}"Erstelle eine Rich Presence im gleichen JSON-Format.')
    print("     Ich will: [z.B. Elden Ring, mystisch, Party 2/4].")
    print(f'     Keine Emojis in Button-Text! Antworte NUR mit JSON."{C_RESET}')
    print("  3. Antwort kopieren → Punkt 10 → einfügen → fertig! 🚀")
    print(f"\n{C_DIM}Kein Token im Export — sicher zu teilen.{C_RESET}")
    pause()


def import_json(cfg):
    clear()
    banner()
    print(f"\n{C_BOLD}── 📥 JSON einfügen (z.B. von KI) ──{C_RESET}\n")
    print("  JSON-Code einfügen (lang drücken → Einfügen),")
    print(f"  dann {C_YELLOW}LEERE Zeile{C_RESET} (Enter) zum Fertigstellen.\n")
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
        print(f"\n{C_RED}❌ Kein JSON gefunden!{C_RESET}")
        pause()
        return
    try:
        data = json.loads(block)
    except json.JSONDecodeError as e:
        print(f"\n{C_RED}❌ JSON fehlerhaft: {e}{C_RESET}")
        print(f"{C_DIM}Tipp: KI sagen 'Antworte NUR mit JSON, ohne Erklärung'.{C_RESET}")
        pause()
        return
    if not isinstance(data, dict):
        print(f"\n{C_RED}❌ Kein gültiges Presence-Objekt.{C_RESET}")
        pause()
        return
    if isinstance(data.get("activity"), dict):
        activity_raw = data["activity"]
        new_status = str(data.get("status", cfg.get("status", "online"))).lower()
        new_appid = str(data.get("application_id", "") or "").strip()
    elif "name" in data:
        activity_raw, new_status, new_appid = data, cfg.get("status", "online"), ""
    else:
        print(f"\n{C_RED}❌ Kein 'activity'/'name' gefunden — falsches Format.{C_RESET}")
        pause()
        return
    cfg["activity"], warns = sanitize_activity(activity_raw)
    if new_status in STATUS_LABELS:
        cfg["status"] = new_status
    if new_appid.isdigit() and len(new_appid) >= 15:
        cfg["application_id"] = new_appid
    elif new_appid:
        warns.append(f"App-ID '{new_appid[:20]}' ungültig → alte behalten.")
    save_config(cfg)
    clear()
    banner()
    print(f"\n{C_GREEN}{C_BOLD}✅ Importiert!{C_RESET}")
    if warns:
        print(f"{C_YELLOW}── Automatisch repariert:{C_RESET}")
        for wmsg in warns:
            print(f"  {C_YELLOW}•{C_RESET} {wmsg}")
    show_preview(cfg)
    name = ask("Als Preset speichern? Name (Enter = nein)")
    if name.strip():
        pname = "".join(c for c in name if c.isalnum() or c in ("-", "_", " ")).strip().replace(" ", "-").lower()
        if pname:
            with open(os.path.join(PRESETS_DIR, pname + ".json"), "w", encoding="utf-8") as f:
                json.dump({"status": cfg.get("status"), "application_id": cfg.get("application_id"),
                           "activity": cfg.get("activity")}, f, ensure_ascii=False, indent=2)
            print(f"{C_GREEN}✅ Preset '{pname}' gespeichert!{C_RESET}")
    print(f"\n{C_GREEN}🚀 Fertig! Tipp: Punkt 11 (Doktor) prüft alles. Dann Punkt 1.{C_RESET}")
    pause()


# ==================================================================== Update
def do_update():
    import subprocess
    clear()
    banner()
    print(f"\n{C_BOLD}── 🔄 Update ──{C_RESET}\n")
    print(f"{C_DIM}Hole neueste Version von GitHub...{C_RESET}\n")
    try:
        r = subprocess.run(["git", "pull"], cwd=BASE_DIR, capture_output=True, text=True, timeout=90)
        out = ((r.stdout or "") + "\n" + (r.stderr or "")).strip()
        print((out[:1500] if out else "(keine Ausgabe)") + "\n")
        if "Already up to date" in out or "bereits aktuell" in out:
            print(f"{C_GREEN}✅ Du bist aktuell (v{VERSION}).{C_RESET}")
        elif r.returncode == 0:
            print(f"{C_GREEN}✅ Update geladen! App neu starten:")
            print(f"   Punkt 0 → dann: bash start.sh{C_RESET}")
        else:
            print(f"{C_RED}⚠️ Unklar — manuell: cd ~/Fufcord && git pull{C_RESET}")
    except FileNotFoundError:
        print(f"{C_RED}❌ git fehlt. Termux: pkg install git{C_RESET}")
    except Exception as e:
        print(f"{C_RED}❌ Update fehlgeschlagen: {e}{C_RESET}")
    pause()


# ===================================================== Discord-API-Prüfung
def discord_api(path, token, timeout=12):
    """GET an Discord-API. Gibt (ok, daten_oder_fehler) zurück."""
    import urllib.request
    import urllib.error
    try:
        req = urllib.request.Request(
            API_BASE + path,
            headers={"Authorization": token,
                     "User-Agent": ("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 "
                                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"),
                     "Accept": "*/*"})
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return True, json.load(r)
    except urllib.error.HTTPError as e:
        return False, f"HTTP {e.code}"
    except Exception as e:
        return False, str(e)[:90]


# ==================================================================== Doktor
def check_presence(cfg):
    """Punkt 11: Alles prüfen (lokal + Discord-API), auto-reparieren, Notfall-Test."""
    while True:
        clear()
        banner()
        print(f"\n{C_BOLD}── 🔍 Doktor ──{C_RESET}\n")
        a = cfg.get("activity", {})
        tok = (cfg.get("token") or "").strip()
        token_set = bool(tok) and not tok.startswith("DEIN")
        raw_app = (cfg.get("application_id") or "").strip()
        app_id = valid_app_id(cfg)
        probs = []

        # ---- Netzwerk-Checks (Token, App, Assets direkt bei Discord) ----
        token_state, app_state, asset_names = "unknown", "unknown", set()
        me_name = ""
        if token_set:
            print(f"  {C_DIM}🌐 Frage Discord-API (Token, App, Bilder)...{C_RESET}")
            ok_me, me = discord_api("/users/@me", tok)
            if ok_me and isinstance(me, dict) and me.get("username"):
                token_state, me_name = "ok", me["username"]
            elif me == "HTTP 401":
                token_state = "invalid"
            if app_id:
                ok_apps, apps = discord_api("/applications?with_team_applications=true", tok)
                if ok_apps and isinstance(apps, list):
                    mine = {str(x.get("id")): str(x.get("name", "")) for x in apps if isinstance(x, dict)}
                    app_state = "ok" if app_id in mine else "missing"
                    app_name = mine.get(app_id, "")
                elif apps == "HTTP 401":
                    app_state = "unknown"
                    if token_state == "unknown":
                        token_state = "invalid"
                if app_state == "ok":
                    ok_as, assets = discord_api(f"/applications/{app_id}/assets", tok)
                    if ok_as and isinstance(assets, list):
                        asset_names = {str(x.get("name", "")) for x in assets if isinstance(x, dict)}
            print("")
        else:
            probs.append(("🔴", "Kein Token gesetzt → Punkt 2! Ohne Token geht gar nichts."))

        # ---- Auswertung ----
        if token_state == "ok":
            probs.append(("🟢", f"Token ist GÜLTIG (Account: {me_name})."))
        elif token_state == "invalid":
            probs.append(("🔴", "Token ist UNGÜLTIG (Discord sagt Nein)! → Neuen holen (TOKEN-HOLEN.md) → Punkt 2."))
        elif token_set:
            probs.append(("🟡", "Token konnte nicht geprüft werden (kein Internet?) — Start trotzdem versuchen."))

        if raw_app and not app_id:
            probs.append(("🔴", f"App-ID '{raw_app[:24]}' ungültig (muss lange Zahl sein)! Bilder/Buttons werden weggelassen."))
        elif app_state == "ok":
            probs.append(("🟢", f"App existiert in deinem Account ({app_name or app_id[:6] + '...'})."))
        elif app_state == "missing":
            probs.append(("🔴", f"App-ID {app_id[:10]}... existiert NICHT in deinem Account! (KI erfunden? Falsche ID?) Bilder/Buttons können NICHT angezeigt werden!"))
        elif app_id:
            probs.append(("🟡", "App konnte nicht geprüft werden (kein Internet?)."))
        elif (a.get("large_image") or a.get("small_image") or a.get("buttons")):
            probs.append(("🟡", "Bilder/Buttons gesetzt, aber KEINE App-ID → werden weggelassen (nur Text)."))

        for key, label in (("large_image", "Großes Bild"), ("small_image", "Kleines Bild")):
            v = (a.get(key) or "").strip()
            if not v:
                continue
            if not re.fullmatch(r"[a-z0-9_]{1,32}", v):
                probs.append(("🔴", f"{label} '{v}': ungültiger Name! Nur a-z, 0-9, _ (max 32)."))
            elif asset_names and v not in asset_names:
                probs.append(("🔴", f"{label} '{v}': NICHT in deiner App hochgeladen! → Activity wird evtl. gar nicht angezeigt! Hochladen oder entfernen."))
            elif not asset_names and app_state == "ok":
                probs.append(("🟡", f"{label} '{v}': Assets konnten nicht geprüft werden (Internet?). Name muss exakt in Art Assets stehen!"))
            else:
                probs.append(("🟢", f"{label} '{v}': in deiner App gefunden ✅"))

        btns = a.get("buttons") or []
        if btns and not app_id:
            probs.append(("🟡", f"{len(btns)} Button(s) ohne App-ID → werden weggelassen."))
        for i, b in enumerate(btns, 1):
            if not isinstance(b, dict):
                probs.append(("🔴", f"Button {i}: kaputtes Format → wird entfernt."))
                continue
            blabel, burl = str(b.get("label", "")), str(b.get("url", ""))
            if EMOJI_RE.search(blabel):
                probs.append(("🔴", f"Button {i} ('{blabel[:20]}'): Emojis → BLOCKIEREN die Anzeige!"))
            if len(blabel) > 32:
                probs.append(("🟡", f"Button {i}: Text zu lang → wird gekürzt."))
            if not (burl.startswith("https://") or burl.startswith("http://")):
                probs.append(("🔴", f"Button {i}: Link ungültig ('{burl[:30]}') → muss mit http(s):// anfangen."))
        if not btns:
            probs.append(("🟢", "Keine Buttons — unproblematisch."))
        else:
            probs.append(("🟡", "Hinweis: Buttons werden von Discord manchmal komplett ignoriert/blockiert — bei Problemen zuerst Buttons entfernen (Option 2)."))

        if len(a.get("details", "") or "") > 128 or len(a.get("state", "") or "") > 128:
            probs.append(("🟡", "Details/State zu lang → wird gekürzt."))
        if safe_type_num(a) != (a.get("type", 0) if isinstance(a.get("type"), int) else -1):
            try:
                if int(a.get("type", 0)) not in ACTIVITY_TYPES:
                    probs.append(("🔴", "Typ ungültig → wird 'Spielt'."))
            except (ValueError, TypeError):
                probs.append(("🔴", "Typ ungültig → wird 'Spielt'."))

        for sym, txt in probs:
            print(f"  {sym} {txt}")

        has_red = any(s == "🔴" for s, _ in probs)
        print(f"\n  {'❌ Es gibt BLOCKER — erst reparieren!' if has_red else '✅ Keine Blocker gefunden.'}")
        print(f"\n  {C_GREEN}1{C_RESET}  🔧 Alles automatisch reparieren")
        print(f"  {C_YELLOW}2{C_RESET}  🔘 Nur Buttons entfernen & speichern")
        print(f"  {C_YELLOW}3{C_RESET}  🖼️ Nur Bilder entfernen & speichern")
        print(f"  {C_YELLOW}4{C_RESET}  🆘 Notfall-Test starten (nur Text)")
        print(f"  {C_RED}X{C_RESET}  Zurück")
        w = input(f"\n{C_BOLD}Auswahl:{C_RESET} ").strip().lower()

        if w == "1":
            cfg["activity"], warns = sanitize_activity(a)
            if app_state == "missing":
                cfg["application_id"] = ""
                warns.append("App-ID existiert nicht → entfernt (nur Text-Modus).")
            if asset_names:
                for key in ("large_image", "small_image"):
                    v = (cfg["activity"].get(key) or "").strip()
                    if v and v not in asset_names:
                        cfg["activity"][key] = ""
                        cfg["activity"][key.replace("image", "text")] = ""
                        warns.append(f"Bild '{v}' nicht in App → entfernt. (Hochladen: Developer Portal → Art Assets)")
            save_config(cfg)
            print(f"\n{C_GREEN}✅ Repariert & gespeichert!{C_RESET}")
            for wmsg in warns or ["Nichts zu tun — schon sauber."]:
                print(f"  {C_YELLOW}•{C_RESET} {wmsg}")
            print(f"\n{C_DIM}Jetzt: Menü → Punkt 1 testen.{C_RESET}")
            pause()
            return
        elif w == "2":
            cfg["activity"]["buttons"] = []
            save_config(cfg)
            print(f"\n{C_GREEN}✅ Buttons entfernt & gespeichert! Jetzt Punkt 1 testen.{C_RESET}")
            pause()
            return
        elif w == "3":
            cfg["activity"]["large_image"] = ""
            cfg["activity"]["large_text"] = ""
            cfg["activity"]["small_image"] = ""
            cfg["activity"]["small_text"] = ""
            save_config(cfg)
            print(f"\n{C_GREEN}✅ Bilder entfernt & gespeichert! Jetzt Punkt 1 testen.{C_RESET}")
            pause()
            return
        elif w == "4":
            print(f"\n{C_MAGENTA}{C_BOLD}🆘 NOTFALL-TEST: nur dein Text, ohne Bilder/Buttons.{C_RESET}")
            print(f"{C_DIM}   Wird DAS angezeigt → lag es an Bild/Button/App-ID!{C_RESET}\n")
            start_rpc(cfg, force_safe=True)
            return
        elif w == "x":
            return


# =================================================================== Gateway
async def run_rpc(cfg, force_safe=False):
    token = (cfg.get("token") or "").strip()
    if not token or token.startswith("DEIN"):
        print(f"\n{C_RED}❌ Kein Token! Erst Menü → Punkt 2.{C_RESET}")
        pause()
        return

    # In-Memory Kopie (Test-Modi dürfen Config NIE überschreiben!)
    eff = json.loads(json.dumps(cfg))
    eff["activity"], fixes = sanitize_activity(eff.get("activity", {}))
    safe = force_safe or bool(cfg.get("safe_mode", False))
    if safe:
        eff["application_id"] = ""
        eff["activity"]["large_image"] = ""
        eff["activity"]["large_text"] = ""
        eff["activity"]["small_image"] = ""
        eff["activity"]["small_text"] = ""
        eff["activity"]["buttons"] = []

    start_ts = int(time.time() * 1000)
    show_preview(eff, safe)
    if fixes:
        print(f"{C_YELLOW}🔧 Auto-Fix für diesen Start:{C_RESET}")
        for m in fixes[:5]:
            print(f"  • {m}")
        print("")
    if not valid_app_id(eff) and not safe:
        print(f"{C_YELLOW}⚠️ Keine gültige App-ID → Bilder/Buttons werden weggelassen (nur Text).{C_RESET}")
        print(f"{C_DIM}   Volle Rich Presence: App-ID in Punkt 2 eintragen.{C_RESET}\n")
    print(f"{C_GREEN}Verbinde mit Discord...{C_RESET}")
    print(f"{C_DIM}(Beenden: Lautstärke-Leiser + C){C_RESET}\n")

    backoff = 5
    while True:
        try:
            async with websockets.connect(GATEWAY_URL, max_size=10 * 1024 * 1024,
                                          ping_interval=None, ping_timeout=None) as ws:
                seq, username = None, "?"
                heartbeat_task = None

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
                        if op == 10:
                            heartbeat_task = asyncio.create_task(heartbeat(msg["d"]["heartbeat_interval"]))
                            await ws.send(json.dumps({
                                "op": 2, "d": {
                                    "token": token, "intents": 0,
                                    "properties": {"os": "Android", "browser": "Discord Android",
                                                   "device": "Android"},
                                    "presence": build_presence(eff, start_ts, safe)}}))
                        elif op == 0:
                            t = msg.get("t")
                            if t == "READY":
                                username = msg["d"]["user"].get("username", "?")
                                backoff = 5
                                try:
                                    await ws.send(json.dumps({"op": 3, "d": build_presence(eff, start_ts, safe)}))
                                except Exception:
                                    pass
                                print(f"{C_GREEN}✅ Online als {C_BOLD}{username}{C_RESET}{C_GREEN}! Presence läuft.{C_RESET}")
                                print(f"{C_DIM}   Prüfen: Mitgliederliste / 2. Account / Firefox (NICHT eigenes Handy-Profil!).{C_RESET}")
                                print(f"{C_DIM}   Nichts zu sehen? → Beenden → Punkt 11 (Doktor).{C_RESET}\n")
                            elif t == "RESUMED":
                                print(f"{C_GREEN}✅ Verbindung wiederhergestellt.{C_RESET}")
                                try:
                                    await ws.send(json.dumps({"op": 3, "d": build_presence(eff, start_ts, safe)}))
                                except Exception:
                                    pass
                        elif op == 7:
                            print(f"{C_YELLOW}↻ Discord verlangt Reconnect...{C_RESET}")
                            break
                        elif op == 9:
                            print(f"{C_YELLOW}↻ Session ungültig — neu verbinden...{C_RESET}")
                            await asyncio.sleep(3)
                            break
                finally:
                    if heartbeat_task:
                        heartbeat_task.cancel()
        except websockets.exceptions.ConnectionClosed as e:
            code = getattr(e, "code", 0)
            if code == 4004:
                print(f"\n{C_RED}❌ TOKEN UNGÜLTIG (Discord-Code 4004)! Neuen holen → Punkt 2.{C_RESET}")
                pause()
                return
            print(f"{C_RED}✖ Verbindung geschlossen ({code}). Neuversuch in {backoff}s...{C_RESET}")
        except Exception as e:
            err = str(e)
            if "401" in err or "Authentication failed" in err or "4004" in err:
                print(f"\n{C_RED}❌ TOKEN UNGÜLTIG! Neuen holen → Punkt 2.{C_RESET}")
                pause()
                return
            print(f"{C_RED}✖ Fehler: {err[:150]} — Neuversuch in {backoff}s...{C_RESET}")
        await asyncio.sleep(backoff)
        backoff = min(backoff * 2, 60)


def start_rpc(cfg, force_safe=False):
    try:
        asyncio.run(run_rpc(cfg, force_safe))
    except KeyboardInterrupt:
        print(f"\n\n{C_YELLOW}👋 RPC gestoppt.{C_RESET}")
        time.sleep(1)


def start_test_mode(cfg):
    test_cfg = json.loads(json.dumps(cfg))
    test_cfg["application_id"] = ""
    test_cfg["status"] = "online"
    test_cfg["activity"] = {
        "name": "Fufcord Test", "type": 0,
        "details": "Wenn du das siehst, geht alles ✅",
        "state": "Test läuft ...",
        "large_image": "", "large_text": "", "small_image": "", "small_text": "",
        "stream_url": "", "buttons": [], "use_timestamp": True,
        "party_current": 0, "party_max": 0}
    print(f"\n{C_MAGENTA}{C_BOLD}🧪 TEST-MODUS — minimal (nur Text).{C_RESET}")
    print(f"{C_DIM}   Prüfen: Mitgliederliste / 2. Account / Firefox.{C_RESET}\n")
    start_rpc(test_cfg)


def toggle_safe_mode(cfg):
    cfg["safe_mode"] = not cfg.get("safe_mode", False)
    save_config(cfg)
    clear()
    banner()
    if cfg["safe_mode"]:
        print(f"\n{C_GREEN}🛡️ Sicher-Modus AN — nur Text wird gesendet (geht IMMER).{C_RESET}")
        print(f"{C_DIM}Bilder/Buttons bleiben gespeichert, werden nur nicht gesendet.{C_RESET}")
    else:
        print(f"\n{C_YELLOW}🛡️ Sicher-Modus AUS — volle Rich Presence (Bilder + Buttons).{C_RESET}")
    pause()


# ================================================================== Anleitung
def show_help():
    clear()
    banner()
    print(f"""\n{C_BOLD}── 📖 Kurzanleitung ──{C_RESET}

{C_YELLOW}1. App erstellen (für Bilder + App-ID):{C_RESET}
   • discord.com/developers/applications → New Application
   • Application ID kopieren → Fufcord Punkt 2
   • Rich Presence → Art Assets → Bilder hochladen (Namen exakt!)

{C_YELLOW}2. Token holen (nur Handy):{C_RESET}
   • Siehe Datei TOKEN-HOLEN.md (Firefox-Methode, 5 Min.)
   • {C_RED}Niemals teilen!{C_RESET}

{C_YELLOW}3. Starten:{C_RESET}
   • Punkt 2: Token + App-ID → Punkt 1: Starten 🚀

{C_YELLOW}4. Nichts zu sehen in Discord?{C_RESET}
   • NICHT im eigenen Handy-Profil prüfen! → Mitgliederliste / 2. Account / Firefox
   • Discord: Einstellungen → Privatsphäre → Aktivitätsstatus AN
   • {C_GREEN}Punkt 11 (Doktor): prüft Token/App/Bilder bei Discord + repariert!{C_RESET}
   • Notfall: Punkt 11 → Option 4 (nur Text) oder Punkt 12 (Sicher-Modus)

{C_YELLOW}5. KI-Presence:{C_RESET}
   • Punkt 9: JSON kopieren → KI → Antwort bei Punkt 10 einfügen

{C_YELLOW}6. Termux:{C_RESET}
   • 'termux-wake-lock' (läuft bei Display-aus weiter)
   • Update: Punkt 8 oder 'cd ~/Fufcord && git pull'
""")
    pause()


# ================================================================== Hauptmenü
def main():
    cfg = load_config()
    cfg["activity"], startup_fixes = sanitize_activity(cfg.get("activity", {}))
    if startup_fixes:
        save_config(cfg)
    show_startup_fixes = bool(startup_fixes)

    if not (cfg.get("token") or "").strip():
        clear()
        banner()
        print(f"\n{C_YELLOW}👋 Willkommen bei Fufcord! Erst Token & App-ID einrichten.{C_RESET}\n")
        pause()
        setup_token_appid(cfg)

    while True:
        clear()
        banner()
        print("")
        show_dashboard(cfg)
        if show_startup_fixes:
            print(f"\n  {C_YELLOW}🔧 Auto-Fix beim Start:{C_RESET}")
            for m in startup_fixes[:5]:
                print(f"    • {m}")
            show_startup_fixes = False

        print(f"\n  {C_DIM}── Start ──{C_RESET}")
        print(f"  {C_GREEN}{C_BOLD}1{C_RESET}  🚀 RPC starten")
        print(f"\n  {C_DIM}── Einrichten ──{C_RESET}")
        print(f"  {C_YELLOW}2{C_RESET}  🔑 Token & Application ID")
        print(f"  {C_YELLOW}3{C_RESET}  🎨 Presence bearbeiten")
        print(f"  {C_YELLOW}4{C_RESET}  📦 Presets")
        print(f"  {C_YELLOW}5{C_RESET}  👁️ Vorschau anzeigen")
        print(f"\n  {C_DIM}── Tools ──{C_RESET}")
        print(f"  {C_YELLOW}6{C_RESET}  📖 Anleitung")
        print(f"  {C_MAGENTA}7{C_RESET}  🧪 Test-Modus (minimal)")
        print(f"  {C_CYAN}8{C_RESET}  🔄 Update laden")
        print(f"  {C_YELLOW}9{C_RESET}  📤 JSON Export (für KI)")
        print(f"  {C_YELLOW}10{C_RESET} 📥 JSON Import (von KI)")
        print(f"  {C_GREEN}11{C_RESET} 🔍 Doktor (prüfen & reparieren)")
        print(f"  {C_YELLOW}12{C_RESET} 🛡️ Sicher-Modus: {'AN 🟢' if cfg.get('safe_mode') else 'AUS'}")
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
            show_preview(cfg, bool(cfg.get("safe_mode")))
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
        elif w == "12":
            toggle_safe_mode(cfg)
        elif w == "0":
            print(f"\n{C_CYAN}👋 Ciao!{C_RESET}")
            break


if __name__ == "__main__":
    main()
