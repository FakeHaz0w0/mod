package com.yanderemod.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Everything tweakable lives in config/yanderemod-common.toml */
public class YandereConfig {
    public static final ForgeConfigSpec SPEC;

    // timing
    public static final ForgeConfigSpec.IntValue INITIAL_SPAWN_DELAY;
    public static final ForgeConfigSpec.IntValue REJOIN_SPAWN_DELAY;
    public static final ForgeConfigSpec.IntValue STALK_MIN;
    public static final ForgeConfigSpec.IntValue STALK_MAX;
    public static final ForgeConfigSpec.IntValue SIGN_MIN;
    public static final ForgeConfigSpec.IntValue SIGN_MAX;
    public static final ForgeConfigSpec.IntValue GIFT_MIN;
    public static final ForgeConfigSpec.IntValue GIFT_MAX;
    public static final ForgeConfigSpec.IntValue RESPAWN_DELAY;

    // mood
    public static final ForgeConfigSpec.IntValue SHY_THRESHOLD;
    public static final ForgeConfigSpec.IntValue AGGRESSIVE_THRESHOLD;
    public static final ForgeConfigSpec.IntValue COMPANION_THRESHOLD;
    public static final ForgeConfigSpec.IntValue DECAY_SECONDS;
    public static final ForgeConfigSpec.IntValue HIT_PENALTY;
    public static final ForgeConfigSpec.IntValue ROSE_BUSH_AFFECTION;
    public static final ForgeConfigSpec.IntValue POPPY_AFFECTION;
    public static final ForgeConfigSpec.IntValue RESPAWN_AFFECTION;

    // behaviour
    public static final ForgeConfigSpec.IntValue STALK_DISTANCE_MIN;
    public static final ForgeConfigSpec.IntValue STALK_DISTANCE_MAX;
    public static final ForgeConfigSpec.IntValue GUARD_RADIUS;
    public static final ForgeConfigSpec.IntValue CHEST_RANGE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_SIGNS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_GIFTS;
    public static final ForgeConfigSpec.BooleanValue ATTACK_OTHER_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue DEVOTION_SAVE;
    public static final ForgeConfigSpec.BooleanValue FLOWER_LURE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.push("timing");
        INITIAL_SPAWN_DELAY = b.comment("Seconds after the obsession player first joins before the yandere shows up.")
                .defineInRange("initialSpawnDelaySeconds", 180, 0, 86400);
        REJOIN_SPAWN_DELAY = b.comment("Seconds after the obsession player re-joins before the yandere returns.")
                .defineInRange("rejoinSpawnDelaySeconds", 20, 0, 86400);
        STALK_MIN = b.comment("Phase 1 (stalking) lasts at least this many seconds before it approaches.")
                .defineInRange("stalkMinSeconds", 240, 10, 86400);
        STALK_MAX = b.comment("Phase 1 (stalking) lasts at most this many seconds before it approaches.")
                .defineInRange("stalkMaxSeconds", 600, 10, 86400);
        SIGN_MIN = b.defineInRange("signMinSeconds", 120, 10, 86400);
        SIGN_MAX = b.defineInRange("signMaxSeconds", 420, 10, 86400);
        GIFT_MIN = b.defineInRange("giftMinSeconds", 180, 10, 86400);
        GIFT_MAX = b.defineInRange("giftMaxSeconds", 600, 10, 86400);
        RESPAWN_DELAY = b.comment("Seconds before a killed yandere comes back.")
                .defineInRange("respawnDelaySeconds", 600, 0, 864000);
        b.pop();

        b.push("mood");
        SHY_THRESHOLD = b.comment("Affection at/above this: it is shy (flees with a diamond when caught). Below: it keeps approaching and hands you a withered rose.")
                .defineInRange("shyThreshold", 60, 0, 100);
        AGGRESSIVE_THRESHOLD = b.comment("Affection BELOW this: it is violently protective and attacks anything near you.")
                .defineInRange("aggressiveThreshold", 30, 0, 100);
        COMPANION_THRESHOLD = b.comment("Affection at/above this: it becomes your companion. (It stays one until affection drops below shyThreshold.)")
                .defineInRange("companionThreshold", 75, 0, 100);
        DECAY_SECONDS = b.comment("Every N seconds of neglect affection drops by 1 (not while a companion). 0 disables.")
                .defineInRange("affectionDecaySeconds", 600, 0, 864000);
        HIT_PENALTY = b.comment("Affection lost when the obsession player hits it.")
                .defineInRange("hitPenalty", 10, 0, 100);
        ROSE_BUSH_AFFECTION = b.defineInRange("roseBushAffection", 10, 0, 100);
        POPPY_AFFECTION = b.defineInRange("poppyAffection", 5, 0, 100);
        RESPAWN_AFFECTION = b.comment("Affection it comes back with after the obsession player killed it.")
                .defineInRange("respawnAffection", 10, 0, 100);
        b.pop();

        b.push("behaviour");
        STALK_DISTANCE_MIN = b.defineInRange("stalkDistanceMin", 16, 6, 64);
        STALK_DISTANCE_MAX = b.defineInRange("stalkDistanceMax", 34, 10, 96);
        GUARD_RADIUS = b.comment("How far from the obsession player it will hunt things (aggressive mood / companion).")
                .defineInRange("guardRadius", 14, 4, 48);
        CHEST_RANGE = b.comment("Gifts are only left in chests the player placed within this many blocks of the player.")
                .defineInRange("giftChestRange", 64, 8, 256);
        ENABLE_SIGNS = b.define("enableSigns", true);
        ENABLE_GIFTS = b.define("enableChestGifts", true);
        ATTACK_OTHER_PLAYERS = b.comment("In the aggressive mood, also attack OTHER players standing near the obsession player.")
                .define("attackOtherPlayers", true);
        DEVOTION_SAVE = b.comment("A companion with 90+ affection can cancel a fatal blow once per 30 minutes.")
                .define("devotionSave", true);
        FLOWER_LURE = b.comment("Holding a rose/flower makes it come close so you can right-click it.")
                .define("flowerLure", true);
        b.pop();

        SPEC = b.build();
    }
}
