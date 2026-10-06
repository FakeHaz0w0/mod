package com.yanderemod.entity;

import com.yanderemod.config.YandereConfig;
import com.yanderemod.data.YandereData;
import com.yanderemod.util.YandereActions;
import com.yanderemod.util.YandereLines;
import com.yanderemod.util.YandereUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.GoalUtils;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The obsessive yandere. The entity is only a body: mood, phase and timers live in {@link YandereData}.
 *
 * Mood (affection 0-100, starts at 50):
 *   below aggressiveThreshold (30)  -> AGGRESSIVE: attacks anything near its obsession
 *   shyThreshold (60) and up        -> shy: flees with a diamond when caught
 *   below shyThreshold              -> keeps approaching, hands over a withered rose
 *   companionThreshold (75) and up  -> COMPANION: follows and protects
 */
public class YandereEntity extends PathfinderMob {

    public enum Mode { NORMAL, AGGRESSIVE, COMPANION }

    private enum Action { NONE, FLEE, LINGER }

    private Action action = Action.NONE;
    private int actionTicks;
    private int seenTicks;
    private int provokeTicks;
    private int hitCooldown;
    private int sayCooldown;

    public YandereEntity(EntityType<? extends YandereEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        if (GoalUtils.hasGroundPathNavigation(this)) {
            ((GroundPathNavigation) this.getNavigation()).setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)      // +5 from the iron sword it pulls out to fight
                .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25D, true));
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    // ================================================================== helpers

    public static Mode modeOf(YandereData d) {
        if (d.phase == YandereData.Phase.COMPANION) return Mode.COMPANION;
        return d.affection < YandereConfig.AGGRESSIVE_THRESHOLD.get() ? Mode.AGGRESSIVE : Mode.NORMAL;
    }

    private boolean isObsession(Entity e) {
        if (this.level().isClientSide || !(e instanceof ServerPlayer sp)) return false;
        MinecraftServer server = this.level().getServer();
        return server != null && sp.getUUID().equals(YandereData.get(server).obsession);
    }

    public void speak(ServerLevel level, String line, ChatFormatting color, boolean force) {
        if (!force && sayCooldown > 0) return;
        sayCooldown = 40;
        YandereActions.say(level, this, line, color);
    }

    private void poof(ServerLevel level) {
        level.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 1.0D, getZ(), 14, 0.3D, 0.6D, 0.3D, 0.02D);
    }

    private void hearts(ServerLevel level, double yOffset, int count) {
        level.sendParticles(ParticleTypes.HEART, getX(), getY() + yOffset, getZ(), count, 0.4D, 0.3D, 0.4D, 0.0D);
    }

    /** Teleports somewhere the player is not looking. */
    private void relocate(ServerLevel level, ServerPlayer p, double min, double max, boolean behind) {
        BlockPos pos = YandereUtil.findHiddenPos(level, p, min, max, behind);
        if (pos == null) return;
        poof(level);
        this.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.getYRot(), 0.0F);
        this.getNavigation().stop();
        poof(level);
    }

    private void syncHeldItem(YandereData d, Mode mode) {
        Item want;
        if (getTarget() != null) want = Items.IRON_SWORD;
        else if (mode == Mode.COMPANION) want = Items.ROSE_BUSH;
        else if (d.affection >= YandereConfig.SHY_THRESHOLD.get()) want = Items.POPPY;
        else want = Items.WITHER_ROSE;
        if (!getMainHandItem().is(want)) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(want));
    }

    /** Only one body may exist. If another live body is registered, this one removes itself. */
    private boolean claimIdentity(ServerLevel level, YandereData d) {
        UUID mine = getUUID();
        if (d.entityId == null) {
            d.entityId = mine;
            d.setDirty();
            return true;
        }
        if (d.entityId.equals(mine)) return true;
        for (ServerLevel l : level.getServer().getAllLevels()) {
            Entity other = l.getEntity(d.entityId);
            if (other != null && other.isAlive()) {
                this.discard();
                return false;
            }
        }
        d.entityId = mine;
        d.setDirty();
        return true;
    }

    // ================================================================== never attack the obsession

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && isObsession(target)) return;
        super.setTarget(target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !isObsession(target) && super.canAttack(target);
    }

    /** Make it hunt something for a while (retaliation, defending the obsession...). */
    public void provoke(LivingEntity target, int ticks) {
        if (target == this || target instanceof YandereEntity || isObsession(target) || !canAttack(target)) return;
        provokeTicks = ticks;
        setTarget(target);
    }

    /** Someone hurt the obsession. */
    public void defend(LivingEntity attacker) {
        if (!(this.level() instanceof ServerLevel level)) return;
        provoke(attacker, 600);
        if (getTarget() == attacker) {
            speak(level, YandereLines.pick(YandereLines.PROTECT, getRandom()), ChatFormatting.DARK_RED, false);
        }
    }

    /** Called from the death event when this entity killed something. */
    public void onKilled(LivingEntity victim) {
        if (!(this.level() instanceof ServerLevel level)) return;
        if (victim == this || isObsession(victim)) return;
        Mode mode = modeOf(YandereData.get(level.getServer()));
        if (mode == Mode.COMPANION) {
            if (getRandom().nextInt(3) == 0) {
                speak(level, YandereLines.pick(YandereLines.PROTECT, getRandom()), ChatFormatting.LIGHT_PURPLE, false);
            }
        } else {
            speak(level, YandereLines.ALL_MINE, ChatFormatting.DARK_RED, true);
            level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY() + 0.8D, victim.getZ(), 10, 0.3D, 0.4D, 0.3D, 0.03D);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel level
                && source.getEntity() instanceof ServerPlayer attacker) {
            YandereData d = YandereData.get(level.getServer());
            if (attacker.getUUID().equals(d.obsession)) {
                if (hitCooldown <= 0) {
                    hitCooldown = 20;
                    d.addAffection(-YandereConfig.HIT_PENALTY.get());
                    speak(level, YandereLines.pick(YandereLines.HURT, getRandom()), ChatFormatting.DARK_PURPLE, true);
                }
            } else {
                provoke(attacker, 400);
            }
        }
        return super.hurt(source, amount);
    }

    // ================================================================== interaction

    /** Affection a held item is worth when given to it (0 = not a gift). */
    public static int affectionFor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(Items.WITHER_ROSE)) return -5;
        if (stack.is(Items.ROSE_BUSH)) return YandereConfig.ROSE_BUSH_AFFECTION.get();
        if (stack.is(Items.POPPY)) return YandereConfig.POPPY_AFFECTION.get();
        if (stack.is(Items.CAKE)) return 12;
        if (stack.is(ItemTags.SMALL_FLOWERS)) return 2;
        return 0;
    }

    /** Flowers (not the wither rose) draw it close so it can be right-clicked. */
    private static boolean isLureItem(ItemStack s) {
        return !s.isEmpty() && !s.is(Items.WITHER_ROSE) && (s.is(Items.ROSE_BUSH) || s.is(ItemTags.SMALL_FLOWERS));
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean gift = affectionFor(stack) != 0;
        boolean pat = stack.isEmpty() && hand == InteractionHand.MAIN_HAND;
        if (!gift && !pat) return super.mobInteract(player, hand);
        if (this.level().isClientSide) return InteractionResult.sidedSuccess(true);
        if (!(player instanceof ServerPlayer sp) || !(this.level() instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }

        YandereData d = YandereData.get(level.getServer());
        if (!sp.getUUID().equals(d.obsession)) {
            speak(level, YandereLines.pick(YandereLines.STRANGER, getRandom()), ChatFormatting.DARK_RED, false);
            provoke(sp, 200);
            return InteractionResult.sidedSuccess(false);
        }

        if (gift) acceptGift(level, d, sp, stack);
        else acceptPat(level, d, sp);
        return InteractionResult.sidedSuccess(false);
    }

    private void acceptGift(ServerLevel level, YandereData d, ServerPlayer sp, ItemStack stack) {
        int delta = affectionFor(stack);
        String line = stack.is(Items.CAKE)
                ? YandereLines.pick(YandereLines.CAKE_THANKS, getRandom())
                : YandereLines.pick(YandereLines.FLOWER_THANKS, getRandom());
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        d.addAffection(delta);
        if (delta < 0) {
            speak(level, YandereLines.WITHER_ROSE_INSULT, ChatFormatting.DARK_PURPLE, true);
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 2.0D, getZ(), 4, 0.3D, 0.2D, 0.3D, 0.0D);
            return;
        }
        hearts(level, 1.9D, 8);
        level.playSound(null, blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0F, 1.0F);
        speak(level, line, ChatFormatting.LIGHT_PURPLE, true);
    }

    private void acceptPat(ServerLevel level, YandereData d, ServerPlayer sp) {
        if (d.phase == YandereData.Phase.COMPANION && sp.isShiftKeyDown()) {
            d.staying = !d.staying;
            getNavigation().stop();
            speak(level, d.staying ? "Okay. I'll wait right here for you." : "I'm coming with you.",
                    ChatFormatting.LIGHT_PURPLE, true);
            d.setDirty();
            return;
        }
        if (d.patCooldown <= 0) {
            d.patCooldown = 1200;
            d.addAffection(1);
            hearts(level, 1.9D, 5);
            level.playSound(null, blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0F, 1.2F);
            speak(level, YandereLines.pick(YandereLines.PAT, getRandom()), ChatFormatting.LIGHT_PURPLE, true);
        } else {
            hearts(level, 1.9D, 1);
        }
    }

    // ================================================================== lifecycle announcements (called by the manager)

    public void announceCompanion(ServerLevel level, ServerPlayer p) {
        action = Action.NONE;
        actionTicks = 0;
        if (distanceTo(p) > 10.0F) relocate(level, p, 3.0D, 6.0D, false);
        hearts(level, 1.9D, 14);
        level.playSound(null, p.blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0F, 0.9F);
        speak(level, YandereLines.COMPANION_JOIN, ChatFormatting.LIGHT_PURPLE, true);
    }

    public void announceRevoked(ServerLevel level, ServerPlayer p) {
        action = Action.NONE;
        speak(level, YandereLines.REVOKED, ChatFormatting.DARK_PURPLE, true);
        relocate(level, p, YandereConfig.STALK_DISTANCE_MIN.get(), YandereConfig.STALK_DISTANCE_MAX.get(), false);
    }

    // ================================================================== the brain

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(this.level() instanceof ServerLevel level)) return;
        YandereData d = YandereData.get(level.getServer());
        if (!claimIdentity(level, d)) return;

        if (hitCooldown > 0) hitCooldown--;
        if (sayCooldown > 0) sayCooldown--;
        if (provokeTicks > 0) provokeTicks--;

        ServerPlayer p = d.obsession == null ? null : level.getServer().getPlayerList().getPlayer(d.obsession);
        if (p == null || p.level() != level || p.isSpectator()) {
            if (provokeTicks <= 0 && getTarget() != null) setTarget(null);
            return;
        }

        Mode mode = modeOf(d);
        double dist = distanceTo(p);

        if (tickCount % 10 == 0) updateTarget(level, d, p, mode);
        if (tickCount % 20 == 0) secondTick(level, d, p, mode, dist);

        // fighting: MeleeAttackGoal drives movement
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) return;

        switch (action) {
            case FLEE -> fleeTick(level, d, p, dist);
            case LINGER -> lingerTick(level, d, p);
            default -> {
                if (d.phase != YandereData.Phase.COMPANION && isLured(p, dist)) {
                    lureTick(level, p, dist);
                } else {
                    switch (d.phase) {
                        case STALKING -> stalkTick(level, p, dist);
                        case APPROACHING -> approachTick(level, d, p, dist);
                        case COMPANION -> companionTick(level, d, p, dist);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- targeting (aggressive mood + companion)

    private void updateTarget(ServerLevel level, YandereData d, ServerPlayer p, Mode mode) {
        LivingEntity current = getTarget();

        if (provokeTicks > 0) {
            if (current != null && current.isAlive()) return;
            provokeTicks = 0;
            setTarget(null);
            current = null;
        }

        if (mode == Mode.NORMAL) {
            if (current != null) setTarget(null);
            syncHeldItem(d, mode);
            return;
        }

        if (current != null && current.isAlive() && isValidGuardTarget(current, p, mode)) return;

        double r = YandereConfig.GUARD_RADIUS.get();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                cand -> isValidGuardTarget(cand, p, mode))) {
            double dd = e.distanceToSqr(p);
            if (dd < bestDist) {
                bestDist = dd;
                best = e;
            }
        }
        setTarget(best);
        syncHeldItem(d, mode);
    }

    private boolean isValidGuardTarget(LivingEntity e, ServerPlayer p, Mode mode) {
        if (e == this || e == p || !e.isAlive() || e instanceof YandereEntity || e instanceof ArmorStand) return false;
        if (!canAttack(e)) return false;
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        if (e instanceof TamableAnimal t && p.getUUID().equals(t.getOwnerUUID())) return false;

        double r = YandereConfig.GUARD_RADIUS.get() + 4.0D;
        if (e.distanceToSqr(p) > r * r) return false;

        if (mode == Mode.COMPANION) {
            if (!(e instanceof Enemy)) return false;
            // do not poke angry-able neutrals unless they are already after the player
            if (e instanceof NeutralMob nm) return nm.getTarget() == p;
            return true;
        }
        // aggressive: anything alive near the obsession, other players only if allowed
        if (e instanceof Player) return YandereConfig.ATTACK_OTHER_PLAYERS.get();
        return true;
    }

    // ---------------------------------------------------------------- once per second

    private void secondTick(ServerLevel level, YandereData d, ServerPlayer p, Mode mode, double dist) {
        RandomSource r = getRandom();
        if (d.phaseTimer < 0) d.phaseTimer = YandereActions.rollStalkTimer(r);
        if (d.signTimer < 0) d.signTimer = YandereActions.rollSignTimer(r) / 4;   // first sign shows up soon
        if (d.giftTimer < 0) d.giftTimer = YandereActions.rollGiftTimer(r) / 2;
        if (d.chatTimer < 0) d.chatTimer = YandereActions.rollChatTimer(r);

        syncHeldItem(d, mode);

        switch (d.phase) {
            case STALKING -> stalkingSecond(level, d, p);
            case APPROACHING -> approachingSecond(level, d, p, dist);
            case COMPANION -> companionSecond(level, d, p, dist);
        }
        d.setDirty();
    }

    private void stalkingSecond(ServerLevel level, YandereData d, ServerPlayer p) {
        RandomSource r = getRandom();
        if (action != Action.NONE) return;

        if (YandereConfig.ENABLE_SIGNS.get()) {
            d.signTimer -= 20;
            if (d.signTimer <= 0) {
                boolean ok = YandereActions.placeSign(level, p, d.affection, r);
                d.signTimer = ok ? YandereActions.rollSignTimer(r) : 100;
            }
        }
        if (YandereConfig.ENABLE_GIFTS.get()) {
            d.giftTimer -= 20;
            if (d.giftTimer <= 0) {
                boolean ok = YandereActions.giftChest(level, p, d, r);
                d.giftTimer = ok ? YandereActions.rollGiftTimer(r) : 600;
            }
        }

        d.phaseTimer -= 20;
        if (d.phaseTimer <= 0) {
            if (p.isSleeping() || p.isSpectator()) d.phaseTimer = 200;
            else beginApproach(level, d, p);
        }
    }

    private void approachingSecond(ServerLevel level, YandereData d, ServerPlayer p, double dist) {
        if (action != Action.NONE) return;
        d.phaseTimer -= 20;

        if (p.isSleeping()) {
            endEncounter(level, d, p);
            return;
        }
        if (d.phaseTimer <= 0) {
            if (dist <= 10.0D) completeApproach(level, d, p);
            else endEncounter(level, d, p);
            return;
        }
        // stuck? hop closer behind the player every ~10s
        if (dist > 8.0D && getNavigation().isDone() && d.phaseTimer % 200 == 0) {
            relocate(level, p, 8.0D, 12.0D, true);
        }
    }

    private void companionSecond(ServerLevel level, YandereData d, ServerPlayer p, double dist) {
        RandomSource r = getRandom();

        if (!d.staying && dist > 28.0D) {
            relocate(level, p, 3.0D, 6.0D, false);
            dist = distanceTo(p);
        }
        if (getHealth() < getMaxHealth()) heal(1.0F);
        if (dist > 16.0D) return;

        // --- love perks, refreshed every second while near ---
        if (p.getHealth() < p.getMaxHealth()) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, false, true));
        }
        if (d.affection >= 85) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0, true, false, true));
        }
        if (d.affection >= 95) {
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0, true, false, true));
        }

        d.chatTimer -= 20;
        if (d.chatTimer <= 0) {
            speak(level, YandereLines.pick(YandereLines.COMPANION_CHAT, r), ChatFormatting.LIGHT_PURPLE, false);
            hearts(level, 1.9D, 3);
            d.chatTimer = YandereActions.rollChatTimer(r);
        }

        d.giftTimer -= 20;
        if (d.giftTimer <= 0) {
            companionGift(level, d, p);
            d.giftTimer = YandereActions.rollGiftTimer(r);
        }
    }

    private void companionGift(ServerLevel level, YandereData d, ServerPlayer p) {
        RandomSource r = getRandom();
        ItemStack gift;
        if (p.getFoodData().getFoodLevel() < 8) {
            gift = new ItemStack(Items.COOKED_BEEF, 3);                     // it notices when you are hungry
        } else {
            int roll = r.nextInt(100) + (d.affection >= 90 ? 10 : 0);
            if (roll < 35) gift = new ItemStack(Items.IRON_INGOT, Mth.nextInt(r, 2, 6));
            else if (roll < 60) gift = new ItemStack(Items.GOLD_INGOT, Mth.nextInt(r, 1, 3));
            else if (roll < 80) gift = new ItemStack(Items.GOLDEN_CARROT, Mth.nextInt(r, 2, 4));
            else if (roll < 95) gift = new ItemStack(Items.EMERALD, Mth.nextInt(r, 1, 2));
            else gift = new ItemStack(Items.DIAMOND, 1);
        }
        YandereUtil.giveItem(p, gift);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 1.2F);
        hearts(level, 1.9D, 4);
        speak(level, YandereLines.GIFT, ChatFormatting.LIGHT_PURPLE, false);
    }

    // ---------------------------------------------------------------- phase 1: stalking from afar

    private void stalkTick(ServerLevel level, ServerPlayer p, double dist) {
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);

        // it hides if you stare at it
        boolean seen = dist < 48.0D && YandereUtil.isLookingAt(p, this, 0.92D);
        seenTicks = seen ? seenTicks + 1 : Math.max(0, seenTicks - 2);
        if (seenTicks > 60 || (seen && dist < 8.0D)) {
            seenTicks = 0;
            relocate(level, p, YandereConfig.STALK_DISTANCE_MIN.get() + 4, YandereConfig.STALK_DISTANCE_MAX.get(), false);
            return;
        }
        if (tickCount % 10 != 0) return;

        double minDist = p.isSleeping() ? 7.0D : YandereConfig.STALK_DISTANCE_MIN.get();
        double maxDist = YandereConfig.STALK_DISTANCE_MAX.get();

        if (dist < minDist) {
            Vec3 away = DefaultRandomPos.getPosAway(this, 14, 6, p.position());
            if (away != null) this.getNavigation().moveTo(away.x, away.y, away.z, 1.1D);
        } else if (dist > maxDist) {
            if (dist > maxDist + 40.0D) relocate(level, p, maxDist - 8.0D, maxDist, false);
            else this.getNavigation().moveTo(p, 1.0D);
        } else if (this.getNavigation().isDone() && getRandom().nextInt(4) == 0) {
            BlockPos pos = YandereUtil.findHiddenPos(level, p, minDist + 2.0D, maxDist - 2.0D, false);
            if (pos != null) this.getNavigation().moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.85D);
        }
    }

    // ---------------------------------------------------------------- phase 2: slowly approaching from behind

    private void beginApproach(ServerLevel level, YandereData d, ServerPlayer p) {
        BlockPos pos = YandereUtil.findHiddenPos(level, p, 20.0D, 28.0D, true);
        if (pos == null) {
            d.phaseTimer = 400;
            return;
        }
        poof(level);
        this.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, this.getYRot(), 0.0F);
        this.getNavigation().stop();
        d.phase = YandereData.Phase.APPROACHING;
        d.phaseTimer = 2400;
        seenTicks = 0;
        d.setDirty();
    }

    private void approachTick(ServerLevel level, YandereData d, ServerPlayer p, double dist) {
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);

        // heartbeat that gets louder as it closes in
        if (tickCount % 30 == 0 && dist < 22.0D) {
            float vol = 0.4F + (float) (1.0D - dist / 22.0D) * 0.9F;
            level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, vol, 1.0F);
        }

        boolean shy = d.affection >= YandereConfig.SHY_THRESHOLD.get();
        boolean caught = dist < 24.0D && YandereUtil.isLookingAt(p, this, 0.78D);
        if (shy && caught) {
            startCaught(level, d, p);
            return;
        }

        double speed = d.affection < YandereConfig.AGGRESSIVE_THRESHOLD.get() ? 0.75D : 0.6D;
        if (tickCount % 10 == 0 || this.getNavigation().isDone()) {
            this.getNavigation().moveTo(p, speed);
        }
        if (dist <= 2.2D) completeApproach(level, d, p);
    }

    /** Shy and caught: leaves a diamond behind and runs. */
    private void startCaught(ServerLevel level, YandereData d, ServerPlayer p) {
        this.spawnAtLocation(new ItemStack(Items.DIAMOND));
        d.addAffection(2);
        action = Action.FLEE;
        actionTicks = 70;
        hearts(level, 1.9D, 6);
        level.playSound(null, blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.6F);
    }

    private void fleeTick(ServerLevel level, YandereData d, ServerPlayer p, double dist) {
        actionTicks--;
        if (tickCount % 5 == 0 || this.getNavigation().isDone()) {
            Vec3 away = DefaultRandomPos.getPosAway(this, 24, 8, p.position());
            if (away != null) this.getNavigation().moveTo(away.x, away.y, away.z, 1.45D);
        }
        if (actionTicks <= 0 || (dist > 24.0D && actionTicks < 50)) {
            // red, no name, just "..!"
            p.sendSystemMessage(Component.literal(YandereLines.CAUGHT).withStyle(ChatFormatting.RED));
            endEncounter(level, d, p);
        }
    }

    /** It reached you. Shy: an unseen hug. Otherwise: a withered rose and a plea. */
    private void completeApproach(ServerLevel level, YandereData d, ServerPlayer p) {
        this.getNavigation().stop();
        boolean shy = d.affection >= YandereConfig.SHY_THRESHOLD.get();
        if (shy) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 160, 0));
            level.sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2D, p.getZ(), 10, 0.4D, 0.4D, 0.4D, 0.02D);
            level.playSound(null, p.blockPosition(), SoundEvents.CAT_PURR, SoundSource.NEUTRAL, 1.0F, 1.0F);
            speak(level, YandereLines.pick(YandereLines.HUG, getRandom()), ChatFormatting.LIGHT_PURPLE, true);
            d.addAffection(4);
            action = Action.LINGER;
            actionTicks = 40;
        } else {
            YandereUtil.giveItem(p, new ItemStack(Items.WITHER_ROSE));
            level.playSound(null, p.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.7F, 0.8F);
            speak(level, YandereLines.PLEASE_LOVE_ME, ChatFormatting.LIGHT_PURPLE, true);
            action = Action.LINGER;
            actionTicks = 60;
        }
    }

    private void lingerTick(ServerLevel level, YandereData d, ServerPlayer p) {
        this.getNavigation().stop();
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);
        if (--actionTicks <= 0) endEncounter(level, d, p);
    }

    /** Back to phase 1: vanish, wait, watch again. */
    private void endEncounter(ServerLevel level, YandereData d, ServerPlayer p) {
        action = Action.NONE;
        actionTicks = 0;
        seenTicks = 0;
        d.phase = YandereData.Phase.STALKING;
        d.phaseTimer = YandereActions.rollStalkTimer(getRandom());
        relocate(level, p, YandereConfig.STALK_DISTANCE_MIN.get() + 4, YandereConfig.STALK_DISTANCE_MAX.get(), false);
        d.setDirty();
    }

    // ---------------------------------------------------------------- flower lure

    private boolean isLured(ServerPlayer p, double dist) {
        if (!YandereConfig.FLOWER_LURE.get() || dist > 24.0D) return false;
        if (!isLureItem(p.getMainHandItem()) && !isLureItem(p.getOffhandItem())) return false;
        return this.hasLineOfSight(p);
    }

    private void lureTick(ServerLevel level, ServerPlayer p, double dist) {
        this.getLookControl().setLookAt(p, 30.0F, 30.0F);
        if (dist > 2.4D) {
            if (tickCount % 5 == 0) this.getNavigation().moveTo(p, 0.8D);
        } else {
            this.getNavigation().stop();
        }
        if (tickCount % 40 == 0) hearts(level, 1.9D, 2);
    }

    // ---------------------------------------------------------------- companion

    private void companionTick(ServerLevel level, YandereData d, ServerPlayer p, double dist) {
        if (dist < 8.0D) this.getLookControl().setLookAt(p, 30.0F, 30.0F);
        if (d.staying) {
            this.getNavigation().stop();
            return;
        }
        if (tickCount % 10 != 0) return;
        if (dist > 4.0D) this.getNavigation().moveTo(p, dist > 10.0D ? 1.3D : 1.1D);
        else if (dist < 2.0D) this.getNavigation().stop();
    }
}
