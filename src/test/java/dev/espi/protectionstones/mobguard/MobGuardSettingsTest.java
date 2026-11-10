package dev.espi.protectionstones.mobguard;

import dev.espi.protectionstones.mobguard.MobGuardSettings.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MobGuardSettingsTest {
    @Test void defaultsProtectPets() {
        MobGuardSettings s = new MobGuardSettings();
        for (String t : new String[]{"VILLAGER", "CAT", "WOLF", "PARROT", "HORSE", "DONKEY", "MULE", "IRON_GOLEM", "COPPER_GOLEM", "HAPPY_GHAST"})
            assertEquals(State.PROTECTED, s.state(t));
        assertEquals(State.NEUTRAL, s.state("ZOMBIE"));
        assertTrue(s.isDefault());
    }
    @Test void toggleStoresOnlyOverrides() {
        MobGuardSettings s = new MobGuardSettings();
        assertEquals(State.NEUTRAL, s.toggle("CAT", false));
        assertEquals(State.PROTECTED, s.toggle("CAT", false));
        assertTrue(s.overrides.isEmpty());
        assertEquals(State.CLEAN, s.toggle("ZOMBIE", true));
        assertEquals(State.NEUTRAL, s.toggle("ZOMBIE", true));
        assertEquals(State.PROTECTED, s.toggle("ZOMBIE", false));
        assertEquals(State.CLEAN, s.toggle("ZOMBIE", true));
    }
    @Test void minutesClamp() {
        assertEquals(1, MobGuardSettings.stepMinutes(0, 1, 1, 60));
        assertEquals(5, MobGuardSettings.stepMinutes(0, 5, 1, 60));
        assertEquals(0, MobGuardSettings.stepMinutes(1, -1, 1, 60));
        assertEquals(60, MobGuardSettings.stepMinutes(58, 5, 1, 60));
        assertEquals(0, MobGuardSettings.stepMinutes(0, -1, 1, 60));
    }
    @Test void cleanNeedsMinutes() {
        MobGuardSettings s = new MobGuardSettings();
        s.toggle("ZOMBIE", true);
        assertFalse(s.hasClean());
        s.minutes = 5;
        assertTrue(s.hasClean());
    }
}
