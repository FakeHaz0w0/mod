package com.yanderemod.util;

import com.yanderemod.config.YandereConfig;
import com.yanderemod.data.YandereData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;

/** Things the yandere does to the world: chat, signs, flowers, chest gifts. */
public final class YandereActions {
    private YandereActions() {}

    private static final Block[] SIGN_BLOCKS = {
            Blocks.OAK_SIGN, Blocks.SPRUCE_SIGN, Blocks.BIRCH_SIGN, Blocks.DARK_OAK_SIGN,
            Blocks.CRIMSON_SIGN, Blocks.WARPED_SIGN
    };

    // ------------------------------------------------------------------ timers (returns ticks)

    public static int rollStalkTimer(RandomSource r) {
        return secs(r, YandereConfig.STALK_MIN.get(), YandereConfig.STALK_MAX.get());
    }

    public static int rollSignTimer(RandomSource r) {
        return secs(r, YandereConfig.SIGN_MIN.get(), YandereConfig.SIGN_MAX.get());
    }

    public static int rollGiftTimer(RandomSource r) {
        return secs(r, YandereConfig.GIFT_MIN.get(), YandereConfig.GIFT_MAX.get());
    }

    public static int rollChatTimer(RandomSource r) {
        return secs(r, 150, 420);
    }

    private static int secs(RandomSource r, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        return Mth.nextInt(r, lo, hi) * 20;
    }

    // ------------------------------------------------------------------ chat

    /** "<Yandere> line" to the obsession player and anyone within 32 blocks of the speaker. */
    public static void say(ServerLevel level, Entity speaker, String line, ChatFormatting color) {
        YandereData d = YandereData.get(level.getServer());
        MutableComponent msg = Component.literal("<")
                .append(Component.translatable("entity.yanderemod.yandere"))
                .append(Component.literal("> " + line));
        msg.withStyle(color);
        for (ServerPlayer sp : level.players()) {
            if (sp.getUUID().equals(d.obsession) || sp.distanceToSqr(speaker) < 1024.0D) {
                sp.sendSystemMessage(msg);
            }
        }
    }

    // ------------------------------------------------------------------ signs

    /** Leaves a waxed standing sign near the player (or near their bed) where they are not looking. */
    public static boolean placeSign(ServerLevel level, ServerPlayer player, int affection, RandomSource r) {
        BlockPos center = player.blockPosition();
        BlockPos respawn = player.getRespawnPosition();
        if (respawn != null && player.getRespawnDimension() == level.dimension()
                && r.nextInt(100) < 30 && respawn.closerThan(center, 80.0D)) {
            center = respawn;
        }

        for (int attempt = 0; attempt < 16; attempt++) {
            double ang = r.nextDouble() * Math.PI * 2.0D;
            double dist = 4 + r.nextInt(11);
            int x = center.getX() + (int) Math.round(Math.cos(ang) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(ang) * dist);
            BlockPos pos = YandereUtil.findStandPos(level, x, center.getY(), z);
            if (pos == null) continue;

            BlockState current = level.getBlockState(pos);
            if (!(current.isAir() || current.canBeReplaced())) continue;

            Vec3 c = Vec3.atCenterOf(pos);
            if (YandereUtil.inView(player, c, 0.55D) && YandereUtil.hasLineOfSight(player, c)) continue;

            // turn the sign so its front faces the player
            double dx = player.getX() - (pos.getX() + 0.5D);
            double dz = player.getZ() - (pos.getZ() + 0.5D);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            int rotation = Mth.floor(yaw * 16.0F / 360.0F + 0.5D) & 15;

            Block block = SIGN_BLOCKS[r.nextInt(SIGN_BLOCKS.length)];
            BlockState state = block.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotation);
            if (!state.canSurvive(level, pos)) continue;

            level.setBlock(pos, state, 3);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SignBlockEntity sign) {
                writeSign(level, pos, state, sign, pickSign(affection, r), affection);
            }
            maybePlaceFlower(level, pos, affection, r);
            return true;
        }
        return false;
    }

    private static String[] pickSign(int affection, RandomSource r) {
        String[][] pool;
        if (affection >= YandereConfig.SHY_THRESHOLD.get()) pool = YandereLines.SIGNS_HIGH;
        else if (affection >= YandereConfig.AGGRESSIVE_THRESHOLD.get()) pool = YandereLines.SIGNS_MID;
        else pool = YandereLines.SIGNS_LOW;
        return pool[r.nextInt(pool.length)];
    }

    private static void writeSign(ServerLevel level, BlockPos pos, BlockState state, SignBlockEntity sign,
                                  String[] lines, int affection) {
        boolean high = affection >= YandereConfig.SHY_THRESHOLD.get();
        boolean low = affection < YandereConfig.AGGRESSIVE_THRESHOLD.get();
        CompoundTag tag = sign.saveWithoutMetadata();
        tag.put("front_text", textTag(lines, high ? "pink" : "red", low));
        tag.put("back_text", textTag(new String[]{"", "", "", ""}, "black", false));
        tag.putBoolean("is_waxed", true);
        sign.load(tag);
        sign.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static CompoundTag textTag(String[] lines, String color, boolean glowing) {
        CompoundTag t = new CompoundTag();
        ListTag messages = new ListTag();
        for (int i = 0; i < 4; i++) {
            String s = i < lines.length ? lines[i] : "";
            messages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(s))));
        }
        t.put("messages", messages);
        t.putString("color", color);
        t.putBoolean("has_glowing_text", glowing);
        return t;
    }

    /** A poppy (it likes you) or a wither rose (it does not) next to the sign. */
    private static void maybePlaceFlower(ServerLevel level, BlockPos signPos, int affection, RandomSource r) {
        if (r.nextInt(100) >= 35) return;
        Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        BlockPos fp = signPos.relative(dir);
        if (!level.getBlockState(fp).isAir()) return;
        Block flower = affection >= YandereConfig.SHY_THRESHOLD.get() ? Blocks.POPPY : Blocks.WITHER_ROSE;
        BlockState fs = flower.defaultBlockState();
        if (fs.canSurvive(level, fp)) level.setBlock(fp, fs, 3);
    }

    // ------------------------------------------------------------------ chest gifts

    /**
     * Drops a little gift (iron / coal, sometimes more) into the nearest chest or barrel the obsession
     * player placed. Returns true if something was delivered. Delivering raises affection by 1.
     */
    public static boolean giftChest(ServerLevel level, ServerPlayer player, YandereData d, RandomSource r) {
        double range = YandereConfig.CHEST_RANGE.get();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        Iterator<YandereData.ChestEntry> it = d.chests.iterator();
        while (it.hasNext()) {
            YandereData.ChestEntry c = it.next();
            if (!c.dim().equals(level.dimension().location())) continue;
            if (!level.isLoaded(c.pos())) continue;
            Block b = level.getBlockState(c.pos()).getBlock();
            if (!(b instanceof ChestBlock) && !(b instanceof BarrelBlock)) {
                it.remove(); // the chest is gone
                d.setDirty();
                continue;
            }
            double dist = c.pos().distSqr(player.blockPosition());
            if (dist > range * range) continue;
            // never let items appear in a chest the player is staring at from close by
            if (dist < 144.0D && YandereUtil.inView(player, Vec3.atCenterOf(c.pos()), 0.6D)) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = c.pos();
            }
        }
        if (best == null) return false;

        BlockEntity be = level.getBlockEntity(best);
        if (!(be instanceof Container container)) return false;

        ItemStack gift = rollChestGift(d.affection, r);
        int before = gift.getCount();
        ItemStack rest = insert(container, gift.copy());
        if (rest.getCount() >= before) return false; // chest full

        d.addAffection(1);
        level.playSound(null, best, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.0F);
        level.sendParticles(ParticleTypes.HEART, best.getX() + 0.5D, best.getY() + 1.1D, best.getZ() + 0.5D,
                3, 0.25D, 0.15D, 0.25D, 0.0D);
        return true;
    }

    private static ItemStack rollChestGift(int affection, RandomSource r) {
        int roll = r.nextInt(100);
        if (roll < 12) return loveLetter(affection, r);
        if (affection >= YandereConfig.SHY_THRESHOLD.get() && roll < 22) {
            return new ItemStack(Items.GOLD_INGOT, Mth.nextInt(r, 1, 3));
        }
        if (roll % 2 == 0) return new ItemStack(Items.IRON_INGOT, Mth.nextInt(r, 1, 5));   // 1-5 iron
        return new ItemStack(Items.COAL, Mth.nextInt(r, 3, 9));                             // 3-9 coal
    }

    private static ItemStack loveLetter(int affection, RandomSource r) {
        String[] pool;
        if (affection >= YandereConfig.SHY_THRESHOLD.get()) pool = YandereLines.LETTERS_HIGH;
        else if (affection >= YandereConfig.AGGRESSIVE_THRESHOLD.get()) pool = YandereLines.LETTERS_MID;
        else pool = YandereLines.LETTERS_LOW;

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        tag.putString("title", "Love Letter");
        tag.putString("author", "???");
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(YandereLines.pick(pool, r)))));
        tag.put("pages", pages);
        return book;
    }

    /** Inserts as much of the stack as fits, returns what is left over. */
    private static ItemStack insert(Container c, ItemStack stack) {
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = c.getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, stack)) {
                int space = Math.min(slot.getMaxStackSize(), c.getMaxStackSize()) - slot.getCount();
                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    slot.grow(move);
                    stack.shrink(move);
                    c.setChanged();
                }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
            if (c.getItem(i).isEmpty()) {
                c.setItem(i, stack.copy());
                stack.setCount(0);
                c.setChanged();
            }
        }
        return stack;
    }
}
