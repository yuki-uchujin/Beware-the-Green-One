package com.bewarethegreenone;

import com.bewarethegreenone.client.CreeperExplosionOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosionCountPacket {

    // 現在までに発生したクリーパー爆発の累計回数
    private final int explosionCount;

    // これまでにクリーパーが爆発したことがあるか
    private final boolean hasExploded;

    // 初回爆発後の解析表示を開始するか
    private final boolean showAnalysis;

    public ExplosionCountPacket(
            int explosionCount,
            boolean hasExploded,
            boolean showAnalysis
    ) {
        this.explosionCount = explosionCount;
        this.hasExploded = hasExploded;
        this.showAnalysis = showAnalysis;
    }

    /**
     * Packet → Buffer
     */
    public static void encode(
            ExplosionCountPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(packet.explosionCount);
        buffer.writeBoolean(packet.hasExploded);
        buffer.writeBoolean(packet.showAnalysis);
    }

    /**
     * Buffer → Packet
     */
    public static ExplosionCountPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new ExplosionCountPacket(
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    /**
     * クライアント側でPacketを処理
     */
    public static void handle(
            ExplosionCountPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> {

            // 現在の累計爆発回数をHUDへ反映
            CreeperExplosionOverlay.setExplosionCount(
                    packet.explosionCount
            );

            // クリーパーが一度でも爆発したか
            BewareTheGreenOne.hasExploded =
                    packet.hasExploded;

            // 初回爆発後の解析表示を開始
            if (packet.showAnalysis) {
                BewareTheGreenOne.analysisStartTime =
                        System.currentTimeMillis();
            }
        });

        context.setPacketHandled(true);
    }
}