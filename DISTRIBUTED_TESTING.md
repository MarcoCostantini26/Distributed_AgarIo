# 🌐 Testing Distributed Agar.io

## 🎯 Overview

Your game now supports **true distributed gameplay** where:
- **SeedNode**: Hosts the GameWorld singleton + GlobalView (referee view)
- **PlayerNode**: Individual player instances that connect to the seed

Each player runs in a **separate terminal/JVM** to simulate distributed execution.

---

## 🚀 Quick Start

### Step 1: Start the Seed Node (GameWorld)

In **Terminal 1** (this MUST be started first):

```bash
sbt -Dakka.remote.artery.canonical.port=2551 "runMain it.unibo.agar.controller.SeedNode"
```

**What happens:**
- ✅ GameWorld singleton starts on port 2551
- ✅ GlobalView window opens (shows entire game)
- ✅ FoodManager starts spawning food
- ✅ Waiting for players to connect...

---

### Step 2: Connect Players

In **Terminal 2** (Player 1):

```bash
sbt -Dakka.remote.artery.canonical.port=2552 "runMain it.unibo.agar.controller.PlayerNode Alice"
```

In **Terminal 3** (Player 2):

```bash
sbt -Dakka.remote.artery.canonical.port=2553 "runMain it.unibo.agar.controller.PlayerNode Bob"
```

In **Terminal 4** (Player 3):

```bash
sbt -Dakka.remote.artery.canonical.port=2554 "runMain it.unibo.agar.controller.PlayerNode Charlie"
```

**What happens for each player:**
- ✅ Connects to cluster on its own port
- ✅ Finds GameWorld via Akka receptionist
- ✅ Creates PlayerActor
- ✅ Opens LocalView window (player's view)
- ✅ Joins the game!

---

## 🎮 Expected Behavior

### Seed Node (Terminal 1)
```
============================================================
🌱 Starting SEED NODE (GameWorld + GlobalView)
============================================================

✅ Seed node started!
📡 Waiting for player nodes to connect...
   Run: sbt "runMain it.unibo.agar.controller.PlayerNode <playerName>"

Press ENTER to shutdown...
```

**GlobalView Window:**
- Shows entire 1000x1000 game world
- Displays ALL players and food
- Updates in real-time as players move/eat

---

### Player Node (Terminals 2, 3, 4...)

```
============================================================
🎮 Starting PLAYER NODE: Alice
============================================================

✅ Player node for 'Alice' started!
📡 Connecting to seed node...

✅ Connected to GameWorld!
🎮 Creating player: Alice
✅ Player Alice ready!
   Close the window to disconnect
```

**LocalView Window:**
- Shows player's personal view (400x400)
- Centered on the player's blob
- Control with mouse movement

---

## 🧪 Testing Scenarios

### ✅ Test 1: Basic Connection

1. Start seed node (Terminal 1)
2. Wait for "Seed node started"
3. Start player Alice (Terminal 2)
4. **Expected**: Alice appears in GlobalView and LocalView

---

### ✅ Test 2: Multiple Players

1. Start seed node
2. Start Alice (Terminal 2)
3. Start Bob (Terminal 3)
4. Start Charlie (Terminal 4)
5. **Expected**:
   - GlobalView shows all 3 players
   - Each LocalView shows their own perspective
   - Players can see each other

---

### ✅ Test 3: Player Disconnect

1. Have 3 players connected
2. Close Alice's LocalView window
3. **Expected**:
   - Alice sends `LeaveGame` message
   - Alice disappears from GlobalView
   - Bob and Charlie continue playing
   - Terminal 2 shows: "👋 Player Alice disconnected"

---

### ✅ Test 4: Late Join

1. Start seed + 2 players
2. Let them play for 30 seconds
3. Add a new player (Terminal 5)
4. **Expected**:
   - New player joins ongoing game
   - Sees current game state
   - Can interact immediately

---

### ✅ Test 5: Seed Node Shutdown

1. Have multiple players connected
2. Close GlobalView (or press ENTER in Terminal 1)
3. **Expected**:
   - All player nodes lose connection
   - All LocalView windows show disconnection

---

## 🐛 Troubleshooting

### Problem: "Still waiting for GameWorld..."

**Cause**: Seed node not running or cluster not formed

**Solution**:
1. Check Terminal 1 is running SeedNode
2. Verify port 2551 is available
3. Check firewall settings
4. Look for cluster formation logs

---

### Problem: Player window closes immediately

**Cause**: GameWorld not found before timeout

**Solution**:
1. Make sure SeedNode started FIRST
2. Wait 5 seconds after seed starts before adding players
3. Check logs for connection errors

---

### Problem: Port already in use

**Error**: `Address already in use`

**Solution**:
```bash
# Find process using port
lsof -i :2551

# Kill process
kill -9 <PID>

# Or use different port
sbt -Dakka.remote.artery.canonical.port=2555 "runMain ..."
```

---

### Problem: Players don't see each other

**Cause**: Cluster not formed properly

**Solution**:
1. Check all nodes use same `ClusterSystem` name
2. Verify seed-nodes config in application.conf
3. Look for "Member is Up" logs

---

## 📊 What to Observe

### In Logs

Look for these messages:

**Seed Node:**
```
[INFO] 🌱 Initializing Seed Node
[INFO] ✅ Seed Node initialized successfully
[INFO] Member is Up: akka://ClusterSystem@127.0.0.1:2552
```

**Player Node:**
```
[INFO] 🎮 Initializing Player Node for: Alice
[INFO] ✅ Found GameWorld! Creating player: Alice
[INFO] ✅ Player Alice initialized successfully
```

**Cluster Formation:**
```
[INFO] Cluster Node [akka://ClusterSystem@127.0.0.1:2551] - Node joined
[INFO] Cluster Node [akka://ClusterSystem@127.0.0.1:2552] - Node joined
```

---

### In Windows

**GlobalView (Seed):**
- ALL players visible
- ALL food visible
- Players move smoothly
- Food disappears when eaten
- New food spawns

**LocalView (Player):**
- Player's blob in center
- Follows mouse cursor
- Other players visible nearby
- Food visible in range
- Grows when eating

---

## 🔧 Advanced Configuration

### Change Hostname (Different Machines)

Edit `application.conf`:

```hocon
canonical {
  hostname = "192.168.1.100"  # Your machine's IP
  port = 2551
}
```

Then update seed-nodes:

```hocon
seed-nodes = [
  "akka://ClusterSystem@192.168.1.100:2551"
]
```

---

### Adjust Game Settings

In `SeedNode.scala`:

```scala
val width = 1000        // World width
val height = 1000       // World height
val numPlayers = 4      // Initial player slots
val numFoods = 100      // Food count
```

In `PlayerNode.scala`:

```scala
val width = 1000        // Must match seed
val height = 1000       // Must match seed
```

---

## 📝 Architecture

```
┌─────────────────────────────────────────────────┐
│              Terminal 1 (Seed Node)             │
│  ┌─────────────┐  ┌──────────────┐             │
│  │ GameWorld   │  │ GlobalView   │             │
│  │ Singleton   │  │ (1000x1000)  │             │
│  └─────────────┘  └──────────────┘             │
│         │                                        │
│         │ Akka Cluster (port 2551)              │
└─────────┼────────────────────────────────────────┘
          │
          ├─────────────┬─────────────┬────────────
          │             │             │
┌─────────▼─────┐ ┌─────▼──────┐ ┌──▼──────────┐
│   Terminal 2  │ │ Terminal 3 │ │ Terminal 4  │
│ PlayerNode    │ │ PlayerNode │ │ PlayerNode  │
│ (Alice:2552)  │ │ (Bob:2553) │ │(Charlie:2554)│
├───────────────┤ ├────────────┤ ├─────────────┤
│ LocalView     │ │ LocalView  │ │ LocalView   │
│ (400x400)     │ │ (400x400)  │ │ (400x400)   │
└───────────────┘ └────────────┘ └─────────────┘
```

---

## ✅ Success Criteria

Your setup is working correctly if:

- [x] Seed node starts without errors
- [x] GlobalView window opens
- [x] Players can connect from different terminals
- [x] Each player gets their LocalView window
- [x] Players appear in GlobalView
- [x] Mouse control works in LocalView
- [x] Players can eat food and grow
- [x] Closing a player window doesn't crash others
- [x] New players can join anytime
- [x] Game state is synchronized across all views

---

## 🎓 What's Happening Behind the Scenes

1. **Seed Node** (Terminal 1):
   - Starts ActorSystem on port 2551
   - Creates GameWorld singleton
   - Registers GameWorld with Akka Receptionist
   - Becomes cluster seed node

2. **Player Nodes** (Terminals 2+):
   - Start ActorSystem on unique port
   - Join cluster by connecting to seed (2551)
   - Query Receptionist for GameWorld
   - Create PlayerActor
   - Send `JoinGame` to GameWorld
   - Create LocalView with distributed manager

3. **Communication**:
   - All messages go through GameWorld singleton
   - GameWorld broadcasts state updates
   - DistributedGameStateManager subscribes to updates
   - Views repaint on state changes

4. **Fault Tolerance**:
   - If player disconnects, sends `LeaveGame`
   - GameWorld removes player from world
   - Other players continue unaffected
   - If seed crashes, cluster stops (single point of failure)

---

## 🚀 Next Steps

To make it more robust:

1. **Add Cluster Singleton Proxy**: Allow GameWorld to fail over to another node
2. **Add Cluster Sharding**: Distribute players across multiple nodes
3. **Add Persistence**: Save game state to recover from crashes
4. **Add HTTP API**: Allow web-based players to join
5. **Add Docker Compose**: Easy multi-container testing

---

## 📚 References

- [Akka Cluster Documentation](https://doc.akka.io/docs/akka/current/typed/cluster.html)
- [Akka Receptionist](https://doc.akka.io/docs/akka/current/typed/actor-discovery.html)
- [Akka Cluster Singleton](https://doc.akka.io/docs/akka/current/typed/cluster-singleton.html)

---

**Happy Distributed Gaming! 🎮🌐**
