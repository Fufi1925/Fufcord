/*
 * FufcordPatch v1.0 — läuft IN Discord (per Xposed/LSPatch injiziert).
 * - Eigene Einstellungs-Seite in Discord (Einstellungen → Fufcord)
 * - Custom Rich Presence direkt aus Discord heraus (eigene Gateway-Verbindung)
 * Technik angelehnt an Revenge/Vendetta (Metro-Finder, Setting-Renderer-Patch).
 * WICHTIG: Plain JS, kein JSX (wird mit hermesc kompiliert).
 */
(function () {
"use strict";

var VERSION = "1.4";
var G = (typeof window !== "undefined" && window) ? window : globalThis;

function LOG() {
    try {
        var a = ["[Fufcord]"];
        for (var i = 0; i < arguments.length; i++) a.push(arguments[i]);
        console.log.apply(console, a);
    } catch (e) {}
}

// ---------------------------------------------------------------- Metro
var registry = {};       // Modul-ID -> exports
var moduleCbs = [];

function onModule(cb) { moduleCbs.push(cb); }

var finding = 0;
var pendingInject = false;
var emitCount = 0;

function emit(ex, id) {
    if (!ex) return;
    try { registry[id] = ex; } catch (e) { return; }
    try { emitCount++; } catch (e) {}
    // Während einer Suche nichts sofort tun (sonst Endlos-Verschachtelung!)
    if (finding > 0) { pendingInject = true; return; }
    runModuleCbs(ex, id);
}

function runModuleCbs(ex, id) {
    for (var i = 0; i < moduleCbs.length; i++) {
        try { moduleCbs[i](ex, id); } catch (e) {}
    }
}

function wrapFactory(rec, id) {
    if (!rec) return;
    try {
        if (rec.__fuf_wrapped) return;
        var orig = rec.factory;
        if (typeof orig !== "function") return;
        rec.__fuf_wrapped = true;
        rec.factory = function (glob, req, impDef, impAll, modObj, expo, depMap) {
            orig(glob, req, impDef, impAll, modObj, expo, depMap);
            try {
                if (modObj && modObj.exports) emit(modObj.exports, id);
            } catch (e) {}
        };
    } catch (e) {}
}

function handleRecord(rec, id) {
    if (!rec) return;
    var ex = null;
    try {
        if (rec.publicModule && rec.publicModule.exports) ex = rec.publicModule.exports;
    } catch (e) { ex = null; }
    if (ex && !isBad(ex)) emit(ex, id);
    else wrapFactory(rec, id);
}

// Bonus-Scan f\u00fcr alte Metros (Objekt-Registry) oder Dev-Laufzeiten (Map-Registry)
function scanRegistry(mods) {
    if (!mods) return 0;
    var n = 0;
    try {
        if (typeof mods.forEach === "function" && typeof mods.get === "function") {
            mods.forEach(function (rec, id) { n++; try { handleRecord(rec, id); } catch (e) {} });
        } else {
            for (var k in mods) {
                try {
                    if (!Object.prototype.hasOwnProperty.call(mods, k)) continue;
                } catch (e) { continue; }
                n++;
                handleRecord(mods[k], k);
            }
        }
    } catch (e) {}
    if (n > 0) LOG("Registry-Scan:", n, "Eintr\u00e4ge");
    return n;
}

// ---- Modul-Finder (nur beobachtete Module \u2014 nie selbst laden!) ----
// Neues Metro: Registry ist intern, getModules nur in Dev-Builds.
// Wir sehen alles \u00fcber den __d-Wrapper (Fabriken beim Definieren).
function isBad(ex) {
    if (!ex) return true;
    try { if (ex === G) return true; } catch (e) {}
    return false;
}

function eachModule(cb) {
    finding++;
    var result = false;
    try { result = eachModuleInner(cb); } catch (e) { result = false; }
    finding--;
    if (finding <= 0) {
        finding = 0;
        if (pendingInject) {
            pendingInject = false;
            try { injectSettings(); } catch (e) {}
        }
    }
    return result;
}

function eachModuleInner(cb) {
    for (var id in registry) {
        try { if (cb(registry[id], id)) return true; } catch (e) {}
    }
    return false;
}

function matchProps(obj, keys) {
    if (!obj) return false;
    var t = typeof obj;
    if (t !== "object" && t !== "function") return false;
    for (var i = 0; i < keys.length; i++) {
        try { if (!(keys[i] in obj)) return false; }
        catch (e) { return false; }
    }
    return true;
}

function findByProps() {
    var keys = [];
    for (var i = 0; i < arguments.length; i++) keys.push(arguments[i]);
    var found = null;
    eachModule(function (m) {
        try {
            if (m.__esModule && m.default && matchProps(m.default, keys)) { found = m.default; return true; }
            if (matchProps(m, keys)) { found = m; return true; }
        } catch (e) {}
        return false;
    });
    return found;
}

function findByName(name) {
    var found = null;
    eachModule(function (m) {
        var cands = [m];
        try { if (m.default) cands.push(m.default); } catch (e) {}
        for (var i = 0; i < cands.length; i++) {
            try {
                if (cands[i] && (cands[i].displayName === name || cands[i].name === name)) { found = m; return true; }
            } catch (e) {}
        }
        return false;
    });
    return found;
}

function after(method, obj, cb) {
    if (!obj) return function () {};
    var orig = null;
    try { orig = obj[method]; } catch (e) { return function () {}; }
    if (typeof orig !== "function") return function () {};
    try {
        obj[method] = function () {
            var args = [];
            for (var i = 0; i < arguments.length; i++) args.push(arguments[i]);
            var ret = orig.apply(this, args);
            try {
                var r2 = cb(args, ret);
                if (r2 !== undefined) ret = r2;
            } catch (e) { LOG("after-cb Fehler:", e && e.message); }
            return ret;
        };
    } catch (e) { return function () {}; }
    return function () { try { obj[method] = orig; } catch (e) {} };
}

function findInReactTree(tree, pred) {
    var stack = [tree];
    var seen = 0;
    while (stack.length > 0 && seen < 8000) {
        var node = stack.pop();
        seen++;
        if (!node || typeof node !== "object") continue;
        try { if (pred(node)) return node; } catch (e) {}
        var ch = null;
        try { ch = node.props ? node.props.children : node.children; } catch (e) {}
        if (Array.isArray(ch)) {
            for (var i = 0; i < ch.length; i++) stack.push(ch[i]);
        } else if (ch && typeof ch === "object") {
            stack.push(ch);
        }
    }
    return null;
}

// ------------------------------------------------------------- Config
var CFG_KEY = "fufcord_cfg_v1";
var cfg = {
    token: "", appId: "",
    name: "Fufcord", type: 0, details: "", state: "",
    large: "", largeText: "", small: "", smallText: "",
    status: "online", safe: false, useTs: true, autorun: true
};
var storage = null;
var storageWarned = false;

function getStorage() {
    if (storage) return storage;
    var m = findByProps("getItem", "setItem", "removeItem", "getAllKeys");
    if (m) { storage = m; LOG("AsyncStorage gefunden"); }
    else if (!storageWarned) { storageWarned = true; LOG("AsyncStorage fehlt → nur RAM (geht nach Neustart verloren)"); }
    return storage;
}

function validAppId(a) {
    a = (a || "").trim();
    if (!a || a.indexOf("DEINE") === 0) return false;
    if (a.length < 15) return false;
    for (var i = 0; i < a.length; i++) {
        var c = a.charCodeAt(i);
        if (c < 48 || c > 57) return false;
    }
    return true;
}

function loadCfg(cb) {
    cb = cb || function () {};
    var st = getStorage();
    if (!st) { cb(); return; }
    try {
        st.getItem(CFG_KEY).then(function (raw) {
            if (raw) {
                try {
                    var o = JSON.parse(raw);
                    for (var k in o) { if (k in cfg) cfg[k] = o[k]; }
                } catch (e) {}
            }
            cb();
        }).catch(function () { cb(); });
    } catch (e) { cb(); }
}

function saveCfg() {
    var st = getStorage();
    if (!st) return;
    try { st.setItem(CFG_KEY, JSON.stringify(cfg)); } catch (e) {}
}

// ----------------------------------------------------------- RPC-Engine
var rpc = {
    ws: null, hbTimer: null, retryTimer: null,
    seq: null, status: "Gestoppt", running: false, backoff: 5
};

function rpcStatus(s) { rpc.status = s; LOG("RPC:", s); }

function buildPresence() {
    var a = {};
    a.name = cfg.name || "Fufcord";
    a.type = cfg.type || 0;
    a.created_at = Date.now();
    if (cfg.details) a.details = String(cfg.details).slice(0, 128);
    if (cfg.state) a.state = String(cfg.state).slice(0, 128);
    var startTs = Date.now();
    if (cfg.useTs) a.timestamps = { start: startTs };
    if (cfg.safe) return { since: Date.now(), activities: [a], status: cfg.status || "online", afk: false };
    var hasApp = validAppId(cfg.appId);
    if (hasApp) a.application_id = cfg.appId.trim();
    if (hasApp && (cfg.large || cfg.small)) {
        var assets = {};
        if (cfg.large) {
            assets.large_image = String(cfg.large).slice(0, 256);
            if (cfg.largeText) assets.large_text = String(cfg.largeText).slice(0, 128);
        }
        if (cfg.small) {
            assets.small_image = String(cfg.small).slice(0, 256);
            if (cfg.smallText) assets.small_text = String(cfg.smallText).slice(0, 128);
        }
        a.assets = assets;
    }
    return { since: Date.now(), activities: [a], status: cfg.status || "online", afk: false };
}

function rpcSend(o) {
    try { if (rpc.ws) rpc.ws.send(JSON.stringify(o)); } catch (e) {}
}

function sendPresence() { rpcSend({ op: 3, d: buildPresence() }); }

function stopHb() {
    if (rpc.hbTimer) { try { clearInterval(rpc.hbTimer); } catch (e) {} rpc.hbTimer = null; }
}

function startHb(ms) {
    stopHb();
    rpcSend({ op: 1, d: rpc.seq });
    var iv = Math.max(1000, ms || 41250);
    rpc.hbTimer = setInterval(function () { rpcSend({ op: 1, d: rpc.seq }); }, iv);
}

function clearRetry() {
    if (rpc.retryTimer) { try { clearTimeout(rpc.retryTimer); } catch (e) {} rpc.retryTimer = null; }
}

function scheduleReconnect(reason) {
    if (!rpc.running) return;
    clearRetry();
    stopHb();
    try { if (rpc.ws) rpc.ws.close(); } catch (e) {}
    rpc.ws = null;
    var wait = rpc.backoff;
    rpc.backoff = Math.min(rpc.backoff * 2, 300);
    rpcStatus("Neu verbinden (" + wait + "s): " + reason);
    rpc.retryTimer = setTimeout(function () { rpc.retryTimer = null; rpcConnect(); }, wait * 1000);
}

function rpcConnect() {
    if (!cfg.token) { rpcStatus("Kein Token!"); rpc.running = false; return; }
    try { if (rpc.ws) rpc.ws.close(); } catch (e) {}
    rpcStatus("Verbinde...");
    var ws = null;
    try {
        ws = new G.WebSocket("wss://gateway.discord.gg/?v=10&encoding=json");
    } catch (e) {
        rpcStatus("Fehler: " + ((e && e.message) || e));
        scheduleReconnect("WS-Fehler");
        return;
    }
    rpc.ws = ws;
    ws.onopen = function () { LOG("Gateway offen"); };
    ws.onmessage = function (ev) {
        var msg = null;
        try { msg = JSON.parse(ev.data); } catch (e) { return; }
        if (msg.s !== undefined && msg.s !== null) rpc.seq = msg.s;
        var op = msg.op;
        if (op === 10) {
            var iv = (msg.d && msg.d.heartbeat_interval) || 41250;
            startHb(iv);
            rpcSend({ op: 2, d: {
                token: cfg.token,
                intents: 0,
                properties: { os: "Android", browser: "Discord Android", device: "Android" },
                presence: buildPresence()
            }});
        } else if (op === 0 && msg.t === "READY") {
            rpc.backoff = 5;
            var u = "?";
            try { u = (msg.d && msg.d.user && msg.d.user.username) || "?"; } catch (e) {}
            sendPresence();
            rpcStatus("Online als " + u);
        } else if (op === 7 || op === 9) {
            scheduleReconnect("Server-Neustart");
        }
    };
    ws.onerror = function () {};
    ws.onclose = function () { if (rpc.running) scheduleReconnect("Verbindung zu"); };
}

function rpcStart() {
    if (!cfg.token) { rpcStatus("Kein Token!"); return; }
    if (typeof setTimeout !== "function" || typeof setInterval !== "function") { rpcStatus("Timer fehlen?!"); return; }
    clearRetry();
    rpc.running = true;
    rpc.backoff = 5;
    rpcConnect();
}

function rpcStop() {
    rpc.running = false;
    clearRetry();
    stopHb();
    try { if (rpc.ws) rpc.ws.close(); } catch (e) {}
    rpc.ws = null;
    rpcStatus("Gestoppt");
}

// ------------------------------------------------- UI-Module (faul laden)
var React_ = null, RN_ = null, Nav_ = null;

function needUI() {
    if (!React_) React_ = findByProps("createElement", "useState", "useEffect", "memo");
    if (!RN_) RN_ = findByProps("View", "Text", "TextInput", "ScrollView", "TouchableOpacity");
    if (!Nav_) Nav_ = findByProps("useNavigation", "useRoute");
    return !!(React_ && RN_);
}

// ------------------------------------------------- Einstellungs-Seite
function fufOnPress() {
    try {
        if (!needUI()) { LOG("UI-Module fehlen noch"); return; }
        var tabsRef = findByProps("getRootNavigationRef");
        var nav = (tabsRef && tabsRef.getRootNavigationRef) ? tabsRef.getRootNavigationRef() : null;
        if (!nav) { LOG("Navigation fehlt"); return; }
        nav.navigate("FUFCORD_PAGE", {
            title: "Fufcord",
            render: function () { return React_.createElement(FufcordScreenSafe, null); }
        });
    } catch (e) { LOG("onPress Fehler:", e && e.message); }
}

function FufPageRenderer() {
    var R = React_;
    var navigation = Nav_.useNavigation();
    var route = Nav_.useRoute();
    var params = (route && route.params) || {};
    R.useEffect(function () {
        try { navigation.setOptions({ title: params.title || "Fufcord" }); } catch (e) {}
    }, []);
    try {
        return params.render ? params.render() : null;
    } catch (e) {
        return R.createElement(RN_.Text, { style: { color: "#ff6666", padding: 16 } },
            "Fufcord-Fehler: " + ((e && e.message) || e));
    }
}

function FufcordScreenSafe() {
    try {
        return FufcordScreen();
    } catch (e) {
        var R = React_, C = RN_;
        return R.createElement(C.Text, { style: { color: "#ff6666", padding: 16 } },
            "Fufcord-Fehler: " + ((e && e.message) || e));
    }
}

function FufcordScreen() {
    var R = React_, C = RN_;
    var S0 = R.useState(cfg.token);      var token = S0[0], setTokenUi = S0[1];
    var S1 = R.useState(cfg.appId);      var appId = S1[0], setAppIdUi = S1[1];
    var S2 = R.useState(cfg.name);       var nm = S2[0], setNmUi = S2[1];
    var S3 = R.useState(cfg.details);    var det = S3[0], setDetUi = S3[1];
    var S4 = R.useState(cfg.state);      var stt = S4[0], setSttUi = S4[1];
    var S5 = R.useState(cfg.large);      var lar = S5[0], setLarUi = S5[1];
    var S6 = R.useState(cfg.small);      var sma = S6[0], setSmaUi = S6[1];
    var S7 = R.useState(cfg.status);     var stat = S7[0], setStatUi = S7[1];
    var S8 = R.useState(!!cfg.safe);     var safe = S8[0], setSafeUi = S8[1];
    var S9 = R.useState(!!cfg.autorun);  var auto = S9[0], setAutoUi = S9[1];
    var S10 = R.useState(rpc.status);    var rstat = S10[0], setRstatUi = S10[1];
    var S11 = R.useState("");            var msg = S11[0], setMsg = S11[1];

    R.useEffect(function () {
        loadCfg(function () {
            setTokenUi(cfg.token); setAppIdUi(cfg.appId); setNmUi(cfg.name);
            setDetUi(cfg.details); setSttUi(cfg.state); setLarUi(cfg.large);
            setSmaUi(cfg.small); setStatUi(cfg.status); setSafeUi(!!cfg.safe);
            setAutoUi(!!cfg.autorun); setRstatUi(rpc.status);
        });
        var t = setInterval(function () { setRstatUi(rpc.status); }, 1000);
        return function () { try { clearInterval(t); } catch (e) {} };
    }, []);

    function set(key, val, ui) {
        cfg[key] = val;
        ui(val);
        saveCfg();
        if (rpc.running && key !== "token" && key !== "autorun") sendPresence();
    }

    function el(type, props) {
        var kids = [];
        for (var i = 2; i < arguments.length; i++) kids.push(arguments[i]);
        return R.createElement.apply(R, [type, props].concat(kids));
    }
    function label(t) {
        return el(C.Text, { style: { color: "#9aa3b2", fontSize: 12, marginTop: 12, marginBottom: 4 } }, t);
    }
    function input(val, onCh, extra) {
        var p = {
            style: { backgroundColor: "#1a1f28", color: "#f2f3f5", borderRadius: 8, padding: 10, fontSize: 15 },
            value: val || "", onChangeText: onCh,
            placeholderTextColor: "#5f6b7d", autoCapitalize: "none", autoCorrect: false
        };
        if (extra) for (var k in extra) p[k] = extra[k];
        return el(C.TextInput, p);
    }
    function btn(title, onP, color) {
        return el(C.TouchableOpacity,
            { onPress: onP, style: { backgroundColor: color || "#5865F2", borderRadius: 10, padding: 12, marginTop: 12, alignItems: "center" } },
            el(C.Text, { style: { color: "#fff", fontWeight: "bold", fontSize: 15 } }, title));
    }
    function toggleBtn(title, val, onP) {
        return el(C.TouchableOpacity,
            { onPress: onP, style: { backgroundColor: "#2b3240", borderRadius: 10, padding: 12, marginTop: 8 } },
            el(C.Text, { style: { color: "#fff", fontSize: 14 } }, (val ? "\u2705 " : "\u2B1C ") + title));
    }

    function autoToken() {
        try {
            var m = findByProps("getToken");
            var fn = m && (m.getToken || (m.default && m.default.getToken));
            if (typeof fn === "function") {
                var t = fn();
                if (t && t.length > 20) {
                    set("token", t, setTokenUi);
                    setMsg("\u2705 Token automatisch geholt!");
                    return;
                }
            }
        } catch (e) {}
        setMsg("\u274C Auto-Token ging nicht \u2014 bitte einf\u00FCgen.");
    }

    var running = rpc.running;
    var statusKeys = ["online", "idle", "dnd", "invisible"];
    var statusNames = { online: "\uD83D\uDFE2", idle: "\uD83C\uDF19", dnd: "\u26D4", invisible: "\u26AB" };
    var rowKids = [];
    for (var i = 0; i < statusKeys.length; i++) {
        (function (k) {
            rowKids.push(el(C.TouchableOpacity,
                { key: k, onPress: function () { set("status", k, setStatUi); },
                  style: { flex: 1, backgroundColor: stat === k ? "#5865F2" : "#2b3240", borderRadius: 8, padding: 10, margin: 2, alignItems: "center" } },
                el(C.Text, { style: { color: "#fff", fontSize: 16 } }, statusNames[k])));
        })(statusKeys[i]);
    }
    var rowArgs = [C.View, { style: { flexDirection: "row", marginTop: 4 } }].concat(rowKids);
    var statusRow = el.apply(null, rowArgs);

    return el(C.ScrollView, { style: { flex: 1, backgroundColor: "#0b0d12" } },
        el(C.View, { style: { padding: 16, paddingBottom: 40 } },
            el(C.Text, { style: { color: "#fff", fontSize: 22, fontWeight: "bold" } }, "\u26A1 Fufcord v" + VERSION),
            el(C.Text, { style: { color: running ? "#57f287" : "#9aa3b2", fontSize: 14, marginTop: 4 } },
                (running ? "\u25CF " : "\u25CB ") + rstat),
            msg ? el(C.Text, { style: { color: "#e0a030", fontSize: 13, marginTop: 8 } }, msg) : null,
            btn(running ? "\u23F9 Stop" : "\u25B6 Start", function () {
                if (rpc.running) rpcStop();
                else {
                    if (!cfg.token) { setMsg("Bitte erst Token eintragen!"); return; }
                    setMsg("");
                    rpcStart();
                }
                setRstatUi(rpc.status);
            }, running ? "#ed4245" : "#5865F2"),
            label("Token"),
            input(token, function (v) { set("token", (v || "").trim(), setTokenUi); },
                { placeholder: "Discord-Token einf\u00FCgen", secureTextEntry: true }),
            btn("\u2728 Token automatisch holen", autoToken, "#2b3240"),
            label("App-ID (f\u00FCr Bilder, optional)"),
            input(appId, function (v) { set("appId", (v || "").trim(), setAppIdUi); },
                { placeholder: "Zahlen\u2026", keyboardType: "numeric" }),
            label("Anzeigename"),
            input(nm, function (v) { set("name", v, setNmUi); }, { placeholder: "Fufcord" }),
            label("Details (Zeile 1)"),
            input(det, function (v) { set("details", v, setDetUi); }, { placeholder: "Was machst du?" }),
            label("Status (Zeile 2)"),
            input(stt, function (v) { set("state", v, setSttUi); }, { placeholder: "\u2026" }),
            label("Gro\u00DFes Bild (Asset-Name)"),
            input(lar, function (v) { set("large", (v || "").trim().toLowerCase(), setLarUi); }, { placeholder: "z. B. spotify" }),
            label("Kleines Bild (Asset-Name)"),
            input(sma, function (v) { set("small", (v || "").trim().toLowerCase(), setSmaUi); }, { placeholder: "optional" }),
            label("Online-Status"),
            statusRow,
            toggleBtn("Sicher-Modus (nur Text)", safe, function () { set("safe", !safe, setSafeUi); }),
            toggleBtn("Auto-Start mit Discord", auto, function () { set("autorun", !auto, setAutoUi); }),
            el(C.Text, { style: { color: "#5f6b7d", fontSize: 12, marginTop: 16 } },
                "Bilder l\u00E4dst du in der Fufcord-App hoch (Studio). Buttons & Party folgen.")
        )
    );
}

// ------------------------------------------------- Injection
var injected = false;

function spliceSection(sections) {
    try {
        if (!sections || !Array.isArray(sections)) return;
        var idx = -1;
        for (var i = 0; i < sections.length; i++) {
            var s = sections[i];
            if (s && s.settings && s.settings.indexOf && s.settings.indexOf("ACCOUNT") !== -1) { idx = i; break; }
        }
        if (idx === -1) return;
        for (var j = 0; j < sections.length; j++) {
            if (sections[j] && sections[j].label === "Fufcord") return;
        }
        sections.splice(idx + 1, 0, { label: "Fufcord", title: "Fufcord", settings: ["FUFCORD"] });
        LOG("Fufcord-Bereich eingefügt");
    } catch (e) {}
}

function injectSettings() {
    if (injected) return;
    var SC = null;
    try { SC = findByProps("SETTING_RENDERER_CONFIG"); } catch (e) {}
    if (!SC || !SC.SETTING_RENDERER_CONFIG) return;
    injected = true;
    LOG("Einstellungen gefunden → injiziere!");
    // 1) Zeilen + Seite registrieren
    try {
        var current = SC.SETTING_RENDERER_CONFIG;
        var merged = {};
        for (var k in current) merged[k] = current[k];
        merged.FUFCORD_PAGE = {
            type: "route",
            useTitle: function () { return "Fufcord"; },
            title: function () { return "Fufcord"; },
            screen: { route: "FUFCORD_PAGE", getComponent: function () { return FufPageRenderer; } }
        };
        merged.FUFCORD = {
            type: "pressable",
            useTitle: function () { return "Fufcord"; },
            title: function () { return "Fufcord"; },
            IconComponent: function () {
                try {
                    if (needUI()) return React_.createElement(RN_.Text, { style: { fontSize: 22 } }, "\u26A1");
                } catch (e) {}
                return null;
            },
            onPress: fufOnPress,
            withArrow: true
        };
        try {
            Object.defineProperty(SC, "SETTING_RENDERER_CONFIG", {
                enumerable: true, configurable: true,
                get: function () {
                    var o = {};
                    for (var kk in current) o[kk] = current[kk];
                    o.FUFCORD_PAGE = merged.FUFCORD_PAGE;
                    o.FUFCORD = merged.FUFCORD;
                    return o;
                },
                set: function (v) { current = v; }
            });
        } catch (e2) {
            current.FUFCORD_PAGE = merged.FUFCORD_PAGE;
            current.FUFCORD = merged.FUFCORD;
        }
    } catch (e) { LOG("Renderer-Patch Fehler:", e && e.message); }
    // 2) Bereich in die Liste einhängen (createList zuerst, sonst Overview-Fallback)
    var CL = null;
    try { CL = findByProps("createList"); } catch (e) {}
    if (CL && typeof CL.createList === "function") {
        after("createList", CL, function (args, ret) {
            try { if (args[0]) spliceSection(args[0].sections); } catch (e) {}
            return ret;
        });
        LOG("createList gepatcht");
    } else {
        var SOS = null;
        try { SOS = findByName("SettingsOverviewScreen"); } catch (e) {}
        if (SOS && SOS.default) {
            after("default", SOS, function (args, ret) {
                try {
                    var node = findInReactTree(ret, function (n) { return n.props && n.props.sections; });
                    if (node && node.props) spliceSection(node.props.sections);
                } catch (e) {}
                return ret;
            });
            LOG("OverviewScreen gepatcht (Fallback)");
        } else {
            LOG("createList/Overview fehlen noch → warte auf Module");
            injected = false; // erneut versuchen, sobald Module nachladen
        }
    }
}

// ------------------------------------------------- Diagnose
// Falls die Einstellungen fehlen: Status-Toast/Dialog (Screenshot schicken!).
function diagCheck() {
    if (injected) return;
    var lines = [];
    lines.push("Fufcord v" + VERSION + " Diagnose (" + bootMode + "):");
    lines.push("Module: " + emitCount);
    try {
        lines.push("React: " + (findByProps("createElement", "useState") ? "ja" : "NEIN"));
        lines.push("RN: " + (findByProps("View", "Text", "TextInput") ? "ja" : "NEIN"));
        lines.push("Storage: " + (findByProps("getItem", "setItem") ? "ja" : "NEIN"));
        lines.push("NavRef: " + (findByProps("getRootNavigationRef") ? "ja" : "NEIN"));
        lines.push("SET_CFG: " + (findByProps("SETTING_RENDERER_CONFIG") ? "ja" : "NEIN"));
        lines.push("createList: " + (findByProps("createList") ? "ja" : "NEIN"));
        lines.push("Overview: " + (findByName("SettingsOverviewScreen") ? "ja" : "NEIN"));
    } catch (e) { lines.push("Check-Fehler"); }
    var msg = lines.join("\n");
    LOG(msg);
    try { G.__fufcord_diag = msg; } catch (e) {}
    try {
        var TA = findByProps("showWithGravity", "SHORT", "LONG");
        if (TA && TA.show) { TA.show(msg, TA.LONG || 1); return; }
    } catch (e) {}
    try {
        var AL = findByProps("alert");
        var fn = AL && (AL.alert || (AL.default && AL.default.alert));
        if (typeof fn === "function") fn("Fufcord-Diagnose", msg);
    } catch (e) {}
}

// ------------------------------------------------- Start
// ------------------------------------------------- Start ohne Timer
// Unser Skript l\u00e4uft VOR Discords Bundle: keine Timer, kein Metro.
// L\u00f6sung: Fallen auf __r/__d (Metro weist sie schlicht zu: global.__r = ...)
// + __d wrappen, um JEDE Modulfabrik schon beim Definieren zu sehen.
// So entgeht uns kein Modul, ohne je selbst etwas laden zu m\u00fcssen.
var booted = false;
var bootMode = "?";
var cfgLoaded = false;
var trapTargets = [];
try {
    trapTargets.push(globalThis);
    if (typeof window !== "undefined" && window && window !== globalThis) trapTargets.push(window);
    if (typeof global !== "undefined" && global && global !== globalThis) trapTargets.push(global);
} catch (e) { trapTargets = [G]; }
var origDP = null;
try { origDP = Object.defineProperty; } catch (e) { origDP = null; }

function isTrapTarget(obj) {
    for (var i = 0; i < trapTargets.length; i++) {
        try { if (obj === trapTargets[i]) return true; } catch (e) {}
    }
    return false;
}

function trapKey(T, key) {
    try {
        var cur = T[key];
        if (cur !== undefined) { try { onRuntimeInit("trap"); } catch (e) {} return; }
        if (!origDP) return;
        origDP.call(Object, T, key, {
            configurable: true, enumerable: false,
            get: function () {
                try { return T["__fuf_" + key]; } catch (e) { return undefined; }
            },
            set: function (v) {
                try { origDP.call(Object, T, "__fuf_" + key, { value: v, writable: true, configurable: true, enumerable: false }); } catch (e) {}
                try { origDP.call(Object, T, key, { value: v, writable: true, configurable: true, enumerable: true }); } catch (e) {}
                try { onRuntimeInit("trap"); } catch (e) {}
            }
        });
    } catch (e) {}
}

function installTraps() {
    // 1) defineProperty-Wrapper (falls Metro __r/__d so zuweist)
    try {
        if (origDP && !Object.defineProperty.__fuf_w) {
            var fufDP = function defineProperty(obj, key, desc) {
                var r = origDP.call(Object, obj, key, desc);
                try {
                    if ((key === "__r" || key === "__d") && isTrapTarget(obj)) onRuntimeInit("dp");
                } catch (e) {}
                return r;
            };
            try { fufDP.__fuf_w = true; } catch (e) {}
            Object.defineProperty = fufDP;
        }
    } catch (e) {}
    // 2) Setter-Fallen (Metro weist schlicht zu)
    for (var i = 0; i < trapTargets.length; i++) {
        try {
            trapKey(trapTargets[i], "__r");
            trapKey(trapTargets[i], "__d");
        } catch (e) {}
    }
}

function onRuntimeInit(how) {
    if (booted) return;
    var r = null, d = null;
    try { r = G.__r; } catch (e) { r = null; }
    try { d = G.__d; } catch (e) { d = null; }
    if (typeof r !== "function" || typeof d !== "function") return;
    booted = true;
    bootMode = how;
    try { initRuntime(); }
    catch (e) { try { LOG("initRuntime: " + ((e && e.message) || e)); } catch (e2) {} }
}

// Wrapper um __d: sieht JEDE Fabrik schon beim Definieren,
// und jedes Modulergebnis direkt beim Ausf\u00fchren (ganz ohne Nachladen).
function wrapDefine(origD) {
    if (typeof origD !== "function" || origD.__fuf_wd) return origD;
    var w = function () {
        try {
            var factory = arguments[0], id = arguments[1];
            if (typeof factory === "function" && !factory.__fuf_w) {
                var origF = factory;
                var wrappedF = function (glob, req, impDef, impAll, modObj, expo, depMap) {
                    origF(glob, req, impDef, impAll, modObj, expo, depMap);
                    try { if (modObj && modObj.exports) emit(modObj.exports, id); } catch (e) {}
                };
                try { wrappedF.__fuf_w = true; } catch (e) {}
                arguments[0] = wrappedF;
            }
        } catch (e) {}
        return origD.apply(this, arguments);
    };
    try { w.__fuf_wd = true; } catch (e) {}
    try { for (var k in origD) { try { w[k] = origD[k]; } catch (e) {} } } catch (e) {}
    return w;
}

function initRuntime() {
    LOG("FufcordPatch v" + VERSION + " startet (" + bootMode + ")");
    // __d auf allen Zielen wrappen (Fabriken schon beim Definieren sehen)
    for (var i = 0; i < trapTargets.length; i++) {
        try {
            var T = trapTargets[i];
            if (T && typeof T.__d === "function") T.__d = wrapDefine(T.__d);
        } catch (e) {}
    }
    // Bonus-Scans (alte Metros / Dev-Modus mit sichtbarer Registry)
    try { scanRegistry(G.modules); } catch (e) {}
    try {
        var r = G.__r;
        if (r && r.getModules) scanRegistry(r.getModules());
    } catch (e) {}
    onModule(function () {
        try { injectSettings(); } catch (e) {}
        try { afterEmit(); } catch (e) {}
    });
    injectSettings();
    tryLoadCfg();
    try { if (typeof setTimeout === "function") setTimeout(function () { try { diagCheck(); } catch (e) {} }, 25000); } catch (e) {}
    try {
        G.__fufcord = {
            version: VERSION,
            mode: bootMode,
            status: function () { return rpc.status; },
            start: function () { rpcStart(); },
            stop: function () { rpcStop(); }
        };
    } catch (e) {}
}

// Nach jedem beobachteten Modul: Config laden (sobald Storage da),
// Diagnose per Z\u00e4hler (falls Timer fehlen, greift kein 25s-Timer).
function afterEmit() {
    if (!cfgLoaded) tryLoadCfg();
    if (!injected && (emitCount === 800 || emitCount === 2500)) {
        try { diagCheck(); } catch (e) {}
    }
}

function tryLoadCfg() {
    if (cfgLoaded) return;
    if (!getStorage()) return;
    cfgLoaded = true;
    loadCfg(function () {
        if (cfg.autorun && cfg.token) {
            LOG("Autostart...");
            try {
                if (typeof setTimeout === "function") setTimeout(function () { try { rpcStart(); } catch (e) {} }, 3000);
                else rpcStart();
            } catch (e) { try { rpcStart(); } catch (e2) {} }
        }
    });
}

var bootTries = 0;
function boot() {
    if (booted) return;
    bootTries++;
    try {
        var r = G.__r, d = G.__d;
        if (typeof r === "function" && typeof d === "function") { onRuntimeInit("poll"); return; }
    } catch (e) {}
    if (bootTries < 300) {
        try {
            if (typeof setTimeout === "function") setTimeout(boot, 100);
            else if (typeof Promise === "function") Promise.resolve().then(function () { try { boot(); } catch (e) {} });
        } catch (e2) {}
    }
}

try {
    installTraps();
} catch (e) {}
try {
    boot();
} catch (e) {
    try { console.log("[Fufcord] fatal: " + ((e && e.message) || e)); } catch (e2) {}
}

})();
