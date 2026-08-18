package com.bewarethegreenone;

import net.minecraft.core.BlockPos;
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

    // 現在の爆発倍率
    private final double multiplier;

    // 今回が何回目の爆発か
    private final int explosionCount;

    private final Queue<BlockPos> queue;
    private final Set<BlockPos> visited;

    private final RandomSource random =
            RandomSource.create();

    private int ticksElapsed = 0;
    private boolean finishLogged = false;

    private int destroyedCount = 0;

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

        System.out.println(
                "EnchantedExplosion: radius=" + radius
                        + ", multiplier=" + multiplier
                        + ", count=" + explosionCount
        );

        /*
         * =========================
         * エンティティへの爆発効果
         * =========================
         */
        applyEntityEffects();


        /*
         * =========================
         * ブロック探索開始
         * =========================
         */

        BlockPos centerPos =
                BlockPos.containing(center);

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

                            // アイテム・経験値などは除外
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

            /*
             * 爆発範囲外
             */
            if (distance > radius) {
                continue;
            }


            /*
             * =========================
             * 距離による爆発力
             * =========================
             *
             * 中心 → 1.0
             * 外周 → 0.0
             */
            double distanceRatio =
                    1.0 -
                            (distance / radius);

            /*
             * 急激すぎないように少しカーブさせる
             */
            double impact =
                    distanceRatio * distanceRatio;


            /*
             * =========================
             * ダメージ
             * =========================
             *
             * 1.0xのクリーパーを
             * 基準ダメージとして扱う。
             *
             * 倍率が上がるほど強くなる。
             */
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
                        level.damageSources().explosion(
                                null
                        ),
                        damage
                );
            }


            /*
             * =========================
             * ノックバック
             * =========================
             */

            double length =
                    Math.sqrt(
                            dx * dx +
                                    dz * dz
                    );

            /*
             * 爆発中心と完全に重なっている
             * 場合のゼロ除算を防ぐ
             */
            if (length < 0.001) {

                dx = 0.01;
                dz = 0.01;

                length =
                        Math.sqrt(
                                dx * dx +
                                        dz * dz
                        );
            }


            /*
             * 水平方向のノックバック
             */
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


            /*
             * 少し上方向にも飛ばす
             */
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


            LOGGER.debug(
                    "Explosion hit {}: distance={}, damage={}, knockback={}",
                    entity.getName().getString(),
                    distance,
                    damage,
                    knockback
            );
        }
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
                dx * dx +
                        dy * dy +
                        dz * dz;

        if (distanceSquared >
                radius * radius) {

            return;
        }

        // 空気でも探索する
        visited.add(pos);
        queue.add(pos);
    }


    public void tick() {

        ticksElapsed++;

        int processed = 0;

        while (processed < 256 && !queue.isEmpty()) {

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

        if (queue.isEmpty() && !finishLogged) {

            finishLogged = true;

            LOGGER.info(
                    "Enchanted explosion finished: count={}, visited={}, destroyed={}, ticks={}, processed this tick={}",
                    explosionCount,
                    visited.size(),
                    destroyedCount,
                    ticksElapsed,
                    processed
            );
        }
    }


    private void processBlock(BlockPos pos) {

        BlockState state =
                level.getBlockState(pos);

        /*
         * 空気はそのまま通過
         */
        if (state.isAir()) {

            addToQueue(pos.above());
            addToQueue(pos.below());
            addToQueue(pos.north());
            addToQueue(pos.south());
            addToQueue(pos.east());
            addToQueue(pos.west());

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


        /*
         * 岩盤などは絶対に破壊しない
         */
        if (resistance >=
                UNBREAKABLE_RESISTANCE) {

            LOGGER.info(
                    "Explosion blocked by {} at {}",
                    state.getBlock()
                            .getName()
                            .getString(),
                    pos
            );

            return;
        }


        /*
         * =========================
         * ブロック破壊確率
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
         * 爆発回数による追加補正
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
         * ブロック破壊
         * =========================
         */

        boolean destroyed = false;

        if (random.nextDouble() < destructionChance) {

            level.destroyBlock(
                    pos,
                    false
            );

            destroyedCount++;
            destroyed = true;
        }


        /*
         * =========================
         * 爆発の浸透率
         * =========================
         */

        double penetrationChance =
                1.0 -
                        Math.exp(
                                -explosionCount /
                                        3.5
                        );


        /*
         * =========================
         * 次のブロックへ
         * =========================
         */

        if (destroyed ||
                random.nextDouble() <
                        penetrationChance) {

            addToQueue(pos.above());
            addToQueue(pos.below());
            addToQueue(pos.north());
            addToQueue(pos.south());
            addToQueue(pos.east());
            addToQueue(pos.west());
        }
    }


    public boolean isFinished() {

        return queue.isEmpty();
    }
}