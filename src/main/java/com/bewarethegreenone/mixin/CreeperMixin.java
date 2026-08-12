package com.bewarethegreenone.mixin;

import com.bewarethegreenone.CreeperExplosionData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Creeper.class)
public class CreeperMixin {

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
                    Math.pow(1.5, explosionCount + 1),
                    512.0
            );
        }

        System.out.println(
                "💥 爆発半径変更: "
                        + radius
                        + " → "
                        + radius * multiplier
                        + " ("
                        + multiplier
                        + "倍)"
        );

        return (float)(radius * multiplier);
    }
}