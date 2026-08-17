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
import net.minecraft.world.level.Explosion;
import net.minecraft.core.Direction;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class EnchantedExplosion {

    private final ServerLevel level;
    private final Vec3 center;
    private final double radius;

    // これから処理するブロック
    private final Queue<BlockPos> queue;

    // すでに探索したブロック
    private final Set<BlockPos> visited;

    private static final float UNBREAKABLE_RESISTANCE = 3_600_000.0F;
    private final RandomSource random = RandomSource.create();

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final BlockPos[] DIRECTIONS = {
            new BlockPos(1, 0, 0),
            new BlockPos(-1, 0, 0),
            new BlockPos(0, 1, 0),
            new BlockPos(0, -1, 0),
            new BlockPos(0, 0, 1),
            new BlockPos(0, 0, -1)
    };

    public EnchantedExplosion(
            ServerLevel level,
            Vec3 center,
            double radius
    ) {
        this.level = level;
        this.center = center;
        this.radius = radius;

        this.queue = new ArrayDeque<>();
        this.visited = new HashSet<>();
    }

    public void start() {

        System.out.println(
                "EnchantedExplosion: radius=" + radius
        );

        // 爆発中心に最も近い6方向のブロックから探索開始
        BlockPos centerPos = BlockPos.containing(center);

        addToQueue(centerPos.above());
        addToQueue(centerPos.below());
        addToQueue(centerPos.north());
        addToQueue(centerPos.south());
        addToQueue(centerPos.east());
        addToQueue(centerPos.west());
    }

    private void addToQueue(BlockPos pos) {

        // すでに探索済みなら無視
        if (visited.contains(pos)) {
            return;
        }

        // 爆発中心からの距離を計算
        double dx = pos.getX() + 0.5 - center.x;
        double dy = pos.getY() + 0.5 - center.y;
        double dz = pos.getZ() + 0.5 - center.z;

        double distanceSquared =
                dx * dx + dy * dy + dz * dz;

        // 爆発範囲外なら探索しない
        if (distanceSquared > radius * radius) {
            return;
        }

        BlockState state = level.getBlockState(pos);

        // 空気なら探索しない
        if (state.isAir()) {
            return;
        }

        // キューに入れた時点で探索済みにする
        visited.add(pos);

        queue.add(pos);
    }


    // =========================
    // 1tick分の爆発処理
    // =========================

    public void tick() {

        int processed = 0;

        while (processed < 128 && !queue.isEmpty()) {

            BlockPos pos = queue.poll();

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


    // =========================
    // ブロック1個を処理
    // =========================

    private void processBlock(BlockPos pos) {

        BlockState state = level.getBlockState(pos);

        // 空気なら何もしない
        if (state.isAir()) {
            return;
        }

        float resistance =
                state.getExplosionResistance(
                        level,
                        pos,
                        null
                );

        // 岩盤などの破壊不能ブロック
        final float UNBREAKABLE_RESISTANCE = 3_600_000.0F;

        if (resistance >= UNBREAKABLE_RESISTANCE) {

            LOGGER.info(
                    "Explosion blocked by {} at {}",
                    state.getBlock().getName().getString(),
                    pos
            );

            return;
        }

        /*
         * 現在のクリーパー倍率
         *
         * ここはEnchantedExplosionのコンストラクタで
         * multiplierを渡すようにするのが理想。
         *
         * ひとまず radius / 3.0 から求められる。
         */
        double currentMultiplier = radius / 3.0;

        /*
         * ブロックごとの「必要倍率指数」
         *
         * 1.5^2 = 2.25
         * 1.5^5 = 7.59375
         * 1.5^7 = 17.0859375
         */

        double requiredExponent;

        if (state.is(net.minecraft.world.level.block.Blocks.OBSIDIAN)) {

            // 黒曜石
            requiredExponent = 7.0;

        } else if (state.is(net.minecraft.world.level.block.Blocks.IRON_BLOCK)) {

            // 鉄ブロック
            requiredExponent = 5.0;

        } else if (state.is(net.minecraft.world.level.block.Blocks.STONE)) {

            // 石
            requiredExponent = 2.0;

        } else {

            /*
             * その他のブロックは爆破耐性から
             * 必要指数をざっくり計算する。
             */
            requiredExponent =
                    Math.max(
                            1.0,
                            Math.log1p(resistance) / Math.log(2.0)
                    );
        }

        double requiredMultiplier =
                Math.pow(1.5, requiredExponent);

        /*
         * 必要倍率に対して現在の倍率が
         * どのくらい達しているか。
         */
        double powerRatio =
                currentMultiplier / requiredMultiplier;

        /*
         * まだ全然足りない場合は破壊しない。
         *
         * さらに、その先にも進ませない。
         */
        if (powerRatio < 0.5) {
            return;
        }

        /*
         * 必要倍率に近づくほど破壊確率が上がる。
         *
         * 0.5倍 → 0%
         * 1.0倍 → 100%
         *
         * その間を滑らかに補間する。
         */
        double destructionChance =
                Math.min(
                        1.0,
                        (powerRatio - 0.5) / 0.5
                );

        if (random.nextDouble() >= destructionChance) {
            return;
        }

        // ブロック破壊
        level.destroyBlock(
                pos,
                false
        );

        // 破壊できた場合だけ、その先へ進む
        addToQueue(pos.above());
        addToQueue(pos.below());
        addToQueue(pos.north());
        addToQueue(pos.south());
        addToQueue(pos.east());
        addToQueue(pos.west());
    }


    // =========================
    // 爆発終了判定
    // =========================

    public boolean isFinished() {

        return queue.isEmpty();
    }
}