package dev.espi.protectionstones.mobguard;

import java.util.*;

/** Per-claim mob rules. Pure logic (no Bukkit) so it can be unit tested. Only overrides vs defaults are stored. */
public class MobGuardSettings {
    public enum State { NEUTRAL, PROTECTED, CLEAN }

    public static final LinkedHashMap<String, List<String>> CATEGORIES = new LinkedHashMap<>();
    public static final Set<String> DEFAULT_PROTECTED = new HashSet<>(Arrays.asList(
            "VILLAGER", "CAT", "WOLF", "PARROT", "HORSE", "DONKEY", "MULE", "IRON_GOLEM", "COPPER_GOLEM", "HAPPY_GHAST"));

    static {
        CATEGORIES.put("FARM", Arrays.asList("COW", "MOOSHROOM", "PIG", "SHEEP", "CHICKEN", "RABBIT", "GOAT", "HORSE", "DONKEY",
                "MULE", "LLAMA", "TRADER_LLAMA", "CAMEL", "VILLAGER", "WANDERING_TRADER", "HAPPY_GHAST"));
        CATEGORIES.put("HOSTILE", Arrays.asList("ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY", "BOGGED", "CREEPER", "SPIDER",
                "CAVE_SPIDER", "ENDERMAN", "WITCH", "SLIME", "MAGMA_CUBE", "PHANTOM", "PILLAGER", "VINDICATOR", "EVOKER",
                "RAVAGER", "VEX", "SILVERFISH", "ENDERMITE", "BLAZE", "GHAST", "ZOMBIFIED_PIGLIN", "PIGLIN_BRUTE", "HOGLIN",
                "ZOGLIN", "GUARDIAN", "BREEZE", "CREAKING"));
        CATEGORIES.put("AQUATIC", Arrays.asList("SQUID", "GLOW_SQUID", "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
                "DOLPHIN", "AXOLOTL", "TADPOLE", "TURTLE"));
        CATEGORIES.put("OTHER", Arrays.asList("CAT", "WOLF", "PARROT", "FOX", "OCELOT", "PANDA", "POLAR_BEAR", "BEE", "ALLAY",
                "ARMADILLO", "SNIFFER", "STRIDER", "FROG", "BAT", "IRON_GOLEM", "COPPER_GOLEM", "SNOW_GOLEM", "PIGLIN"));
    }

    public final Map<String, State> overrides = new HashMap<>();
    public int minutes = 0; // 0 = cleanup off

    public static State defaultState(String type) {
        return DEFAULT_PROTECTED.contains(type) ? State.PROTECTED : State.NEUTRAL;
    }

    public State state(String type) {
        State s = overrides.get(type);
        return s != null ? s : defaultState(type);
    }

    public void set(String type, State s) {
        if (s == defaultState(type)) overrides.remove(type); else overrides.put(type, s);
    }

    /** Left click: protected <-> neutral. Right click: clean <-> neutral. */
    public State toggle(String type, boolean clean) {
        State cur = state(type);
        State next = clean ? (cur == State.CLEAN ? State.NEUTRAL : State.CLEAN) : (cur == State.PROTECTED ? State.NEUTRAL : State.PROTECTED);
        set(type, next);
        return next;
    }

    /** Step minutes: below min goes to 0 (off), above max is clamped. */
    public static int stepMinutes(int cur, int delta, int min, int max) {
        int n = cur + delta;
        if (cur == 0 && delta > 0) n = Math.max(min, delta > 0 ? min + delta - 1 : min);
        if (n < min) return 0;
        return Math.min(n, max);
    }

    public boolean hasClean() {
        return minutes > 0 && overrides.containsValue(State.CLEAN);
    }

    public boolean isDefault() {
        return overrides.isEmpty() && minutes == 0;
    }
}
