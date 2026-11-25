package org.b0basaurea.life.Managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DeathManager implements Listener {

    private final LivesManager livesManager;
    private final ScoreboardManager scoreboardManager;
    private final FinalHuntManager finalHuntManager;

    private final JavaPlugin plugin;
    private final Set<UUID> pendingRedPlayers = new HashSet<>();

    public DeathManager(
            JavaPlugin plugin,
            LivesManager manager,
            ScoreboardManager scoreboardManager,
            FinalHuntManager finalHuntManager
    ) {
        this.plugin = plugin;
        livesManager = manager;
        this.scoreboardManager = scoreboardManager;
        this.finalHuntManager = finalHuntManager;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event)
    {
        Player p = event.getEntity();
        livesManager.removeLives(p, 1);

        int lives = livesManager.getLives(p);

        if (lives == 1
                && (finalHuntManager.isCountdownActive()
                || finalHuntManager.isHuntActive())) {

            pendingRedPlayers.add(p.getUniqueId());

            finalHuntManager.checkWhetherOneWillLive();
        }

        scoreboardManager.updatePlayerTeam(p, lives);

        for(Player player : Bukkit.getOnlinePlayers())
        {
            player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }

        if(lives <= 0) {
            p.setGameMode(GameMode.SPECTATOR);
            p.getWorld().strikeLightningEffect(p.getLocation());

            Title title = Title.title(
                    Component.text(p.getName(), NamedTextColor.RED),
                    Component.text("IS OUT!", NamedTextColor.GRAY),
                    Title.Times.times(
                            Duration.ofMillis(500),   // Fade in
                            Duration.ofSeconds(3),    // Stay
                            Duration.ofMillis(1000)   // Fade out
                    ));

            for (Player player : Bukkit.getOnlinePlayers()) {
                player.showTitle(title);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        if (!pendingRedPlayers.remove(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(
                plugin,
                () -> finalHuntManager.showRedPlayerTitles(player)
        );
    }
}
