--!strict
-- GenerateIslandMap.server.lua
-- Procedurally generates the island map from the reference art using Roblox smooth terrain.
-- Place this Script in ServerScriptService and run the game.
--
-- Layout (top-down, +X = east, +Z = south):
--   * North-west : volcano island (basalt ring, ash cone, glowing lava crater, smoke)
--   * West       : crescent island with a tall cliff ridge and a waterfall into ponds
--   * East       : large green island with a mountain cluster, a river, forest mounds,
--                  a sunken crater grove and sandy flats
--   * Centre     : scattered sandy islets, a low green atoll, and a spiky iceberg
--
-- Everything is driven by fractal noise so coastlines, hills and rivers feel natural
-- instead of geometric.

local Terrain = workspace.Terrain

--------------------------------------------------------------------------------
-- Tunables
--------------------------------------------------------------------------------
local SEED = 1337               -- change for a different-but-similar map
local VOXEL = 4                 -- Roblox terrain voxel size (do not change)
local SEA_LEVEL = 0             -- y of the ocean surface
local MAP_MIN = Vector3.new(-1100, -120, -1100)
local MAP_MAX = Vector3.new(1100, 400, 1100)

local rng = Random.new(SEED)

--------------------------------------------------------------------------------
-- Noise helpers
--------------------------------------------------------------------------------
-- Fractal Brownian motion built on math.noise: several octaves of noise summed
-- together. Low octaves give big soft shapes, high octaves add rough detail.
local function fbm(x: number, z: number, octaves: number, scale: number, seedOffset: number): number
	local total, amplitude, frequency, maxValue = 0, 1, 1 / scale, 0
	for _ = 1, octaves do
		total += math.noise(x * frequency + seedOffset, z * frequency - seedOffset, seedOffset * 0.37) * amplitude
		maxValue += amplitude
		amplitude *= 0.5
		frequency *= 2
	end
	return total / maxValue -- roughly -1 .. 1
end

-- Smoothstep for soft blends between regions.
local function smoothstep(edge0: number, edge1: number, x: number): number
	local t = math.clamp((x - edge0) / (edge1 - edge0), 0, 1)
	return t * t * (3 - 2 * t)
end

-- Distance from point to a segment (used for the river).
local function segmentDistance(px: number, pz: number, ax: number, az: number, bx: number, bz: number): number
	local abx, abz = bx - ax, bz - az
	local t = math.clamp(((px - ax) * abx + (pz - az) * abz) / (abx * abx + abz * abz), 0, 1)
	local cx, cz = ax + abx * t, az + abz * t
	return math.sqrt((px - cx) ^ 2 + (pz - cz) ^ 2)
end

--------------------------------------------------------------------------------
-- Island shape helpers
--------------------------------------------------------------------------------
-- A wobbly radial mask: 1 at the island centre fading to 0 at a noisy edge,
-- so no island is a perfect circle.
local function islandMask(x: number, z: number, cx: number, cz: number, radius: number, wobble: number, seedOffset: number): number
	local dx, dz = x - cx, z - cz
	local dist = math.sqrt(dx * dx + dz * dz)
	local angle = math.atan2(dz, dx)
	local edge = radius * (1 + wobble * fbm(math.cos(angle) * radius, math.sin(angle) * radius, 3, radius * 0.8, seedOffset))
	return 1 - smoothstep(edge * 0.55, edge, dist)
end

--------------------------------------------------------------------------------
-- 1) Volcano island (north-west)
--------------------------------------------------------------------------------
local VOLCANO_C = Vector2.new(-420, -520)
local VOLCANO_R = 260

local function volcanoSample(x: number, z: number): (number?, Enum.Material?)
	local mask = islandMask(x, z, VOLCANO_C.X, VOLCANO_C.Y, VOLCANO_R, 0.35, 11)
	if mask <= 0.02 then
		return nil, nil
	end

	local dx, dz = x - VOLCANO_C.X, z - VOLCANO_C.Y
	local dist = math.sqrt(dx * dx + dz * dz)

	-- Pale beach shelf around the whole island.
	local height = 6 + mask * 10 + fbm(x, z, 3, 90, 12) * 4
	local material = Enum.Material.Sandstone

	-- Jagged basalt ring around the cone (the dark boulder rim in the art).
	local ringNoise = fbm(x, z, 4, 40, 13)
	if dist > VOLCANO_R * 0.42 and dist < VOLCANO_R * 0.78 then
		local ringT = 1 - math.abs(dist - VOLCANO_R * 0.60) / (VOLCANO_R * 0.18)
		height += math.max(0, ringT) * (26 + ringNoise * 18)
		material = Enum.Material.Basalt
	end

	-- The cone itself: rises steeply toward the middle, then drops into a crater.
	local coneT = 1 - smoothstep(0, VOLCANO_R * 0.5, dist)
	if coneT > 0 then
		local cone = coneT * 150 * (1 + fbm(x, z, 4, 55, 14) * 0.25)
		local craterR = VOLCANO_R * 0.13
		if dist < craterR then
			-- carve the crater bowl and fill it with lava
			cone -= (1 - dist / craterR) * 70
			material = Enum.Material.CrackedLava
		elseif coneT > 0.15 then
			material = Enum.Material.Slate
			-- lava streaks running down the flanks
			local streak = math.abs(math.noise(math.atan2(dz, dx) * 3.5, 7.7, SEED))
			if streak > 0.34 and dist < VOLCANO_R * 0.45 then
				material = Enum.Material.CrackedLava
			end
		end
		height += math.max(cone, 0)
	end

	-- Green fringe at the very edge, like the art's little tree tufts.
	if mask < 0.35 and fbm(x, z, 3, 60, 15) > 0.05 then
		material = Enum.Material.LeafyGrass
	end

	return height * mask, material
end

--------------------------------------------------------------------------------
-- 2) Crescent cliff island with waterfall (west)
--------------------------------------------------------------------------------
local CRESCENT_C = Vector2.new(-780, 120)
local CRESCENT_R = 420

local function crescentSample(x: number, z: number): (number?, Enum.Material?)
	local dx, dz = x - CRESCENT_C.X, z - CRESCENT_C.Y
	local dist = math.sqrt(dx * dx + dz * dz)
	local angle = math.atan2(dz, dx)

	-- The island is a crescent: a band of land at a fixed radius, opening east.
	-- Angle 0 points east (the opening); land lives away from the opening.
	local opening = math.abs(angle) -- 0 at the opening, pi at the far west
	local along = smoothstep(0.55, 1.3, opening) -- 0 in the mouth, 1 on the arc
	if along <= 0.02 then
		return nil, nil
	end

	local bandR = CRESCENT_R * (0.55 + 0.05 * fbm(math.cos(angle) * 300, math.sin(angle) * 300, 3, 200, 21))
	local bandW = 120 * (0.7 + 0.5 * fbm(x, z, 3, 150, 22))
	local band = 1 - smoothstep(bandW * 0.4, bandW, math.abs(dist - bandR))
	if band <= 0.02 then
		return nil, nil
	end

	local height = 8 + band * 12 + fbm(x, z, 4, 70, 23) * 6
	local material = Enum.Material.Grass

	-- Tall layered cliff ridge on the northern arm.
	local ridgeT = smoothstep(1.6, 2.6, opening) * (if angle < 0 then 1 else 0)
	if ridgeT > 0 then
		height += ridgeT * band * (120 + fbm(x, z, 5, 60, 24) * 45)
		if ridgeT * band > 0.35 then
			material = Enum.Material.Sandstone -- layered brown cliff rock
		end
	end

	-- Forested tufts along the middle of the arm.
	if ridgeT < 0.4 and fbm(x, z, 4, 45, 25) > 0.12 then
		material = Enum.Material.LeafyGrass
	end
	-- Little rocky pebbles near the outer coast.
	if band < 0.4 and fbm(x, z, 3, 25, 26) > 0.3 then
		material = Enum.Material.Rock
	end

	return height * band * along, material
end

-- The waterfall: a water ribbon cut down the tall cliff face into a plunge pool.
local function crescentWaterfall()
	local topAngle = -2.35
	local topR = CRESCENT_R * 0.55
	local top = Vector3.new(
		CRESCENT_C.X + math.cos(topAngle) * topR,
		SEA_LEVEL + 120,
		CRESCENT_C.Y + math.sin(topAngle) * topR
	)
	local bottom = Vector3.new(top.X + 30, SEA_LEVEL + 6, top.Z + 130)

	local steps = 26
	for i = 0, steps do
		local t = i / steps
		local p = top:Lerp(bottom, t)
		-- narrow ribbon of water hugging the cliff, widening at the base
		local r = 7 + t * 9 + math.noise(i * 0.7, SEED, 3) * 2
		Terrain:FillBall(p, r, Enum.Material.Water)
	end
	-- Plunge pool and a short stream running toward the sea.
	Terrain:FillBall(bottom, 22, Enum.Material.Water)
	Terrain:FillBall(bottom + Vector3.new(25, -2, 55), 16, Enum.Material.Water)

	-- Small ponds dotted across the lowland like the art's blue specks.
	for _ = 1, 10 do
		local a = rng:NextNumber(0.7, 2.6)
		local r = CRESCENT_R * rng:NextNumber(0.45, 0.68)
		local px = CRESCENT_C.X + math.cos(a) * r
		local pz = CRESCENT_C.Y + math.sin(a) * r
		Terrain:FillBall(Vector3.new(px, SEA_LEVEL + 4, pz), rng:NextNumber(6, 13), Enum.Material.Water)
	end
end

--------------------------------------------------------------------------------
-- 3) Large green island (east): mountains, river, crater grove, forests
--------------------------------------------------------------------------------
local BIG_C = Vector2.new(560, 60)
local BIG_R = 460
local MOUNTAIN_C = Vector2.new(470, -300)
local CRATER_C = Vector2.new(720, 60)
-- River control points, snaking west-to-east across the island's north half.
local RIVER = {
	Vector2.new(120, -160),
	Vector2.new(330, -140),
	Vector2.new(520, -90),
	Vector2.new(720, -130),
	Vector2.new(980, -170),
}

local function riverDistance(x: number, z: number): number
	local best = math.huge
	for i = 1, #RIVER - 1 do
		local a, b = RIVER[i], RIVER[i + 1]
		best = math.min(best, segmentDistance(x, z, a.X, a.Y, b.X, b.Y))
	end
	-- wobble the banks so the river meanders naturally
	return best + fbm(x, z, 3, 120, 31) * 22
end

local function bigIslandSample(x: number, z: number): (number?, Enum.Material?)
	local mask = islandMask(x, z, BIG_C.X, BIG_C.Y, BIG_R, 0.4, 32)
	if mask <= 0.02 then
		return nil, nil
	end

	local height = 10 + mask * 18 + fbm(x, z, 4, 160, 33) * 10
	local material = Enum.Material.Grass

	-- Sandy shore ring.
	if mask < 0.22 then
		material = Enum.Material.Sand
	end

	-- Mountain cluster in the north with snow-flecked grey peaks.
	local mdx, mdz = x - MOUNTAIN_C.X, z - MOUNTAIN_C.Y
	local mdist = math.sqrt(mdx * mdx + mdz * mdz)
	local mT = 1 - smoothstep(40, 190, mdist)
	if mT > 0 then
		local peaks = mT * (170 + fbm(x, z, 5, 45, 34) * 70)
		height += math.max(peaks, 0)
		if peaks > 45 then
			material = Enum.Material.Rock
		end
		if peaks > 150 then
			material = Enum.Material.Snow
		end
	end

	-- Sunken crater grove in the east: raised earthen rim, leafy floor below.
	local cdx, cdz = x - CRATER_C.X, z - CRATER_C.Y
	local cdist = math.sqrt(cdx * cdx + cdz * cdz)
	local craterR = 110 * (1 + fbm(x, z, 3, 90, 35) * 0.15)
	if cdist < craterR then
		height -= (1 - cdist / craterR) * 26 -- bowl
		material = Enum.Material.LeafyGrass
	elseif cdist < craterR * 1.25 then
		height += 14 * (1 - (cdist - craterR) / (craterR * 0.25))
		material = Enum.Material.Ground -- earthen rim
	end

	-- Forest mounds: patches of leafy grass raised a little, like tree clumps.
	local forest = fbm(x, z, 4, 130, 36)
	if forest > 0.16 and material == Enum.Material.Grass then
		height += (forest - 0.16) * 40
		material = Enum.Material.LeafyGrass
	end

	-- Dry sandy flats in the south-west quarter (the pale patch in the art).
	if x < BIG_C.X - 60 and z > BIG_C.Y - 20 and fbm(x, z, 3, 140, 37) > -0.1 then
		if material == Enum.Material.Grass then
			material = Enum.Material.Sandstone
		end
	end

	-- Carve the river valley; the water itself is filled in afterwards.
	local rd = riverDistance(x, z)
	local riverW = 34
	if rd < riverW then
		local cut = 1 - rd / riverW
		height = math.min(height, 6 + (1 - cut) * 4) -- drop to just above sea level
		material = Enum.Material.Sand -- banks
	end

	return height * mask, material
end

-- Fill the river channel with water after the land is written.
local function bigIslandRiver()
	for i = 1, #RIVER - 1 do
		local a, b = RIVER[i], RIVER[i + 1]
		local steps = math.ceil((b - a).Magnitude / 14)
		for s = 0, steps do
			local t = s / steps
			local x = a.X + (b.X - a.X) * t
			local z = a.Y + (b.Y - a.Y) * t
			-- meander offset matching riverDistance's wobble
			local off = fbm(x, z, 3, 120, 31) * 10
			Terrain:FillBall(Vector3.new(x + off, SEA_LEVEL + 2, z + off), 20, Enum.Material.Water)
		end
	end
	-- Pale rocky shallows scattered near the river mouth (west side).
	for _ = 1, 14 do
		local px = rng:NextNumber(150, 420)
		local pz = rng:NextNumber(-60, 220)
		Terrain:FillBall(Vector3.new(px, SEA_LEVEL + 6, pz), rng:NextNumber(5, 11), Enum.Material.Limestone)
	end
end

--------------------------------------------------------------------------------
-- 4) Small central islets (sand bars and a low green atoll)
--------------------------------------------------------------------------------
type Islet = { cx: number, cz: number, r: number, mat: Enum.Material }
local ISLETS: { Islet } = {
	{ cx = -330, cz = 210, r = 42, mat = Enum.Material.Sand },
	{ cx = -210, cz = 260, r = 55, mat = Enum.Material.Sand },
	{ cx = -120, cz = 320, r = 26, mat = Enum.Material.Sand },
	{ cx = -160, cz = 560, r = 95, mat = Enum.Material.Grass }, -- the low layered green atoll
	{ cx = -60, cz = 610, r = 60, mat = Enum.Material.LeafyGrass },
}

local function isletSample(x: number, z: number): (number?, Enum.Material?)
	local bestH: number? = nil
	local bestM: Enum.Material? = nil
	for i, islet in ipairs(ISLETS) do
		local mask = islandMask(x, z, islet.cx, islet.cz, islet.r, 0.45, 41 + i)
		if mask > 0.05 then
			local h = (5 + mask * 7 + fbm(x, z, 3, 40, 42 + i) * 3) * mask
			if bestH == nil or h > bestH then
				bestH = h
				bestM = islet.mat
			end
		end
	end
	return bestH, bestM
end

--------------------------------------------------------------------------------
-- 5) Iceberg (south-centre): a low ice floe with jagged spikes
--------------------------------------------------------------------------------
local ICE_C = Vector2.new(120, 420)
local ICE_R = 110

local function icebergSample(x: number, z: number): (number?, Enum.Material?)
	local mask = islandMask(x, z, ICE_C.X, ICE_C.Y, ICE_R, 0.5, 51)
	if mask <= 0.05 then
		return nil, nil
	end
	local height = 4 + mask * 6 + fbm(x, z, 3, 35, 52) * 3
	return height * mask, Enum.Material.Glacier
end

local function icebergSpikes()
	-- Sharp shards of ice jutting out of the floe, like the art's crystal spikes.
	for _ = 1, 9 do
		local a = rng:NextNumber(0, math.pi * 2)
		local r = rng:NextNumber(0, ICE_R * 0.55)
		local base = Vector3.new(ICE_C.X + math.cos(a) * r, SEA_LEVEL + 2, ICE_C.Y + math.sin(a) * r)
		local h = rng:NextNumber(28, 70)
		local segments = 6
		for s = 0, segments do
			local t = s / segments
			local radius = (1 - t) * rng:NextNumber(8, 14) + 2
			Terrain:FillBall(base + Vector3.new(0, t * h, 0), radius, Enum.Material.Glacier)
		end
	end
	-- A couple of tiny drifting floes nearby.
	for _ = 1, 3 do
		local p = Vector3.new(
			ICE_C.X + rng:NextNumber(-180, -80),
			SEA_LEVEL + 1,
			ICE_C.Y + rng:NextNumber(20, 120)
		)
		Terrain:FillBlock(CFrame.new(p), Vector3.new(rng:NextNumber(14, 26), 6, rng:NextNumber(12, 22)), Enum.Material.Ice)
	end
end

--------------------------------------------------------------------------------
-- Terrain writing
--------------------------------------------------------------------------------
local SAMPLERS = { volcanoSample, crescentSample, bigIslandSample, isletSample, icebergSample }

local function generateLand()
	local minX = math.floor(MAP_MIN.X / VOXEL)
	local maxX = math.ceil(MAP_MAX.X / VOXEL)
	local minZ = math.floor(MAP_MIN.Z / VOXEL)
	local maxZ = math.ceil(MAP_MAX.Z / VOXEL)
	local floorY = SEA_LEVEL - 40 -- sea floor

	local column = 0
	for gx = minX, maxX do
		local x = gx * VOXEL
		for gz = minZ, maxZ do
			local z = gz * VOXEL

			local height: number? = nil
			local material: Enum.Material? = nil
			for _, sampler in ipairs(SAMPLERS) do
				local h, m = sampler(x, z)
				if h and (height == nil or h > height) then
					height, material = h, m
				end
			end

			if height and height > 1 and material then
				local top = SEA_LEVEL + height
				local base = floorY
				if top - base > 14 then
					-- rocky core with a 10-stud skin of the surface material
					Terrain:FillBlock(
						CFrame.new(Vector3.new(x, (top - 10 + base) / 2, z)),
						Vector3.new(VOXEL, (top - 10) - base, VOXEL),
						Enum.Material.Rock
					)
					Terrain:FillBlock(
						CFrame.new(Vector3.new(x, top - 5, z)),
						Vector3.new(VOXEL, 10, VOXEL),
						material
					)
				else
					Terrain:FillBlock(
						CFrame.new(Vector3.new(x, (top + base) / 2, z)),
						Vector3.new(VOXEL, top - base, VOXEL),
						material
					)
				end
			end
		end
		column += 1
		if column % 24 == 0 then
			task.wait() -- keep the server responsive on big maps
		end
	end
end

local function generateOcean()
	-- One shallow sea covering the whole map. The land pass runs first and
	-- Region3-aligned water fills blend around the coastlines naturally.
	local size = Vector3.new(MAP_MAX.X - MAP_MIN.X, 36, MAP_MAX.Z - MAP_MIN.Z)
	local center = Vector3.new(
		(MAP_MIN.X + MAP_MAX.X) / 2,
		SEA_LEVEL - size.Y / 2 + 2,
		(MAP_MIN.Z + MAP_MAX.Z) / 2
	)
	local region = Region3.new(center - size / 2, center + size / 2):ExpandToGrid(VOXEL)
	Terrain:ReplaceMaterial(region, VOXEL, Enum.Material.Air, Enum.Material.Water)
	-- Sea floor.
	Terrain:FillBlock(
		CFrame.new(center - Vector3.new(0, size.Y / 2 + 6, 0)),
		Vector3.new(size.X, 12, size.Z),
		Enum.Material.Sand
	)
end

--------------------------------------------------------------------------------
-- Volcano smoke + lava glow (non-terrain dressing)
--------------------------------------------------------------------------------
local function addVolcanoEffects()
	local anchor = Instance.new("Part")
	anchor.Name = "VolcanoSmokeAnchor"
	anchor.Anchored = true
	anchor.CanCollide = false
	anchor.Transparency = 1
	anchor.Size = Vector3.new(4, 4, 4)
	anchor.Position = Vector3.new(VOLCANO_C.X, SEA_LEVEL + 150, VOLCANO_C.Y)
	anchor.Parent = workspace

	local smoke = Instance.new("Smoke")
	smoke.Size = 40
	smoke.RiseVelocity = 8
	smoke.Opacity = 0.35
	smoke.Color = Color3.fromRGB(105, 95, 90)
	smoke.Parent = anchor

	local glow = Instance.new("PointLight")
	glow.Color = Color3.fromRGB(255, 120, 30)
	glow.Range = 60
	glow.Brightness = 3
	glow.Parent = anchor
end

--------------------------------------------------------------------------------
-- Run
--------------------------------------------------------------------------------
print("[IslandMap] clearing terrain...")
Terrain:Clear()

print("[IslandMap] raising islands...")
generateLand()

print("[IslandMap] flooding the sea...")
generateOcean()

print("[IslandMap] carving rivers and waterfalls...")
bigIslandRiver()
crescentWaterfall()

print("[IslandMap] shaping the iceberg...")
icebergSpikes()

print("[IslandMap] final touches...")
addVolcanoEffects()

print("[IslandMap] done! Explore the archipelago.")
