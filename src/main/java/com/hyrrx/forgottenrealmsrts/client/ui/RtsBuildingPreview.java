package com.hyrrx.forgottenrealmsrts.client.ui;

import com.hyrrx.forgottenrealmsrts.RtsMode;
import com.hyrrx.forgottenrealmsrts.client.build.BuildingPreviewShape;
import com.hyrrx.forgottenrealmsrts.network.BuildingPreviewPayload;
import com.hyrrx.forgottenrealmsrts.network.RequestBuildingCatalogPayload;
import com.hyrrx.forgottenrealmsrts.network.RequestBuildingPreviewPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a saved structure as a small 3D building, inside a HUD rectangle.
 *
 * <p><strong>Why it is built out of item renders.</strong> Minecraft 26.1 deleted
 * {@code BlockRenderDispatcher}; block rendering now goes through a deferred submit-node pipeline
 * ({@code ModelBlockRenderer.tesselateBlock} + {@code BlockQuadOutput} + {@code SubmitNodeCollection}),
 * and rendering an arbitrary {@code BlockState} into a GUI means writing a custom
 * {@code PictureInPictureRenderer}. The vanilla GUI item renderer, however, already draws a block
 * item as a 3D cube at a 30°/225° isometric angle — which is exactly the projection an RTS building
 * icon wants. Stacking those at isometric offsets gives a real voxel building for the price of a
 * loop.
 *
 * <p><strong>The painter's ordering is the part that must not be got wrong.</strong> GUI item draws
 * do not depth-test against each other, so the blocks are sorted by {@code x + y + z} and drawn
 * back to front. Sorted the other way, or not at all, the building renders inside-out and looks
 * like noise.
 *
 * <p><strong>Known limitation, chosen deliberately</strong> (see the blueprint's TWISTS.md): an item
 * form carries no block <em>state</em>, so stairs, logs and slabs draw in their default orientation,
 * and a block with no item at all (wall torches, technical blocks) is skipped.
 */
public final class RtsBuildingPreview {
    /** Structures whose blocks have arrived, keyed by identifier. */
    private static final Map<Identifier, Preview> CACHE = new HashMap<>();
    /** Structures already asked for, so a tray redraw does not spam one request per frame. */
    private static final Set<Identifier> REQUESTED = new HashSet<>();

    /**
     * One block to draw: its stack, and where it lands relative to the structure's origin. Screen
     * offsets are computed once at receipt rather than per frame, because the isometric projection
     * of a block never changes — only the scale does.
     */
    private record Placement(ItemStack stack, float offsetX, float offsetY) {
    }

    /**
     * A structure ready to draw: its placements already sorted back-to-front, and the bounds of the
     * projected result, so a draw can fit it to any rectangle by scaling alone.
     */
    private record Preview(List<Placement> placements,
                           float minX, float minY, float spanX, float spanY) {
    }

    /** Item render size in GUI pixels — vanilla draws an item icon 16x16. */
    private static final float ITEM_SIZE = 16.0F;
    /** Isometric cell: half a cell right per +x, half left per +z, quarter down per both, half up
     *  per +y. These are the standard 2:1 isometric ratios and they are what make the stacked item
     *  cubes line up edge to edge instead of overlapping or leaving gaps. */
    private static final float STEP_X = ITEM_SIZE / 2.0F;
    private static final float STEP_Y = ITEM_SIZE / 4.0F;
    private static final float STEP_UP = ITEM_SIZE / 2.0F;

    /** Previous tick's RTS-mode state, so the catalog is fetched on the edge and not every tick. */
    private static boolean wasActive;

    private RtsBuildingPreview() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RtsBuildingPreview::onClientTick);
    }

    /**
     * Refetches the catalog whenever RTS mode turns on, and forgets everything whenever it turns
     * off or the player leaves the world.
     *
     * <p>Fetching on the activation edge is what makes the structure-block workflow feel immediate:
     * save a structure, {@code /game deactivate}, {@code /game activate}, and it is in the tray. No
     * restart, no reload, no polling.
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = minecraft.player != null && RtsMode.isActive(minecraft.player);
        if (active == wasActive) {
            return;
        }
        wasActive = active;

        clear();
        RtsHudState.setSelectedBuilding(null);
        RtsHudState.setSelectedPlacedBuilding(null);
        RtsHudState.acceptCatalog(Map.of());
        if (active) {
            ClientPacketDistributor.sendToServer(RequestBuildingCatalogPayload.INSTANCE);
        }
    }

    /** Drops every cached structure. Called when the catalog is replaced or the world changes. */
    public static void clear() {
        CACHE.clear();
        REQUESTED.clear();
        BuildingPreviewShape.clear();
    }

    /** Payload handler: turns the packed block list into ready-to-draw placements. */
    public static void accept(BuildingPreviewPayload payload) {
        List<Identifier> palette = payload.palette();
        List<ItemStack> stacks = new ArrayList<>(palette.size());
        for (Identifier blockId : palette) {
            stacks.add(stackFor(blockId));
        }

        // Back to front. Sorting on the packed ints directly would sort by x first, which is not
        // the same thing at all.
        List<Integer> packed = new ArrayList<>(payload.blocks());
        packed.sort((a, b) -> Integer.compare(depth(a), depth(b)));

        List<Placement> placements = new ArrayList<>(packed.size());
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (int block : packed) {
            ItemStack stack = stacks.get(BuildingPreviewPayload.unpackPalette(block));
            if (stack.isEmpty()) {
                continue;
            }
            int x = BuildingPreviewPayload.unpackX(block);
            int y = BuildingPreviewPayload.unpackY(block);
            int z = BuildingPreviewPayload.unpackZ(block);

            float offsetX = (x - z) * STEP_X;
            float offsetY = (x + z) * STEP_Y - y * STEP_UP;
            placements.add(new Placement(stack, offsetX, offsetY));

            minX = Math.min(minX, offsetX);
            maxX = Math.max(maxX, offsetX + ITEM_SIZE);
            minY = Math.min(minY, offsetY);
            maxY = Math.max(maxY, offsetY + ITEM_SIZE);
        }

        if (placements.isEmpty()) {
            // Every block was stateless scenery with no item form. Cache the emptiness anyway, or
            // the tray asks the server for it again on every single frame.
            CACHE.put(payload.structure(), new Preview(List.of(), 0.0F, 0.0F, 1.0F, 1.0F));
            return;
        }

        CACHE.put(payload.structure(),
                new Preview(placements, minX, minY, maxX - minX, maxY - minY));
    }

    private static int depth(int packed) {
        return BuildingPreviewPayload.unpackX(packed)
                + BuildingPreviewPayload.unpackY(packed)
                + BuildingPreviewPayload.unpackZ(packed);
    }

    private static ItemStack stackFor(Identifier blockId) {
        Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
        if (block == null) {
            return ItemStack.EMPTY;
        }
        Item item = block.asItem();
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /**
     * Draws {@code building} centred in the given rectangle, requesting it from the server the
     * first time it is asked for.
     *
     * @return {@code true} if something was actually drawn — the caller uses this to decide whether
     *         to fall back to a label.
     */
    public static boolean draw(GuiGraphicsExtractor graphics, Identifier building,
                               int x, int y, int width, int height) {
        Preview preview = CACHE.get(building);
        if (preview == null) {
            request(building);
            return false;
        }
        if (preview.placements().isEmpty()) {
            return false;
        }

        // Fit by scale alone: the placements are already in a fixed projected space, so one uniform
        // scale plus a translation puts any structure inside any box. Capped at 1.0 so a two-block
        // hut is not blown up into a blurry monolith.
        float scale = Math.min(Math.min(width / preview.spanX(), height / preview.spanY()), 1.0F);
        float drawnWidth = preview.spanX() * scale;
        float drawnHeight = preview.spanY() * scale;
        float originX = x + (width - drawnWidth) / 2.0F - preview.minX() * scale;
        float originY = y + (height - drawnHeight) / 2.0F - preview.minY() * scale;

        graphics.pose().pushMatrix();
        graphics.pose().translate(originX, originY);
        graphics.pose().scale(scale, scale);
        for (Placement placement : preview.placements()) {
            // Item draws take ints; the pose scale above is what gives sub-pixel placement, so the
            // rounding here is in pre-scale space and stays sharp.
            graphics.item(placement.stack(),
                    Math.round(placement.offsetX()), Math.round(placement.offsetY()));
        }
        graphics.pose().popMatrix();
        return true;
    }

    /**
     * Asks the server for a structure's blocks, at most once per structure per session.
     *
     * <p>Client→server sending is {@code ClientPacketDistributor}, not {@code PacketDistributor} —
     * the latter is server-side only in NeoForge 26.1 and has no {@code sendToServer} at all.
     */
    private static void request(Identifier building) {
        if (REQUESTED.add(building)) {
            ClientPacketDistributor.sendToServer(new RequestBuildingPreviewPayload(building));
        }
    }
}
