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

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.plugin.java.JavaPlugin;

public class DeathLockout extends JavaPlugin implements Listener {
    private Set<String> lockedOut = new CopyOnWriteArraySet<String>();
    private int timeout;
    private int minutes;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.minutes = this.getConfig().getInt("timeout");
        this.timeout = this.minutes * 20 * 60;

        this.getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            final Player died = (Player) event.getEntity();
            final String name = died.getName();
            if (died.hasPermission("deathlockout.exempt")) {
                this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                    @Override
                    public void run() {
                        final Player target = DeathLockout.this.getServer().getPlayerExact(name);
                        if (target != null) {
                            target.sendMessage(ChatColor.YELLOW + "[DeathLockout] You would have been revived now.");
                        }
                    }
                }, this.timeout);
                died.sendMessage(ChatColor.YELLOW + "[DeathLockout] Exempt from being kicked, you may stay.");
                died.sendMessage(ChatColor.YELLOW + "       I will inform you when time would have been up.");
                return;
            }
            this.lockedOut.add(died.getName());
            this.getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
                @Override
                public void run() {
                    DeathLockout.this.lockedOut.remove(name);
                }
            }, this.timeout);
            died.kickPlayer(ChatColor.WHITE + "You died. " + ChatColor.RED + this.minutes + ChatColor.WHITE + " minutes until you revive");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (this.lockedOut.contains(event.getName())) {
            event.disallow(Result.KICK_OTHER, ChatColor.RED.toString() + this.minutes + ChatColor.WHITE + " minutes until you revive");
        }
    }
}