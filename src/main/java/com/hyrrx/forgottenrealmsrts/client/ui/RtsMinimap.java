package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Arrays;

/**
 * A real minimap: surface terrain sampled out of the chunks the client actually has, with
 * everything it does not have drawn as fog.
 *
 * <p><strong>The fog test is the whole trick, and it is free.</strong> The client only holds the
 * chunks the server has sent it, so
 * {@link ClientChunkCache#getChunk(int, int, ChunkStatus, boolean)} returning {@code null} <em>is</em>
 * "this chunk is not rendered". No render-distance arithmetic, no {@code LevelRenderer}
 * introspection — lower the render distance and the fog closes in on its own.
 *
 * <p><strong>Moving scrolls the map; it does not rebuild it.</strong> The first version cleared the
 * whole image every time the camera crossed a chunk boundary and let the sweep refill it, which
 * meant the map blanked and took seconds to come back every sixteen blocks. Now the pixels that are
 * still on screen are shifted to their new position and only the newly exposed band is unknown, so
 * walking around leaves the map intact and fills in at the edge you are moving toward.
 *
 * <p><strong>Sampling is spread over ticks and prioritised.</strong> Cells never sampled at the
 * current origin are done first (that is the band the scroll just exposed), and once none are left
 * the cursor sweeps cyclically to pick up terrain changes and chunks that have since loaded.
 */
public final class RtsMinimap {
    /** Texture edge in pixels. Square; the panel crops it to its own aspect ratio when drawing. */
    private static final int SIZE = 256;
    /**
     * One block per pixel, so the window covers 256x256 blocks — 16 chunks.
     *
     * <p>This was 2 blocks per pixel over a 32-chunk window, and that was simply too big to ever
     * fill: a render distance is typically 8 to 16 chunks, so the outer half of the map was
     * permanently fog no matter what the sampler did. Sizing the window to roughly what a client
     * actually holds means the map is mostly map, and the extra resolution comes free.
     */
    private static final int BLOCKS_PER_PIXEL = 1;
    private static final int WINDOW_BLOCKS = SIZE * BLOCKS_PER_PIXEL;
    private static final int WINDOW_CHUNKS = WINDOW_BLOCKS / 16;
    /** Pixels down one edge of a chunk cell: 16 blocks / 2 blocks per pixel. */
    private static final int PIXELS_PER_CHUNK = 16 / BLOCKS_PER_PIXEL;
    private static final int CELL_COUNT = WINDOW_CHUNKS * WINDOW_CHUNKS;

    /**
     * Chunk cells sampled per client tick. A cell is now 16x16 pixels rather than 8x8, so each one
     * costs four times what it used to; eight of them is ~2,000 column walks a tick and a full
     * 256-cell sweep in about a second and a half.
     */
    private static final int CHUNKS_PER_TICK = 8;

    /**
     * Unexplored. A cool dark grey-blue rather than a brown: the first version used a dark brown
     * here and every column the sampler failed on came out looking like a field of dirt instead of
     * looking like missing data. Fog must not be mistakable for terrain.
     */
    private static final int COLOR_FOG = 0xFF14161C;
    /** Pixels this deep from an unloaded neighbour fade toward fog, so the frontier is soft rather
     *  than a chunk-aligned staircase. */
    private static final int FOG_FEATHER = 3;
    /** Sentinel in {@link #heights} for "never sampled", so shading does not compare against a zero
     *  that would fake a cliff at every unsampled edge. */
    private static final int NO_HEIGHT = Integer.MIN_VALUE;

    private static final int COLOR_MARKER = 0xFFF4E9C8;
    private static final int COLOR_MARKER_EDGE = 0xFF241F18;

    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(ForgottenRealmsRTS.MOD_ID, "dynamic/minimap");

    private static DynamicTexture texture;
    private static NativeImage image;

    /** Our own copy of the image, so scrolling is an array shift rather than a read-back. */
    private static int[] buffer;
    /** Surface height per pixel, kept so shading can compare against the column to the north even
     *  when that column lives in a different chunk cell sampled at a different time. */
    private static int[] heights;
    /** Per chunk cell: has it been sampled since the window last moved? */
    private static boolean[] sampled;

    /** World block coordinate of the window's top-left corner, always chunk-aligned. */
    private static int originBlockX;
    private static int originBlockZ;
    private static boolean hasOrigin;
    /** Background refresh cursor, used only once nothing is unsampled. */
    private static int cursor;

    /** Row range changed since the last upload; inverted (min &gt; max) means "nothing to push". */
    private static int dirtyMinY = SIZE;
    private static int dirtyMaxY = -1;

    /** Scratch: the surface height of the column {@link #columnColor} last looked at. Single
     *  threaded (client tick only), and avoids allocating a holder per pixel. */
    private static int lastSampleHeight;

    private RtsMinimap() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsMinimap::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null || !RtsMode.isActive(minecraft.player)) {
            return;
        }

        ensureTexture(minecraft);
        recentre(minecraft.gameRenderer.getMainCamera().position());

        ClientChunkCache chunkSource = level.getChunkSource();
        int budget = CHUNKS_PER_TICK;
        // Newly exposed cells first: those are the edge the player is walking toward, and filling
        // them late is what a "the map is rebuilding" stutter actually looks like.
        for (int cell = 0; cell < CELL_COUNT && budget > 0; cell++) {
            if (!sampled[cell]) {
                sampleChunkCell(level, chunkSource, cell);
                budget--;
            }
        }
        // Whatever budget is left goes on the cyclic refresh, so chunks that load later and terrain
        // that changes are picked up without any dirty tracking.
        for (; budget > 0; budget--) {
            sampleChunkCell(level, chunkSource, cursor);
            cursor = (cursor + 1) % CELL_COUNT;
        }

        uploadBuffer();
    }

    private static void ensureTexture(Minecraft minecraft) {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(TEXTURE_ID::toString, SIZE, SIZE, false);
        image = texture.getPixels();
        buffer = new int[SIZE * SIZE];
        heights = new int[SIZE * SIZE];
        sampled = new boolean[CELL_COUNT];
        Arrays.fill(buffer, COLOR_FOG);
        Arrays.fill(heights, NO_HEIGHT);
        markDirtyRows(0, SIZE - 1);
        minecraft.getTextureManager().register(TEXTURE_ID, texture);
    }

    /**
     * Keeps the window centred on the camera, snapped to chunk boundaries, by <em>scrolling</em>
     * what is already known rather than discarding it.
     *
     * <p>Snapping matters: a pixel then always covers the same two blocks for as long as the window
     * does not move, so any cell can be repainted independently and a move is a whole number of
     * pixels.
     */
    private static void recentre(Vec3 camera) {
        int chunkX = Math.floorDiv((int) Math.floor(camera.x), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(camera.z), 16);
        int newOriginX = (chunkX - WINDOW_CHUNKS / 2) * 16;
        int newOriginZ = (chunkZ - WINDOW_CHUNKS / 2) * 16;

        if (hasOrigin && newOriginX == originBlockX && newOriginZ == originBlockZ) {
            return;
        }

        if (!hasOrigin) {
            hasOrigin = true;
            originBlockX = newOriginX;
            originBlockZ = newOriginZ;
            Arrays.fill(sampled, false);
            return;
        }

        int shiftPixelsX = (newOriginX - originBlockX) / BLOCKS_PER_PIXEL;
        int shiftPixelsZ = (newOriginZ - originBlockZ) / BLOCKS_PER_PIXEL;
        scroll(shiftPixelsX, shiftPixelsZ);

        int shiftCellsX = (newOriginX - originBlockX) / 16;
        int shiftCellsZ = (newOriginZ - originBlockZ) / 16;
        scrollCells(shiftCellsX, shiftCellsZ);

        originBlockX = newOriginX;
        originBlockZ = newOriginZ;
    }

    /** Shifts the pixel buffer and its heights by whole pixels; whatever moves in from outside the
     *  old window becomes fog and unknown height. */
    private static void scroll(int shiftX, int shiftZ) {
        int[] newBuffer = new int[buffer.length];
        int[] newHeights = new int[heights.length];
        Arrays.fill(newBuffer, COLOR_FOG);
        Arrays.fill(newHeights, NO_HEIGHT);

        for (int y = 0; y < SIZE; y++) {
            int sourceY = y + shiftZ;
            if (sourceY < 0 || sourceY >= SIZE) {
                continue;
            }
            for (int x = 0; x < SIZE; x++) {
                int sourceX = x + shiftX;
                if (sourceX < 0 || sourceX >= SIZE) {
                    continue;
                }
                newBuffer[x + y * SIZE] = buffer[sourceX + sourceY * SIZE];
                newHeights[x + y * SIZE] = heights[sourceX + sourceY * SIZE];
            }
        }

        buffer = newBuffer;
        heights = newHeights;
        // A scroll moves every row, so all of them have to be pushed.
        markDirtyRows(0, SIZE - 1);
    }

    private static void scrollCells(int shiftX, int shiftZ) {
        boolean[] next = new boolean[CELL_COUNT];
        for (int z = 0; z < WINDOW_CHUNKS; z++) {
            int sourceZ = z + shiftZ;
            if (sourceZ < 0 || sourceZ >= WINDOW_CHUNKS) {
                continue;
            }
            for (int x = 0; x < WINDOW_CHUNKS; x++) {
                int sourceX = x + shiftX;
                if (sourceX < 0 || sourceX >= WINDOW_CHUNKS) {
                    continue;
                }
                next[x + z * WINDOW_CHUNKS] = sampled[sourceX + sourceZ * WINDOW_CHUNKS];
            }
        }
        sampled = next;
    }

    /**
     * Pushes only the rows touched since the last upload.
     *
     * <p>A full push is 65,536 {@code setPixel} calls; at twenty ticks a second that is over a
     * million a second to redraw a map that changes by a couple of chunk cells. Tracking the row
     * range instead means a quiet tick costs nothing and a busy one costs a few hundred rows.
     */
    private static void uploadBuffer() {
        if (dirtyMinY > dirtyMaxY) {
            return;
        }
        for (int y = dirtyMinY; y <= dirtyMaxY; y++) {
            int rowStart = y * SIZE;
            for (int x = 0; x < SIZE; x++) {
                image.setPixel(x, y, buffer[rowStart + x]);
            }
        }
        texture.upload();
        dirtyMinY = SIZE;
        dirtyMaxY = -1;
    }

    private static void markDirtyRows(int fromY, int toY) {
        dirtyMinY = Math.min(dirtyMinY, Math.max(0, fromY));
        dirtyMaxY = Math.max(dirtyMaxY, Math.min(SIZE - 1, toY));
    }

    /** Called when the player changes dimension or leaves the world. */
    public static void invalidate() {
        hasOrigin = false;
        if (buffer != null) {
            Arrays.fill(buffer, COLOR_FOG);
            Arrays.fill(heights, NO_HEIGHT);
            Arrays.fill(sampled, false);
            markDirtyRows(0, SIZE - 1);
        }
    }

    private static void sampleChunkCell(ClientLevel level, ClientChunkCache chunkSource, int cell) {
        int cellX = cell % WINDOW_CHUNKS;
        int cellZ = cell / WINDOW_CHUNKS;
        int chunkX = Math.floorDiv(originBlockX, 16) + cellX;
        int chunkZ = Math.floorDiv(originBlockZ, 16) + cellZ;

        int pixelX0 = cellX * PIXELS_PER_CHUNK;
        int pixelZ0 = cellZ * PIXELS_PER_CHUNK;

        LevelChunk chunk = chunkSource.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
        if (chunk == null) {
            for (int localZ = 0; localZ < PIXELS_PER_CHUNK; localZ++) {
                for (int localX = 0; localX < PIXELS_PER_CHUNK; localX++) {
                    int index = (pixelX0 + localX) + (pixelZ0 + localZ) * SIZE;
                    buffer[index] = COLOR_FOG;
                    heights[index] = NO_HEIGHT;
                }
            }
            sampled[cell] = true;
            markDirtyRows(pixelZ0, pixelZ0 + PIXELS_PER_CHUNK - 1);
            // Marked sampled even though there was nothing to sample. It is tempting to leave an
            // absent chunk unsampled so it gets retried immediately — that is exactly what this
            // code did at first, and it blanked the entire map: the window is 32x32 chunks but a
            // render distance is typically 8-16, so the corner cells are *permanently* absent, and
            // the priority pass below restarts at cell 0 every tick and spends its whole budget on
            // those same corners. The loaded chunks in the middle were never reached at all.
            // Chunks that arrive later are picked up by the cyclic refresh instead, which is what
            // that sweep is for.
            return;
        }

        boolean openWest = chunkSource.getChunk(chunkX - 1, chunkZ, ChunkStatus.FULL, false) == null;
        boolean openEast = chunkSource.getChunk(chunkX + 1, chunkZ, ChunkStatus.FULL, false) == null;
        boolean openNorth = chunkSource.getChunk(chunkX, chunkZ - 1, ChunkStatus.FULL, false) == null;
        boolean openSouth = chunkSource.getChunk(chunkX, chunkZ + 1, ChunkStatus.FULL, false) == null;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int localZ = 0; localZ < PIXELS_PER_CHUNK; localZ++) {
            for (int localX = 0; localX < PIXELS_PER_CHUNK; localX++) {
                int blockX = chunkX * 16 + localX * BLOCKS_PER_PIXEL;
                int blockZ = chunkZ * 16 + localZ * BLOCKS_PER_PIXEL;
                int index = (pixelX0 + localX) + (pixelZ0 + localZ) * SIZE;

                MapColor mapColor = columnColor(level, chunk, pos, blockX, blockZ);
                int height = lastSampleHeight;

                int argb;
                if (mapColor == MapColor.NONE) {
                    argb = COLOR_FOG;
                    height = NO_HEIGHT;
                } else {
                    // Shade against the pixel to the north — vanilla's own map shading, and the
                    // reason a flat 2D map reads as terrain with hills in it.
                    int northIndex = index - SIZE;
                    int northHeight = northIndex >= 0 ? heights[northIndex] : NO_HEIGHT;
                    int delta = northHeight == NO_HEIGHT ? 0 : height - northHeight;
                    argb = mapColor.calculateARGBColor(brightnessFor(delta));
                }

                int fade = edgeFade(localX, localZ, openWest, openEast, openNorth, openSouth);
                if (fade > 0) {
                    argb = blend(argb, COLOR_FOG, fade / (float) FOG_FEATHER);
                }

                buffer[index] = argb;
                heights[index] = height;
            }
        }

        markDirtyRows(pixelZ0, pixelZ0 + PIXELS_PER_CHUNK - 1);
        sampled[cell] = true;
    }

    /**
     * The map colour of one column's surface, following vanilla's own walk
     * ({@code MapItem.update}): start at the surface heightmap and step down while the block has no
     * map colour, so plants, snow layers and other decoration resolve to the ground beneath them
     * instead of to nothing.
     *
     * <p>Reading a single block at {@code height - 1} — which is what the first version did — is
     * what made large areas come out as flat fog: any column whose top block had
     * {@link MapColor#NONE} produced no colour at all, and with a brown fog colour that read as a
     * field of dirt.
     *
     * <p>Sets {@link #lastSampleHeight} to the y the colour was found at.
     */
    private static MapColor columnColor(ClientLevel level, LevelChunk chunk,
                                        BlockPos.MutableBlockPos pos, int blockX, int blockZ) {
        int minY = level.getMinY();
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15);
        // A heightmap the client was never sent reports the bottom of the world. Scanning from the
        // build limit is slow, but it is correct, and it only happens when the fast path is broken.
        if (y <= minY) {
            y = level.getMaxY();
        }

        while (y > minY) {
            pos.set(blockX, y, blockZ);
            BlockState state = chunk.getBlockState(pos);
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != MapColor.NONE) {
                lastSampleHeight = y;
                return mapColor;
            }
            y--;
        }

        lastSampleHeight = NO_HEIGHT;
        return MapColor.NONE;
    }

    /** Vanilla's four-step map shading, keyed on the rise or drop to the north. */
    private static MapColor.Brightness brightnessFor(int delta) {
        if (delta > 1) {
            return MapColor.Brightness.HIGH;
        }
        if (delta < -1) {
            return MapColor.Brightness.LOW;
        }
        return MapColor.Brightness.NORMAL;
    }

    private static int edgeFade(int localX, int localZ,
                                boolean openWest, boolean openEast,
                                boolean openNorth, boolean openSouth) {
        int fade = 0;
        if (openWest) {
            fade = Math.max(fade, FOG_FEATHER - localX);
        }
        if (openEast) {
            fade = Math.max(fade, FOG_FEATHER - (PIXELS_PER_CHUNK - 1 - localX));
        }
        if (openNorth) {
            fade = Math.max(fade, FOG_FEATHER - localZ);
        }
        if (openSouth) {
            fade = Math.max(fade, FOG_FEATHER - (PIXELS_PER_CHUNK - 1 - localZ));
        }
        return Math.clamp(fade, 0, FOG_FEATHER);
    }

    /**
     * Inverse of the marker projection: the world (x, z) block under a click on the map, or
     * {@code null} if the map is not ready or the click fell outside it. The Y is the caller's to
     * resolve — the map is a flat top-down plate.
     */
    public static int[] screenToWorld(int clickX, int clickY, int mapX, int mapY, int mapW, int mapH) {
        if (texture == null || !hasOrigin || mapW <= 0 || mapH <= 0) {
            return null;
        }
        if (clickX < mapX || clickX >= mapX + mapW || clickY < mapY || clickY >= mapY + mapH) {
            return null;
        }
        int sourceHeight = Math.min(SIZE, Math.max(1, SIZE * mapH / mapW));
        int sourceTop = (SIZE - sourceHeight) / 2;
        float texelX = (clickX - mapX) * (float) SIZE / mapW;
        float texelZ = sourceTop + (clickY - mapY) * (float) sourceHeight / mapH;
        return new int[]{
                originBlockX + Math.round(texelX * BLOCKS_PER_PIXEL),
                originBlockZ + Math.round(texelZ * BLOCKS_PER_PIXEL)
        };
    }

    private static int blend(int argb, int towardArgb, float amount) {
        float keep = 1.0F - amount;
        int r = (int) (((argb >> 16) & 0xFF) * keep + ((towardArgb >> 16) & 0xFF) * amount);
        int g = (int) (((argb >> 8) & 0xFF) * keep + ((towardArgb >> 8) & 0xFF) * amount);
        int b = (int) ((argb & 0xFF) * keep + (towardArgb & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Draws the map into the given rectangle, then the camera marker on top.
     *
     * <p>The texture is square and the panel's slot is not, so the source is <strong>centre-cropped
     * </strong> rather than stretched — a squashed map would misreport distances, which is the one
     * thing a minimap must not do.
     */
    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (texture == null) {
            return;
        }

        int sourceHeight = Math.min(SIZE, Math.max(1, SIZE * height / width));
        int sourceTop = (SIZE - sourceHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, x, y, 0.0F, sourceTop,
                width, height, SIZE, sourceHeight, SIZE, SIZE);

        drawEntityMarkers(graphics, x, y, width, height, sourceTop, sourceHeight);
        drawMarker(graphics, x, y, width, height, sourceTop, sourceHeight);
    }

    /**
     * Tactical dots: raiders red, guardians gold, villagers green — so the minimap reads the battle
     * at a glance rather than being terrain alone.
     */
    private static void drawEntityMarkers(GuiGraphicsExtractor graphics, int x, int y,
                                          int width, int height, int sourceTop, int sourceHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !hasOrigin) {
            return;
        }
        for (net.minecraft.world.entity.Entity entity : minecraft.level.entitiesForRendering()) {
            int color;
            if (entity instanceof com.hyrrx.forgottenrealmsrts.RtsVillagerEntity) {
                color = 0xFF4CC24C;
            } else if (entity instanceof net.minecraft.world.entity.animal.golem.IronGolem golem
                    && golem.isPlayerCreated()) {
                color = 0xFFE8C874;
            } else if (com.hyrrx.forgottenrealmsrts.RtsEntities.isAlliedCombatUnit(entity)) {
                color = 0xFF6BA7FF;
            } else if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                color = 0xFFCC3333;
            } else {
                continue;
            }
            float texelX = (float) (entity.getX() - originBlockX) / BLOCKS_PER_PIXEL;
            float texelZ = (float) (entity.getZ() - originBlockZ) / BLOCKS_PER_PIXEL;
            if (texelX < 0 || texelX > SIZE || texelZ < sourceTop || texelZ > sourceTop + sourceHeight) {
                continue;
            }
            int markerX = x + Math.round(texelX * width / SIZE);
            int markerY = y + Math.round((texelZ - sourceTop) * height / sourceHeight);
            graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, color);
        }
    }

    /**
     * A single marker at the camera's position.
     *
     * <p>There was a facing indicator here — a run of dots stepping along the camera's yaw. It read
     * as a tail hanging off the player rather than as a view cone, and in this mod it was close to
     * meaningless anyway, because the RTS camera's yaw is locked, so it always pointed the same
     * way. Removed rather than restyled.
     */
    private static void drawMarker(GuiGraphicsExtractor graphics, int x, int y,
                                   int width, int height, int sourceTop, int sourceHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !hasOrigin) {
            return;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 position = camera.position();

        float texelX = (float) (position.x - originBlockX) / BLOCKS_PER_PIXEL;
        float texelZ = (float) (position.z - originBlockZ) / BLOCKS_PER_PIXEL;
        if (texelZ < sourceTop || texelZ > sourceTop + sourceHeight) {
            return;
        }

        int markerX = x + Math.round(texelX * width / SIZE);
        int markerY = y + Math.round((texelZ - sourceTop) * height / sourceHeight);

        graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, COLOR_MARKER_EDGE);
        graphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, COLOR_MARKER);
    }
}
