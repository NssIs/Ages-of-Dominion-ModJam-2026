package com.hyrrx.forgottenrealmsrts.client.cursor;

import com.hyrrx.forgottenrealmsrts.ForgottenRealmsRTS;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class RtsCursor {
    private static final Identifier CURSOR_TEXTURE = Identifier.fromNamespaceAndPath(
            ForgottenRealmsRTS.MOD_ID,
            "textures/gui/rts_cursor.png"
    );
    private static final int TEXTURE_WIDTH = 1536;
    private static final int TEXTURE_HEIGHT = 1024;
    private static final int SOURCE_X = 557;
    private static final int SOURCE_Y = 157;
    private static final int SOURCE_WIDTH = 466;
    private static final int SOURCE_HEIGHT = 616;
    private static final int DISPLAY_WIDTH = 18;
    private static final int DISPLAY_HEIGHT = 24;
    private static final int PRESSED_DISPLAY_WIDTH = 13;
    private static final int PRESSED_DISPLAY_HEIGHT = 18;

    private RtsCursor() {
    }

    public static void hideNativeCursor(Minecraft minecraft) {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    public static void showNativeCursor(Minecraft minecraft) {
        GLFW.glfwSetInputMode(minecraft.getWindow().handle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float pressAmount) {
        float amount = Math.max(0.0F, Math.min(1.0F, pressAmount));
        int displayWidth = Math.round(DISPLAY_WIDTH
                - (DISPLAY_WIDTH - PRESSED_DISPLAY_WIDTH) * amount);
        int displayHeight = Math.round(DISPLAY_HEIGHT
                - (DISPLAY_HEIGHT - PRESSED_DISPLAY_HEIGHT) * amount);
        drawClickFlash(graphics, mouseX, mouseY, amount);
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                CURSOR_TEXTURE,
                mouseX - 3,
                mouseY - 2,
                SOURCE_X,
                SOURCE_Y,
                displayWidth,
                displayHeight,
                SOURCE_WIDTH,
                SOURCE_HEIGHT,
                TEXTURE_WIDTH,
                TEXTURE_HEIGHT
        );
    }

    /** Four bright corner ticks make a rapid click read as an action, not a stuck cursor sprite. */
    private static void drawClickFlash(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
                                       float amount) {
        if (amount <= 0.01F) {
            return;
        }
        int alpha = Math.max(1, Math.min(220, Math.round(220.0F * amount)));
        int color = (alpha << 24) | 0xF4C95D;
        int radius = 8 + Math.round(4.0F * amount);
        graphics.fill(mouseX - radius - 2, mouseY, mouseX - radius, mouseY + 1, color);
        graphics.fill(mouseX + radius, mouseY, mouseX + radius + 2, mouseY + 1, color);
        graphics.fill(mouseX, mouseY - radius - 2, mouseX + 1, mouseY - radius, color);
        graphics.fill(mouseX, mouseY + radius, mouseX + 1, mouseY + radius + 2, color);
    }

    public static int scaledMouseX(Minecraft minecraft) {
        MouseHandler mouseHandler = minecraft.mouseHandler;
        return floor(mouseHandler.getScaledXPos(minecraft.getWindow()));
    }

    public static int scaledMouseY(Minecraft minecraft) {
        MouseHandler mouseHandler = minecraft.mouseHandler;
        return floor(mouseHandler.getScaledYPos(minecraft.getWindow()));
    }

    private static int floor(double value) {
        int integer = (int)value;
        return value < integer ? integer - 1 : integer;
    }
}
