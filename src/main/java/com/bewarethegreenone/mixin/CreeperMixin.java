package com.bewarethegreenone.mixin;

import com.bewarethegreenone.BewareTheGreenOne;
import com.bewarethegreenone.CreeperExplosionData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.monster.Creeper;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Creeper.class)
public class CreeperMixin {

    private static final Logger LOGGER =
            LogUtils.getLogger();

    @ModifyArg(
            method = "explodeCreeper",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"
            ),
            index = 4
    )
    private float modifyExplosionRadius(float radius) {

        Creeper creeper =
                (Creeper) (Object) this;

        /*
         * Enchanted Modeでは
         * バニラ爆発そのものをキャンセルするので、
         * Mixinは何もしない。
         */
        if (com.bewarethegreenone.Config.explosionMode != 0) {
            return radius;
        }

        MinecraftServer server =
                creeper.getServer();

        if (server == null) {
            return radius;
        }

        CreeperExplosionData data =
                CreeperExplosionData.get(server);

        int explosionCount =
                data.getExplosionCount();

        /*
         * 爆発倍率は
         * BewareTheGreenOne側の共通メソッドを使用。
         *
         * 0 = 1回目 → 1.0x
         * 1 = 2回目 → 1.5x
         * 2 = 3回目 → 2.25x
         */
        double multiplier =
                BewareTheGreenOne.getExplosionMultiplier(
                        explosionCount
                );

        double newRadius =
                radius * multiplier;

        LOGGER.debug(
                "Explosion radius changed: {} -> {} ({}x)",
                radius,
                newRadius,
                multiplier
        );

        return (float) newRadius;
    }
}