package com.bewarethegreenone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    private final double multiplier;
    private final int explosionCount;

    private final Queue<BlockPos> queue;
    private final Set<BlockPos> visited;

    private final RandomSource random =
            RandomSource.create();

    private int ticksElapsed = 0;
    private boolean finishLogged = false;

    /*
     * =========================
     * 計測用カウンタ
     * =========================
     */

    private long processedCount = 0;
    private long queuedCount = 0;
    private long solidCount = 0;
    private long destroyedCount = 0;

    private static final Logger LOGGER =
            LogUtils.getLogger();


    public EnchantedExplosion(
            ServerLevel level,
            Vec3 center,
            double radius,
            double multiplier,
            int explosionCount
    ) {
        this.level = level;
        this.center = center;
        this.radius = radius;
        this.multiplier = multiplier;
        this.explosionCount = explosionCount;

        this.queue = new ArrayDeque<>();
        this.visited = new HashSet<>();
    }


    public void start() {

        LOGGER.info(
                "EnchantedExplosion: radius={}, multiplier={}, count={}",
                radius,
                multiplier,
                explosionCount
        );

        applyEntityEffects();

        BlockPos centerPos =
                BlockPos.containing(center);

        /*
         * 中心付近から探索開始
         */
        addToQueue(centerPos.above());
        addToQueue(centerPos.below());
        addToQueue(centerPos.north());
        addToQueue(centerPos.south());
        addToQueue(centerPos.east());
        addToQueue(centerPos.west());
    }


    /*
     * =========================
     * ダメージ・ノックバック
     * =========================
     */

    private void applyEntityEffects() {

        AABB area =
                new AABB(
                        center.x - radius,
                        center.y - radius,
                        center.z - radius,
                        center.x + radius,
                        center.y + radius,
                        center.z + radius
                );

        for (Entity entity :
                level.getEntities(
                        (Entity) null,
                        area,
                        entity -> {

                            if (!entity.isAlive()) {
                                return false;
                            }

                            if (entity instanceof ItemEntity) {
                                return false;
                            }

                            if (entity instanceof ExperienceOrb) {
                                return false;
                            }

                            if (entity instanceof PrimedTnt) {
                                return false;
                            }

                            return true;
                        }
                )) {

            double dx =
                    entity.getX() - center.x;

            double dy =
                    entity.getY()
                            + entity.getBbHeight() * 0.5
                            - center.y;

            double dz =
                    entity.getZ() - center.z;

            double distance =
                    Math.sqrt(
                            dx * dx +
                                    dy * dy +
                                    dz * dz
                    );

            if (distance > radius) {
                continue;
            }

            double distanceRatio =
                    1.0 -
                            distance / radius;

            double impact =
                    distanceRatio *
                            distanceRatio;

            double effectiveMultiplier =
                    Math.pow(multiplier, 0.8);

            float damage =
                    (float) (
                            12.0 *
                                    effectiveMultiplier *
                                    impact
                    );

            if (damage > 0.0F) {

                entity.hurt(
                        level.damageSources().explosion(null),
                        damage
                );
            }

            double length =
                    Math.sqrt(
                            dx * dx +
                                    dz * dz
                    );

            if (length < 0.001) {

                dx = 0.01;
                dz = 0.01;

                length =
                        Math.sqrt(
                                dx * dx +
                                        dz * dz
                        );
            }

            double knockback =
                    1.2 *
                            effectiveMultiplier *
                            impact;

            double knockbackX =
                    dx / length *
                            knockback;

            double knockbackZ =
                    dz / length *
                            knockback;

            double knockbackY =
                    0.35 *
                            knockback *
                            impact;

            entity.push(
                    knockbackX,
                    knockbackY,
                    knockbackZ
            );

            entity.hurtMarked = true;
        }
    }


    /*
     * =========================
     * キュー追加
     * =========================
     */

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
                dx * dx +
                        dy * dy +
                        dz * dz;

        if (distanceSquared >
                radius * radius) {

            return;
        }

        visited.add(pos);
        queue.add(pos);

        queuedCount++;
    }


    /*
     * =========================
     * Tick処理
     * =========================
     */

    public void tick() {

        ticksElapsed++;

        int processed = 0;

        long startTime =
                System.nanoTime();

        final long MAX_TIME_NANOS =
                8_000_000L;


        while (!queue.isEmpty()) {

            BlockPos pos =
                    queue.poll();

            processBlock(pos);

            processed++;

            if ((processed & 63) == 0) {

                long elapsed =
                        System.nanoTime() - startTime;

                if (elapsed >= MAX_TIME_NANOS) {
                    break;
                }
            }
        }


        /*
         * デバッグ
         */

        if (explosionCount >= 10) {

            LOGGER.debug(
                    "EnchantedExplosion tick: count={}, processed={}, remaining={}, visited={}, totalProcessed={}, queued={}, solid={}, destroyed={}",
                    explosionCount,
                    processed,
                    queue.size(),
                    visited.size(),
                    processedCount,
                    queuedCount,
                    solidCount,
                    destroyedCount
            );
        }


        /*
         * =========================
         * 完了
         * =========================
         */

        if (queue.isEmpty() &&
                !finishLogged) {

            finishLogged = true;

            LOGGER.info(
                    "Enchanted explosion finished: count={}, visited={}, processed={}, queued={}, solid={}, destroyed={}, ticks={}",
                    explosionCount,
                    visited.size(),
                    processedCount,
                    queuedCount,
                    solidCount,
                    destroyedCount,
                    ticksElapsed
            );
        }
    }


    /*
     * =========================
     * ブロック処理
     * =========================
     */

    private void processBlock(BlockPos pos) {

        processedCount++;

        BlockState state =
                level.getBlockState(pos);

        /*
         * RAY方式ではqueueに入るのは
         * 基本的に最初のsolidなので、
         * 空気処理はここでは行わない。
         */

        if (state.isAir()) {
            return;
        }

        solidCount++;


        /*
         * =========================
         * 爆発耐性
         * =========================
         */

        float resistance =
                state.getExplosionResistance(
                        level,
                        pos,
                        null
                );

        final float UNBREAKABLE_RESISTANCE =
                3_600_000.0F;

        if (resistance >=
                UNBREAKABLE_RESISTANCE) {

            return;
        }


        /*
         * =========================
         * 破壊確率
         * =========================
         */

        double requiredPower =
                1.0 +
                        resistance * 0.5;

        double powerRatio =
                multiplier /
                        requiredPower;

        double adjustedPowerRatio =
                powerRatio * 1.15;

        double powerSquared =
                adjustedPowerRatio *
                        adjustedPowerRatio;

        double destructionChance =
                powerSquared /
                        (1.0 + powerSquared);


        /*
         * 爆発回数による補正
         */

        double countBonus =
                Math.min(
                        0.15,
                        explosionCount * 0.015
                );

        destructionChance =
                Math.min(
                        1.0,
                        destructionChance +
                                countBonus
                );


        /*
         * =========================
         * 破壊
         * =========================
         */

        boolean destroyed = false;

        if (random.nextDouble() <
                destructionChance) {

            level.destroyBlock(
                    pos,
                    false
            );

            destroyedCount++;
            destroyed = true;
        }


        /*
         * =========================
         * 浸透
         * =========================
         */

        double penetrationChance =
                1.0 -
                        Math.exp(
                                -explosionCount / 3.5
                        );


        if (destroyed ||
                random.nextDouble() <
                        penetrationChance) {

            addNeighbours(pos);
        }
    }


    /*
     * =========================
     * RAY方式
     * =========================
     */

    private void spreadRay(BlockPos pos) {

        castRay(pos, Direction.UP);
        castRay(pos, Direction.DOWN);
        castRay(pos, Direction.NORTH);
        castRay(pos, Direction.SOUTH);
        castRay(pos, Direction.EAST);
        castRay(pos, Direction.WEST);
    }


    private void castRay(
            BlockPos start,
            Direction direction
    ) {

        BlockPos.MutableBlockPos current =
                start.mutable();

        current.move(direction);

        while (true) {

            /*
             * 爆発範囲外なら終了
             */

            double dx =
                    current.getX() + 0.5 - center.x;

            double dy =
                    current.getY() + 0.5 - center.y;

            double dz =
                    current.getZ() + 0.5 - center.z;

            double distanceSquared =
                    dx * dx +
                            dy * dy +
                            dz * dz;

            if (distanceSquared >
                    radius * radius) {

                return;
            }


            BlockState state =
                    level.getBlockState(current);


            /*
             * 空気ならさらに進む
             */

            if (state.isAir()) {

                current.move(direction);
                continue;
            }


            /*
             * 最初のsolidを発見
             */

            addToQueue(
                    current.immutable()
            );

            return;
        }
    }


    /*
     * =========================
     * 周囲6ブロック
     * =========================
     */

    private void addNeighbours(BlockPos pos) {

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