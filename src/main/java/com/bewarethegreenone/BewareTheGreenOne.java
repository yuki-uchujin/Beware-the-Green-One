package com.bewarethegreenone;

import com.bewarethegreenone.client.BewareTheGreenOneConfigScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraftforge.common.MinecraftForge;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.TickEvent;


@Mod(BewareTheGreenOne.MODID)
public class BewareTheGreenOne {

    public static final String MODID = "bewarethegreenone";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean hasExploded = false;
    public static long analysisStartTime = 0;

    private static final List<EnchantedExplosion> activeExplosions =
            new ArrayList<>();

    public BewareTheGreenOne(FMLJavaModLoadingContext context) {

        // ネットワーク登録
        NetworkHandler.register();

        // Forgeのイベントバスへ登録
        MinecraftForge.EVENT_BUS.register(this);

        // クライアント設定
        context.registerConfig(
                ModConfig.Type.CLIENT,
                Config.SPEC
        );

        // MOD設定画面
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
     * クリーパーの爆発を検知
     */
    @SubscribeEvent
    public void onExplosion(ExplosionEvent.Detonate event) {

        Entity source =
                event.getExplosion().getDirectSourceEntity();

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

        // 今回の爆発が何回目か
        // ※爆発前の値なので、1回目なら0
        int explosionCount =
                data.getExplosionCount();

        // 今回の爆発倍率
        // 1回目: 1.0倍
        // 2回目: 1.5倍
        // 3回目: 2.25倍
        double multiplier;

        if (explosionCount == 0) {
            multiplier = 1.0;
        } else {
            multiplier = Math.min(
                    Math.pow(1.5, explosionCount),
                    Config.maxExplosionMultiplier
            );
        }

        LOGGER.debug(
                "Creeper explosion detected!"
        );

        LOGGER.debug(
                "Explosion count: {}",
                explosionCount + 1
        );

        LOGGER.debug(
                "Current multiplier: {}x",
                multiplier
        );

        // 爆発回数を保存
        data.incrementExplosionCount();

        // 増加後の累計爆発回数を取得
        int newExplosionCount =
                data.getExplosionCount();

        // クライアントへ現在の爆発回数を同期
        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new ExplosionCountPacket(
                        newExplosionCount,
                        true,
                        newExplosionCount == 1
                )
        );
    }

    @SubscribeEvent
    public void onExplosionStart(ExplosionEvent.Start event) {

        Entity source =
                event.getExplosion().getDirectSourceEntity();

        LOGGER.info(
                "ExplosionEvent.Start fired! source={}",
                source
        );

        // クリーパー以外は通常の爆発
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

        // =========================
        // 今回の爆発回数
        // =========================

        // 0 = 1回目
        // 1 = 2回目
        // 2 = 3回目
        int explosionCount =
                data.getExplosionCount();

        // =========================
        // 今回の倍率を計算
        // =========================

        double multiplier =
                Math.min(
                        Math.pow(1.5, explosionCount),
                        Config.maxExplosionMultiplier
                );

        // =========================
        // 爆発回数を増加
        // =========================

        data.incrementExplosionCount();

        int newExplosionCount =
                data.getExplosionCount();

        // =========================
        // クライアントへ同期
        // =========================

        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new ExplosionCountPacket(
                        newExplosionCount,
                        true,
                        newExplosionCount == 1
                )
        );

        // =========================
        // 爆発範囲
        // =========================

        // バニラクリーパーの爆発半径 3.0 × 倍率
        double radius = 3.0 * multiplier;

        LOGGER.info(
                "Starting Enchanted explosion! multiplier={}x, radius={}",
                multiplier,
                radius
        );

        // =========================
        // バニラ爆発をキャンセル
        // =========================

        event.setCanceled(true);

        // =========================
        // EnchantedExplosion開始
        // =========================

        ServerLevel level =
                (ServerLevel) source.level();

        EnchantedExplosion explosion =
                new EnchantedExplosion(
                        level,
                        event.getExplosion().getPosition(),
                        radius
                );

        explosion.start();

        activeExplosions.add(explosion);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        activeExplosions.removeIf(explosion -> {

            explosion.tick();

            return explosion.isFinished();
        });
    }

    /**
     * プレイヤーがサーバーへログインしたとき、
     * 保存されている爆発回数を同期する。
     */
    @SubscribeEvent
    public void onPlayerLogin(
            PlayerEvent.PlayerLoggedInEvent event
    ) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
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