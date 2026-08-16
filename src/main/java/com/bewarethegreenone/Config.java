package com.bewarethegreenone;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(
        modid = BewareTheGreenOne.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER =
            new ForgeConfigSpec.Builder();


    // =========================
    // HUD Settings
    // =========================

    private static final ForgeConfigSpec.IntValue HUD_POSITION =
            BUILDER
                    .comment(
                            "Position of the Creeper Explosion HUD",
                            "0 = Top Right",
                            "1 = Top Left",
                            "2 = Bottom Right",
                            "3 = Bottom Left"
                    )
                    .defineInRange(
                            "hudPosition",
                            0,
                            0,
                            3
                    );

    private static final ForgeConfigSpec.BooleanValue HUD_VISIBLE =
            BUILDER
                    .comment("Whether to display the Creeper Explosion HUD")
                    .define(
                            "hudVisible",
                            true
                    );


    // =========================
    // Explosion Settings
    // =========================

    private static final ForgeConfigSpec.DoubleValue MAX_EXPLOSION_MULTIPLIER =
            BUILDER
                    .comment(
                            "Maximum explosion multiplier",
                            "The explosion multiplier follows 1.5^n and will not exceed this value."
                    )
                    .defineInRange(
                            "maxExplosionMultiplier",
                            512.0,
                            1.0,
                            Double.MAX_VALUE
                    );


    // =========================
    // Config Spec
    // =========================

    static final ForgeConfigSpec SPEC =
            BUILDER.build();


    // =========================
    // Current Settings
    // =========================

    public static int hudPosition = 0;
    public static boolean hudVisible = true;
    public static double maxExplosionMultiplier = 512.0;


    // =========================
    // Load Config
    // =========================

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {

        hudPosition = HUD_POSITION.get();
        hudVisible = HUD_VISIBLE.get();
        maxExplosionMultiplier = MAX_EXPLOSION_MULTIPLIER.get();
    }


    // =========================
    // HUD Position
    // =========================

    public static void setHudPosition(int position) {

        HUD_POSITION.set(position);
        hudPosition = position;
    }


    // =========================
    // HUD Visibility
    // =========================

    public static void setHudVisible(boolean visible) {

        HUD_VISIBLE.set(visible);
        hudVisible = visible;
    }


    // =========================
    // Maximum Explosion Multiplier
    // =========================

    public static void setMaxExplosionMultiplier(double multiplier) {

        MAX_EXPLOSION_MULTIPLIER.set(multiplier);
        maxExplosionMultiplier = multiplier;
    }
}