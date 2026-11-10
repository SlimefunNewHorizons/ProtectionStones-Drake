package dev.espi.protectionstones.mobguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageSanitizerTest {
    @Test void acceptsColorsAndPlayer() {
        assertTrue(MessageSanitizer.clean("&aBienvenido a casa de %player%").ok());
    }
    @Test void rejectsMiniMessageAndFormatInjection() {
        assertFalse(MessageSanitizer.clean("<click:run_command:/op x>hola").ok());
        assertFalse(MessageSanitizer.clean("&#ff0000 hola").ok());
        assertFalse(MessageSanitizer.clean("&x&f&f hola").ok());
        assertFalse(MessageSanitizer.clean("hola §a").ok());
        assertFalse(MessageSanitizer.clean("%vault_eco_balance%").ok());
    }
    @Test void rejectsOptionsLinksAndLength() {
        assertFalse(MessageSanitizer.clean("-g all hola").ok());
        assertFalse(MessageSanitizer.clean("entra a discord.gg/abc").ok());
        assertFalse(MessageSanitizer.clean("visita www.algo").ok());
        assertFalse(MessageSanitizer.clean("a".repeat(101)).ok());
        assertTrue(MessageSanitizer.clean("&a" + "a".repeat(100)).ok());
        assertFalse(MessageSanitizer.clean("   ").ok());
        assertFalse(MessageSanitizer.clean("&a&l").ok());
    }
}
