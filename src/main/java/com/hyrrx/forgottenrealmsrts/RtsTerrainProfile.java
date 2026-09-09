package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Persistent marker for the terrain rules used by Ages of Dominion.
 *
 * <p>Version 2 deliberately contains no terrain-rewrite operation. Removing the old generator
 * injection makes vanilla generation authoritative for both old worlds' future chunks and new
 * worlds. The stored basin is only a deterministic, natural starting-location hint; selecting it
 * never sets a block or rebuilds an already-generated chunk.
 */
public final class RtsTerrainProfile extends SavedData {
    public static final int CURRENT_PROFILE_VERSION = 2;

    private static final Identifier ID = Identifier.fromNamespaceAndPath(
            ForgottenRealmsRTS.MOD_ID, "rts_terrain_profile");
    private static final int[][] CANDIDATE_OFFSETS = {
            {0, 0}, {8, 0}, {-8, 0}, {0, 8}, {0, -8},
            {16, 0}, {-16, 0}, {0, 16}, {0, -16},
            {8, 8}, {8, -8}, {-8, 8}, {-8, -8},
            {24, 0}, {-24, 0}, {0, 24}, {0, -24},
            {16, 8}, {16, -8}, {-16, 8}, {-16, -8},
            {8, 16}, {8, -16}, {-8, 16}, {-8, -16}
    };
    private static final int SAMPLE_RADIUS = 8;
    private static final int SAMPLE_STEP = 8;

    private static final Codec<RtsTerrainProfile> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("profile_version", 0)
                            .forGetter(profile -> profile.profileVersion),
                    Codec.INT.optionalFieldOf("basin_x", 0)
                            .forGetter(profile -> profile.basinX),
                    Codec.INT.optionalFieldOf("basin_y", 0)
                            .forGetter(profile -> profile.basinY),
                    Codec.INT.optionalFieldOf("basin_z", 0)
                            .forGetter(profile -> profile.basinZ),
                    Codec.BOOL.optionalFieldOf("basin_selected", false)
                            .forGetter(profile -> profile.basinSelected)
            ).apply(instance, RtsTerrainProfile::new));

    public static final SavedDataType<RtsTerrainProfile> TYPE = new SavedDataType<>(
            ID, RtsTerrainProfile::new, CODEC);

    private int profileVersion;
    private int basinX;
    private int basinY;
    private int basinZ;
    private boolean basinSelected;

    public RtsTerrainProfile() {
        this(0, 0, 0, 0, false);
    }

    private RtsTerrainProfile(int profileVersion, int basinX, int basinY, int basinZ,
                              boolean basinSelected) {
        this.profileVersion = Math.max(0, profileVersion);
        this.basinX = basinX;
        this.basinY = basinY;
        this.basinZ = basinZ;
        this.basinSelected = basinSelected;
    }

    /** Loads the marker and upgrades it in memory without touching chunk blocks. */
    public static RtsTerrainProfile get(ServerLevel level) {
        RtsTerrainProfile profile = level.getDataStorage().computeIfAbsent(TYPE);
        if (profile.profileVersion != CURRENT_PROFILE_VERSION || !profile.basinSelected) {
            profile.migrate(level);
        }
        return profile;
    }

    public int profileVersion() {
        return profileVersion;
    }

    public BlockPos startingBasin() {
        return new BlockPos(basinX, basinY, basinZ);
    }

    /** Version 2 means the corrected vanilla generator is active for every future chunk. */
    public boolean usesNaturalTerrain() {
        return profileVersion >= CURRENT_PROFILE_VERSION;
    }

    private void migrate(ServerLevel level) {
        if (!basinSelected) {
            BlockPos basin = chooseBasin(level);
            basinX = basin.getX();
            basinY = basin.getY();
            basinZ = basin.getZ();
            basinSelected = true;
        }
        profileVersion = CURRENT_PROFILE_VERSION;
        setDirty();
    }

    /**
     * Chooses a seed-stable low-relief candidate from natural terrain close to world spawn. The
     * bounded sample area is loaded before scoring because ServerStartedEvent fires before vanilla
     * finishes its normal spawn preparation. Chunk loading here only asks vanilla to generate the
     * terrain; this profile still never edits a block.
     */
    private static BlockPos chooseBasin(ServerLevel level) {
        BlockPos spawn = level.getRespawnData().pos();
        long seed = mix64(level.getSeed());
        int start = Math.floorMod((int) (seed ^ (seed >>> 32)), CANDIDATE_OFFSETS.length);
        BasinCandidate best = null;

        for (int index = 0; index < CANDIDATE_OFFSETS.length; index++) {
            int[] offset = CANDIDATE_OFFSETS[(start + index) % CANDIDATE_OFFSETS.length];
            int x = spawn.getX() + offset[0];
            int z = spawn.getZ() + offset[1];
            BasinCandidate candidate = scoreCandidate(level, spawn, x, z);
            if (candidate != null && (best == null || candidate.score() < best.score())) {
                best = candidate;
            }
        }

        if (best != null) {
            return new BlockPos(best.x(), best.y(), best.z());
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                spawn.getX(), spawn.getZ());
        return new BlockPos(spawn.getX(), y, spawn.getZ());
    }

    private static BasinCandidate scoreCandidate(ServerLevel level, BlockPos spawn,
                                                 int centerX, int centerZ) {
        int minHeight = Integer.MAX_VALUE;
        int maxHeight = Integer.MIN_VALUE;
        int totalHeight = 0;
        int waterColumns = 0;
        int samples = 0;

        for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
            for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
                int x = centerX + dx;
                int z = centerZ + dz;
                level.getChunk(x >> 4, z >> 4);
                int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (height <= level.getMinY()) {
                    return null;
                }
                BlockPos surface = new BlockPos(x, height - 1, z);
                if (!level.getFluidState(surface).isEmpty()) {
                    waterColumns++;
                }
                minHeight = Math.min(minHeight, height);
                maxHeight = Math.max(maxHeight, height);
                totalHeight += height;
                samples++;
            }
        }

        int relief = maxHeight - minHeight;
        int average = totalHeight / Math.max(1, samples);
        int centerHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                centerX, centerZ);
        if (centerHeight <= level.getMinY()) {
            return null;
        }
        int dx = centerX - spawn.getX();
        int dz = centerZ - spawn.getZ();
        int distanceSquared = dx * dx + dz * dz;
        long score = waterColumns * 10_000L + relief * 100L
                + average * 2L + distanceSquared;
        return new BasinCandidate(centerX, centerHeight, centerZ, score);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private record BasinCandidate(int x, int y, int z, long score) {
    }
}
