# Design Pattern: Manager Unico vs Manager per Player

## La Domanda

> Perché creare un manager per ogni player invece di uno condiviso?

## Risposta Breve

**Non è necessario.** Un **singolo manager condiviso** è la scelta migliore.

---

## Confronto delle Due Architetture

### ❌ Architettura Originale (SBAGLIATA)

```scala
// Manager separato per ogni player
playerIds.foreach { playerId =>
  val manager = new DistributedGameStateManager(gameWorld)(context.system)  // ❌ Duplicazione!

  val playerActor = context.spawn(PlayerActor(playerId, gameWorld), ...)

  if (playerId == "p1") player1Manager = manager
  else if (playerId == "p2") player2Manager = manager
}

globalManager = new DistributedGameStateManager(gameWorld)(context.system)  // ❌ Altro duplicato!
```

**Risultato:**
- 3 manager distinti (p1Manager, p2Manager, globalManager)
- 3 scheduler di polling separati (ogni 100ms)
- 3 cache separate dello stesso `World`
- **3x overhead di risorse**

### ✅ Architettura Corretta (GIUSTA)

```scala
// SINGOLO manager condiviso
val sharedManager = new DistributedGameStateManager(gameWorld)(context.system)

player1Manager = sharedManager
player2Manager = sharedManager
globalManager = sharedManager

playerIds.foreach { playerId =>
  val playerActor = context.spawn(PlayerActor(playerId, gameWorld), ...)
  // Usa sharedManager implicitamente quando le view vengono create
}
```

**Risultato:**
- 1 solo manager
- 1 solo scheduler di polling
- 1 sola cache condivisa
- **Risorse ottimizzate**

---

## Analisi Dettagliata

### Cosa fa il DistributedGameStateManager?

```scala
class DistributedGameStateManager(gameWorldActor: ActorRef[GameMessage]) {

  // [1] Cache CONDIVISA dello stato del mondo
  private val cachedWorld = new AtomicReference[World](...)

  // [2] Polling in background
  private val cancellable = system.scheduler.scheduleAtFixedRate(100.millis) { () =>
    gameWorldActor.ask(GetWorld.apply).foreach { world =>
      cachedWorld.set(world)  // Aggiorna la cache
    }
  }

  // [3] Lettura dalla cache
  def getWorld: World = cachedWorld.get()

  // [4] Invio comandi
  def movePlayerDirection(id: String, dx: Double, dy: Double): Unit =
    gameWorldActor ! MovePlayer(id, dx, dy)
}
```

### Perché NON serve un manager per player?

**Motivo 1: Cache Identica**

Tutti i manager interrogano lo **stesso** `GameWorldActor` e ottengono lo **stesso** `World`.

```scala
// Manager 1 (p1)
gameWorldActor.ask(GetWorld) → World(players=[p1, p2], foods=[...])

// Manager 2 (p2)
gameWorldActor.ask(GetWorld) → World(players=[p1, p2], foods=[...])  // IDENTICO!

// Manager 3 (global)
gameWorldActor.ask(GetWorld) → World(players=[p1, p2], foods=[...])  // IDENTICO!
```

**Risultato:** 3 copie della stessa informazione in memoria.

**Motivo 2: Polling Ridondante**

Ogni manager fa polling indipendente:

```
t=0ms:   Manager1 ask → World
t=0ms:   Manager2 ask → World  } 3 ask simultanei ogni 100ms!
t=0ms:   Manager3 ask → World  }

t=100ms: Manager1 ask → World
t=100ms: Manager2 ask → World  } 3 ask simultanei ogni 100ms!
t=100ms: Manager3 ask → World  }
```

**Overhead:** 3x ask al GameWorldActor → **bottleneck artificiale**.

**Motivo 3: Comando MovePlayer Identico**

```scala
// LocalView p1 chiama:
player1Manager.movePlayerDirection("p1", dx, dy)
  → gameWorldActor ! MovePlayer("p1", dx, dy)

// LocalView p2 chiama:
player2Manager.movePlayerDirection("p2", dx, dy)
  → gameWorldActor ! MovePlayer("p2", dx, dy)
```

Non c'è differenza! Il manager è solo un **proxy** al GameWorldActor.

---

## Quando Avrebbe Senso Manager Separati?

### Scenario 1: Cache Locale per Ottimizzazioni

Se ogni player avesse **vista parziale** del mondo:

```scala
class LocalGameStateManager(playerId: String, gameWorldActor: ActorRef[GameMessage]) {

  // Cache solo la regione vicino al player
  private val visibleRadius = 500

  def getWorld: World = {
    val fullWorld = cachedWorld.get()

    fullWorld.playerById(playerId) match {
      case Some(player) =>
        // Filtra solo entità visibili
        World(
          width = fullWorld.width,
          height = fullWorld.height,
          players = fullWorld.players.filter(p =>
            distanceTo(player, p) < visibleRadius
          ),
          foods = fullWorld.foods.filter(f =>
            distanceTo(player, f) < visibleRadius
          )
        )
      case None => fullWorld
    }
  }
}
```

**Quando usare:**
- Mondo molto grande (10000x10000)
- Migliaia di entità
- Bandwidth limitata

**Nel nostro caso:** Mondo 1000x1000, max 50 player → non necessario.

---

### Scenario 2: Manager su Nodi Diversi

Se i player fossero su **macchine fisiche diverse**:

```
Node 1 (Server A):
  ├─ PlayerActor(p1)
  └─ DistributedGameStateManager → GameWorldActor (su Node 3)

Node 2 (Server B):
  ├─ PlayerActor(p2)
  └─ DistributedGameStateManager → GameWorldActor (su Node 3)

Node 3 (Server C):
  └─ GameWorldActor (Cluster Singleton)
```

In questo caso, **ogni nodo ha il suo manager locale** per:
- Evitare serializzazione ripetuta
- Cache locale sulla macchina
- Ridurre latenza di rete

**Nel nostro caso:** Tutti i player nello stesso processo → manager condiviso è più efficiente.

---

## Confronto Performance

### Overhead di Memoria

| Architettura | Manager | Scheduler | Cache | Totale Heap |
|--------------|---------|-----------|-------|-------------|
| **Manager per player** (3) | 3 * 1KB = 3KB | 3 threads | 3 * 500KB = 1.5MB | ~1.5MB |
| **Manager condiviso** (1) | 1KB | 1 thread | 500KB | ~500KB |

**Risparmio:** 1MB di heap, 2 thread in meno.

### Overhead di Network (ask al GameWorldActor)

| Architettura | Ask/secondo | Throughput |
|--------------|-------------|------------|
| **Manager per player** (3) | 3 * 10 = 30 ask/sec | Alto |
| **Manager condiviso** (1) | 10 ask/sec | Basso |

**Risparmio:** 66% di riduzione dei messaggi.

### Latency per getWorld

| Architettura | Cache Hits | Latenza |
|--------------|------------|---------|
| **Manager per player** | 100% | <1ms (identico) |
| **Manager condiviso** | 100% | <1ms (identico) |

**Differenza:** Nessuna (entrambi leggono dalla cache).

---

## Conclusione

### Manager Unico Condiviso è Migliore Perché:

1. ✅ **Riduce overhead di risorse** (memoria, thread, network)
2. ✅ **Semplifica il codice** (un solo oggetto da gestire)
3. ✅ **Stessa performance** (latency identica per getWorld)
4. ✅ **Facilita shutdown** (un solo cancellable.cancel())
5. ✅ **Coerenza garantita** (un solo punto di polling)

### Quando Usare Manager Separati:

1. ⚠️ **Deploy distribuito reale** (player su nodi diversi)
2. ⚠️ **Vista parziale del mondo** (ottimizzazione bandwidth)
3. ⚠️ **Cache specializzata** (filtri diversi per player)

Nel nostro caso (single-node con 2-4 player), **manager condiviso è la scelta ottimale**.

---

## Codice Finale Corretto

```scala
object DistributedMain extends SimpleSwingApplication:

  private val system: ActorSystem[GuardianCommand] = ActorSystem(
    guardian(),
    "ClusterSystem"
  )

  // SINGOLO manager condiviso
  @volatile private var sharedManager: DistributedGameStateManager = _

  def guardian(): Behavior[GuardianCommand] =
    Behaviors.setup { context =>
      val gameWorld = context.spawn(
        GameWorldSingleton(initialWorld),
        "game-world-manager"
      )

      // Crea UN SOLO manager
      sharedManager = new DistributedGameStateManager(gameWorld)(context.system)

      // Crea PlayerActors (che non usano direttamente il manager)
      Seq("p1", "p2").foreach { playerId =>
        val playerActor = context.spawn(
          PlayerActor(playerId, gameWorld),
          s"player-actor-$playerId"
        )
        playerActor ! StartPlayer(width, height)
        playerActor ! JoinGame
      }

      // ... rest of setup
    }

  override def top: Frame =
    Thread.sleep(500)

    // TUTTE le view usano lo STESSO manager
    new LocalView(sharedManager, "p1").open()
    new LocalView(sharedManager, "p2").open()
    new GlobalView(sharedManager)  // main frame
```

**Vantaggi:**
- 1 solo scheduler (10 ask/sec invece di 30)
- 1 sola cache (500KB invece di 1.5MB)
- Codice più semplice e leggibile
- Shutdown più facile

---

## Diagramma Architettura Corretta

```
┌────────────────────────────────────────────────┐
│         DistributedMain Process                │
│                                                │
│  ┌──────────────────────────────────────────┐ │
│  │  GameWorldActor (Cluster Singleton)      │ │
│  │  - world: World                          │ │
│  │  - directions: Map[String, (Double,Double)]│ │
│  └──────────────────────────────────────────┘ │
│                   ▲    ▲    ▲                  │
│                   │    │    │                  │
│          ┌────────┼────┼────┼────────┐        │
│          │        │    │    │        │        │
│          │  ┌─────┴────┴────┴─────┐  │        │
│          │  │ DistributedGame     │  │        │
│          │  │   StateManager      │  │        │
│          │  │  (SHARED INSTANCE)  │  │        │
│          │  │  - cachedWorld      │  │        │
│          │  │  - polling (100ms)  │  │        │
│          │  └─────┬────┬────┬─────┘  │        │
│          │        │    │    │        │        │
│    ┌─────▼────┐   │    │    │   ┌────▼─────┐ │
│    │LocalView │   │    │    │   │LocalView │ │
│    │   p1     │◄──┘    │    └──►│   p2     │ │
│    │(400x400) │        │        │(400x400) │ │
│    └──────────┘        │        └──────────┘ │
│                        │                      │
│                  ┌─────▼──────┐              │
│                  │GlobalView  │              │
│                  │  (800x800) │              │
│                  └────────────┘              │
│                                                │
│    ┌──────────┐      ┌──────────┐            │
│    │Player    │      │Player    │            │
│    │Actor p1  │      │Actor p2  │            │
│    └──────────┘      └──────────┘            │
│                                                │
└────────────────────────────────────────────────┘

Legend:
  ━━━ Ownership/Contains
  ──► Uses/References
```

**Notare:** Tutte le View puntano allo **stesso** DistributedGameStateManager.
