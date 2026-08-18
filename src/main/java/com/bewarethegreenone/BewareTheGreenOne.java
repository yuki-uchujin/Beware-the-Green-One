package com.bewarethegreenone;

import com.bewarethegreenone.client.BewareTheGreenOneConfigScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod(BewareTheGreenOne.MODID)
public class BewareTheGreenOne {

    public static final String MODID = "bewarethegreenone";

    private static final Logger LOGGER =
            LogUtils.getLogger();

    public static boolean hasExploded = false;
    public static long analysisStartTime = 0;

    private static final List<EnchantedExplosion> activeExplosions =
            new ArrayList<>();


    public BewareTheGreenOne(
            FMLJavaModLoadingContext context
    ) {

        NetworkHandler.register();

        MinecraftForge.EVENT_BUS.register(this);

        context.registerConfig(
                ModConfig.Type.CLIENT,
                Config.SPEC
        );

        context.registerExtensionPoint(
                net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, screen) ->
                                new BewareTheGreenOneConfigScreen(screen)
                )
        );

        LOGGER.info("Beware the Green One loaded!");
    }


    /**
     * 現在のモードに応じて爆発倍率を計算する。
     *
     * explosionCount:
     * 0 = 1回目
     * 1 = 2回目
     * 2 = 3回目
     */
    public static double getExplosionMultiplier(
            int explosionCount
    ) {

        double base;

        /*
         * 0 = Vanilla → 1.5^n
         * 1 = Enchanted → 1.3^n
         */
        if (Config.explosionMode == 0) {
            base = 1.5;
        } else {
            base = 1.2;
        }

        return Math.min(
                Math.pow(base, explosionCount),
                Config.maxExplosionMultiplier
        );
    }


    /**
     * クリーパー爆発開始時
     */
    @SubscribeEvent
    public void onExplosionStart(
            ExplosionEvent.Start event
    ) {

        Entity source =
                event.getExplosion()
                        .getDirectSourceEntity();

        LOGGER.info(
                "ExplosionEvent.Start fired! source={}",
                source
        );

        // クリーパー以外は何もしない
        if (!(source instanceof Creeper)) {
            return;
        }

        MinecraftServer server =
                source.level().getServer();

        if (server == null) {
            return;
        }

        CreeperExplosionData data =
                CreeperExplosionData.get(server);

        /*
         * 今回の爆発回数
         *
         * 0 = 1回目
         * 1 = 2回目
         * 2 = 3回目
         */
        int explosionCount =
                data.getExplosionCount();

        /*
         * ========================================
         * Vanilla Mode
         * ========================================
         *
         * Mixin側に処理を任せる。
         *
         * ここでは爆発をキャンセルしない。
         */
        if (Config.explosionMode == 0) {

            LOGGER.info(
                    "Vanilla explosion mode"
            );

            /*
             * 爆発倍率はMixinと同じ計算になる。
             */
            double multiplier =
                    getExplosionMultiplier(
                            explosionCount
                    );

            LOGGER.info(
                    "Vanilla explosion: count={}, multiplier={}x",
                    explosionCount + 1,
                    multiplier
            );

            /*
             * 爆発回数を増やす。
             */
            data.incrementExplosionCount();

            int newExplosionCount =
                    data.getExplosionCount();

            /*
             * HUDへ同期
             */
            NetworkHandler.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new ExplosionCountPacket(
                            newExplosionCount,
                            true,
                            newExplosionCount == 1
                    )
            );

            /*
             * ここではevent.setCanceled(true)しない！
             *
             * Minecraft本来の爆発処理へ進む。
             */
            return;
        }


        /*
         * ========================================
         * Enchanted Mode
         * ========================================
         */

        double multiplier =
                getExplosionMultiplier(
                        explosionCount
                );

        LOGGER.info(
                "Enchanted explosion: count={}, multiplier={}x",
                explosionCount + 1,
                multiplier
        );

        /*
         * 爆発回数を増やす。
         */
        data.incrementExplosionCount();

        int newExplosionCount =
                data.getExplosionCount();

        /*
         * HUDへ同期
         */
        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new ExplosionCountPacket(
                        newExplosionCount,
                        true,
                        newExplosionCount == 1
                )
        );

        /*
         * バニラクリーパーの爆発半径
         */
        double radius =
                3.0 * multiplier;

        LOGGER.info(
                "Starting Enchanted explosion! multiplier={}x, radius={}",
                multiplier,
                radius
        );

        /*
         * バニラ爆発をキャンセル
         */
        event.setCanceled(true);

        /*
         * 独自爆発開始
         */
        ServerLevel level =
                (ServerLevel) source.level();

        EnchantedExplosion explosion =
                new EnchantedExplosion(
                        level,
                        event.getExplosion().getPosition(),
                        radius,
                        multiplier,
                        explosionCount + 1
                );

        explosion.start();

        activeExplosions.add(explosion);
    }


    /**
     * 1tickごとに独自爆発を処理
     */
    @SubscribeEvent
    public void onServerTick(
            TickEvent.ServerTickEvent event
    ) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        activeExplosions.removeIf(explosion -> {

            explosion.tick();

            return explosion.isFinished();
        });
    }


    /**
     * プレイヤーログイン時に
     * 爆発回数を同期する。
     */
    @SubscribeEvent
    public void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {

        if (!(event.getEntity()
                instanceof ServerPlayer player)) {
            return;
        }

        MinecraftServer server =
                player.getServer();

        if (server == null) {
            return;
        }

        CreeperExplosionData data =
                CreeperExplosionData.get(server);

        NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(
                        () -> player
                ),
                new ExplosionCountPacket(
                        data.getExplosionCount(),
                        data.getExplosionCount() > 0,
                        false
                )
        );
    }
}