package com.yanderemod.event;

import com.yanderemod.YandereMod;
import com.yanderemod.config.YandereConfig;
import com.yanderemod.data.YandereData;
import com.yanderemod.entity.YandereEntity;
import com.yanderemod.registry.ModEntities;
import com.yanderemod.util.YandereLines;
import com.yanderemod.util.YandereUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;

/**
 * Server-side glue: picks the obsession, keeps exactly one yandere alive near them, moves affection
 * between phases, and reacts to world events (chests placed, fights, deaths, jealousy...).
 */
@Mod.EventBusSubscriber(modid = YandereMod.MODID)
public class YandereManager {
    private static int counter;
    private static int missingTicks;

    // ================================================================== helpers

    @Nullable
    public static YandereEntity find(MinecraftServer server, YandereData d) {
        if (d.entityId == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(d.entityId);
            if (e instanceof YandereEntity y && y.isAlive()) return y;
        }
        return null;
    }

    private static boolean isObsession(YandereData d, Entity e) {
        return e instanceof ServerPlayer sp && sp.getUUID().equals(d.obsession);
    }

    // ================================================================== obsession selection

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        YandereData d = YandereData.get(server);

        if (d.obsession == null) {
            // the FIRST player to ever join is the one it falls for
            d.obsession = sp.getUUID();
            d.spawnDelay = YandereConfig.INITIAL_SPAWN_DELAY.get() * 20;
        } else if (d.obsession.equals(sp.getUUID())) {
            d.spawnDelay = Math.max(d.spawnDelay, YandereConfig.REJOIN_SPAWN_DELAY.get() * 20);
        }
        d.setDirty();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        YandereData d = YandereData.get(server);
        if (!isObsession(d, sp)) return;
        YandereEntity y = find(server, d);
        if (y != null) y.discard();
        d.entityId = null;
        missingTicks = 0;
        d.setDirty();
    }

    // ================================================================== the heartbeat of the mod

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++counter % 20 != 0) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) secondTick(server);
    }

    private static void secondTick(MinecraftServer server) {
        YandereData d = YandereData.get(server);
        if (d.obsession == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(d.obsession);
        if (p == null) return;

        d.ageTicks += 20;
        d.patCooldown = Math.max(0, d.patCooldown - 20);
        d.jealousyCooldown = Math.max(0, d.jealousyCooldown - 20);
        d.devotionCooldown = Math.max(0, d.devotionCooldown - 20);

        YandereEntity e = find(server, d);

        // --- slow affection drift ---
        if (d.phase != YandereData.Phase.COMPANION) {
            int decay = YandereConfig.DECAY_SECONDS.get();
            if (decay > 0) {
                d.decayTimer += 20;
                if (d.decayTimer >= decay * 20) {
                    d.decayTimer = 0;
                    d.addAffection(-1);
                }
            }
        } else if (e != null && e.level() == p.level() && e.distanceTo(p) < 12.0F) {
            d.bondTimer += 20;
            if (d.bondTimer >= 3600) {          // time together makes it love you more
                d.bondTimer = 0;
                d.addAffection(1);
            }
        }

        updatePhase(d, p, e);

        // --- keep exactly one body around ---
        if (e == null) {
            missingTicks += 20;
            if (d.respawnTimer > 0) d.respawnTimer -= 20;
            if (d.spawnDelay > 0) d.spawnDelay -= 20;
            if (missingTicks >= 200 && d.respawnTimer <= 0 && d.spawnDelay <= 0) {
                if (spawn(p, d)) missingTicks = 0;
            }
        } else {
            missingTicks = 0;
            if (e.level() != p.level()) {
                // the obsession changed dimension: drop the old body, a new one appears next to them
                e.discard();
                d.entityId = null;
                d.spawnDelay = Math.max(d.spawnDelay, 60);
                missingTicks = 200;
            }
        }
        d.setDirty();
    }

    /** Affection thresholds decide whether it is a companion or not. */
    private static void updatePhase(YandereData d, ServerPlayer p, @Nullable YandereEntity e) {
        boolean sameLevel = e != null && e.level() == p.level() && e.level() instanceof ServerLevel;
        if (d.phase == YandereData.Phase.COMPANION) {
            if (d.affection < YandereConfig.SHY_THRESHOLD.get()) {
                d.phase = YandereData.Phase.STALKING;
                d.staying = false;
                d.phaseTimer = -1;
                if (sameLevel) e.announceRevoked((ServerLevel) e.level(), p);
            }
        } else if (d.affection >= YandereConfig.COMPANION_THRESHOLD.get()) {
            d.phase = YandereData.Phase.COMPANION;
            d.staying = false;
            d.chatTimer = -1;
            d.giftTimer = -1;
            if (sameLevel) e.announceCompanion((ServerLevel) e.level(), p);
        }
    }

    private static boolean spawn(ServerPlayer p, YandereData d) {
        ServerLevel level = p.serverLevel();
        boolean companion = d.phase == YandereData.Phase.COMPANION;
        BlockPos pos = companion
                ? YandereUtil.findHiddenPos(level, p, 3.0D, 6.0D, false)
                : YandereUtil.findHiddenPos(level, p, 24.0D, 34.0D, false);
        if (pos == null) return false;
        YandereEntity e = ModEntities.YANDERE.get().create(level);
        if (e == null) return false;
        e.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, level.getRandom().nextFloat() * 360.0F, 0.0F);
        d.entityId = e.getUUID();
        level.addFreshEntity(e);
        d.setDirty();
        return true;
    }

    // ================================================================== chests the player placed

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        Block b = event.getPlacedBlock().getBlock();
        if (!(b instanceof ChestBlock) && !(b instanceof BarrelBlock)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        YandereData d = YandereData.get(server);
        if (!isObsession(d, sp)) return;
        d.addChest(level.dimension().location(), event.getPos().immutable());
    }

    // ================================================================== fights

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.level().isClientSide) return;
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == sp || attacker instanceof YandereEntity) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        YandereData d = YandereData.get(server);
        if (!isObsession(d, sp)) return;
        YandereEntity y = find(server, d);
        if (y == null || y.level() != sp.level() || y.distanceTo(sp) > 48.0F) return;
        y.defend(attacker);       // anyone who hurts its obsession is marked
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;

        Entity killer = event.getSource().getEntity();
        if (killer instanceof YandereEntity y) y.onKilled(victim);

        YandereData d = YandereData.get(server);

        // ---- the yandere itself died ----
        if (victim instanceof YandereEntity y) {
            if (y.getUUID().equals(d.entityId)) {
                d.entityId = null;
                d.respawnTimer = YandereConfig.RESPAWN_DELAY.get() * 20;
                if (killer instanceof ServerPlayer sp && isObsession(d, sp)) {
                    d.affection = YandereConfig.RESPAWN_AFFECTION.get();
                    d.phase = YandereData.Phase.STALKING;
                    d.staying = false;
                    d.phaseTimer = -1;
                    if (y.level() instanceof ServerLevel sl) {
                        y.speak(sl, YandereLines.FORGIVE, ChatFormatting.DARK_PURPLE, true);
                    }
                }
                d.setDirty();
            }
            return;
        }

        // ---- devotion: a deeply loving companion will not let its obsession die ----
        if (victim instanceof ServerPlayer sp && isObsession(d, sp) && YandereConfig.DEVOTION_SAVE.get()
                && d.phase == YandereData.Phase.COMPANION && d.affection >= 90 && d.devotionCooldown <= 0) {
            DamageSource src = event.getSource();
            if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;   // void, /kill
            YandereEntity y = find(server, d);
            if (y == null || y.level() != sp.level() || y.distanceTo(sp) > 24.0F) return;

            event.setCanceled(true);
            sp.setHealth(Math.max(6.0F, sp.getMaxHealth() * 0.4F));
            sp.removeAllEffects();
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 1));
            sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 1));
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 2));
            sp.level().broadcastEntityEvent(sp, (byte) 35);                 // totem animation
            y.setHealth(Math.max(1.0F, y.getHealth() - 15.0F));             // it pays for it
            d.devotionCooldown = 36000;
            if (y.level() instanceof ServerLevel sl) {
                y.speak(sl, YandereLines.SAVE, ChatFormatting.LIGHT_PURPLE, true);
            }
            d.setDirty();
        }
    }

    // ================================================================== jealousy

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof AbstractVillager)) return;
        MinecraftServer server = sp.getServer();
        if (server == null) return;
        YandereData d = YandereData.get(server);
        if (!isObsession(d, sp) || d.jealousyCooldown > 0) return;

        d.jealousyCooldown = 1200;
        d.addAffection(-1);
        YandereEntity y = find(server, d);
        if (y != null && y.level() == sp.level() && y.distanceTo(sp) < 64.0F && y.level() instanceof ServerLevel sl) {
            y.speak(sl, YandereLines.pick(YandereLines.JEALOUS, y.getRandom()), ChatFormatting.DARK_PURPLE, false);
        }
    }
}
