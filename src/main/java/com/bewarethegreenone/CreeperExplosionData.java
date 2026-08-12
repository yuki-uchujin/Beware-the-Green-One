package com.bewarethegreenone;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public class CreeperExplosionData extends SavedData {

    private static final String DATA_NAME = "beware_the_green_one";

    private int explosionCount = 0;

    public int getExplosionCount() {
        return explosionCount;
    }

    public void incrementExplosionCount() {
        explosionCount++;
        setDirty();
    }

    public static CreeperExplosionData load(CompoundTag tag) {
        CreeperExplosionData data = new CreeperExplosionData();

        data.explosionCount = tag.getInt("ExplosionCount");

        return data;
    }

    public static CreeperExplosionData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CreeperExplosionData::load,
                CreeperExplosionData::new,
                DATA_NAME
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("ExplosionCount", explosionCount);
        return tag;
    }
}