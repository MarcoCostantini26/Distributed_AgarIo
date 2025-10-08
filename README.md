## Project Assignment: Distributed Agar.io Implementation

## Game Overview
Agar.io is a popular multiplayer online game where players control a circular cell in a 2D environment (see [Wikipedia](https://en.wikipedia.org/wiki/Agar.io)).
The primary goal is to grow larger by consuming two types of entities:
1. Food Pellets: Small, static, randomly scattered pellets that increase a player's mass when consumed.
2. Other Player Cells: Players can consume other, smaller player cells.

Starting from this current solution (which is a centralized version of Agar.io), 
 you will extend it to create a distributed version using an actor-based system (e.g., Akka).

## Requirements
- The game is always active: players can join at any time and immediately start playing.
- Players can be located on different nodes (from `akka cluster` perspective) and must be able to join or leave dynamically (*distributed player management*).
  - Each player should have their own `LocalView`
- When a player consumes food, that food must be removed for all players in the system (*distributed food management*).
- Every player must have a consistent view of the world, including the positions of all players and food (*consistent world view*).
  - No player should see another player or food that is not visible to others.
  - No two players should see the same food in different positions.
- Food is generated randomly and distributed across nodes, and new food must be visible to all players (*distributed food generation*).
- The game ends when a player reaches a specific mass (e.g., 1000 units), and this end condition must be checked and enforced in a distributed way for all players (*distributed game end condition*).

## Implementation Hints
Try to reuse as much of the existing code as possible, especially the game logic.
You should focus in how to generate the `GameStateManager` in a way that it can be distributed across nodes.
In this way, you can reuse the also existing Views (local and global).


## Final Considerations
Choose your Akka features thoughtfully, considering the trade-offs between consistency, scalability, and fault tolerance. 
For example, using a `Cluster Singleton` can simplify global state management (such as the authoritative game state or food generation),
but it introduces a single point of failure and may limit scalability. 

Justify each architectural choice by explaining how it impacts the system's reliability, responsiveness, and maintainability. 
Strive for a solution where actors collaborate to maintain a consistent world view without unnecessary centralization, 
leveraging Akka's distributed features to balance load and resilience across the cluster.
