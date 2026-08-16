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

        int minX = (int) Math.floor(center.x - radius);
        int maxX = (int) Math.ceil(center.x + radius);

        int minY = (int) Math.floor(center.y - radius);
        int maxY = (int) Math.ceil(center.y + radius);

        int minZ = (int) Math.floor(center.z - radius);
        int maxZ = (int) Math.ceil(center.z + radius);

        // 球の外周を1ブロック間隔で探索
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {

                    double dx = x + 0.5 - center.x;
                    double dy = y + 0.5 - center.y;
                    double dz = z + 0.5 - center.z;

                    double distanceSquared =
                            dx * dx + dy * dy + dz * dz;

                    // 球の外側
                    if (distanceSquared > radius * radius) {
                        continue;
                    }

                    // 球の表面付近だけを初期探索点にする
                    double innerRadius = Math.max(radius - 1.0, 0.0);

                    if (distanceSquared < innerRadius * innerRadius) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);

                    addToQueue(pos);
                }
            }
        }
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

        final double BEDROCK_RESISTANCE = 3_600_000.0;

        // 破壊不能ブロックは壁
        if (resistance >= BEDROCK_RESISTANCE) {

            LOGGER.info(
                    "Explosion blocked by {} at {}",
                    state.getBlock().getName().getString(),
                    pos
            );

            return;
        }

        double destructionChance =
                1.0 - (resistance / BEDROCK_RESISTANCE);

        // 破壊失敗 → その先へ進まない
        if (random.nextDouble() >= destructionChance) {
            return;
        }

        // 破壊
        level.destroyBlock(
                pos,
                true
        );

        // 6方向へ探索
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