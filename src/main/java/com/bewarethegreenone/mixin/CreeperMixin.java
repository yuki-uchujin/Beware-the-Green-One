package com.bewarethegreenone.mixin;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    @ModifyArg(
            method = "explodeCreeper",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"
            ),
            index = 4
    )
    private float modifyExplosionRadius(float radius) {

        Creeper creeper = (Creeper)(Object)this;

        MinecraftServer server = creeper.getServer();

        if (server == null) {
            return radius;
        }

        CreeperExplosionData data =
                CreeperExplosionData.get(server);

        int explosionCount = data.getExplosionCount();

        double multiplier;

        if (explosionCount == 0) {
            multiplier = 1.0;
        } else {
            multiplier = Math.min(
                    Math.pow(1.5, explosionCount),
                    512.0
            );
        }

        LOGGER.debug(
                "Explosion radius changed: {} -> {} ({}x)",
                radius,
                radius * multiplier,
                multiplier
        );

        return (float)(radius * multiplier);
    }
}