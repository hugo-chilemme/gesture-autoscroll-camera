# Gesture Scroll

Défilement **mains-libres** dans n'importe quelle app Android, piloté par des gestes de la main détectés par la caméra frontale.

Geste **bas → haut** = scroll vers le haut (élément suivant). Geste **haut → bas** = scroll arrière.

## Comment ça marche

- **CameraX** capte le flux de la caméra frontale dans un *foreground service* (tourne même quand tu es dans une autre app).
- **MediaPipe HandLandmarker** track le poignet et détecte le swipe vertical.
- **AccessibilityService** (`dispatchGesture`) envoie un swipe sur l'app active à l'écran.
- Un **overlay flottant** (déplaçable) donne un toggle actif/pause + le statut.

Le service est **générique** : il ne cible aucune app en particulier, il scrolle simplement ce qui est au premier plan.

## Build (GitHub Actions)

Push sur `main`. Le workflow `.github/workflows/build.yml` :
1. télécharge le modèle `hand_landmarker.task`,
2. build l'APK debug,
3. le signe,
4. l'expose en artifact `gesture-scroll-apk`.

Récupère l'APK dans l'onglet **Actions → run → Artifacts**.

## Installation

1. Sideload l'APK (`adb install gesture-scroll-signed.apk` ou transfert direct).
2. Ouvre l'app, accorde les **3 permissions** :
   - **Caméra** (prompt système)
   - **Overlay** (« Afficher par-dessus les autres apps »)
   - **Accessibilité** (Paramètres → Accessibilité → Gesture Scroll → Activer)
3. Appuie sur **Démarrer**.
4. Ouvre l'app que tu veux scroller, fais le geste bas→haut devant la caméra.

## Limites connues

- La caméra frontale doit te voir : garde le téléphone posé/incliné vers toi.
- Sur certains constructeurs (Xiaomi, Samsung), il faut autoriser le *démarrage auto* et désactiver l'optimisation batterie pour que le service survive en arrière-plan.
- Automatiser des interactions sur des apps tierces peut enfreindre leurs conditions d'utilisation. Outil fourni pour l'accessibilité et l'usage perso.

## Réglages

Dans `HandTracker.kt` :
- `SWIPE_THRESHOLD` : sensibilité (plus bas = plus sensible).
- `COOLDOWN_MS` : délai anti-répétition entre deux scrolls.

Dans `GestureScrollService.kt` : `startY`/`endY` contrôlent l'amplitude du scroll.
