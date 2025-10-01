# 🧹 Project Cleanup Report

## ❌ File INUTILIZZATI (da eliminare)

### 1. **Message.scala** ❌
**Path:** `src/main/scala/it/unibo/agar/Message.scala`

**Motivo:** Trait vuoto non più utilizzato. Era usato in `application.conf` per serialization bindings:
```hocon
serialization-bindings {
  "it.unibo.agar.Message" = jackson-json  # ← Non serve più
}
```

Tutti i messaggi ora estendono `GameMessage` o `PlayerMessage`, non `Message`.

**Azione:** ❌ **ELIMINA**

---

### 2. **AIMovement.scala** ❌
**Path:** `src/main/scala/it/unibo/agar/model/AIMovement.scala`

**Motivo:** Utilizzato solo in `GameStateManager` (che è anch'esso legacy). Non usato in `DistributedGameStateManager`.

**Azione:** ❌ **ELIMINA** (dopo aver rimosso GameStateManager)

---

### 3. **GameStateManager.scala** ⚠️
**Path:** `src/main/scala/it/unibo/agar/model/GameStateManager.scala`

**Motivo:** Versione NON distribuita, sostituita da `DistributedGameStateManager`.

**Utilizzato da:**
- `LocalView` (se usata in LocalDemo)
- `GlobalView` (se usata in LocalDemo)
- `AIMovement` (legacy)

**Azione:** ⚠️ **MANTIENI** se tieni LocalDemo, altrimenti **ELIMINA**

---

### 4. **agario.conf** ❌
**Path:** `src/main/resources/agario.conf`

**Motivo:** Configurazione alternativa mai utilizzata. L'applicazione usa solo `application.conf`.

**Contenuto obsoleto:**
```hocon
cluster {
  seed-nodes = ["akka://agario@127.0.0.1:25251"]  # Nome cluster sbagliato
}
```

Il cluster si chiama `ClusterSystem`, non `agario`.

**Azione:** ❌ **ELIMINA**

---

## 📋 File DOCUMENTAZIONE LEGACY (da pulire/archiviare)

### 5. **LEGGIMI_PRIMA.md** ⚠️
**Contenuto:** Istruzioni vecchie (probabilmente per DistributedMain)

**Azione:** ⚠️ **VERIFICA e AGGIORNA** o elimina se obsoleto

---

### 6. **CORREZIONI.md** 📦
**Contenuto:** Correzioni fatte durante lo sviluppo

**Azione:** 📦 **ARCHIVIA** in una cartella `docs/history/`

---

### 7. **RIEPILOGO_CORREZIONI.md** 📦
**Contenuto:** Riepilogo correzioni

**Azione:** 📦 **ARCHIVIA** in `docs/history/`

---

### 8. **FIX_WINDOW_CLOSE.md** 📦
**Contenuto:** Fix tecnico specifico già implementato

**Azione:** 📦 **ARCHIVIA** in `docs/fixes/` o elimina

---

### 9. **MANAGER_DESIGN.md** 📦
**Contenuto:** Design del GameStateManager

**Azione:** 📦 **ARCHIVIA** in `docs/design/`

---

### 10. **VERIFICA_REQUISITI.md** 📦
**Contenuto:** Checklist requisiti

**Azione:** 📦 **MANTIENI** nella root o archivia se completato

---

### 11. **RELAZIONE_BREVE.md** 📄
**Contenuto:** Relazione breve del progetto

**Azione:** 📄 **MANTIENI** se necessario per università/consegna

---

### 12. **RELAZIONE_FINALE.md** 📄
**Contenuto:** Relazione finale del progetto

**Azione:** 📄 **MANTIENI** se necessario per università/consegna

---

## ✅ File ATTIVI E NECESSARI

### Controller (Main)
- ✅ `SeedNode.scala` - Server distribuito
- ✅ `PlayerNode.scala` - Client distribuito
- ⚠️ `LocalDemo.scala` - Demo locale (rinominato da DistributedMain)

### Model
- ✅ `GameWorldActor.scala` - Core game logic
- ✅ `GameWorldSingleton.scala` - Cluster singleton wrapper
- ✅ `PlayerActor.scala` - Player actor
- ✅ `FoodManagerActor.scala` - Food spawning
- ✅ `DistributedGameStateManager.scala` - Distributed state manager
- ✅ `GameMessages.scala` - Message definitions
- ✅ `GameModels.scala` - Data models (World, Player, Food)
- ✅ `GameInitializer.scala` - Initial game setup
- ✅ `EatingManager.scala` - Eating logic

### View
- ✅ `GlobalView.scala` - Global view window
- ✅ `LocalView.scala` - Player view window
- ✅ `AgarViewUtils.scala` - View utilities

### Utils
- ✅ `utils.scala` - General utilities

### Config
- ✅ `application.conf` - Akka configuration
- ✅ `.scalafmt.conf` - Formatter config

### Scripts
- ✅ `start-seed.sh` - Start seed node
- ✅ `start-player.sh` - Start player node
- ✅ `start-local-demo.sh` - Start local demo

### Documentation (Keep)
- ✅ `README.md` - Main readme
- ✅ `HOW_TO_RUN.md` - Usage guide
- ✅ `DISTRIBUTED_TESTING.md` - Testing guide
- ✅ `PROJECT_CLEANUP_REPORT.md` - This file

---

## 🗑️ COMANDI PER PULIZIA

### Eliminare file inutilizzati:
```bash
# 1. Elimina Message.scala
rm src/main/scala/it/unibo/agar/Message.scala

# 2. Elimina AIMovement.scala
rm src/main/scala/it/unibo/agar/model/AIMovement.scala

# 3. Elimina agario.conf
rm src/main/resources/agario.conf

# 4. Se NON usi LocalDemo, elimina anche GameStateManager
# rm src/main/scala/it/unibo/agar/model/GameStateManager.scala
```

### Archiviare documentazione legacy:
```bash
# Crea cartelle archivio
mkdir -p docs/history
mkdir -p docs/fixes
mkdir -p docs/design

# Sposta file
mv CORREZIONI.md docs/history/
mv RIEPILOGO_CORREZIONI.md docs/history/
mv FIX_WINDOW_CLOSE.md docs/fixes/
mv MANAGER_DESIGN.md docs/design/

# Opzionale: archivia relazioni se non servono
# mkdir -p docs/reports
# mv RELAZIONE_*.md docs/reports/
```

### Pulire serialization binding obsoleto:
Rimuovi da `application.conf`:
```hocon
# RIMUOVI QUESTA RIGA (Message.scala non esiste più)
serialization-bindings {
  "it.unibo.agar.Message" = jackson-json  # ← ELIMINA
}
```

---

## 📊 Statistiche

### Prima della pulizia:
- File Scala: **17**
- File MD: **11**
- File Config: **3**
- File Script: **3**

### Dopo la pulizia (proposta):
- File Scala: **13-14** (dipende da LocalDemo)
- File MD: **5** (essenziali) + archiviati
- File Config: **2**
- File Script: **3**

### Spazio risparmiato:
~15-20% del codice sorgente (file legacy)

---

## ✅ Checklist Pulizia

- [ ] Elimina `Message.scala`
- [ ] Elimina `AIMovement.scala`
- [ ] Elimina `agario.conf`
- [ ] Rimuovi binding `Message` da `application.conf`
- [ ] Decidi se mantenere `LocalDemo.scala` (ex-DistributedMain)
- [ ] Se elimini LocalDemo, elimina anche `GameStateManager.scala`
- [ ] Archivia documentazione legacy in `docs/`
- [ ] Aggiorna `README.md` con riferimenti aggiornati
- [ ] Verifica `LEGGIMI_PRIMA.md` e aggiorna/elimina
- [ ] Test: Verifica che tutto compili dopo la pulizia

---

## 🎯 Raccomandazione Finale

**Configurazione MINIMA PULITA per distributed testing:**

```
src/main/scala/it/unibo/agar/
├── controller/
│   ├── SeedNode.scala        ← Keep
│   └── PlayerNode.scala      ← Keep
├── model/
│   ├── GameWorldActor.scala
│   ├── GameWorldSingleton.scala
│   ├── PlayerActor.scala
│   ├── FoodManagerActor.scala
│   ├── DistributedGameStateManager.scala
│   ├── GameMessages.scala
│   ├── GameModels.scala
│   ├── GameInitializer.scala
│   └── EatingManager.scala
├── view/
│   ├── GlobalView.scala
│   ├── LocalView.scala
│   └── AgarViewUtils.scala
└── utils.scala

src/main/resources/
└── application.conf           ← Keep (cleaned)

Docs:
├── README.md
├── HOW_TO_RUN.md
└── DISTRIBUTED_TESTING.md

Scripts:
├── start-seed.sh
└── start-player.sh
```

**Totale file essenziali: ~20** (contro i ~34 attuali)

---

**Data:** 2025-10-01
**Autore:** Claude Code Analysis
