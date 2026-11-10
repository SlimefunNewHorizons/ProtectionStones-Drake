package dev.espi.protectionstones.mobguard;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dev.espi.protectionstones.PSRegion;
import dev.espi.protectionstones.ProtectionStones;
import dev.espi.protectionstones.mobguard.MobGuardSettings.State;
import dev.espi.protectionstones.utils.WGUtils;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.projectiles.ProjectileSource;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Claim panel: right click the protection block (empty hand, no sneak) to open it.
 * It is only a UI layer: every action runs the existing /ps commands (so their permissions and rules apply).
 * Also holds the mob protection / timed cleanup rules, keyed by "world:regionId" (each world/modality keeps its own).
 */
public class ClaimPanel implements Listener {
    private static final Set<String> SAFE_PDC_NAMESPACES = Set.of("minecraft", "levelledmobs", "paper", "protectionstones");
    private static final String[] CATS = {"FARM", "HOSTILE", "AQUATIC", "OTHER"};
    /** flag, icon, visitor-type (applied to nonmembers), ES text, EN text */
    private static final Object[][] FLAGS = {
            {"pvp", Material.IRON_SWORD, false, "PvP", "PvP"},
            {"tnt", Material.TNT, false, "TNT", "TNT"},
            {"creeper-explosion", Material.CREEPER_HEAD, false, "Explosion de creeper", "Creeper explosion"},
            {"other-explosion", Material.FIRE_CHARGE, false, "Otras explosiones", "Other explosions"},
            {"wither-damage", Material.WITHER_SKELETON_SKULL, false, "Dano de wither", "Wither damage"},
            {"ghast-fireball", Material.GHAST_TEAR, false, "Bolas de fuego de ghast", "Ghast fireballs"},
            {"enderman-grief", Material.ENDER_PEARL, false, "Endermans toman bloques", "Enderman grief"},
            {"fire-spread", Material.FLINT_AND_STEEL, false, "Propagacion de fuego", "Fire spread"},
            {"lava-fire", Material.LAVA_BUCKET, false, "Fuego por lava", "Lava fire"},
            {"mob-spawning", Material.SPAWNER, false, "Aparicion de mobs", "Mob spawning"},
            {"entry", Material.IRON_DOOR, true, "Entrada de visitantes", "Visitor entry"},
            {"chest-access", Material.CHEST, true, "Cofres (visitantes)", "Chests (visitors)"},
            {"use", Material.STONE_BUTTON, true, "Puertas/botones (visitantes)", "Doors/buttons (visitors)"},
    };

    private enum Page { MAIN, MEMBERS, FLAGS, MOBS, CONFIRM, MSGS }
    private static final String[] MSG_FLAGS = {"greeting", "farewell", "greeting-title", "farewell-title"};
    private static final int[] MSG_SLOTS = {11, 13, 29, 31};
    private enum Ask { RENAME, ADD, EDIT }
    private record Pending(Ask ask, Location loc, long at, String flag) {}

    private static class Menu implements InventoryHolder {
        final Location loc; final String key; Page page = Page.MAIN; String category = "FARM"; Inventory inv;
        Menu(Location loc, String key) { this.loc = loc; this.key = key; }
        @Override public Inventory getInventory() { return inv; }
    }

    private final ProtectionStones plugin;
    private final File dataFile;
    private final Map<String, MobGuardSettings> data = new HashMap<>();
    private final Map<UUID, Long> firstSeen = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final Set<String> disabledWorlds = new HashSet<>();
    private boolean dirty = false;
    private long lastRebuild = 0;
    private int minMinutes = 1, maxMinutes = 60, claimsPerRun = 2, intervalTicks = 600, maxChunksPerClaim = 400;

    public ClaimPanel(ProtectionStones plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "mobguard-data.yml");
    }

    public void start() {
        loadConfig();
        loadData();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) saveData(); }, 200L, 200L);
    }

    public void stop() { if (dirty) saveData(); }

    // ---------------------------------------------------------------- config / data

    private void loadConfig() {
        File f = new File(plugin.getDataFolder(), "mobguard.yml");
        YamlConfiguration c = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        c.addDefault("min-minutes", 1);
        c.addDefault("max-minutes", 60);
        c.addDefault("claims-per-run", 2);
        c.addDefault("rebuild-interval-ticks", 600);
        c.addDefault("max-chunks-per-claim", 400);
        c.addDefault("disabled-worlds", new ArrayList<String>());
        c.options().copyDefaults(true);
        try { c.save(f); } catch (IOException ignored) {}
        minMinutes = Math.max(1, c.getInt("min-minutes"));
        maxMinutes = Math.max(minMinutes, c.getInt("max-minutes"));
        claimsPerRun = Math.max(1, c.getInt("claims-per-run"));
        intervalTicks = Math.max(100, c.getInt("rebuild-interval-ticks"));
        maxChunksPerClaim = Math.max(1, c.getInt("max-chunks-per-claim"));
        disabledWorlds.addAll(c.getStringList("disabled-worlds"));
    }

    private void loadData() {
        data.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : y.getKeys(false)) {
            MobGuardSettings s = new MobGuardSettings();
            s.minutes = y.getInt(key + ".minutes", 0);
            var sec = y.getConfigurationSection(key + ".mobs");
            if (sec != null) for (String t : sec.getKeys(false)) {
                try { s.set(t, State.valueOf(sec.getString(t, "NEUTRAL"))); } catch (IllegalArgumentException ignored) {}
            }
            data.put(key, s);
        }
    }

    private void saveData() {
        dirty = false;
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((k, s) -> {
            if (s.isDefault()) return;
            y.set(k + ".minutes", s.minutes);
            s.overrides.forEach((t, st) -> y.set(k + ".mobs." + t, st.name()));
        });
        try { y.save(dataFile); } catch (IOException e) { plugin.getLogger().warning("mobguard save failed: " + e.getMessage()); }
    }

    private static String key(PSRegion r) { return r.getWorld().getName() + ":" + r.getId(); }
    private MobGuardSettings settings(String key) { return data.computeIfAbsent(key, k -> new MobGuardSettings()); }
    private static final MobGuardSettings DEFAULTS = new MobGuardSettings();
    private MobGuardSettings settingsFor(String key) { MobGuardSettings s = data.get(key); return s != null ? s : DEFAULTS; }

    private static String t(Player p, String es, String en) { return p.locale().getLanguage().equals("es") ? es : en; }
    private static Component c(String s, NamedTextColor col) { return Component.text(s, col); }
    private String cmd() { return plugin.getConfigOptions().base_command; }

    private static boolean full(Player p, PSRegion r) {
        return r.isOwner(p.getUniqueId()) || p.hasPermission("protectionstones.admin");
    }

    // ---------------------------------------------------------------- open

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        if (p.isSneaking() || e.isBlockInHand() || disabledWorlds.contains(p.getWorld().getName())) return;
        if (!ProtectionStones.isProtectBlock(e.getClickedBlock())) return;
        PSRegion r = PSRegion.fromLocationGroup(e.getClickedBlock().getLocation());
        if (r == null) return;
        if (!full(p, r) && !r.isMember(p.getUniqueId())) return;
        e.setCancelled(true);
        open(p, new Menu(e.getClickedBlock().getLocation(), key(r)));
    }

    private void open(Player p, Menu m) {
        PSRegion r = PSRegion.fromLocationGroup(m.loc);
        if (r == null || !(full(p, r) || r.isMember(p.getUniqueId()))) { p.closeInventory(); return; }
        boolean full = full(p, r);
        if (!full) m.page = Page.MAIN;
        String title = switch (m.page) {
            case MAIN -> t(p, "Panel del claim", "Claim panel");
            case MEMBERS -> t(p, "Miembros", "Members");
            case FLAGS -> t(p, "Opciones del claim", "Claim flags");
            case MOBS -> t(p, "Mobs del claim", "Claim mobs");
            case CONFIRM -> t(p, "Quitar proteccion?", "Remove protection?");
            case MSGS -> t(p, "Bienvenida y despedida", "Greeting and farewell");
        };
        Inventory inv = Bukkit.createInventory(m, 54, Component.text(title));
        m.inv = inv;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, c(" ", NamedTextColor.GRAY), null, false);
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        switch (m.page) {
            case MAIN -> drawMain(p, r, inv, full);
            case MEMBERS -> drawMembers(p, r, inv);
            case FLAGS -> drawFlags(p, r, inv);
            case MOBS -> drawMobs(p, inv, m);
            case CONFIRM -> drawConfirm(p, r, inv);
            case MSGS -> drawMsgs(p, r, inv);
        }
        if (m.page != Page.MAIN) inv.setItem(45, item(Material.ARROW, c(t(p, "Volver", "Back"), NamedTextColor.WHITE), null, false));
        inv.setItem(53, item(Material.OAK_DOOR, c(t(p, "Cerrar", "Close"), NamedTextColor.WHITE), null, false));
        p.openInventory(inv);
    }

    private static String name(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n != null ? n : u.toString().substring(0, 8);
    }

    private void drawMain(Player p, PSRegion r, Inventory inv, boolean full) {
        ProtectedRegion w = r.getWGRegion();
        BlockVector3 mn = w.getMinimumPoint(), mx = w.getMaximumPoint();
        StringJoiner owners = new StringJoiner(", ");
        r.getOwners().forEach(u -> owners.add(name(u)));
        List<Component> lore = new ArrayList<>();
        lore.add(c(t(p, "Dueno(s): ", "Owner(s): ") + owners, NamedTextColor.GRAY));
        lore.add(c(t(p, "Miembros: ", "Members: ") + r.getMembers().size(), NamedTextColor.GRAY));
        lore.add(c(t(p, "Tamano: ", "Size: ") + (mx.x() - mn.x() + 1) + " x " + (mx.z() - mn.z() + 1) + " x " + (mx.y() - mn.y() + 1), NamedTextColor.GRAY));
        lore.add(c(t(p, "Limites: ", "Bounds: ") + mn.x() + "," + mn.y() + "," + mn.z() + " -> " + mx.x() + "," + mx.y() + "," + mx.z(), NamedTextColor.DARK_GRAY));
        lore.add(c("ID: " + r.getId(), NamedTextColor.DARK_GRAY));
        inv.setItem(4, item(Material.MAP, c(r.getName() != null ? r.getName() : t(p, "Claim sin nombre", "Unnamed claim"), NamedTextColor.GOLD), lore, false));
        inv.setItem(30, item(Material.ENDER_PEARL, c(t(p, "Teletransportarme al claim", "Teleport to claim"), NamedTextColor.AQUA), null, false));
        if (!full) return;
        inv.setItem(19, item(Material.PLAYER_HEAD, c(t(p, "Miembros", "Members"), NamedTextColor.GREEN),
                List.of(c(t(p, "Anadir / quitar jugadores de confianza", "Add / remove trusted players"), NamedTextColor.GRAY)), false));
        inv.setItem(21, item(Material.REDSTONE_TORCH, c(t(p, "Opciones (flags)", "Options (flags)"), NamedTextColor.YELLOW),
                List.of(c(t(p, "PvP, explosiones, fuego, visitantes...", "PvP, explosions, fire, visitors..."), NamedTextColor.GRAY)), false));
        inv.setItem(23, item(Material.SADDLE, c(t(p, "Mobs del claim", "Claim mobs"), NamedTextColor.GOLD),
                List.of(c(t(p, "Proteger mobs y limpieza por tiempo", "Protect mobs and timed cleanup"), NamedTextColor.GRAY)), false));
        inv.setItem(28, item(Material.OAK_SIGN, c(t(p, "Mensajes de bienvenida / despedida", "Greeting / farewell messages"), NamedTextColor.LIGHT_PURPLE),
                List.of(c(t(p, "Ver, editar, borrar y probar", "View, edit, clear and preview"), NamedTextColor.GRAY)), false));
        inv.setItem(25, item(Material.NAME_TAG, c(t(p, "Renombrar claim", "Rename claim"), NamedTextColor.WHITE), null, false));
        inv.setItem(32, item(Material.TNT_MINECART, c(t(p, "Quitar proteccion (unclaim)", "Remove protection (unclaim)"), NamedTextColor.RED),
                List.of(c(t(p, "Pide confirmacion", "Asks for confirmation"), NamedTextColor.GRAY)), false));
    }

    private void drawMembers(Player p, PSRegion r, Inventory inv) {
        int slot = 9;
        for (UUID u : r.getOwners()) if (slot < 27) inv.setItem(slot++, head(u, c(name(u), NamedTextColor.GOLD), List.of(c(t(p, "Dueno", "Owner"), NamedTextColor.GRAY))));
        for (UUID u : r.getMembers()) if (slot < 27)
            inv.setItem(slot++, head(u, c(name(u), NamedTextColor.GREEN), List.of(c(t(p, "Miembro - clic para quitar", "Member - click to remove"), NamedTextColor.GRAY))));
        slot = 28;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (slot > 44) break;
            if (o.equals(p) || r.isOwner(o.getUniqueId()) || r.isMember(o.getUniqueId())) continue;
            inv.setItem(slot++, head(o.getUniqueId(), c(o.getName(), NamedTextColor.YELLOW), List.of(c(t(p, "Clic para anadir como miembro", "Click to add as member"), NamedTextColor.GRAY))));
        }
        inv.setItem(27, item(Material.PAPER, c(t(p, "Jugadores conectados:", "Online players:"), NamedTextColor.GRAY), null, false));
        inv.setItem(47, item(Material.WRITABLE_BOOK, c(t(p, "Anadir por nombre", "Add by name"), NamedTextColor.WHITE),
                List.of(c(t(p, "Lo escribes en el chat", "You type it in chat"), NamedTextColor.GRAY)), false));
    }

    private static Object[] flagInfo(String name) {
        for (Object[] f : FLAGS) if (f[0].equals(name)) return f;
        return null;
    }

    /** Group to use for a flag following the block's allowed_flags, or null if the flag can't be edited here. */
    private static String flagGroup(PSRegion r, Object[] fi) {
        List<String> groups = r.getTypeOptions().allowedFlags.get((String) fi[0]);
        if (groups == null || groups.isEmpty()) return null;
        if ((boolean) fi[2]) return groups.contains("nonmembers") ? "nonmembers" : null;
        return groups.contains("all") ? "all" : groups.get(0);
    }

    private void drawFlags(Player p, PSRegion r, Inventory inv) {
        int slot = 9;
        for (Object[] fi : FLAGS) {
            String name = (String) fi[0];
            String group = flagGroup(r, fi);
            if (group == null) continue;
            Flag<?> f = Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), name);
            if (!(f instanceof StateFlag sf)) continue;
            Object v = r.getWGRegion().getFlag(sf);
            boolean perm = p.hasPermission("protectionstones.flags.edit." + name);
            NamedTextColor col = v == StateFlag.State.DENY ? NamedTextColor.RED : v == StateFlag.State.ALLOW ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            String st = v == StateFlag.State.DENY ? t(p, "Bloqueado", "Blocked") : v == StateFlag.State.ALLOW ? t(p, "Permitido", "Allowed") : t(p, "Por defecto", "Default");
            List<Component> lore = new ArrayList<>();
            lore.add(c(st, col));
            lore.add(perm ? c(t(p, "Clic: por defecto > bloquear > permitir", "Click: default > block > allow"), NamedTextColor.GRAY)
                    : c(t(p, "Sin permiso para cambiarlo", "No permission to change it"), NamedTextColor.RED));
            if ((boolean) fi[2]) lore.add(c(t(p, "Afecta solo a visitantes (no miembros)", "Only affects visitors (non-members)"), NamedTextColor.DARK_GRAY));
            inv.setItem(slot++, item((Material) fi[1], c(t(p, (String) fi[3], (String) fi[4]), col), lore, v == StateFlag.State.DENY));
            if (slot > 44) break;
        }
    }

    private static String flagText(PSRegion r, String name) {
        Flag<?> f = Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), name);
        if (!(f instanceof StringFlag sf)) return null;
        return r.getWGRegion().getFlag(sf);
    }

    private static boolean msgAllowed(PSRegion r, String name) {
        return r.getTypeOptions().allowedFlags.containsKey(name) && Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), name) instanceof StringFlag;
    }

    private void drawMsgs(Player p, PSRegion r, Inventory inv) {
        String[][] names = {{"Bienvenida (chat)", "Greeting (chat)"}, {"Despedida (chat)", "Farewell (chat)"},
                {"Bienvenida (titulo)", "Greeting (title)"}, {"Despedida (titulo)", "Farewell (title)"}};
        Material[] icons = {Material.OAK_SIGN, Material.SPRUCE_SIGN, Material.GOLDEN_CARROT, Material.GLOW_INK_SAC};
        for (int i = 0; i < 4; i++) {
            if (!msgAllowed(r, MSG_FLAGS[i])) continue;
            String cur = flagText(r, MSG_FLAGS[i]);
            boolean perm = p.hasPermission("protectionstones.flags.edit." + MSG_FLAGS[i]);
            List<Component> lore = new ArrayList<>();
            lore.add(cur == null ? c(t(p, "(sin mensaje)", "(no message)"), NamedTextColor.DARK_GRAY)
                    : LegacyComponentSerializer.legacyAmpersand().deserialize(cur.replace('\u00a7', '&')).decoration(TextDecoration.ITALIC, false));
            if (perm) {
                lore.add(c(t(p, "Clic izq: editar (escribes en el chat)", "Left click: edit (type in chat)"), NamedTextColor.GRAY));
                lore.add(c(t(p, "Clic der: borrar", "Right click: clear"), NamedTextColor.GRAY));
            } else lore.add(c(t(p, "Sin permiso para cambiarlo", "No permission to change it"), NamedTextColor.RED));
            lore.add(c(t(p, "Shift + clic: vista previa", "Shift + click: preview"), NamedTextColor.GRAY));
            lore.add(c(t(p, "Max " + MessageSanitizer.MAX_LEN + " caracteres, codigos &, sin enlaces", "Max " + MessageSanitizer.MAX_LEN + " chars, & codes, no links"), NamedTextColor.DARK_GRAY));
            inv.setItem(MSG_SLOTS[i], item(icons[i], c(t(p, names[i][0], names[i][1]), NamedTextColor.LIGHT_PURPLE), lore, cur != null));
        }
    }

    private void drawConfirm(Player p, PSRegion r, Inventory inv) {
        inv.setItem(22, item(Material.BARRIER, c(t(p, "Se perdera la proteccion de este claim", "This claim will lose its protection"), NamedTextColor.RED),
                List.of(c(t(p, "Cualquiera podra construir y romper aqui.", "Anyone will be able to build and break here."), NamedTextColor.GRAY),
                        c(t(p, "Tus bloques y cofres NO se borran.", "Your blocks and chests are NOT deleted."), NamedTextColor.GRAY),
                        c(t(p, "Debes estar dentro del claim para confirmar.", "You must be inside the claim to confirm."), NamedTextColor.GRAY)), false));
        inv.setItem(20, item(Material.LIME_CONCRETE, c(t(p, "Cancelar", "Cancel"), NamedTextColor.GREEN), null, false));
        inv.setItem(24, item(Material.RED_CONCRETE, c(t(p, "SI, quitar proteccion", "YES, remove protection"), NamedTextColor.RED), null, false));
    }

    private void drawMobs(Player p, Inventory inv, Menu m) {
        MobGuardSettings s = settings(m.key);
        Material[] icons = {Material.WHEAT, Material.ZOMBIE_HEAD, Material.COD, Material.NAME_TAG};
        String[][] names = {{"Granja", "Farm"}, {"Hostiles", "Hostile"}, {"Acuaticos", "Aquatic"}, {"Otros", "Other"}};
        for (int i = 0; i < 4; i++) {
            boolean sel = CATS[i].equals(m.category);
            inv.setItem(1 + i * 2, item(icons[i], c(t(p, names[i][0], names[i][1]), sel ? NamedTextColor.GOLD : NamedTextColor.YELLOW),
                    List.of(c(t(p, "Clic para ver", "Click to view"), NamedTextColor.GRAY)), sel));
        }
        int slot = 9;
        for (String type : types(m.category)) {
            EntityType et = EntityType.valueOf(type);
            State st = s.state(type);
            NamedTextColor col = st == State.PROTECTED ? NamedTextColor.GREEN : st == State.CLEAN ? NamedTextColor.RED : NamedTextColor.GRAY;
            String stTxt = st == State.PROTECTED ? t(p, "Protegido", "Protected")
                    : st == State.CLEAN ? t(p, "Se limpia tras " + s.minutes + " min", "Cleaned after " + s.minutes + " min")
                    : t(p, "Sin proteger", "Unprotected");
            inv.setItem(slot++, item(eggFor(type), Component.translatable(et.translationKey()).color(col),
                    List.of(c(stTxt, col),
                            c(t(p, "Clic izq: proteger / quitar", "Left click: protect / unprotect"), NamedTextColor.GRAY),
                            c(t(p, "Clic der: limpiar / no limpiar", "Right click: clean / don't clean"), NamedTextColor.GRAY)),
                    st == State.PROTECTED));
            if (slot > 44) break;
        }
        inv.setItem(46, item(Material.BARRIER, c(t(p, "Restablecer valores", "Reset to defaults"), NamedTextColor.RED), null, false));
        String tm = s.minutes == 0 ? t(p, "Limpieza apagada", "Cleanup off") : s.minutes + " min";
        inv.setItem(49, item(Material.CLOCK, c(t(p, "Limpieza: ", "Cleanup: ") + tm, NamedTextColor.AQUA),
                List.of(c(t(p, "Clic izq: +1 (shift +5)", "Left click: +1 (shift +5)"), NamedTextColor.GRAY),
                        c(t(p, "Clic der: -1 (shift -5)", "Right click: -1 (shift -5)"), NamedTextColor.GRAY),
                        c(t(p, "Limites: ", "Limits: ") + minMinutes + "-" + maxMinutes + " min", NamedTextColor.DARK_GRAY),
                        c(t(p, "Solo afecta mobs marcados para limpiar", "Only affects mobs marked to clean"), NamedTextColor.DARK_GRAY)), false));
    }

    private static List<String> types(String category) {
        List<String> out = new ArrayList<>();
        for (String type : MobGuardSettings.CATEGORIES.get(category)) {
            try { EntityType.valueOf(type); out.add(type); } catch (IllegalArgumentException ignored) {}
        }
        return out;
    }

    private static Material eggFor(String type) {
        switch (type) {
            case "IRON_GOLEM": return Material.IRON_BLOCK;
            case "COPPER_GOLEM": return Material.COPPER_BLOCK;
            case "SNOW_GOLEM": return Material.CARVED_PUMPKIN;
        }
        Material m = Material.matchMaterial(type + "_SPAWN_EGG");
        return m != null ? m : Material.PAPER;
    }

    private static ItemStack item(Material mat, Component name, List<Component> lore, boolean glow) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.displayName(name.decoration(TextDecoration.ITALIC, false));
        if (lore != null) im.lore(lore.stream().map(l -> l.decoration(TextDecoration.ITALIC, false)).toList());
        if (glow) im.setEnchantmentGlintOverride(true);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack head(UUID u, Component name, List<Component> lore) {
        ItemStack it = item(Material.PLAYER_HEAD, name, lore, false);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(u));
        it.setItemMeta(sm);
        return it;
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        PSRegion r = PSRegion.fromLocationGroup(m.loc);
        if (r == null || !(full(p, r) || r.isMember(p.getUniqueId()))) { p.closeInventory(); return; }
        boolean full = full(p, r);
        int slot = e.getRawSlot();
        if (slot == 53) { p.closeInventory(); return; }
        if (slot == 45 && m.page != Page.MAIN) { m.page = Page.MAIN; open(p, m); return; }

        switch (m.page) {
            case MAIN -> {
                if (slot == 30) { p.closeInventory(); p.performCommand(cmd() + " tp " + r.getId()); return; }
                if (!full) return;
                switch (slot) {
                    case 19 -> m.page = Page.MEMBERS;
                    case 21 -> m.page = Page.FLAGS;
                    case 23 -> m.page = Page.MOBS;
                    case 32 -> m.page = Page.CONFIRM;
                    case 28 -> m.page = Page.MSGS;
                    case 25 -> { ask(p, Ask.RENAME, m.loc, null, t(p, "Escribe el nuevo nombre del claim en el chat (o 'cancelar').", "Type the new claim name in chat (or 'cancel').")); return; }
                    default -> { return; }
                }
            }
            case MEMBERS -> {
                if (!full) return;
                if (slot == 47) { ask(p, Ask.ADD, m.loc, null, t(p, "Escribe el nombre del jugador a anadir (o 'cancelar').", "Type the player name to add (or 'cancel').")); return; }
                if (clicked.getType() != Material.PLAYER_HEAD || !(clicked.getItemMeta() instanceof SkullMeta sm) || sm.getOwningPlayer() == null) return;
                UUID u = sm.getOwningPlayer().getUniqueId();
                String n = name(u);
                if (slot >= 9 && slot < 27) { if (r.getMembers().contains(u)) p.performCommand(cmd() + " remove " + n); }
                else if (slot >= 28 && slot <= 44) p.performCommand(cmd() + " add " + n);
            }
            case FLAGS -> {
                if (!full || slot < 9 || slot > 44) return;
                List<Object[]> shown = new ArrayList<>();
                for (Object[] fi : FLAGS) {
                    if (flagGroup(r, fi) != null && Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), (String) fi[0]) instanceof StateFlag) shown.add(fi);
                }
                if (slot - 9 >= shown.size()) return;
                Object[] fi = shown.get(slot - 9);
                String name = (String) fi[0];
                if (!p.hasPermission("protectionstones.flags.edit." + name)) return;
                String group = flagGroup(r, fi);
                Object cur = r.getWGRegion().getFlag((StateFlag) Flags.fuzzyMatchFlag(WGUtils.getFlagRegistry(), name));
                String next = cur == null ? "deny" : cur == StateFlag.State.DENY ? "allow" : "none";
                p.performCommand(cmd() + " flag " + (group.equals("all") ? "" : "-g " + group + " ") + name + " " + next);
            }
            case MOBS -> {
                if (!full) return;
                MobGuardSettings s = settings(m.key);
                boolean right = e.isRightClick();
                if (slot >= 1 && slot <= 7 && slot % 2 == 1) m.category = CATS[slot / 2];
                else if (slot >= 9 && slot <= 44) {
                    List<String> types = types(m.category);
                    if (slot - 9 >= types.size()) return;
                    if (right && s.minutes == 0) {
                        p.sendMessage(c(t(p, "Primero activa la limpieza (reloj) y elige los minutos.", "First enable cleanup (clock) and choose minutes."), NamedTextColor.RED));
                        return;
                    }
                    s.toggle(types.get(slot - 9), right);
                    dirty = true;
                } else if (slot == 49) {
                    int step = e.isShiftClick() ? 5 : 1;
                    s.minutes = MobGuardSettings.stepMinutes(s.minutes, right ? -step : step, minMinutes, maxMinutes);
                    dirty = true;
                } else if (slot == 46) { s.overrides.clear(); s.minutes = 0; dirty = true; }
                else return;
            }
            case MSGS -> {
                if (!full) return;
                int idx = -1;
                for (int i = 0; i < 4; i++) if (MSG_SLOTS[i] == slot) idx = i;
                if (idx < 0 || !msgAllowed(r, MSG_FLAGS[idx])) return;
                String fl = MSG_FLAGS[idx];
                if (e.isShiftClick()) {
                    String cur = flagText(r, fl);
                    if (cur == null) { p.sendMessage(c(t(p, "No hay mensaje configurado.", "No message set."), NamedTextColor.GRAY)); return; }
                    Component msg = LegacyComponentSerializer.legacyAmpersand().deserialize(cur.replace("%player%", p.getName()));
                    if (fl.endsWith("title")) p.showTitle(Title.title(msg, Component.empty())); else p.sendMessage(msg);
                    return;
                }
                if (!p.hasPermission("protectionstones.flags.edit." + fl)) return;
                if (e.isRightClick()) p.performCommand(cmd() + " flag " + fl + " none");
                else { ask(p, Ask.EDIT, m.loc, fl, t(p, "Escribe el mensaje en el chat (o 'cancelar'). Colores con &, max " + MessageSanitizer.MAX_LEN + " caracteres, %player% = nombre del dueno.",
                        "Type the message in chat (or 'cancel'). Use & colors, max " + MessageSanitizer.MAX_LEN + " chars, %player% = owner name.")); return; }
            }
            case CONFIRM -> {
                if (!full) return;
                if (slot == 20) m.page = Page.MAIN;
                else if (slot == 24) {
                    PSRegion here = PSRegion.fromLocationGroup(p.getLocation());
                    if (here == null || !key(here).equals(m.key)) {
                        p.sendMessage(c(t(p, "Debes estar dentro del claim para quitar la proteccion.", "You must be inside the claim to remove its protection."), NamedTextColor.RED));
                        return;
                    }
                    p.closeInventory();
                    p.performCommand(cmd() + " unclaim");
                    return;
                } else return;
            }
        }
        open(p, m);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- chat prompts (rename / add by name)

    private void ask(Player p, Ask a, Location loc, String flag, String msg) {
        p.closeInventory();
        pending.put(p.getUniqueId(), new Pending(a, loc, System.currentTimeMillis(), flag));
        p.sendMessage(c(msg, NamedTextColor.YELLOW));
    }

    // HIGHEST + not ignoring cancelled: if the server chat filter (Odysseia ChatFilterListener, LOWEST) cancelled it, the text is rejected.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onChat(AsyncChatEvent e) {
        Pending pd = pending.remove(e.getPlayer().getUniqueId());
        if (pd == null) return;
        boolean filtered = e.isCancelled();
        e.setCancelled(true);
        Player p = e.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (System.currentTimeMillis() - pd.at() > 60_000 || text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("cancelar")) {
            p.sendMessage(c(t(p, "Cancelado.", "Cancelled."), NamedTextColor.GRAY));
            return;
        }
        if (filtered) { p.sendMessage(c(t(p, "Texto rechazado por el filtro del chat.", "Text rejected by the chat filter."), NamedTextColor.RED)); return; }
        if (pd.ask() == Ask.EDIT) {
            MessageSanitizer.Result res = MessageSanitizer.clean(text);
            if (!res.ok()) { p.sendMessage(c(t(p, res.errorEs(), res.errorEn()), NamedTextColor.RED)); return; }
            final String clean = res.text();
            Bukkit.getScheduler().runTask(plugin, () -> {
                PSRegion r = PSRegion.fromLocationGroup(pd.loc());
                if (r == null || !full(p, r) || !msgAllowed(r, pd.flag()) || !p.hasPermission("protectionstones.flags.edit." + pd.flag())) return;
                p.performCommand(cmd() + " flag " + pd.flag() + " " + clean);
                Menu m = new Menu(pd.loc(), key(r));
                m.page = Page.MSGS;
                open(p, m);
            });
            return;
        }
        boolean ok = pd.ask() == Ask.RENAME ? text.length() <= 32 && !text.isEmpty() : text.matches("[A-Za-z0-9_.]{1,16}");
        if (!ok) { p.sendMessage(c(t(p, "Texto no valido.", "Invalid text."), NamedTextColor.RED)); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            PSRegion r = PSRegion.fromLocationGroup(pd.loc());
            if (r == null || !full(p, r)) return;
            p.performCommand(cmd() + (pd.ask() == Ask.RENAME ? " name " : " add ") + text);
            Menu m = new Menu(pd.loc(), key(r));
            m.page = pd.ask() == Ask.ADD ? Page.MEMBERS : Page.MAIN;
            open(p, m);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { pending.remove(e.getPlayer().getUniqueId()); }

    // ---------------------------------------------------------------- mob protection

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le) || le instanceof Player || disabledWorlds.contains(le.getWorld().getName())) return;
        Player attacker = null;
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (e instanceof EntityDamageByEntityEvent be) {
            Entity d = be.getDamager();
            if (d instanceof Projectile pr) { ProjectileSource src = pr.getShooter(); if (src instanceof Player sp) attacker = sp; else return; }
            else if (d instanceof Player sp) attacker = sp;
            else if (cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        } else if (cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION) return;
        PSRegion r = PSRegion.fromLocationGroup(le.getLocation());
        if (r == null) return;
        if (attacker != null && (r.isOwner(attacker.getUniqueId()) || r.isMember(attacker.getUniqueId()))) return;
        if (settingsFor(key(r)).state(le.getType().name()) == State.PROTECTED) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- timed cleanup (per claim, budgeted per run)

    private void tick() {
        if (queue.isEmpty()) {
            long now = System.currentTimeMillis();
            if (now - lastRebuild < intervalTicks * 50L) return;
            lastRebuild = now;
            data.forEach((k, s) -> { if (s.hasClean()) queue.add(k); });
            firstSeen.keySet().removeIf(u -> Bukkit.getEntity(u) == null);
            return;
        }
        for (int i = 0; i < claimsPerRun && !queue.isEmpty(); i++) sweep(queue.poll());
    }

    private void sweep(String key) {
        MobGuardSettings s = data.get(key);
        if (s == null || !s.hasClean()) return;
        int sep = key.indexOf(':');
        World w = Bukkit.getWorld(key.substring(0, sep));
        if (w == null || disabledWorlds.contains(w.getName())) return;
        RegionManager rm = WGUtils.getRegionManagerWithWorld(w);
        if (rm == null) return;
        ProtectedRegion pr = rm.getRegion(key.substring(sep + 1));
        if (pr == null) { data.remove(key); dirty = true; return; } // claim gone
        BlockVector3 mn = pr.getMinimumPoint(), mx = pr.getMaximumPoint();
        int cx0 = mn.x() >> 4, cx1 = mx.x() >> 4, cz0 = mn.z() >> 4, cz1 = mx.z() >> 4;
        if ((long) (cx1 - cx0 + 1) * (cz1 - cz0 + 1) > maxChunksPerClaim) return;
        long now = System.currentTimeMillis(), limit = s.minutes * 60_000L;
        for (int cx = cx0; cx <= cx1; cx++) for (int cz = cz0; cz <= cz1; cz++) {
            Chunk ch = w.isChunkLoaded(cx, cz) ? w.getChunkAt(cx, cz) : null;
            if (ch == null) continue;
            for (Entity en : ch.getEntities()) {
                if (!(en instanceof LivingEntity le) || s.state(en.getType().name()) != State.CLEAN) continue;
                Location l = en.getLocation();
                if (!pr.contains(l.getBlockX(), l.getBlockY(), l.getBlockZ()) || exempt(le)) { firstSeen.remove(en.getUniqueId()); continue; }
                Long first = firstSeen.putIfAbsent(en.getUniqueId(), now);
                if (first != null && now - first >= limit) { firstSeen.remove(en.getUniqueId()); en.remove(); }
            }
        }
    }

    /** Never clean: players, named/tamed/leashed, bosses, mounted, tagged, or mobs owned by other plugins (Odysseia, MultiverseCreatures, bosses...). */
    static boolean exempt(LivingEntity le) {
        if (le instanceof Player || le instanceof Boss || le instanceof ArmorStand) return true;
        if (le.customName() != null || le.isLeashed() || !le.getPassengers().isEmpty() || le.isInsideVehicle()) return true;
        if (le instanceof Tameable t && (t.isTamed() || t.getOwnerUniqueId() != null)) return true;
        if (!le.getScoreboardTags().isEmpty() || le.hasMetadata("NPC")) return true;
        for (NamespacedKey k : le.getPersistentDataContainer().getKeys())
            if (!SAFE_PDC_NAMESPACES.contains(k.getNamespace())) return true;
        return false;
    }
}
