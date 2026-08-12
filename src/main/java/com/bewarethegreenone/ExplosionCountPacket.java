package com.bewarethegreenone;

import com.bewarethegreenone.client.CreeperExplosionOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ExplosionCountPacket {

    private final int explosionCount;
    private final boolean hasExploded;
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

    public static void encode(
            ExplosionCountPacket packet,
            FriendlyByteBuf buffer
    ) {
        buffer.writeInt(packet.explosionCount);
        buffer.writeBoolean(packet.hasExploded);
        buffer.writeBoolean(packet.showAnalysis);
    }

    public static ExplosionCountPacket decode(
            FriendlyByteBuf buffer
    ) {
        return new ExplosionCountPacket(
                buffer.readInt(),
                buffer.readBoolean(),
                buffer.readBoolean()
        );
    }

    public static void handle(
            ExplosionCountPacket packet,
            Supplier<NetworkEvent.Context> supplier
    ) {
        NetworkEvent.Context context = supplier.get();

        context.enqueueWork(() -> {

            CreeperExplosionOverlay.setExplosionCount(
                    packet.explosionCount
            );

            BewareTheGreenOne.hasExploded =
                    packet.hasExploded;

            if (packet.showAnalysis) {
                BewareTheGreenOne.analysisStartTime =
                        System.currentTimeMillis();
            }
        });

        context.setPacketHandled(true);
    }
}