# 🎮 Come eseguire Agar.io Distribuito

## 🚀 Opzione 1: Testing Distribuito (CONSIGLIATO)

Simula un vero ambiente distribuito con seed node e player separati.

### Step 1: Avvia il Seed Node (Server)
```bash
./start-seed.sh
```

Cosa fa:
- ✅ Crea il GameWorld singleton
- ✅ Apre GlobalView (1000x1000)
- ✅ Spawna food automaticamente
- ✅ Aspetta connessioni dei player

### Step 2: Connetti i Player (Client)

**Terminal 2 - Player Alice:**
```bash
./start-player.sh Alice 2552
```

**Terminal 3 - Player Bob:**
```bash
./start-player.sh Bob 2553
```

**Terminal 4 - Player Charlie:**
```bash
./start-player.sh Charlie 2554
```

Ogni player:
- ✅ Si connette al cluster
- ✅ Apre LocalView (400x400)
- ✅ Controlla con il mouse
- ✅ Vede altri player

### Vantaggi:
- ✅ Vero cluster Akka distribuito
- ✅ Ogni player = processo separato
- ✅ Simula ambiente di produzione
- ✅ Player illimitati
- ✅ Player possono join/leave durante il gioco

---

## 🏠 Opzione 2: Demo Locale (Quick Test)

Tutto in un singolo processo, per test rapidi.

```bash
./start-local-demo.sh
```

Cosa fa:
- ✅ Apre 3 finestre: GlobalView + 2 LocalViews
- ✅ 2 player hardcoded (p1, p2)
- ✅ Tutto in un processo

### Vantaggi:
- ✅ Avvio rapido (1 comando)
- ✅ Niente cluster da configurare
- ✅ Più semplice da debuggare

### Svantaggi:
- ❌ Non è distribuito (solo simulato)
- ❌ Solo 2 player fissi
- ❌ Non realistico

---

## 🛑 Come Fermare

### Distributed Mode:
- **Seed Node:** Premi ENTER nel terminal
- **Player Node:** Chiudi la finestra LocalView con X

### Local Demo:
- Chiudi la finestra GlobalView (chiude tutto)

---

## 🎯 Quale scegliere?

| Caso d'uso                        | Soluzione       |
|-----------------------------------|-----------------|
| Testing distribuito reale         | `start-seed.sh` + `start-player.sh` |
| Demo veloce locale                | `start-local-demo.sh` |
| Testing con 1-2 player            | `start-local-demo.sh` |
| Testing con 3+ player             | `start-seed.sh` + `start-player.sh` |
| Simulare crash di player          | Distributed Mode |
| Debugging semplice                | Local Demo |

---

## 🔧 Configurazione

### Modificare numero di player dummy iniziali

Apri [SeedNode.scala](src/main/scala/it/unibo/agar/controller/SeedNode.scala):

```scala
val numPlayers = 0  // 0 = nessun dummy, solo player reali
val numFoods = 100  // Numero di food nel mondo
```

### Modificare dimensioni mondo

```scala
val width = 1000   // Larghezza mondo
val height = 1000  // Altezza mondo
```

---

## 📚 File principali

| File              | Scopo                          | Comando                          |
|-------------------|--------------------------------|----------------------------------|
| `SeedNode.scala`  | Server di gioco (GameWorld)    | `./start-seed.sh`                |
| `PlayerNode.scala`| Client player                  | `./start-player.sh <name> <port>`|
| `LocalDemo.scala` | Demo locale (tutto in uno)     | `./start-local-demo.sh`          |

---

## 🐛 Troubleshooting

### Problema: "Port already in use"
```
Causa: Porta già occupata
Soluzione: Cambia porta o uccidi processo
```

```bash
# Trova processo
lsof -i :2551

# Uccidi
kill -9 <PID>
```

### Problema: "GameWorld not found"
```
Causa: Seed node non avviato o cluster non formato
Soluzione: Avvia seed node PRIMA dei player
```

### Problema: Player non si muovono
```
Causa: Serialization error o messaggio non arriva
Soluzione: Controlla logs per errori Akka
```

---

## 🎉 Enjoy!

Per domande: leggi [DISTRIBUTED_TESTING.md](DISTRIBUTED_TESTING.md)
