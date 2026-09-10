// ==UserScript==
// @name         Fufcord Token Helper
// @namespace    https://github.com/Fufi1925/Fufcord
// @version      1.1
// @description  Zeigt deinen EIGENEN Discord-Token auf discord.com als kopierbaren Text. Sendet NICHTS irgendwohin - alles bleibt lokal in deinem Browser.
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

  function showUI() {
    if (document.getElementById('fufcord-token-btn')) return true;
    const host = document.body || document.documentElement;
    if (!host) return false;

    // Grüner Toast oben = Beweis dass das Script läuft
    const toast = document.createElement('div');
    toast.textContent = 'Fufcord Helper aktiv ✅';
    toast.style.cssText = 'position:fixed;top:14px;left:50%;transform:translateX(-50%);z-index:999999;background:#3ba55d;color:#fff;padding:10px 18px;border-radius:10px;font-size:14px;font-weight:bold;box-shadow:0 4px 14px rgba(0,0,0,.4);';
    host.appendChild(toast);
    setTimeout(function () { toast.remove(); }, 4000);

    // Schwebender Button unten rechts
    const btn = document.createElement('button');
    btn.id = 'fufcord-token-btn';
    btn.textContent = '🎫 Token anzeigen';
    btn.style.cssText = 'position:fixed;bottom:24px;right:16px;z-index:999999;padding:14px 18px;font-size:16px;font-weight:bold;background:#5865F2;color:#fff;border:none;border-radius:12px;box-shadow:0 4px 14px rgba(0,0,0,.4);';
    btn.addEventListener('click', function () {
      try {
        const raw = localStorage.getItem('token');
        if (!raw) {
          alert('Kein Token gefunden.\n\nErst auf discord.com einloggen, dann Button druecken!');
          return;
        }
        prompt('Dein Token - kopieren (lang druecken) - NIEMALS teilen!', raw.replace(/"/g, ''));
      } catch (e) {
        alert('Fehler: ' + e);
      }
    });
    host.appendChild(btn);
    return true;
  }

  // Sofort + alle 500ms erneut versuchen (max 20s), falls Seite langsam lädt
  let tries = 0;
  showUI();
  const timer = setInterval(function () {
    tries++;
    if (showUI() || tries > 40) clearInterval(timer);
  }, 500);
})();
