package com.yanderemod.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The yandere's "soul". All of its mood and progress lives here (saved with the world), the entity is
 * just a body. That is what lets us guarantee exactly one yandere, move it between dimensions and
 * respawn it without losing its feelings.
 */
public class YandereData extends SavedData {
    private static final String NAME = "yanderemod_obsession";
    private static final int MAX_CHESTS = 48;

    public enum Phase { STALKING, APPROACHING, COMPANION }

    public record ChestEntry(ResourceLocation dim, BlockPos pos) {}

    @Nullable public UUID obsession;
    @Nullable public UUID entityId;

    public int affection = 50;
    public Phase phase = Phase.STALKING;
    public boolean staying;

    // timers in ticks, -1 = "roll a fresh value when needed"
    public int phaseTimer = -1;
    public int signTimer = -1;
    public int giftTimer = -1;
    public int chatTimer = -1;

    public int spawnDelay;
    public int respawnTimer;
    public int decayTimer;
    public int bondTimer;
    public int devotionCooldown;
    public int jealousyCooldown;
    public int patCooldown;
    public long ageTicks;

    public final List<ChestEntry> chests = new ArrayList<>();

    public static YandereData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(YandereData::load, YandereData::new, NAME);
    }

    public void addAffection(int delta) {
        int before = affection;
        affection = Mth.clamp(affection + delta, 0, 100);
        if (before != affection) setDirty();
    }

    public void addChest(ResourceLocation dim, BlockPos pos) {
        for (ChestEntry c : chests) {
            if (c.dim().equals(dim) && c.pos().equals(pos)) return;
        }
        chests.add(new ChestEntry(dim, pos));
        while (chests.size() > MAX_CHESTS) chests.remove(0);
        setDirty();
    }

    /** Wipes mood + progress (keeps who it is obsessed with and the tracked chests). */
    public void resetBehaviour() {
        affection = 50;
        phase = Phase.STALKING;
        staying = false;
        phaseTimer = -1;
        signTimer = -1;
        giftTimer = -1;
        chatTimer = -1;
        respawnTimer = 0;
        decayTimer = 0;
        bondTimer = 0;
        devotionCooldown = 0;
        jealousyCooldown = 0;
        patCooldown = 0;
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (obsession != null) tag.putUUID("Obsession", obsession);
        if (entityId != null) tag.putUUID("EntityId", entityId);
        tag.putInt("Affection", affection);
        tag.putString("Phase", phase.name());
        tag.putBoolean("Staying", staying);
        tag.putInt("PhaseTimer", phaseTimer);
        tag.putInt("SignTimer", signTimer);
        tag.putInt("GiftTimer", giftTimer);
        tag.putInt("ChatTimer", chatTimer);
        tag.putInt("SpawnDelay", spawnDelay);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putInt("DecayTimer", decayTimer);
        tag.putInt("BondTimer", bondTimer);
        tag.putInt("DevotionCooldown", devotionCooldown);
        tag.putInt("JealousyCooldown", jealousyCooldown);
        tag.putInt("PatCooldown", patCooldown);
        tag.putLong("AgeTicks", ageTicks);

        ListTag list = new ListTag();
        for (ChestEntry c : chests) {
            CompoundTag t = new CompoundTag();
            t.putString("Dim", c.dim().toString());
            t.putInt("X", c.pos().getX());
            t.putInt("Y", c.pos().getY());
            t.putInt("Z", c.pos().getZ());
            list.add(t);
        }
        tag.put("Chests", list);
        return tag;
    }

    public static YandereData load(CompoundTag tag) {
        YandereData d = new YandereData();
        if (tag.hasUUID("Obsession")) d.obsession = tag.getUUID("Obsession");
        if (tag.hasUUID("EntityId")) d.entityId = tag.getUUID("EntityId");
        d.affection = tag.contains("Affection") ? Mth.clamp(tag.getInt("Affection"), 0, 100) : 50;
        try {
            d.phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException ex) {
            d.phase = Phase.STALKING;
        }
        d.staying = tag.getBoolean("Staying");
        d.phaseTimer = geti(tag, "PhaseTimer", -1);
        d.signTimer = geti(tag, "SignTimer", -1);
        d.giftTimer = geti(tag, "GiftTimer", -1);
        d.chatTimer = geti(tag, "ChatTimer", -1);
        d.spawnDelay = geti(tag, "SpawnDelay", 0);
        d.respawnTimer = geti(tag, "RespawnTimer", 0);
        d.decayTimer = geti(tag, "DecayTimer", 0);
        d.bondTimer = geti(tag, "BondTimer", 0);
        d.devotionCooldown = geti(tag, "DevotionCooldown", 0);
        d.jealousyCooldown = geti(tag, "JealousyCooldown", 0);
        d.patCooldown = geti(tag, "PatCooldown", 0);
        d.ageTicks = tag.getLong("AgeTicks");

        ListTag list = tag.getList("Chests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("Dim"));
            if (dim == null) continue;
            d.chests.add(new ChestEntry(dim, new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"))));
        }
        return d;
    }

    private static int geti(CompoundTag tag, String key, int def) {
        return tag.contains(key) ? tag.getInt(key) : def;
    }
}
