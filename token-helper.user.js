// ==UserScript==
// @name         Fufcord Token Helper
// @namespace    https://github.com/Fufi1925/Fufcord
// @version      1.0
// @description  Zeigt deinen EIGENEN Discord-Token auf discord.com als kopierbaren Text. Sendet NICHTS irgendwohin - alles bleibt lokal in deinem Browser.
// @match        https://discord.com/*
// @run-at       document-idle
// @grant        none
// ==/UserScript==

(function () {
  'use strict';

  // Sicherheits-Check: nur auf discord.com laufen
  if (!location.hostname.endsWith('discord.com')) return;

  // Schwebender Button unten rechts
  const btn = document.createElement('button');
  btn.textContent = '🎫 Token anzeigen';
  btn.style.cssText = [
    'position:fixed', 'bottom:24px', 'right:16px', 'z-index:999999',
    'padding:14px 18px', 'font-size:16px', 'font-weight:bold',
    'background:#5865F2', 'color:#fff', 'border:none',
    'border-radius:12px', 'box-shadow:0 4px 14px rgba(0,0,0,.4)'
  ].join(';');

  btn.addEventListener('click', function () {
    try {
      const raw = localStorage.getItem('token');
      if (!raw) {
        alert('Kein Token gefunden.\n\nBist du auf discord.com eingeloggt?\n(Desktopwebsite aktivieren + einloggen, dann Button druecken)');
        return;
      }
      const token = raw.replace(/"/g, '');
      prompt('Dein Token - kopieren (lang druecken) - NIEMALS teilen!', token);
    } catch (e) {
      alert('Fehler: ' + e);
    }
  });

  document.documentElement.appendChild(btn);
})();
