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

@Mod(BewareTheGreenOne.MODID)
public class BewareTheGreenOne {

    public static final String MODID = "bewarethegreenone";

    private static final Logger LOGGER = LogUtils.getLogger();

    public static boolean hasExploded = false;
    public static long analysisStartTime = 0;

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
        int explosionCount =
                data.getExplosionCount();

        // 今回の爆発倍率
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

        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new ExplosionCountPacket(
                        explosionCount,
                        true,
                        explosionCount == 0
                )
        );
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