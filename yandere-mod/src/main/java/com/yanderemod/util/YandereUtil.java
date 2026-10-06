package com.yanderemod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

public final class YandereUtil {
    private YandereUtil() {}

    /** True if the entity is roughly where the player's crosshair is AND not behind a wall. */
    public static boolean isLookingAt(ServerPlayer player, LivingEntity entity, double minDot) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(entity.getX() - player.getX(), entity.getEyeY() - player.getEyeY(), entity.getZ() - player.getZ()).normalize();
        return look.dot(to) > minDot && player.hasLineOfSight(entity);
    }

    /** Angle-only check against a point. */
    public static boolean inView(ServerPlayer player, Vec3 target, double minDot) {
        Vec3 look = player.getViewVector(1.0F).normalize();
        Vec3 to = target.subtract(player.getEyePosition()).normalize();
        return look.dot(to) > minDot;
    }

    public static boolean hasLineOfSight(ServerPlayer player, Vec3 target) {
        BlockHitResult hit = player.level().clip(new ClipContext(player.getEyePosition(), target,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** Finds a spot at (x,z) near the given height where something 2 tall can stand on solid ground. */
    @Nullable
    public static BlockPos findStandPos(ServerLevel level, int x, int aroundY, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (!level.isLoaded(pos.set(x, aroundY, z))) return null;
        for (int dy = 8; dy >= -12; dy--) {
            int y = aroundY + dy;
            if (y <= level.getMinBuildHeight() + 1 || y >= level.getMaxBuildHeight() - 3) continue;
            pos.set(x, y, z);
            BlockState feet = level.getBlockState(pos);
            BlockState head = level.getBlockState(pos.above());
            BlockPos belowPos = pos.below();
            BlockState ground = level.getBlockState(belowPos);
            if (feet.getCollisionShape(level, pos).isEmpty()
                    && head.getCollisionShape(level, pos.above()).isEmpty()
                    && level.getFluidState(pos).isEmpty()
                    && ground.isFaceSturdy(level, belowPos, Direction.UP)
                    && !ground.is(BlockTags.LEAVES)) {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * Picks a standing spot between minDist and maxDist from the player, preferring spots the player
     * cannot currently see. If {@code behind} is true, spots behind the player's back are preferred.
     */
    @Nullable
    public static BlockPos findHiddenPos(ServerLevel level, ServerPlayer player, double minDist, double maxDist, boolean behind) {
        RandomSource r = level.getRandom();
        Vec3 look = player.getLookAngle();
        double baseAngle = Math.atan2(look.z, look.x);
        BlockPos fallback = null;
        for (int i = 0; i < 24; i++) {
            double ang = behind
                    ? baseAngle + Math.PI + (r.nextDouble() - 0.5D) * Math.PI * 0.9D
                    : r.nextDouble() * Math.PI * 2.0D;
            double dist = minDist + r.nextDouble() * Math.max(0.5D, maxDist - minDist);
            int x = Mth.floor(player.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(player.getZ() + Math.sin(ang) * dist);
            BlockPos pos = findStandPos(level, x, player.getBlockY(), z);
            if (pos == null) continue;
            if (fallback == null) fallback = pos;
            Vec3 eyes = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.6D, pos.getZ() + 0.5D);
            boolean visible = inView(player, eyes, 0.35D) && hasLineOfSight(player, eyes);
            if (!visible) return pos;
        }
        return fallback;
    }

    /** Puts the stack in the player's inventory, dropping it at their feet if full. */
    public static void giveItem(ServerPlayer player, ItemStack stack) {
        boolean added = player.getInventory().add(stack);
        if (!added || !stack.isEmpty()) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) drop.setNoPickUpDelay();
        }
    }
}
