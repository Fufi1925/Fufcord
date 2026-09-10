// ==UserScript==
// @name         Fufcord Token Helper
// @namespace    https://github.com/Fufi1925/Fufcord
// @version      1.3
// @description  Liest deinen EIGENEN Discord-Token direkt aus der Seite (Seiten-Kontext, ohne Popups). Sendet NICHTS irgendwohin - alles bleibt lokal in deinem Browser.
// @match        https://discord.com/*
// @noframes
// @run-at       document-idle
// @inject-into  page
// @grant        none
// ==/UserScript==

// Fufcord — https://github.com/Fufi1925/Fufcord
// Copyright (c) 2026 Fufcord. Alle Rechte vorbehalten.
// Lizenziert unter der MIT-Lizenz (siehe LICENSE im Repo-Root).

(function () {
  'use strict';

  // Sicherheits-Checks: nur Top-Frame, nur discord.com
  if (window.top !== window.self) return;
  if (!location.hostname.endsWith('discord.com')) return;

  var diag = { storageKeys: null, storageError: null, source: '–' };

  function clean(s) { return String(s).replace(/["\s]/g, ''); }

  // Methode 1: Browser-Speicher (localStorage) - direkt im Seiten-Kontext
  function readLocalStorage() {
    try {
      var keys = [];
      for (var i = 0; i < localStorage.length; i++) keys.push(localStorage.key(i));
      diag.storageKeys = keys;
      var raw = localStorage.getItem('token');
      if (raw && raw.length > 10) { diag.source = 'Browser-Speicher'; return clean(raw); }
      for (var j = 0; j < keys.length; j++) {
        if (keys[j] && /token/i.test(keys[j])) {
          var v = localStorage.getItem(keys[j]);
          if (v && v.length > 20) { diag.source = 'Browser-Speicher'; return clean(v); }
        }
      }
    } catch (e) { diag.storageError = String(e).slice(0, 80); }
    return null;
  }

  // Methode 2: Discord-interner Speicher (Backup, falls Methode 1 leer ist)
  function readWebpack() {
    try {
      var chunk = window.webpackChunkdiscord_app;
      if (!chunk || !chunk.push) return null;
      var found = null;
      chunk.push([['fufcord' + Date.now()], {}, function (req) {
        try {
          var cache = req.c || {};
          var ids = Object.keys(cache);
          for (var n = 0; n < ids.length && !found; n++) {
            var mod = null;
            try { mod = req(ids[n]); } catch (e) { continue; }
            found = searchExports(mod, 0);
          }
        } catch (e) {}
      }]);
      if (found) diag.source = 'Discord-Speicher';
      return found;
    } catch (e) { return null; }
  }

  function searchExports(obj, depth) {
    if (!obj || depth > 4) return null;
    try {
      if (typeof obj.getToken === 'function') {
        var t = obj.getToken();
        if (typeof t === 'string' && t.length > 20) return clean(t);
      }
      if (depth >= 3) return null;
      for (var k in obj) {
        if (k === '__esModule') continue;
        var v = null;
        try { v = obj[k]; } catch (e) { continue; }
        if (v && (typeof v === 'object' || typeof v === 'function')) {
          var r = searchExports(v, depth + 1);
          if (r) return r;
        }
      }
    } catch (e) {}
    return null;
  }

  function readToken() {
    return readLocalStorage() || readWebpack();
  }

  // Overlay mit Token DIREKT auf der Seite (kein Popup!)
  function showToken() {
    if (document.getElementById('fufcord-overlay')) return;
    var token = readToken();
    var host = document.body || document.documentElement;

    var ov = document.createElement('div');
    ov.id = 'fufcord-overlay';
    ov.style.cssText = 'position:fixed;inset:0;z-index:1000000;background:rgba(0,0,0,.85);display:flex;align-items:center;justify-content:center;padding:20px;';

    var card = document.createElement('div');
    card.style.cssText = 'background:#2b2d31;color:#fff;border-radius:14px;padding:20px;width:100%;max-width:440px;box-shadow:0 8px 30px rgba(0,0,0,.6);';

    var title = document.createElement('div');
    title.textContent = token ? '🎫 Dein Token' : '⚠️ Kein Token gefunden';
    title.style.cssText = 'font-size:18px;font-weight:bold;margin-bottom:8px;';
    card.appendChild(title);

    var warn = document.createElement('div');
    warn.textContent = token
      ? '🔴 NIEMALS teilen! Wie ein Passwort behandeln.'
      : 'Tipp: Schild-Symbol in der Adressleiste → Tracking-Schutz für discord.com AUS → Seite neu laden.';
    warn.style.cssText = 'font-size:13px;color:#faa;margin-bottom:12px;';
    card.appendChild(warn);

    if (token) {
      var ta = document.createElement('textarea');
      ta.readOnly = true;
      ta.rows = 4;
      ta.value = token;
      ta.style.cssText = 'width:100%;box-sizing:border-box;background:#1e1f22;color:#fff;border:1px solid #5865F2;border-radius:8px;padding:10px;font-size:11px;word-break:break-all;';
      ta.addEventListener('click', function () { ta.select(); });
      card.appendChild(ta);

      var row = document.createElement('div');
      row.style.cssText = 'display:flex;gap:10px;margin-top:12px;';

      var copyBtn = document.createElement('button');
      copyBtn.textContent = '📋 Kopieren';
      copyBtn.style.cssText = 'flex:1;padding:12px;font-size:15px;font-weight:bold;background:#3ba55d;color:#fff;border:none;border-radius:10px;';
      copyBtn.addEventListener('click', function () {
        function done() { copyBtn.textContent = '✅ Kopiert!'; }
        function fallback() {
          ta.select();
          try { document.execCommand('copy'); done(); }
          catch (e) { copyBtn.textContent = '⚠️ Lang drücken zum Kopieren'; ta.select(); }
        }
        try {
          if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(token).then(done, fallback);
          } else { fallback(); }
        } catch (e) { fallback(); }
      });
      row.appendChild(copyBtn);

      var closeBtn = document.createElement('button');
      closeBtn.textContent = '✖';
      closeBtn.style.cssText = 'padding:12px 16px;font-size:15px;font-weight:bold;background:#4e5058;color:#fff;border:none;border-radius:10px;';
      closeBtn.addEventListener('click', function () { ov.remove(); });
      row.appendChild(closeBtn);
      card.appendChild(row);

      var src = document.createElement('div');
      src.textContent = 'Quelle: ' + diag.source;
      src.style.cssText = 'font-size:11px;color:#b5bac1;margin-top:10px;';
      card.appendChild(src);
    } else {
      var d = document.createElement('div');
      var lines = ['Quelle: ' + diag.source];
      if (diag.storageError) lines.push('Speicher-Fehler: ' + diag.storageError);
      else if (diag.storageKeys) lines.push('Speicher-Keys (' + diag.storageKeys.length + '): ' + (diag.storageKeys.slice(0, 20).join(', ') || 'keine'));
      else lines.push('Speicher: nicht lesbar');
      lines.push('Discord-App-Daten: ' + (window.webpackChunkdiscord_app ? 'gefunden' : 'nicht gefunden'));
      d.textContent = 'Diagnose: ' + lines.join(' | ');
      d.style.cssText = 'font-size:11px;color:#b5bac1;background:#1e1f22;border-radius:8px;padding:8px;margin-bottom:12px;word-break:break-all;';
      card.appendChild(d);

      var okBtn = document.createElement('button');
      okBtn.textContent = 'OK';
      okBtn.style.cssText = 'width:100%;padding:12px;font-size:15px;font-weight:bold;background:#5865F2;color:#fff;border:none;border-radius:10px;margin-top:4px;';
      okBtn.addEventListener('click', function () { ov.remove(); });
      card.appendChild(okBtn);
    }

    ov.addEventListener('click', function (e) { if (e.target === ov) ov.remove(); });
    ov.appendChild(card);
    host.appendChild(ov);
    if (token) { try { ta.select(); } catch (e) {} }
  }

  function showUI() {
    if (document.getElementById('fufcord-token-btn')) return true;
    var host = document.body || document.documentElement;
    if (!host) return false;

    var hasToken = !!readToken();
    var toast = document.createElement('div');
    toast.textContent = hasToken
      ? 'Fufcord Helper aktiv ✅ – Token bereit!'
      : 'Fufcord Helper aktiv ✅ – kein Token (eingeloggt?)';
    toast.style.cssText = 'position:fixed;top:14px;left:50%;transform:translateX(-50%);z-index:999999;background:' + (hasToken ? '#3ba55d' : '#e09112') + ';color:#fff;padding:10px 18px;border-radius:10px;font-size:14px;font-weight:bold;box-shadow:0 4px 14px rgba(0,0,0,.4);max-width:92%;text-align:center;';
    host.appendChild(toast);
    setTimeout(function () { toast.remove(); }, 5000);

    var btn = document.createElement('button');
    btn.id = 'fufcord-token-btn';
    btn.textContent = '🎫 Token anzeigen';
    btn.style.cssText = 'position:fixed;bottom:24px;right:16px;z-index:999999;padding:14px 18px;font-size:16px;font-weight:bold;background:#5865F2;color:#fff;border:none;border-radius:12px;box-shadow:0 4px 14px rgba(0,0,0,.4);touch-action:manipulation;';
    btn.addEventListener('click', function (e) { e.stopPropagation(); showToken(); });
    host.appendChild(btn);
    return true;
  }

  var tries = 0;
  showUI();
  var timer = setInterval(function () {
    tries++;
    if (showUI() || tries > 40) clearInterval(timer);
  }, 500);
})();
