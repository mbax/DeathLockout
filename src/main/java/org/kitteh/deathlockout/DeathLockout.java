/*
 * * Copyright (C) 2012-2014 Matt Baxter http://kitteh.org
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.kitteh.deathlockout;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public final class DeathLockout extends JavaPlugin implements Listener {
    private class Lockout {
        private boolean preventJoin;
        private final long time;
        private final String username;

        private Lockout(String username) {
            this.time = System.currentTimeMillis() + DeathLockout.this.millis;
            this.username = username;
        }

        private void exempt() {
            this.preventJoin = false;
        }

        private boolean isBlocked() {
            return this.preventJoin;
        }

        private long getTime() {
            return this.time;
        }

        private String getUsername() {
            return this.username;
        }
    }

    private static final String FORMAT = ChatColor.RED + "%d" + ChatColor.WHITE + " %s until you revive";

    private final Map<UUID, Lockout> lockedOut = new ConcurrentHashMap<UUID, Lockout>();
    private final Map<String, UUID> userMap = new HashMap<String, UUID>();
    private int millis;
    private int minutes;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return false;
        }
        final UUID uuid = this.userMap.remove(args[0].toLowerCase());
        if (uuid == null) {
            sender.sendMessage(ChatColor.YELLOW + "Could not find a locked out player named \"" + ChatColor.WHITE + args[0] + ChatColor.YELLOW + "\"");
        } else {
            Lockout lockout = this.lockedOut.remove(uuid);
            final String name = lockout != null ? lockout.getUsername() : args[0];
            sender.sendMessage(ChatColor.YELLOW + "[DeathLockout] " + ChatColor.WHITE + name + ChatColor.YELLOW + " can now rejoin");
        }
        return true;
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.minutes = this.getConfig().getInt("timeout");
        this.millis = this.minutes * 60 * 1000;

        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                Iterator<Map.Entry<UUID, Lockout>> iterator = DeathLockout.this.lockedOut.entrySet().iterator();
                Map.Entry<UUID, Lockout> entry;
                while (iterator.hasNext()) {
                    entry = iterator.next();
                    if (entry.getValue().getTime() < currentTime) {
                        UUID uuid = entry.getKey();
                        DeathLockout.this.userMap.remove(entry.getValue().getUsername().toLowerCase());
                        iterator.remove();
                        Player player = DeathLockout.this.getServer().getPlayer(uuid);
                        if (player != null) {
                            player.sendMessage(ChatColor.YELLOW + "[DeathLockout] You would have been revived now.");
                        }
                    }
                }
            }
        }, 20, 20);

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            final Player died = (Player) event.getEntity();
            final UUID uuid = died.getUniqueId();
            final String name = died.getName();
            Lockout lockout = new Lockout(name);
            if (died.hasPermission("deathlockout.exempt")) {
                died.sendMessage(ChatColor.YELLOW + "[DeathLockout] Exempt from being kicked, you may stay.");
                died.sendMessage(ChatColor.YELLOW + "       I will inform you when time would have been up.");
                lockout.exempt();
            } else {
                died.kickPlayer(ChatColor.WHITE + "You died. " + String.format(FORMAT, this.minutes, "minutes"));
                this.userMap.put(name.toLowerCase(), uuid);
            }
            this.lockedOut.put(uuid, lockout);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        Lockout lockout = this.lockedOut.get(event.getUniqueId());
        if (lockout != null && lockout.isBlocked()) {
            long count;
            String unit;
            long remaining = (lockout.getTime() - System.currentTimeMillis()) / 1000;
            if (remaining < 60) {
                count = remaining;
                unit = "seconds";
            } else {
                count = remaining / 60;
                unit = "minutes";
            }
            event.disallow(Result.KICK_OTHER, String.format(FORMAT, count, unit));
        }
    }
}