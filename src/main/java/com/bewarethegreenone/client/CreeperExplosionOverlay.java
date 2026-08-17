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

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {
            return;
        }

        if (!Config.hudVisible) {
            return;
        }

        GuiGraphics guiGraphics =
                event.getGuiGraphics();

        int screenWidth =
                guiGraphics.guiWidth();

        int screenHeight =
                guiGraphics.guiHeight();

        int x;
        int y;

        if (Config.hudPosition == 0) {

            x = screenWidth - WIDTH - 10;
            y = 10;

        } else if (Config.hudPosition == 1) {

            x = 10;
            y = 10;

        } else if (Config.hudPosition == 2) {

            x = screenWidth - WIDTH - 10;
            y = screenHeight - HEIGHT - 10;

        } else {

            x = 10;
            y = screenHeight - HEIGHT - 10;
        }


        guiGraphics.fill(
                x,
                y,
                x + WIDTH,
                y + HEIGHT,
                0x66000000
        );


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


        long elapsed =
                System.currentTimeMillis()
                        - BewareTheGreenOne.analysisStartTime;


        if (elapsed < 1500) {

            int dots =
                    (int) ((elapsed / 300) % 4);

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


        /*
         * ★ここも倍率計算を直接しない。
         *
         * explosionCount は
         * 「爆発した回数」なので、
         * 1回目 → 1.0倍
         * 2回目 → 1.3倍 / 1.5倍
         * 3回目 → 1.3²倍 / 1.5²倍
         */
        double multiplier =
                BewareTheGreenOne.getExplosionMultiplier(
                        Math.max(explosionCount - 1, 0)
                );


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
                        explosionCount
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