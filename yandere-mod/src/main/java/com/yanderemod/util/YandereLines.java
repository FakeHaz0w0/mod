package com.yanderemod.util;

import net.minecraft.util.RandomSource;

/**
 * Every word the yandere says or writes. Edit freely, it is plain Java strings.
 */
public final class YandereLines {
    private YandereLines() {}

    // ---- the signature lines ----
    public static final String CAUGHT = "..!";                       // red, no name, shy + caught
    public static final String PLEASE_LOVE_ME = "Please Love Me Back.";
    public static final String ALL_MINE = "Your All Mine.";
    public static final String COMPANION_JOIN = "I Love You.";

    public static final String[] COMPANION_CHAT = {
            "I Love You.",
            "I Enjoy Our Time Together.",
            "Stay close to me, okay?",
            "I'll keep you safe. Always.",
            "You smell like home.",
            "Don't ever leave me.",
            "Just us. Forever.",
            "I counted every step we took today.",
            "Nobody else understands you the way I do.",
            "You're my favorite person. My only person.",
            "Look at me. Just a little longer.",
            "I'd burn the whole world for one more minute with you."
    };

    public static final String[] HUG = {
            "Don't turn around... just stay like this.",
            "You're so warm...",
            "I finally got close to you."
    };

    public static final String[] FLOWER_THANKS = {
            "For me...? I'll keep it forever.",
            "A rose... you remembered.",
            "I'll press it in a book and read it every night.",
            "You're so kind to me. Nobody else is.",
            "Say you'll stay with me."
    };

    public static final String[] CAKE_THANKS = {
            "You made... a cake? For me?",
            "Sweet. Just like you."
    };

    public static final String WITHER_ROSE_INSULT = "...You're giving it back? Why would you do that to me?";

    public static final String[] PAT = {
            "...!",
            "Do that again.",
            "Mine...",
            "Don't stop."
    };

    public static final String[] HURT = {
            "Why... why would you hurt me? I only ever wanted you.",
            "It's okay. I forgive you. I always forgive you.",
            "You're hurting me... but I'll still stay."
    };

    public static final String[] JEALOUS = {
            "Who was that? Why were you smiling at them?",
            "You looked at them for so long...",
            "You don't need them. You have me."
    };

    public static final String[] PROTECT = {
            "Nobody touches you. Nobody.",
            "Don't worry. I'll handle it.",
            "They'll never hurt you again."
    };

    public static final String[] STRANGER = {
            "Don't touch me.",
            "You're not them. Go away.",
            "Only one person may touch me."
    };

    public static final String REVOKED = "You don't love me anymore... do you?";
    public static final String SAVE = "I won't let you die. Not before me.";
    public static final String FORGIVE = "...I forgive you. I'll always come back to you.";
    public static final String GIFT = "I made this for you.";

    // ---- sign texts (4 lines each, keep lines <= 15 chars) ----
    public static final String[][] SIGNS_HIGH = {
            {"", "Your Mine.", "", ""},
            {"I Made This", "For You <3", "", ""},
            {"Be Mine,", "Forever.", "", ""},
            {"I See You", "Every Day.", "", ""},
            {"Thinking Of", "You Always.", "", ""},
            {"Don't Look", "Away, Love.", "", ""}
    };
    public static final String[][] SIGNS_MID = {
            {"Your Mine.", "", "", ""},
            {"Why Won't", "You Look?", "", ""},
            {"I'm Always", "Watching.", "", ""},
            {"Only Me.", "Only Me.", "Only Me.", ""},
            {"Don't Leave", "Me.", "", ""},
            {"I Waited", "For You.", "", ""},
            {"Mine. Mine.", "Mine. Mine.", "", ""}
    };
    public static final String[][] SIGNS_LOW = {
            {"Your Mine.", "NO ONE ELSE.", "", ""},
            {"Stay Away", "From Them.", "", ""},
            {"I'll Remove", "Anyone Who", "Touches You.", ""},
            {"They Will", "Never Have", "You.", ""},
            {"LOOK AT ME.", "", "", ""},
            {"Your Mine.", "Your Mine.", "Your Mine.", ""}
    };

    // ---- love letters left in chests (one page each) ----
    public static final String[] LETTERS_HIGH = {
            "My dearest,\n\nI watched you build today. You hum when you think nobody is listening.\n\nI like that I'm the only one who noticed.\n\n- Yours",
            "I left you something small.\nI hope it makes you smile.\n\nI keep every smile of yours.\n\n- Always near"
    };
    public static final String[] LETTERS_MID = {
            "You walk past me every day and never see me.\n\nOne day you will.\n\n- Waiting",
            "I know where you sleep.\nI know what you eat.\nI only want to know you.\n\nIs that so wrong?"
    };
    public static final String[] LETTERS_LOW = {
            "I saw how they looked at you.\n\nI took care of it.\nYou're welcome.\n\n- Yours. Only yours.",
            "Stop talking to them.\n\nI am so very patient.\nBut I am not endless."
    };

    public static String pick(String[] pool, RandomSource random) {
        return pool[random.nextInt(pool.length)];
    }
}
