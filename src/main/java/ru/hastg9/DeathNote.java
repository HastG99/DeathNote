package ru.hastg9;

import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class DeathNote extends JavaPlugin implements Listener {

    private static final Logger LOGGER = LogManager.getLogManager().getLogger(DeathNote.class.getSimpleName());

    private static final HashMap<String, Integer> deathQueue = new HashMap<>();
    private static final HashMap<String, EntityDamageEvent.DamageCause> deathReasons = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginCommand("givenote").setExecutor(this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        String name = event.getPlayer().getName();

        if(!deathQueue.containsKey(name)) return;

        killTarget(name, deathQueue.get(name), target -> target.setHealth(0.0));

    }

    @EventHandler
    public void onEvent(PlayerInteractEvent event) {
        ItemStack item = event.getItem();

        if(item == null || item.getType() != Material.WRITABLE_BOOK) return;

        if(!item.hasItemMeta()) return;

        if(event.getAction() != Action.LEFT_CLICK_AIR) return;

        onBookUsage(event);

    }

    @EventHandler
    public void onEvent(PlayerDeathEvent event) {
        event.setDeathMessage("");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(!sender.hasPermission("deathnote.get")) return true;

        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);

        BookMeta meta = (BookMeta) book.getItemMeta();

        meta.setDisplayName(ChatColor.BLACK + "Death Note");


        meta.addPage("§0§lDeath Note\n\n" +
                "§rUsage:\n" +
                "1. Player whose name you write in notebook will die.\n" +
                "2. After the player's name, you can set the date of his death in seconds.\n" +
                "3. After specifying the time of death, you can determine the cause.\n",
                "4. After writing the name in the notebook, close it and click the left mouse button.\n" +
                "5. If you don't write the date of death, the player will die in 20 seconds.\n" +
                "6. If you do not specify a cause of death, the player dies of a heart attack.",
                "7. Names can be written starting from page 4. Otherwise, nothing will happen.");

        book.setItemMeta(meta);

        NBTItem nbtItem = new NBTItem(book);

        nbtItem.setString("death-note", UUID.randomUUID().toString());

        nbtItem.applyNBT(book);

        Player player = Bukkit.getPlayer(sender.getName());

        player.getInventory().addItem(book);

        player.sendMessage("Тетрадь смерти получена.");

        return true;
    }

    public void onBookUsage(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        ItemStack book = event.getItem();

        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if(!checkBook(book)) return;

        List<String> pages = bookMeta.getPages();

        if(pages.size() < 4) return;

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {

            for(int i = 3; i < pages.size(); i++) {
                bookMeta.setPage(i + 1, parsePage(pages.get(i)));
            }

            book.setItemMeta(bookMeta);

        });
    }

    public boolean checkBook(ItemStack book) {
        NBTItem nbtItem = new NBTItem(book);

        return nbtItem.hasKey("death-note");
    }

    public String parsePage(String page) {
        String[] entries = page.trim().split("\n");

        for(int i = 0; i < entries.length; i++) {
            String entry = entries[i];
            boolean result = parseEntry(entries[i]);

            entries[i] =  result ? "§m" + entry + "§r" + "\n" : entry;
        }

        return String.join("\n", entries);
    }

    public boolean parseEntry(String entry) {
        entry = entry.trim();

        if(entry.startsWith("§m")) return false;

        String[] args = entry.split("\\s");

        if(args.length < 1) return true;

        String name = ChatColor.stripColor(args[0]);

        int t = 20;

        if(args.length > 1) {
            String time = args[1];

            try {
                t = Integer.parseInt(time);
            } catch (NumberFormatException ex) {
            }
        }

        if(args.length > 2) {
            String reason = args[2];

            if(reason.equalsIgnoreCase("explosion")) {
                killTarget(name, t, target -> {
                    target.getWorld().createExplosion(target.getLocation(), 4f);
                    target.damage(100);
                });

                return true;
            }

            if(reason.equalsIgnoreCase("anvil")) {
                killTarget(name, t, target -> {
                    Location anvilLoc = target.getLocation().add(0, 3, 0);
                    anvilLoc.getWorld().spawnFallingBlock(anvilLoc, new MaterialData(Material.ANVIL));
                    target.damage(100);
                });

                return true;
            }

            if(reason.equalsIgnoreCase("suicide")) {
                killTarget(name, t, target -> {
                    target.damage(100);
                });

                return true;
            }

            if(reason.equalsIgnoreCase("fall")) {
                killTarget(name, t, target -> {
                    target.teleport(target.getLocation().add(0, 100, 0));
                });

                return true;
            }

            if(reason.equalsIgnoreCase("lava")) {
                killTarget(name, t, target -> {
                    target.getWorld().getBlockAt(target.getLocation()).setType(Material.LAVA);
                    target.damage(100);
                });

                return true;
            }

            if(reason.equalsIgnoreCase("void")) {
                killTarget(name, t, target -> {
                    target.teleport(new Location(target.getWorld(), target.getLocation().getX(), -10, target.getLocation().getZ()));
                });

                return true;
            }

            return true;
        }

        killTarget(name, t, target -> target.setHealth(0.0));

        return true;
    }

    public void killTarget(String targetName, int time, Consumer<Player> callback) {

        Player target = Bukkit.getPlayer(targetName);

        if(deathQueue.containsKey(targetName) && deathQueue.get(targetName) != 2) return;

        deathQueue.put(targetName, time);

        if(target == null) return;

        Bukkit.getScheduler().runTaskLater(this, () -> {

            if(!target.isOnline()) {
                deathQueue.put(targetName, 2);
                return;
            }

            callback.accept(target);

            deathQueue.remove(targetName);
        }, time  * 20L);
    }


}
