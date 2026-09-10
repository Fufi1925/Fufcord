#!/bin/bash
# Fufcord — Setup für Termux (Handy) & Linux
echo "📦 Fufcord Setup startet..."
echo ""

# Termux oder Linux erkennen
if command -v pkg >/dev/null 2>&1; then
  echo "📱 Termux erkannt — installiere Pakete..."
  pkg update -y
  pkg install python git -y
else
  echo "💻 Linux/PC erkannt..."
  if command -v apt >/dev/null 2>&1; then
    sudo apt update && sudo apt install -y python3 python3-pip git
  fi
fi

echo ""
echo "📥 Installiere Python-Abhängigkeiten..."
pip install --upgrade pip
pip install -r requirements.txt

chmod +x start.sh
chmod +x setup.sh

echo ""
echo "✅ Fertig! Starten mit:  bash start.sh"
echo "   oder:  python main.py"
