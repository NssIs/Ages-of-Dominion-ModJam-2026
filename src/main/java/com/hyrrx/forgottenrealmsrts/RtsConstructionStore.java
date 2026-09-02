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

/** Persistent metadata for structures that are being assembled block by block. */
public final class RtsConstructionStore extends SavedData {
    public record Entry(long id, UUID owner, Identifier structure, BlockPos origin, Rotation rotation,
                        int totalBlocks, int placedBlocks, int workProgress) {
        public Entry {
            origin = origin == null ? BlockPos.ZERO : origin.immutable();
            totalBlocks = Math.max(1, totalBlocks);
            placedBlocks = Math.max(0, Math.min(totalBlocks, placedBlocks));
            workProgress = Math.max(0, workProgress);
        }

        private static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("id").forGetter(Entry::id),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Identifier.CODEC.fieldOf("structure").forGetter(Entry::structure),
                BlockPos.CODEC.fieldOf("origin").forGetter(Entry::origin),
                Rotation.CODEC.fieldOf("rotation").forGetter(Entry::rotation),
                Codec.INT.fieldOf("total_blocks").forGetter(Entry::totalBlocks),
                Codec.INT.fieldOf("placed_blocks").forGetter(Entry::placedBlocks),
                Codec.INT.fieldOf("work_progress").forGetter(Entry::workProgress)
        ).apply(instance, Entry::new));
    }

    public static final SavedDataType<RtsConstructionStore> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "rts_constructions"),
            RtsConstructionStore::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.LONG.fieldOf("next_id").forGetter(store -> store.nextId),
                    Entry.CODEC.listOf().fieldOf("entries")
                            .forGetter(store -> new ArrayList<>(store.entries.values()))
            ).apply(instance, RtsConstructionStore::new))
    );

    private long nextId;
    private final Map<Long, Entry> entries;

    public RtsConstructionStore() {
        this(1L, List.of());
    }

    private RtsConstructionStore(long nextId, List<Entry> savedEntries) {
        this.nextId = Math.max(1L, nextId);
        this.entries = new LinkedHashMap<>();
        for (Entry entry : savedEntries) {
            entries.put(entry.id(), entry);
            this.nextId = Math.max(this.nextId, entry.id() + 1L);
        }
    }

    public static RtsConstructionStore get(ServerLevel level) {
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

    public Entry add(UUID owner, Identifier structure, BlockPos origin, Rotation rotation,
                     int totalBlocks) {
        Entry entry = new Entry(nextId++, owner, structure, origin, rotation,
                totalBlocks, 0, 0);
        entries.put(entry.id(), entry);
        setDirty();
        return entry;
    }

    public void update(Entry entry) {
        if (!entries.containsKey(entry.id())) {
            throw new IllegalArgumentException("Unknown RTS construction id " + entry.id());
        }
        entries.put(entry.id(), entry);
        setDirty();
    }

    public boolean remove(long id, UUID owner) {
        Entry entry = entries.get(id);
        if (entry == null || !entry.owner().equals(owner)) {
            return false;
        }
        entries.remove(id);
        setDirty();
        return true;
    }

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
