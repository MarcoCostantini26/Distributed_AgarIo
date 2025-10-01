# Agar.io Distribuito - Architettura e Flusso di Gioco

## Indice
1. [Introduzione](#introduzione)
2. [Architettura Generale](#architettura-generale)
3. [Flusso di Avvio](#flusso-di-avvio)
4. [Comunicazione Distribuita](#comunicazione-distribuita)
5. [Gestione dello Stato](#gestione-dello-stato)
6. [Sincronizzazione e Coerenza](#sincronizzazione-e-coerenza)
7. [Conclusioni](#conclusioni)

---

## 1. Introduzione

Questo progetto implementa una versione distribuita del gioco Agar.io utilizzando **Akka Cluster** per la gestione di un ambiente multi-nodo. L'architettura è progettata per separare il **server di gioco** (SeedNode) dai **client player** (PlayerNode), consentendo a ogni player di eseguire in un processo JVM separato, simulando un ambiente distribuito realistico.

### Tecnologie Utilizzate
- **Scala 3.3.6** - Linguaggio di programmazione
- **Akka Typed 2.10.5** - Framework attori e cluster
- **Scala Swing** - Interfaccia grafica
- **Akka Cluster Singleton** - Gestione stato centralizzato
- **Akka Receptionist** - Service discovery distribuito

---

## 2. Architettura Generale

L'architettura si basa su un modello **client-server distribuito** dove:
- Un **SeedNode** gestisce il mondo di gioco centralizzato
- Multipli **PlayerNode** si connettono come client
- La comunicazione avviene tramite **messaggi Akka** su rete TCP

### 2.1 Componenti Principali

#### SeedNode (Server)
```
┌─────────────────────────────────────┐
│         SeedNode Process            │
│  (JVM 1 - porta 2551)               │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  GameWorldSingleton           │ │
│  │  ┌─────────────────────────┐  │ │
│  │  │  GameWorldActor         │  │ │
│  │  │  - World State          │  │ │
│  │  │  - Players List         │  │ │
│  │  │  - Food List            │  │ │
│  │  │  - Movement Directions  │  │ │
│  │  └─────────────────────────┘  │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  FoodManagerActor             │ │
│  │  - Automatic food spawning    │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  GlobalView (Swing)           │ │
│  │  - Visualizza mondo intero    │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  Receptionist                 │ │
│  │  - Registra GameWorld         │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Responsabilità:**
- Mantenere lo stato autoritativo del gioco
- Processare tick di gioco (ogni 30ms)
- Gestire logica di eating (player vs player, player vs food)
- Broadcastare aggiornamenti a tutti i player
- Spawnare food automaticamente

#### PlayerNode (Client)
```
┌─────────────────────────────────────┐
│      PlayerNode Process             │
│  (JVM 2 - porta 2552)               │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  PlayerActor                  │ │
│  │  - Player ID (es. "Alice")    │ │
│  │  - Invia comandi movimento    │ │
│  │  - Riceve world updates       │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  DistributedGameStateManager  │ │
│  │  - Cache world state          │ │
│  │  - Polling asincrono          │ │
│  └───────────────────────────────┘ │
│                                     │
│  ┌───────────────────────────────┐ │
│  │  LocalView (Swing)            │ │
│  │  - Vista centrata su player   │ │
│  │  - Input mouse                │ │
│  └───────────────────────────────┘ │
└─────────────────────────────────────┘
```

**Responsabilità:**
- Gestire input utente (mouse movement)
- Mantenere cache locale dello stato
- Visualizzare vista locale del player
- Inviare comandi di movimento al server

### 2.2 Cluster Akka

La comunicazione tra nodi avviene attraverso **Akka Cluster**:

```
     Akka Cluster Network
           (TCP)
              │
    ┌─────────┴─────────┐
    │                   │
┌───▼────┐        ┌────▼────┐
│ Seed   │        │ Player  │
│ :2551  │◄──────►│ :2552   │
└────────┘        └─────────┘
                        ▲
                        │
                  ┌─────┴──────┐
                  │            │
            ┌─────▼────┐  ┌───▼─────┐
            │ Player   │  │ Player  │
            │ :2553    │  │ :2554   │
            └──────────┘  └─────────┘
```

**Configurazione Cluster:**
- **Seed nodes:** `127.0.0.1:2551` (fisso)
- **Player nodes:** Porte dinamiche (`2552`, `2553`, ...)
- **System name:** `ClusterSystem` (uguale per tutti)
- **Transport:** Akka Artery TCP

---

## 3. Flusso di Avvio

### 3.1 Avvio SeedNode

**Comando:**
```bash
sbt -Dakka.remote.artery.canonical.port=2551 "runMain it.unibo.agar.controller.SeedNode"
```

**Sequenza di inizializzazione:**

```
1. Main thread
   │
   ├─► Crea ActorSystem("ClusterSystem")
   │   │
   │   └─► Akka Cluster si inizializza su porta 2551
   │
2. Guardian Actor (seedBehavior)
   │
   ├─► Sottoscrive evento SelfUp del cluster
   │   (aspetta che il cluster sia pronto)
   │
   ├─► Inizializza mondo di gioco
   │   - numPlayers = 0 (nessun dummy)
   │   - numFoods = 100
   │   - World(1000, 1000, players=[], foods=[...])
   │
   ├─► Crea GameWorldSingleton
   │   │
   │   └─► Crea GameWorldActor con stato iniziale
   │       - Gestisce Map[playerId -> direction]
   │       - Set[registeredPlayers] per broadcast
   │
3. Cluster raggiunge stato "Up"
   │
   ├─► Riceve evento ClusterReady
   │
   ├─► Registra GameWorld nel Receptionist
   │   (permette ai player di trovarlo)
   │
   ├─► Crea FoodManagerActor
   │   - Spawna food ogni 5 secondi
   │
   ├─► Crea DistributedGameStateManager
   │   - Mantiene riferimento a GameWorld
   │
   ├─► Avvia Timer per Tick
   │   - Ogni 30ms invia Tick a GameWorld
   │   - Aggiorna rendering di tutte le finestre
   │
   └─► Apre GlobalView (Swing EDT)
       - Finestra 800x800
       - Mostra intero mondo di gioco

4. Seed pronto
   │
   └─► Stampa: "📡 Waiting for player nodes to connect..."
```

**Stato finale:**
- Cluster formato con 1 nodo
- GameWorld attivo e in ascolto
- GlobalView visualizzata
- Timer tick attivo (30ms)
- FoodManager spawna food

### 3.2 Avvio PlayerNode

**Comando:**
```bash
sbt -Dakka.remote.artery.canonical.port=2552 "runMain it.unibo.agar.controller.PlayerNode Alice"
```

**Sequenza di connessione:**

```
1. Main thread
   │
   ├─► Legge args[0] = "Alice" (nome player)
   │
   ├─► Crea ActorSystem("ClusterSystem")
   │   │
   │   └─► Akka Cluster si inizializza su porta 2552
   │
   └─► Blocca su Await.result(system.whenTerminated)
       (mantiene processo vivo)

2. Cluster join
   │
   ├─► Contatta seed node su 127.0.0.1:2551
   │
   ├─► Riceve InitJoinAck dal seed
   │
   └─► Diventa membro del cluster (stato "Up")

3. Guardian Actor (playerBehavior)
   │
   ├─► Sottoscrive al Receptionist
   │   - Cerca servizio "game-world-manager"
   │   - Riceve aggiornamenti quando disponibile
   │
   ├─► Stato: waitingForGameWorld
   │   - Ogni 2 secondi controlla
   │   - Max 15 tentativi (30 secondi timeout)
   │
   └─► Riceve GameWorldFound(gameWorldRef)

4. GameWorld trovato!
   │
   ├─► Crea PlayerActor("Alice", gameWorldRef)
   │   │
   │   ├─► PlayerActor crea message adapter
   │   │   (converte GameMessage → PlayerMessage)
   │   │
   │   └─► Invia RegisterPlayer(Alice, adapter)
   │       a GameWorld
   │
   ├─► PlayerActor riceve StartPlayer(1000, 1000)
   │   - Memorizza dimensioni mondo
   │
   ├─► PlayerActor riceve JoinGame
   │   │
   │   ├─► Genera posizione casuale (x, y)
   │   │
   │   ├─► Invia PlayerJoined(Alice, x, y, 120.0)
   │   │   a GameWorld
   │   │
   │   └─► Passa a stato "active"
   │
   ├─► Crea DistributedGameStateManager
   │   - Polling asincrono ogni 100ms
   │   - Mantiene cache del World
   │
   └─► Apre LocalView (Swing EDT)
       │
       ├─► Finestra 400x400
       ├─► Listener per mouse movement
       └─► Rendering centrato su Alice

5. Player pronto
   │
   └─► Stampa: "✅ Player Alice ready!"
```

**Stato finale:**
- Player connesso al cluster
- PlayerActor registrato in GameWorld
- LocalView visualizzata
- Controllo mouse attivo
- Cache mondo locale aggiornata

---

## 4. Comunicazione Distribuita

### 4.1 Protocollo di Messaggi

Il sistema usa **messaggi tipati** per la comunicazione:

#### Messaggi GameWorld → Player (Broadcast)
```scala
case class WorldStateUpdate(world: World) extends GameMessage
```
- Inviato ogni tick (30ms) a tutti i player registrati
- Contiene stato completo del mondo
- Serializzato tramite Java serialization

#### Messaggi Player → GameWorld (Commands)
```scala
// Movimento
case class MovePlayer(id: String, dx: Double, dy: Double) extends GameMessage

// Join/Leave
case class PlayerJoined(id: String, x: Double, y: Double, mass: Double) extends GameMessage
case class PlayerLeft(id: String) extends GameMessage

// Registrazione
case class RegisterPlayer(playerId: String, playerNode: ActorRef[GameMessage]) extends GameMessage
case class UnregisterPlayer(playerId: String) extends GameMessage
```

### 4.2 Ciclo di Comunicazione Tipico

**Esempio: Alice muove il mouse**

```
1. LocalView (Swing EDT - Alice's JVM)
   │
   ├─► MouseMoved event
   │
   └─► Calcola direzione normalizzata (dx, dy)

2. DistributedGameStateManager (Alice's JVM)
   │
   └─► movePlayerDirection("Alice", dx, dy)

3. Messaggio inviato via Akka Remoting
   │
   └─► MovePlayer("Alice", dx, dy)
       │
       └─► TCP → 127.0.0.1:2551

4. GameWorldActor (SeedNode JVM)
   │
   ├─► Riceve MovePlayer("Alice", dx, dy)
   │
   ├─► Aggiorna Map[directions]
   │   directions += ("Alice" -> (dx, dy))
   │
   └─► Al prossimo Tick, applica movimento

5. Tick (30ms dopo)
   │
   ├─► GameWorldActor.processTick()
   │   │
   │   ├─► Per ogni (playerId, direction):
   │   │   - Trova player nel world
   │   │   - Calcola nuova posizione
   │   │   - Controlla eating (food e player)
   │   │   - Aggiorna world
   │   │
   │   └─► Broadcast WorldStateUpdate(newWorld)
   │       a tutti i player registrati
   │
6. PlayerActor (Alice's JVM)
   │
   ├─► Riceve WorldStateUpdate(world) via adapter
   │   │
   │   └─► Converte in PlayerWorldUpdate(world)
   │
   ├─► Trova Alice nel world.players
   │
   └─► Aggiorna posizione locale

7. DistributedGameStateManager (Alice's JVM)
   │
   ├─► Polling separato ogni 100ms
   │   (fallback mechanism)
   │
   └─► updateWorld(world)
       - Aggiorna cache AtomicReference

8. LocalView (Swing EDT - Alice's JVM)
   │
   ├─► Timer repaint (30ms)
   │
   ├─► getWorld() da manager (non blocking)
   │
   └─► Disegna world centrato su Alice
```

**Latenza totale:** ~30-60ms (tick + network)

### 4.3 Service Discovery con Receptionist

Il **Receptionist** di Akka permette ai player di trovare il GameWorld senza hardcoded references:

```scala
// SeedNode: Registrazione
val GameWorldKey = ServiceKey[GameMessage]("game-world-manager")
context.system.receptionist ! Receptionist.Register(GameWorldKey, gameWorld)

// PlayerNode: Discovery
context.system.receptionist ! Receptionist.Subscribe(GameWorldKey, adapter)

// PlayerNode: Quando trovato
case GameWorldFound(gameWorldRef) =>
  // Ora possiamo comunicare con gameWorldRef
```

**Vantaggi:**
- Location transparency (player non sa dove è GameWorld)
- Dynamic discovery (GameWorld può migrare)
- Fault tolerance (se GameWorld si riavvia, viene ri-scoperto)

---

## 5. Gestione dello Stato

### 5.1 Single Source of Truth: GameWorldActor

Il **GameWorldActor** è l'unica fonte autoritativa dello stato:

```scala
private def gameWorld(
  world: World,                          // Stato corrente
  directions: Map[String, (Double, Double)],  // Input player
  registeredPlayers: Set[ActorRef[GameMessage]]  // Subscriber
): Behavior[GameMessage]
```

**Stato immutabile:**
```scala
case class World(
  width: Int,
  height: Int,
  players: Seq[Player],
  foods: Seq[Food]
)

case class Player(id: String, x: Double, y: Double, mass: Double)
case class Food(id: String, x: Int, y: Int, mass: Double)
```

**Ad ogni Tick:**
1. Crea nuovo `World` con posizioni aggiornate
2. Applica logica eating (immutabile)
3. Broadcast nuovo stato a tutti

### 5.2 Cache Locale: DistributedGameStateManager

Ogni PlayerNode mantiene una **cache locale**:

```scala
class DistributedGameStateManager(gameWorldActor: ActorRef[GameMessage]):

  private val cachedWorld = new AtomicReference[World](
    World(1000, 1000, Seq.empty, Seq.empty)
  )

  // Polling asincrono ogni 100ms
  private val cancellable = system.scheduler.scheduleAtFixedRate(...) { () =>
    gameWorldActor.ask(GetWorld.apply).foreach { world =>
      cachedWorld.set(world)
    }
  }

  // Non-blocking read per UI
  def getWorld: World = cachedWorld.get()
```

**Perché cache?**
- UI deve essere **non-blocking**
- `paintComponent` chiamato 30+ volte/sec
- Ask pattern sarebbe troppo lento
- Trade-off: dati leggermente stale (max 100ms)

### 5.3 Eventual Consistency

Il sistema usa **eventual consistency**:

```
T=0ms:  Alice muove mouse
        └─► LocalView invia MovePlayer

T=5ms:  GameWorld riceve comando
        └─► Aggiorna directions map

T=30ms: Tick processa movimento
        └─► Nuovo world calcolato

T=35ms: WorldStateUpdate broadcast

T=40ms: Alice riceve update
        └─► Cache aggiornata

T=45ms: LocalView repaint
        └─► Mostra nuova posizione
```

**Totale delay:** ~45ms (accettabile per gioco casual)

---

## 6. Sincronizzazione e Coerenza

### 6.1 Cluster Singleton per Coerenza

**GameWorldSingleton** garantisce che ci sia **un solo GameWorld** nel cluster:

```scala
val gameWorldSingletonRef = clusterSingleton.init(
  SingletonActor(
    Behaviors.supervise(GameWorldActor(initialWorld))
      .onFailure(akka.actor.typed.SupervisorStrategy.restart),
    "game-world-singleton"
  )
)
```

**Benefici:**
- **Coerenza forte:** Un solo attore gestisce tutto lo stato
- **Fault tolerance:** Se il nodo seed crasha, singleton migra
- **Serializable operations:** Tutti i comandi processati sequenzialmente

### 6.2 Gestione Concorrenza

**Akka Actor Model** garantisce:

1. **Mailbox serialization:**
   - Messaggi processati uno alla volta
   - No race conditions

2. **Immutabilità:**
   - `World` è immutabile
   - Ogni update crea nuovo oggetto

3. **Copy-on-write:**
   ```scala
   val updatedWorld = world.copy(
     players = world.players.map {
       case p if p.id == playerId => p.copy(x = newX, y = newY)
       case other => other
     }
   )
   ```

### 6.3 Broadcast Atomico

```scala
case Tick =>
  val updatedWorld = processTick(world, directions)

  // Broadcast atomico a tutti
  registeredPlayers.foreach { playerRef =>
    playerRef ! WorldStateUpdate(updatedWorld)
  }

  gameWorld(updatedWorld, directions, registeredPlayers)
```

**Garanzie:**
- Tutti i player ricevono **stesso snapshot**
- No partial updates
- Order preserved per singolo player

### 6.4 Gestione Disconnessioni

**Player leave:**
```scala
case PlayerLeft(id) =>
  val updatedWorld = world.copy(
    players = world.players.filterNot(_.id == id)
  )
  val newDirections = directions.removed(id)
  gameWorld(updatedWorld, newDirections, registeredPlayers)
```

**Cleanup risorse:**
```scala
// PlayerNode: Window closing
localView.reactions += {
  case WindowClosing(_) =>
    playerActor ! LeaveGame
    localView.dispose()
    selfRef ! Shutdown
}

// PlayerActor: Leave game
case LeaveGame =>
  gameWorldActor ! PlayerLeft(playerId)
  gameWorldActor ! UnregisterPlayer(playerId)
```

---

## 7. Conclusioni

### 7.1 Architettura Distribuita Realizzata

Il progetto implementa con successo un'architettura **client-server distribuita** con:

✅ **Separazione fisica:** Seed e Player in processi JVM separati
✅ **Comunicazione remota:** Akka Cluster con TCP transport
✅ **Service discovery:** Receptionist per location transparency
✅ **Fault tolerance:** Cluster Singleton con supervision
✅ **Scalabilità:** N player possono connettersi dinamicamente
✅ **Coerenza:** Single source of truth nel GameWorld
✅ **Responsiveness:** Cache locale per UI non-blocking

### 7.2 Flusso Completo Riassunto

```
1. AVVIO SEED
   └─► GameWorld + GlobalView + Receptionist

2. AVVIO PLAYER
   └─► Cluster join → Discovery → PlayerActor → LocalView

3. GAMEPLAY LOOP
   ├─► Mouse input → MovePlayer → GameWorld
   ├─► Tick (30ms) → processTick → Broadcast
   └─► WorldStateUpdate → Cache → Render

4. DISCONNESSIONE
   └─► Window close → LeaveGame → Cleanup
```

### 7.3 Caratteristiche Architetturali

| Aspetto | Implementazione |
|---------|----------------|
| **Pattern** | Client-Server distribuito |
| **Concorrenza** | Actor Model (Akka Typed) |
| **Comunicazione** | Message passing remoto |
| **Coerenza** | Strong consistency (Singleton) |
| **Disponibilità** | High availability (Cluster) |
| **Latency** | ~30-60ms (tick-based) |
| **Scalabilità** | Orizzontale (N player) |

### 7.4 Punti di Forza

1. **Architettura pulita:** Separazione SeedNode/PlayerNode
2. **Actor model:** No locks, no race conditions
3. **Type-safe:** Messaggi tipati, compile-time checks
4. **Testabile:** Ogni nodo avviabile indipendentemente
5. **Fault tolerant:** Cluster supervision + restart strategies

### 7.5 Possibili Estensioni

**Performance:**
- Serialization ottimizzata (Protobuf/Avro)
- Spatial partitioning per world state
- Differential updates invece di full world

**Scalabilità:**
- Cluster sharding per >100 player
- Multiple GameWorld per world regions
- Load balancing player su nodi

**Features:**
- Persistent game state (Akka Persistence)
- Replay/time-travel debugging
- Metrics e monitoring (Lightbend Telemetry)

---

## Appendice: Comandi Utili

### Avvio Sistema
```bash
# Terminal 1: Seed
sbt -Dakka.remote.artery.canonical.port=2551 "runMain it.unibo.agar.controller.SeedNode"

# Terminal 2: Player Alice
sbt -Dakka.remote.artery.canonical.port=2552 "runMain it.unibo.agar.controller.PlayerNode Alice"

# Terminal 3: Player Bob
sbt -Dakka.remote.artery.canonical.port=2553 "runMain it.unibo.agar.controller.PlayerNode Bob"
```

### Debugging
```bash
# Logs cluster
sbt -Dakka.loglevel=DEBUG ...

# Monitor JVM
jps -l | grep it.unibo.agar

# Network traffic
lsof -i :2551
```

---

**Fine Relazione**

*Questo documento descrive l'architettura distribuita implementata nel progetto Agar.io, evidenziando il flusso completo dall'avvio alla comunicazione tra nodi, garantendo coerenza e scalabilità.*
