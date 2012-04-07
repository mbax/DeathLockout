package org.kitteh.deathlockout;

import java.util.HashSet;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public class DeathLockout extends JavaPlugin implements Listener {

    private HashSet<String> lockedOut;
    private int timeout;
    private int minutes;

    @Override
    public void onEnable() {
        this.lockedOut = new HashSet<String>();

        this.getConfig().options().copyDefaults(true);
        this.saveConfig();
        this.minutes = this.getConfig().getInt("timeout");
        this.timeout = this.minutes * 20 * 60;

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            final Player died = (Player) event.getEntity();
            if (died.hasPermission("deathlockout.exempt")) {
                this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Notify(died.getName()), this.timeout);
                died.sendMessage(ChatColor.YELLOW + "[DeathLockout] Exempt from being kicked, you may stay.");
                died.sendMessage(ChatColor.YELLOW + "       I will inform you when time would have been up.");
                return;
            }
            this.lockedOut.add(died.getName());
            this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Unlock(died.getName()), this.timeout);
            died.kickPlayer(ChatColor.WHITE + "You died. " + ChatColor.RED + this.minutes + ChatColor.WHITE + " minutes until you revive");
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void onPreLogin(PlayerPreLoginEvent event) {
        if (this.lockedOut.contains(event.getName())) {
            event.disallow(Result.KICK_OTHER, ChatColor.RED.toString() + this.minutes + ChatColor.WHITE + " minutes until you revive");
        }
    }

    private class Unlock implements Runnable {
        private final String name;

        public Unlock(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            DeathLockout.this.lockedOut.remove(this.name);
        }
    }

    private class Notify implements Runnable {
        private final String name;

        public Notify(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            final Player target = DeathLockout.this.getServer().getPlayerExact(this.name);
            if ((target != null) && target.isOnline()) {
                target.sendMessage(ChatColor.YELLOW + "[DeathLockout] You would have been revived now.");
            }
        }
    }
}
