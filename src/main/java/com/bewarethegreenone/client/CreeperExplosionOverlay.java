package com.bewarethegreenone.client;

import com.bewarethegreenone.Config;
import com.bewarethegreenone.BewareTheGreenOne;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = BewareTheGreenOne.MODID,
        value = net.minecraftforge.api.distmarker.Dist.CLIENT
)
public class CreeperExplosionOverlay {

    private static final int WIDTH = 180;
    private static final int HEIGHT = 65;

    private static int explosionCount = 0;

    public static void setExplosionCount(int count) {
        explosionCount = count;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        if (!Config.hudVisible) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();

        int x;
        int y;

        if (Config.hudPosition == 0) {
            // 右上
            x = screenWidth - WIDTH - 10;
            y = 10;

        } else if (Config.hudPosition == 1) {
            // 左上
            x = 10;
            y = 10;

        } else if (Config.hudPosition == 2) {
            // 右下
            x = screenWidth - WIDTH - 10;
            y = screenHeight - HEIGHT - 10;

        } else {
            // 左下
            x = 10;
            y = screenHeight - HEIGHT - 10;
        }

        // 黒い半透明背景
        guiGraphics.fill(
                x,
                y,
                x + WIDTH,
                y + HEIGHT,
                0x66000000
        );

        // まだクリーパーが爆発していない
        if (!BewareTheGreenOne.hasExploded) {

            guiGraphics.drawString(
                    minecraft.font,
                    Component.translatable(
                            "bewarethegreenone.overlay.unknown"
                    ),
                    x + 10,
                    y + 10,
                    0xFFFFFF
            );

            guiGraphics.drawString(
                    minecraft.font,
                    Component.translatable(
                            "bewarethegreenone.overlay.unknown"
                    ),
                    x + 10,
                    y + 30,
                    0xFFFFFF
            );

            return;
        }

        // 初回爆発からの経過時間
        long elapsed =
                System.currentTimeMillis()
                        - BewareTheGreenOne.analysisStartTime;

        // 解析中
        if (elapsed < 1500) {

            int dots = (int) ((elapsed / 300) % 4);

            MutableComponent text =
                    Component.translatable(
                            "bewarethegreenone.overlay.analyzing"
                    );

            for (int i = 0; i < dots; i++) {
                text.append(".");
            }

            guiGraphics.drawString(
                    minecraft.font,
                    text,
                    x + 10,
                    y + 10,
                    0xFFFFFF
            );

            guiGraphics.drawString(
                    minecraft.font,
                    Component.translatable(
                            "bewarethegreenone.overlay.detected"
                    ),
                    x + 10,
                    y + 30,
                    0xAAAAAA
            );

            return;
        }

        // =========================
        // 通常表示
        // =========================

        double multiplier;

        if (explosionCount == 0) {
            multiplier = 1.0;
        } else {
            multiplier = Math.min(
                    Math.pow(1.5, explosionCount),
                    512.0
            );
        }

        guiGraphics.drawString(
                minecraft.font,
                Component.translatable(
                        "bewarethegreenone.overlay.title"
                ),
                x + 10,
                y + 8,
                0xFFFFFF
        );

        guiGraphics.drawString(
                minecraft.font,
                Component.translatable(
                        "bewarethegreenone.overlay.explosions",
                        explosionCount + 1
                ),
                x + 10,
                y + 26,
                0xFFFFFF
        );

        guiGraphics.drawString(
                minecraft.font,
                Component.translatable(
                        "bewarethegreenone.overlay.multiplier",
                        String.format("%.2f", multiplier)
                ),
                x + 10,
                y + 44,
                0xFFFFFF
        );
    }
}