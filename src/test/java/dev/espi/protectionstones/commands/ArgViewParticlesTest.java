package dev.espi.protectionstones.commands;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArgViewParticlesTest {
    private static final World WORLD = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
            new Class<?>[]{World.class}, (proxy, method, args) -> {
                throw new AssertionError("Unexpected world operation: " + method.getName());
            });
    private record Scheduled(Runnable task, long delay) {}

    @Test void visibleMarkersScheduleValidDustAndKeepTheirColors() throws Exception {
        withScheduler(tasks -> {
            for (String color : List.of("Purple", "Pink", "Blue")) {
                tasks.clear();
                List<Particle.DustOptions> emitted = new ArrayList<>();
                Player player = player(true, emitted);
                assertTrue(marker(color, player, new Location(WORLD, 0, 0, 0)));
                assertEquals(30, tasks.size());
                for (int i = 0; i < tasks.size(); i++) {
                    assertEquals(i * 20L, tasks.get(i).delay());
                    tasks.get(i).task().run();
                }
                assertEquals(30, emitted.size());
                Color expected = switch (color) {
                    case "Purple" -> Color.fromRGB(255, 0, 255);
                    case "Pink" -> Color.fromRGB(233, 30, 99);
                    default -> Color.fromRGB(0, 255, 255);
                };
                for (Particle.DustOptions dust : emitted) {
                    assertEquals(expected, dust.getColor());
                    // The server's dust packet conversion rejects sizes outside this range.
                    assertTrue(Float.isFinite(dust.getSize()) && dust.getSize() >= 0.01f && dust.getSize() <= 4f,
                            () -> color + " marker cannot be sent: size=" + dust.getSize());
                }
            }
        });
    }

    @Test void distantMarkersDoNotScheduleAndOfflinePlayersReceiveNoDust() throws Exception {
        withScheduler(tasks -> {
            List<Particle.DustOptions> emitted = new ArrayList<>();
            Player player = player(false, emitted);
            for (String color : List.of("Purple", "Pink", "Blue")) {
                assertFalse(marker(color, player, new Location(WORLD, 151, 0, 0)));
                assertFalse(marker(color, player, new Location(WORLD, 0, 31, 0)));
            }
            assertTrue(tasks.isEmpty());
            assertTrue(marker("Purple", player, new Location(WORLD, 0, 0, 0)));
            assertEquals(30, tasks.size());
            for (Scheduled task : tasks) task.task().run();
            assertTrue(emitted.isEmpty());
        });
    }

    private static boolean marker(String color, Player player, Location location) throws Exception {
        Method handler = ArgView.class.getDeclaredMethod("handle" + color + "Particle", Player.class, Location.class);
        handler.setAccessible(true);
        return (boolean) handler.invoke(null, player, location);
    }

    private static Player player(boolean online, List<Particle.DustOptions> emitted) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLocation" -> new Location(WORLD, 0, 0, 0);
                    case "isOnline" -> online;
                    case "spawnParticle" -> {
                        if (args[0] == Particle.DUST) emitted.add((Particle.DustOptions) args[args.length - 1]);
                        yield null;
                    }
                    default -> throw new AssertionError("Unexpected player operation: " + method.getName());
                });
    }

    @FunctionalInterface private interface Scenario { void run(List<Scheduled> tasks) throws Exception; }

    private static void withScheduler(Scenario scenario) throws Exception {
        List<Scheduled> tasks = new ArrayList<>();
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(BukkitScheduler.class.getClassLoader(),
                new Class<?>[]{BukkitScheduler.class}, (proxy, method, args) -> {
                    assertEquals("runTaskLater", method.getName());
                    tasks.add(new Scheduled((Runnable) args[1], (long) args[2]));
                    return null;
                });
        Server server = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    assertEquals("getScheduler", method.getName());
                    return scheduler;
                });
        Field singleton = Bukkit.class.getDeclaredField("server");
        singleton.setAccessible(true);
        Object previous = singleton.get(null);
        try {
            singleton.set(null, server);
            scenario.run(tasks);
        } finally {
            singleton.set(null, previous);
        }
    }
}
