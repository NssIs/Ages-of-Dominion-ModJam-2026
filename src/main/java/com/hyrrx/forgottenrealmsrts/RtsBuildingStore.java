package com.hyrrx.forgottenrealmsrts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Persistent metadata for buildings placed through the RTS building system. */
public final class RtsBuildingStore extends SavedData {
    public record Entry(long id, UUID owner, Identifier structure, BlockPos origin, Rotation rotation,
                        boolean normalizedOrigin, int health, int maxHealth) {
        /** New entries use the normalized footprint-corner convention. */
        public Entry(long id, UUID owner, Identifier structure, BlockPos origin, Rotation rotation) {
            this(id, owner, structure, origin, rotation, true, 0, 0);
        }

        /** Compatibility constructor for entries written after origin normalization but before health. */
        public Entry(long id, UUID owner, Identifier structure, BlockPos origin, Rotation rotation,
                     boolean normalizedOrigin) {
            this(id, owner, structure, origin, rotation, normalizedOrigin, 0, 0);
        }

        public Entry {
            origin = origin == null ? BlockPos.ZERO : origin.immutable();
            rotation = rotation == null ? Rotation.NONE : rotation;
            maxHealth = Math.max(0, maxHealth);
            health = Math.max(0, Math.min(maxHealth, health));
        }

        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(Entry::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Identifier.CODEC.fieldOf("structure").forGetter(Entry::structure),
                BlockPos.CODEC.fieldOf("origin").forGetter(Entry::origin),
                Rotation.CODEC.fieldOf("rotation").forGetter(Entry::rotation),
                Codec.BOOL.optionalFieldOf("normalized_origin", false)
                        .forGetter(Entry::normalizedOrigin),
                Codec.INT.optionalFieldOf("health", 0).forGetter(Entry::health),
                Codec.INT.optionalFieldOf("max_health", 0).forGetter(Entry::maxHealth)
        ).apply(instance, Entry::new));
    }

    public static final SavedDataType<RtsBuildingStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "rts_buildings"),
            RtsBuildingStore::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("next_id").forGetter(store -> store.nextId),
                    Entry.CODEC.listOf().fieldOf("entries")
                            .forGetter(store -> new ArrayList<>(store.entries.values()))
            ).apply(instance, RtsBuildingStore::new))
    );

    private long nextId;
    private final Map<Long, Entry> entries;

    public RtsBuildingStore() {
        this(1L, List.of());
    }

    private RtsBuildingStore(long nextId, List<Entry> savedEntries) {
        this.nextId = Math.max(1L, nextId);
        this.entries = new LinkedHashMap<>();
        for (Entry entry : savedEntries) {
            entries.put(entry.id(), entry);
            this.nextId = Math.max(this.nextId, entry.id() + 1L);
        }
    }

    public static RtsBuildingStore get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public List<Entry> entries() {
        return List.copyOf(entries.values());
    }

    public Optional<Entry> find(long id, UUID owner) {
        Entry entry = entries.get(id);
        return entry != null && entry.owner().equals(owner) ? Optional.of(entry) : Optional.empty();
    }

    public Optional<Entry> findAt(UUID owner, Predicate<Entry> containsPosition) {
        return entries.values().stream()
                .filter(entry -> entry.owner().equals(owner))
                .filter(containsPosition)
                .findFirst();
    }

    public Entry add(UUID owner, Identifier structure, BlockPos origin, Rotation rotation) {
        Entry entry = new Entry(nextId++, owner, structure, origin, rotation);
        entries.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    /** Adds a new building at full server-authoritative durability. */
    public Entry add(ServerLevel level, UUID owner, Identifier structure, BlockPos origin,
                      Rotation rotation) {
        return add(level, owner, structure, origin, rotation,
                RtsBuildingDurability.solidBlockCount(level, structure));
    }

    /** Adds a one-block linear entry with the requested physical block count. */
    public Entry add(ServerLevel level, UUID owner, Identifier structure, BlockPos origin,
                      Rotation rotation, int solidBlockCount) {
        int maxHealth = RtsBuildingDurability.maxHealth(level, structure, solidBlockCount);
        Entry entry = new Entry(nextId++, owner, structure, origin, rotation, true,
                maxHealth, maxHealth);
        entries.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public void update(Entry entry) {
        if (!entries.containsKey(entry.id())) {
            throw new IllegalArgumentException("Unknown RTS building id " + entry.id());
        }
        entries.put(entry.id(), entry);
        setDirty();
    }

    /** Drops one owned entry, e.g. after a demolish. Returns whether an entry was actually removed. */
    public boolean remove(long id, UUID owner) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.owner().equals(owner)) {
            return false;
        }
        entries.remove(id);
        setDirty();
        return true;
    }

    /** Removes every tracked structure belonging to one player and returns the number removed. */
    public int removeOwner(UUID owner) {
        int before = entries.size();
        entries.values().removeIf(entry -> entry.owner().equals(owner));
        int removed = before - entries.size();
        if (removed > 0) {
            setDirty();
        }
        return removed;
    }
}
