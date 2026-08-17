package com.bewarethegreenone;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;


public class EnchantedExplosion {

    private final ServerLevel level;
    private final Vec3 center;
    private final double radius;

    // ★現在の爆発倍率
    private final double multiplier;

    private final Queue<BlockPos> queue;
    private final Set<BlockPos> visited;

    private final RandomSource random =
            RandomSource.create();

    private static final Logger LOGGER =
            LogUtils.getLogger();


    public EnchantedExplosion(
            ServerLevel level,
            Vec3 center,
            double radius,
            double multiplier
    ) {
        this.level = level;
        this.center = center;
        this.radius = radius;
        this.multiplier = multiplier;

        this.queue = new ArrayDeque<>();
        this.visited = new HashSet<>();
    }


    public void start() {

        System.out.println(
                "EnchantedExplosion: radius=" + radius
                        + ", multiplier=" + multiplier
        );

        BlockPos centerPos =
                BlockPos.containing(center);

        addToQueue(centerPos.above());
        addToQueue(centerPos.below());
        addToQueue(centerPos.north());
        addToQueue(centerPos.south());
        addToQueue(centerPos.east());
        addToQueue(centerPos.west());
    }


    private void addToQueue(BlockPos pos) {

        if (visited.contains(pos)) {
            return;
        }

        double dx =
                pos.getX() + 0.5 - center.x;

        double dy =
                pos.getY() + 0.5 - center.y;

        double dz =
                pos.getZ() + 0.5 - center.z;

        double distanceSquared =
                dx * dx + dy * dy + dz * dz;

        if (distanceSquared > radius * radius) {
            return;
        }

        BlockState state =
                level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        visited.add(pos);

        queue.add(pos);
    }


    public void tick() {

        int processed = 0;

        while (processed < 128 && !queue.isEmpty()) {

            BlockPos pos =
                    queue.poll();

            processBlock(pos);

            processed++;
        }

        System.out.println(
                "EnchantedExplosion tick: processed="
                        + processed
                        + ", remaining="
                        + queue.size()
        );
    }


    private void processBlock(BlockPos pos) {

        BlockState state =
                level.getBlockState(pos);

        if (state.isAir()) {
            return;
        }

        float resistance =
                state.getExplosionResistance(
                        level,
                        pos,
                        null
                );

        final float UNBREAKABLE_RESISTANCE =
                3_600_000.0F;

        if (resistance >= UNBREAKABLE_RESISTANCE) {

            LOGGER.info(
                    "Explosion blocked by {} at {}",
                    state.getBlock().getName().getString(),
                    pos
            );

            return;
        }

        /*
         * ブロックを破壊するために必要なパワー
         */
        double requiredPower =
                1.0 + resistance * 0.5;

        /*
         * 現在の爆発が、
         * このブロックに対してどれくらい強いか。
         */
        double powerRatio =
                multiplier / requiredPower;

        /*
         * 破壊確率
         */
        double powerSquared =
                powerRatio * powerRatio;

        double destructionChance =
                powerSquared / (1.0 + powerSquared);

        if (random.nextDouble() >= destructionChance) {
            return;
        }

        level.destroyBlock(
                pos,
                false
        );

        addToQueue(pos.above());
        addToQueue(pos.below());
        addToQueue(pos.north());
        addToQueue(pos.south());
        addToQueue(pos.east());
        addToQueue(pos.west());
    }


    public boolean isFinished() {
        return queue.isEmpty();
    }
}