// ==UserScript==
// @name         Fufcord Token Helper
// @namespace    https://github.com/Fufi1925/Fufcord
// @version      1.2
// @description  Zeigt deinen EIGENEN Discord-Token direkt auf der Seite (ohne Popups). Sendet NICHTS irgendwohin - alles bleibt lokal in deinem Browser.
// @match        https://discord.com/*
// @noframes
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  // Sicherheits-Checks: nur Top-Frame, nur discord.com
  if (window.top !== window.self) return;
  if (!location.hostname.endsWith('discord.com')) return;

  // Token lesen (mehrere Versuche, ohne Popups)
  function readToken() {
    try {
      var raw = localStorage.getItem('token');
      if (raw && raw.length > 10) return raw.replace(/["\s]/g, '');
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && /token/i.test(k)) {
          var v = localStorage.getItem(k);
          if (v && v.length > 20) return v.replace(/["\s]/g, '');
        }
      }
    } catch (e) { /* Zugriff blockiert */ }
    return null;
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
      : 'Bist du auf discord.com eingeloggt? (Desktopwebsite + Login, dann Seite neu laden)';
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
    } else {
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

    // Status-Toast: zeigt SOFORT ob ein Token da ist
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
