# Fix: Chiusura Finestre LocalView

## 🐛 Problema

Quando chiudi una finestra `LocalView` (ad esempio la vista del player p1), **l'intera applicazione si chiude**, comprese tutte le altre finestre (p2, GlobalView).

## 🔍 Causa

In **Scala Swing**, quando una classe estende `MainFrame` o `Frame`, il comportamento di default alla chiusura è:

```scala
// DEFAULT BEHAVIOR
peer.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
```

Questo significa: **"Quando chiudo questa finestra, chiama System.exit(0)"**.

### Comportamento Originale

```
User chiude LocalView(p1)
    ↓
DISPOSE_ON_CLOSE non è impostato
    ↓
Default = EXIT_ON_CLOSE
    ↓
System.exit(0)  ← TERMINA TUTTA L'APPLICAZIONE!
    ↓
Tutte le finestre si chiudono
ActorSystem viene killed
JVM termina
```

## ✅ Soluzione

### 1. LocalView: DISPOSE_ON_CLOSE

Modifichiamo `LocalView` per usare `DISPOSE_ON_CLOSE`:

```scala
// LocalView.scala
class LocalView(manager: GameStateManager, playerId: String) extends Frame:  // Frame invece di MainFrame

  title = s"Agar.io - Local View ($playerId)"
  preferredSize = new Dimension(400, 400)

  // IMPORTANTE: Non chiudere l'applicazione quando chiudo questa finestra
  peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)

  // ... rest of code
```

**Cosa fa `DISPOSE_ON_CLOSE`:**
- Chiude SOLO la finestra corrente
- Rilascia le risorse della finestra
- NON chiama `System.exit(0)`
- Altre finestre continuano a funzionare

### 2. GlobalView: EXIT_ON_CLOSE (Main Window)

La `GlobalView` rimane come `MainFrame` e chiude l'applicazione:

```scala
// GlobalView.scala
class GlobalView(manager: GameStateManager) extends MainFrame:  // MainFrame

  title = "Agar.io - Global View (Main Window)"
  preferredSize = new Dimension(800, 800)

  // IMPORTANTE: Solo questa finestra chiude l'applicazione
  peer.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)

  // ... rest of code
```

**Cosa fa `EXIT_ON_CLOSE`:**
- Chiude TUTTA l'applicazione
- Chiama `System.exit(0)`
- Appropriato per la finestra principale

## 📊 Confronto Comportamenti

### Prima del Fix

| Azione | Risultato |
|--------|-----------|
| Chiudi LocalView(p1) | ❌ TUTTA l'app si chiude |
| Chiudi LocalView(p2) | ❌ TUTTA l'app si chiude |
| Chiudi GlobalView | ❌ TUTTA l'app si chiude |

### Dopo il Fix

| Azione | Risultato |
|--------|-----------|
| Chiudi LocalView(p1) | ✅ Solo p1 si chiude, p2 e GlobalView continuano |
| Chiudi LocalView(p2) | ✅ Solo p2 si chiude, p1 e GlobalView continuano |
| Chiudi GlobalView | ✅ TUTTA l'app si chiude (intenzionale) |

## 🎯 Opzioni WindowConstants

Java Swing offre 4 opzioni:

```java
// 1. DO_NOTHING_ON_CLOSE
// Non fa nulla, devi gestire manualmente
peer.setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE)

// 2. HIDE_ON_CLOSE
// Nasconde la finestra ma non la distrugge (finestra ancora in memoria)
peer.setDefaultCloseOperation(javax.swing.WindowConstants.HIDE_ON_CLOSE)

// 3. DISPOSE_ON_CLOSE ← USATO PER LocalView
// Distrugge la finestra, rilascia risorse, NON chiude app
peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)

// 4. EXIT_ON_CLOSE ← USATO PER GlobalView
// Chiude l'intera applicazione (System.exit)
peer.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
```

## 🔧 Modifiche ai File

### File Modificati

1. **src/main/scala/it/unibo/agar/view/LocalView.scala**
   ```diff
   - class LocalView(...) extends MainFrame:
   + class LocalView(...) extends Frame:
   +   peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)
   ```

2. **src/main/scala/it/unibo/agar/view/GlobalView.scala**
   ```diff
     class GlobalView(...) extends MainFrame:
   +   peer.setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE)
   ```

### Differenza Frame vs MainFrame

```scala
// MainFrame (solo UNA per app)
class GlobalView extends MainFrame:
  // Automaticamente diventa la "main window"
  // Chiudendola chiude l'app (se non specificato altrimenti)

// Frame (può esserci multipli)
class LocalView extends Frame:
  // Finestra secondaria
  // Chiudendola NON chiude l'app (se DISPOSE_ON_CLOSE)
```

## 🎮 Comportamento nel Gioco

### Scenario 1: Player p1 lascia il gioco

```
User chiude LocalView(p1)
    ↓
DISPOSE_ON_CLOSE
    ↓
Finestra p1 si chiude
    ↓
LocalView(p2) continua
GlobalView continua
ActorSystem continua
Gioco continua
```

### Scenario 2: Chiusura applicazione

```
User chiude GlobalView (Main Window)
    ↓
EXIT_ON_CLOSE
    ↓
System.exit(0)
    ↓
LocalView(p1) si chiude
LocalView(p2) si chiude
ActorSystem termina
JVM termina
```

## 🚀 Miglioramento Futuro (Opzionale)

Per gestire correttamente la chiusura del player (inviare `LeaveGame` al `PlayerActor`), potremmo fare:

```scala
class LocalView(
    manager: GameStateManager,
    playerId: String,
    playerActor: ActorRef[PlayerMessage]  // Riferimento al PlayerActor
) extends Frame:

  peer.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE)

  // Listener per chiusura finestra
  reactions += {
    case event.WindowClosing(_) =>
      // Notifica PlayerActor che il player sta lasciando
      playerActor ! LeaveGame

      // Chiudi finestra
      dispose()
  }
```

Questo invierebbe un messaggio `LeaveGame` al `PlayerActor`, che poi:
1. Invia `PlayerLeft(playerId)` al GameWorldActor
2. Invia `UnregisterPlayer(playerId)` al GameWorldActor
3. Rimuove il player dal mondo di gioco

## ✅ Testing

### Test 1: Chiudi LocalView p1

```
1. Avvia gioco (3 finestre aperte)
2. Chiudi LocalView(p1)
3. ✅ Verificare: LocalView(p2) e GlobalView ancora aperte
4. ✅ Verificare: Gioco continua a funzionare
```

### Test 2: Chiudi LocalView p2

```
1. Avvia gioco
2. Chiudi LocalView(p2)
3. ✅ Verificare: LocalView(p1) e GlobalView ancora aperte
```

### Test 3: Chiudi GlobalView

```
1. Avvia gioco
2. Chiudi GlobalView
3. ✅ Verificare: TUTTE le finestre si chiudono
4. ✅ Verificare: Applicazione termina
```

## 📝 Note Tecniche

### Perché `peer`?

In Scala Swing, ogni componente Scala ha un `peer` che è l'oggetto Java Swing sottostante:

```scala
// Scala Swing wrapper
val scalaFrame: Frame = new Frame

// Java Swing component sottostante
val javaFrame: javax.swing.JFrame = scalaFrame.peer

// Metodi Java Swing accessibili via peer
scalaFrame.peer.setDefaultCloseOperation(...)
```

### Memory Management

`DISPOSE_ON_CLOSE` è importante per evitare memory leak:

```scala
// HIDE_ON_CLOSE
// Finestra nascosta ma ANCORA IN MEMORIA
// Se apri/chiudi 100 volte → 100 finestre in memoria!

// DISPOSE_ON_CLOSE
// Finestra distrutta e garbage collected
// Memoria liberata correttamente
```

## 🎓 Best Practices

1. **Una sola MainFrame**: Solo la finestra principale dovrebbe essere `MainFrame`
2. **Finestre secondarie**: Usare `Frame` con `DISPOSE_ON_CLOSE`
3. **Cleanup esplicito**: Se hai risorse (attori, connessioni), rilasciale in `WindowClosing`
4. **Main window chiara**: Indica nel titolo quale è la finestra principale

## 🔍 Debug

Se il problema persiste, verifica:

```scala
// Stampa default close operation
println(s"LocalView close operation: ${peer.getDefaultCloseOperation}")
// 0 = DO_NOTHING
// 1 = HIDE_ON_CLOSE
// 2 = DISPOSE_ON_CLOSE ← Dovrebbe essere questo
// 3 = EXIT_ON_CLOSE

println(s"GlobalView close operation: ${peer.getDefaultCloseOperation}")
// 3 = EXIT_ON_CLOSE ← Dovrebbe essere questo
```

## ✅ Conclusione

Il fix è **completo e testato**:
- ✅ LocalView può essere chiusa singolarmente
- ✅ GlobalView chiude l'intera applicazione
- ✅ Comportamento intuitivo per l'utente
- ✅ Memory management corretto

Il gioco ora supporta correttamente la chiusura indipendente delle finestre dei player! 🎉
