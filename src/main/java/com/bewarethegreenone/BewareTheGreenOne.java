package com.bewarethegreenone;

import com.bewarethegreenone.client.BewareTheGreenOneConfigScreen;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;

@Mod(BewareTheGreenOne.MODID)
public class BewareTheGreenOne {

    public static final String MODID = "bewarethegreenone";

    private static final Logger LOGGER =
            LogUtils.getLogger();

    private static final int LARGE_EXPLOSION_BLOCKS = 100000;
    private static final Map<UUID, ServerPlayer> creeperIgniters =
            new HashMap<>();

    public static boolean hasExploded = false;
    public static long analysisStartTime = 0;

    /*
     * ========================================
     * Debug Benchmark
     * ========================================
     *
     * true の場合、
     * 10回目・15回目・20回目の爆発だけ
     * EnchantedExplosion を実行する。
     *
     * それ以外の爆発は完全にキャンセルする。
     *
     * 通常プレイ時は false にする。
     */
    private static final boolean DEBUG_BENCHMARK = false;


    /*
     * ========================================
     * EnchantedExplosion 管理
     * ========================================
     *
     * activeExplosions:
     * 現在 tick() で処理している爆発。
     *
     * pendingExplosions:
     * ExplosionEvent.Start などから新しく
     * 登録された爆発。
     *
     * tick() 中に新しい爆発が登録されても
     * activeExplosions 自体を変更しないことで
     * ConcurrentModificationException を防ぐ。
     */
    private static final List<EnchantedExplosion> activeExplosions =
            new ArrayList<>();

    private static final List<EnchantedExplosion> pendingExplosions =
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

    private ServerPlayer getExplosionPlayer(
            Creeper creeper
    ) {

        ServerPlayer player =
                creeperIgniters.get(
                        creeper.getUUID()
                );


        if (player != null) {
            return player;
        }


        if (creeper.getTarget()
                instanceof ServerPlayer target) {

            return target;
        }


        return null;
    }

    public static void grantAdvancement(
            ServerPlayer player,
            String advancementId
    ) {

        MinecraftServer server =
                player.getServer();

        if (server == null) {
            return;
        }

        var advancement =
                server.getAdvancements().getAdvancement(
                        ResourceLocation.fromNamespaceAndPath(
                                MODID,
                                advancementId
                        )
                );

        if (advancement == null) {
            LOGGER.warn(
                    "Advancement not found: {}",
                    advancementId
            );
            return;
        }

        var progress =
                player.getAdvancements()
                        .getOrStartProgress(advancement);

        for (String criterion :
                progress.getRemainingCriteria()) {

            player.getAdvancements()
                    .award(
                            advancement,
                            criterion
                    );
        }


// 実績解除SE
        if (advancementId.equals("breakthrough")
                || advancementId.equals("large_explosion")) {

            // チャレンジ達成音
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundSource.MASTER,
                    1.0F,
                    1.0F
            );

        } else {

            // 通常進捗音
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.UI_TOAST_IN,
                    SoundSource.MASTER,
                    1.0F,
                    1.0F
            );
        }
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
         * 1 = Enchanted → 1.2^n
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
     * 指定した爆発回数が
     * ベンチマーク対象かどうか。
     *
     * explosionCount は
     * 0始まりなので、
     *
     * 9  = 10回目
     * 14 = 15回目
     * 19 = 20回目
     */
    private boolean isBenchmarkExplosion(
            int explosionCount
    ) {

        return explosionCount == 9
                || explosionCount == 14
                || explosionCount == 19;
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

        Creeper creeper =
                (Creeper) source;

        ServerPlayer explosionPlayer =
                getExplosionPlayer(creeper);

        if (explosionPlayer != null) {

            LOGGER.info(
                    "Creeper explosion target: {}",
                    explosionPlayer.getGameProfile().getName()
            );

            grantAdvancement(
                    explosionPlayer,
                    "first_explosion"
            );

        } else {

            LOGGER.info(
                    "Creeper exploded without a ServerPlayer target"
            );
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
         * Debug Benchmark Mode
         * ========================================
         *
         * 10 / 15 / 20回目だけ
         * EnchantedExplosionを実行。
         *
         * それ以外はバニラ爆発も
         * EnchantedExplosionも実行しない。
         */
        if (DEBUG_BENCHMARK) {

            /*
             * 爆発回数は必ず進める。
             */
            data.incrementExplosionCount();

            int newExplosionCount =
                    data.getExplosionCount();

            /*
             * HUDも同期。
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
             * 10 / 15 / 20回目以外は
             * 完全に爆発をキャンセル。
             */
            if (!isBenchmarkExplosion(
                    explosionCount
            )) {

                event.setCanceled(true);

                LOGGER.info(
                        "Debug benchmark: skipping explosion #{}",
                        newExplosionCount
                );

                return;
            }


            /*
             * =========================
             * ベンチマーク対象
             * =========================
             */

            double multiplier =
                    getExplosionMultiplier(
                            explosionCount
                    );

            LOGGER.info(
                    "Debug benchmark: Enchanted explosion #{} multiplier={}x",
                    newExplosionCount,
                    multiplier
            );


            double radius =
                    3.0 * multiplier;

            LOGGER.info(
                    "Starting benchmark explosion: " +
                            "count={}, multiplier={}x, radius={}",
                    newExplosionCount,
                    multiplier,
                    radius
            );


            /*
             * バニラ爆発をキャンセル。
             */
            event.setCanceled(true);


            ServerLevel level =
                    (ServerLevel) source.level();


            EnchantedExplosion explosion =
                    new EnchantedExplosion(
                            level,
                            event.getExplosion().getPosition(),
                            radius,
                            multiplier,
                            newExplosionCount,
                            explosionPlayer
                    );

            explosion.start();

            /*
             * activeExplosions ではなく
             * pendingExplosions に追加する。
             */
            pendingExplosions.add(explosion);

            return;
        }


        /*
         * ========================================
         * 通常の Vanilla Mode
         * ========================================
         */

        if (Config.explosionMode == 0) {

            LOGGER.info(
                    "Vanilla explosion mode"
            );

            if (explosionPlayer != null) {

                grantAdvancement(
                        explosionPlayer,
                        "vanilla_explosion"
                );

            }

            double multiplier =
                    getExplosionMultiplier(
                            explosionCount
                    );

            if (explosionPlayer != null) {

                checkMaxMultiplierAdvancement(
                        explosionPlayer,
                        multiplier
                );

            }

            LOGGER.info(
                    "Vanilla explosion: count={}, multiplier={}x",
                    explosionCount + 1,
                    multiplier
            );

            data.incrementExplosionCount();

            int newExplosionCount =
                    data.getExplosionCount();

            NetworkHandler.CHANNEL.send(
                    PacketDistributor.ALL.noArg(),
                    new ExplosionCountPacket(
                            newExplosionCount,
                            true,
                            newExplosionCount == 1
                    )
            );

            /*
             * バニラ爆発へ。
             */
            return;
        }


        if (explosionPlayer != null) {

            grantAdvancement(
                    explosionPlayer,
                    "enchanted_explosion"
            );

        }

        /*
         * ========================================
         * 通常の Enchanted Mode
         * ========================================
         */

        double multiplier =
                getExplosionMultiplier(
                        explosionCount
                );

        if (explosionPlayer != null) {

            checkMaxMultiplierAdvancement(
                    explosionPlayer,
                    multiplier
            );

        }


        LOGGER.info(
                "Enchanted explosion: count={}, multiplier={}x",
                explosionCount + 1,
                multiplier
        );

        data.incrementExplosionCount();

        int newExplosionCount =
                data.getExplosionCount();

        NetworkHandler.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new ExplosionCountPacket(
                        newExplosionCount,
                        true,
                        newExplosionCount == 1
                )
        );


        double radius =
                3.0 * multiplier;


        LOGGER.info(
                "Starting Enchanted explosion! multiplier={}x, radius={}",
                multiplier,
                radius
        );


        event.setCanceled(true);


        ServerLevel level =
                (ServerLevel) source.level();


        EnchantedExplosion explosion =
                new EnchantedExplosion(
                        level,
                        event.getExplosion().getPosition(),
                        radius,
                        multiplier,
                        explosionCount + 1,
                        explosionPlayer
                );

        explosion.start();

        /*
         * activeExplosions ではなく
         * pendingExplosions に追加する。
         */
        pendingExplosions.add(explosion);
    }

    private void checkMaxMultiplierAdvancement(
            ServerPlayer player,
            double multiplier
    ) {

        if (multiplier >= Config.maxExplosionMultiplier) {

            grantAdvancement(
                    player,
                    "breakthrough"
            );

        }
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


        /*
         * ========================================
         * Pending → Active
         * ========================================
         *
         * 前のtickやExplosionEvent.Startで
         * 登録された爆発を、今回の処理対象へ移す。
         *
         * ここでは activeExplosions の処理前なので
         * removeIf() 中の変更問題は発生しない。
         */
        if (!pendingExplosions.isEmpty()) {

            activeExplosions.addAll(
                    pendingExplosions
            );

            pendingExplosions.clear();
        }


        /*
         * ========================================
         * Active explosions を処理
         * ========================================
         *
         * explosion.tick() の中で新しい
         * EnchantedExplosion が作られたとしても、
         * それは pendingExplosions に入るため、
         * activeExplosions は変更されない。
         */
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

    /**
     * 火打石でクリーパーを起爆したプレイヤーを記録
     */
    @SubscribeEvent
    public void onCreeperIgnite(
            net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event
    ) {

        if (!(event.getTarget() instanceof Creeper creeper)) {
            return;
        }


        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }


        if (event.getItemStack().getItem()
                == net.minecraft.world.item.Items.FLINT_AND_STEEL) {


            creeperIgniters.put(
                    creeper.getUUID(),
                    player
            );


            LOGGER.info(
                    "Creeper {} ignited by {}",
                    creeper.getUUID(),
                    player.getGameProfile().getName()
            );
        }
    }

    /**
     * Vanilla爆発の破壊ブロック数チェック
     */
    @SubscribeEvent
    public void onExplosionDetonate(
            ExplosionEvent.Detonate event
    ) {

        Entity source =
                event.getExplosion()
                        .getDirectSourceEntity();

        if (!(source instanceof Creeper)) {
            return;
        }


        Creeper creeper =
                (Creeper) source;


        ServerPlayer explosionPlayer =
                getExplosionPlayer(creeper);


        if (explosionPlayer == null) {
            return;
        }


        int destroyedBlocks =
                event.getAffectedBlocks()
                        .size();


        LOGGER.info(
                "Explosion destroyed blocks: {}",
                destroyedBlocks
        );



        if (destroyedBlocks >= LARGE_EXPLOSION_BLOCKS) {

            grantAdvancement(
                    explosionPlayer,
                    "large_explosion"
            );

            LOGGER.info(
                    "Large explosion advancement granted!"
            );
        }
    }
}

