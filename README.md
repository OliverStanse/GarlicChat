# Garlic Chat 2.1.1

Vollständiges Android-Projekt. Java 17, Gradle 8.9, AGP 8.7.3, SDK 35.

## GitHub
ZIP entpacken, Inhalte auf Repository-Ebene hochladen (nicht ZIP oder umgebenden Ordner). Die versteckte Datei `.github/workflows/android.yml` muss ebenfalls hochgeladen oder im bestehenden Repository ersetzt werden. Keine zweite app-Ebene anlegen. Actions → Build Android APK → Artifacts → GarlicChat-debug-apk.

## Funktionen
WLAN, WireGuard und Tailscale, OpenAI-kompatible API, Tastatur und Diktat, Android-TTS mit lokaler Stimmauswahl und 0,5–2× Geschwindigkeit, automatisches Senden nach Diktat (optional, 0,5–10 s). Der Android-Sprachdienst entscheidet über das Erkennungsende; danach startet die einstellbare Wartezeit. Zwiebel-Icon, größere Einstellungen-Grafik bei 48 dp, Chatblasen mit Schatten.

Debug-APK: Bei abweichender Signatur kann eine Neuinstallation nötig sein (lokale Daten gehen dabei verloren).
