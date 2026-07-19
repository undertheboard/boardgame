# Roblox Island Map Generator

`GenerateIslandMap.server.lua` procedurally builds an archipelago matching the
reference fantasy map using Roblox smooth terrain:

- **North-west** — a volcano island with a jagged basalt ring, an ash cone,
  glowing lava streaks, a lava-filled crater, rising smoke and an orange glow.
- **West** — a crescent island whose northern arm rises into a tall layered
  cliff ridge with a waterfall pouring down into a plunge pool, plus grassy
  lowlands dotted with ponds and forest tufts.
- **East** — a large green island with a grey (snow-flecked) mountain cluster,
  a meandering river cutting across it, forest mounds, a sunken crater grove
  with an earthen rim, and dry sandy flats.
- **Centre** — scattered sandy islets, a low green atoll, and an iceberg with
  jagged glacier spikes and small drifting floes.

All coastlines, heights and the river are perturbed with fractal noise
(`math.noise` fBm) so the terrain feels natural rather than geometric.

## Usage

1. Open a place in Roblox Studio.
2. Insert a **Script** into `ServerScriptService`.
3. Paste the contents of `GenerateIslandMap.server.lua` into it.
4. Press **Play** (or **Run**). Generation takes a few seconds and prints
   progress to the Output window.

Tweak `SEED` at the top of the script for a different-but-similar map, or
adjust the per-island centre/radius constants to rearrange the archipelago.
