package com.yanderemod.event;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.yanderemod.YandereMod;
import com.yanderemod.data.YandereData;
import com.yanderemod.entity.YandereEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** /yandere status | affection set|add <n> | phase stalking|companion | approach | obsession <player> | respawn | reset */
@Mod.EventBusSubscriber(modid = YandereMod.MODID)
public class YandereCommands {

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("yandere")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("status").executes(YandereCommands::status))
                .then(Commands.literal("affection")
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> affection(c, false))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("value", IntegerArgumentType.integer(-100, 100))
                                        .executes(c -> affection(c, true)))))
                .then(Commands.literal("phase")
                        .then(Commands.literal("stalking").executes(c -> phase(c, YandereData.Phase.STALKING)))
                        .then(Commands.literal("companion").executes(c -> phase(c, YandereData.Phase.COMPANION))))
                .then(Commands.literal("approach").executes(YandereCommands::approach))
                .then(Commands.literal("obsession")
                        .then(Commands.argument("player", EntityArgument.player()).executes(YandereCommands::obsession)))
                .then(Commands.literal("respawn").executes(YandereCommands::respawn))
                .then(Commands.literal("reset").executes(YandereCommands::reset)));
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        YandereData d = YandereData.get(server);
        ServerPlayer p = d.obsession == null ? null : server.getPlayerList().getPlayer(d.obsession);
        YandereEntity y = YandereManager.find(server, d);
        String who = d.obsession == null ? "nobody yet" : (p != null ? p.getName().getString() : d.obsession.toString() + " (offline)");
        String text = "Obsessed with: " + who
                + "\nAffection: " + d.affection + "/100"
                + "\nPhase: " + d.phase + "   Mood: " + YandereEntity.modeOf(d)
                + "\nBody present: " + (y != null) + (d.staying ? "   (told to stay)" : "")
                + "\nNext approach in: " + (d.phaseTimer < 0 ? "?" : (d.phaseTimer / 20) + "s")
                + "\nRespawn in: " + (Math.max(0, d.respawnTimer) / 20) + "s"
                + "\nTracked chests: " + d.chests.size();
        c.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int affection(CommandContext<CommandSourceStack> c, boolean add) {
        YandereData d = YandereData.get(c.getSource().getServer());
        int v = IntegerArgumentType.getInteger(c, "value");
        if (add) d.addAffection(v);
        else d.addAffection(v - d.affection);
        d.setDirty();
        int now = d.affection;
        c.getSource().sendSuccess(() -> Component.literal("Affection is now " + now + "/100"), true);
        return now;
    }

    private static int phase(CommandContext<CommandSourceStack> c, YandereData.Phase phase) {
        YandereData d = YandereData.get(c.getSource().getServer());
        d.phase = phase;
        d.staying = false;
        d.phaseTimer = -1;
        d.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("Phase set to " + phase), true);
        return 1;
    }

    private static int approach(CommandContext<CommandSourceStack> c) {
        YandereData d = YandereData.get(c.getSource().getServer());
        d.phase = YandereData.Phase.STALKING;
        d.phaseTimer = 20;                      // approach begins on its next second
        d.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("It is about to approach..."), true);
        return 1;
    }

    private static int obsession(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer target = EntityArgument.getPlayer(c, "player");
        YandereData d = YandereData.get(server);
        YandereEntity y = YandereManager.find(server, d);
        if (y != null) y.discard();
        d.entityId = null;
        d.obsession = target.getUUID();
        d.spawnDelay = 100;
        d.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("It is now obsessed with " + target.getName().getString()), true);
        return 1;
    }

    private static int respawn(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        YandereData d = YandereData.get(server);
        YandereEntity y = YandereManager.find(server, d);
        if (y != null) y.discard();
        d.entityId = null;
        d.respawnTimer = 0;
        d.spawnDelay = 0;
        d.setDirty();
        c.getSource().sendSuccess(() -> Component.literal("It will reappear within a few seconds."), true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) {
        YandereData d = YandereData.get(c.getSource().getServer());
        d.resetBehaviour();
        c.getSource().sendSuccess(() -> Component.literal("Mood and progress reset (affection 50, stalking)."), true);
        return 1;
    }
}
