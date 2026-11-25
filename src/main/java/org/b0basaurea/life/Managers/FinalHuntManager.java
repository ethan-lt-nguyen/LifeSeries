package org.b0basaurea.life.Managers;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FinalHuntManager {

    private static final long SECOND = 20L;
    private static final long FIVE_MINUTES = 5 * 60 * SECOND;
//    private static final long COUNTDOWN_DURATION = 60 * SECOND;

    private static final long REVEAL_INTERVAL = 30 * SECOND;
    private static final int REVEAL_DURATION = 8 * 20;
    private static final double PROXIMITY_RANGE = 24.0;

    private final JavaPlugin plugin;
    private final LivesManager livesManager;
    private final ScoreboardManager scoreboardManager;

    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private final Map<UUID, Integer> heartbeatCooldowns = new HashMap<>();

    private BukkitTask countdownTask;
    private BukkitTask revealTask;
    private BukkitTask proximityTask;

    private UUID boogeyman;
    private boolean countdownActive;
    private boolean huntActive;
    private boolean rerollPending;
    private boolean oneWillLiveShown;

    public FinalHuntManager(
            JavaPlugin plugin,
            LivesManager livesManager,
            ScoreboardManager scoreboardManager
    ) {
        this.plugin = plugin;
        this.livesManager = livesManager;
        this.scoreboardManager = scoreboardManager;
    }

    public void startCountdown() {
        if (countdownActive || huntActive) {
            return;
        }

        countdownActive = true;
        oneWillLiveShown = false;

        Bukkit.broadcast(Component.text(
                "THE END BEGINS IN 5 MINUTES",
                NamedTextColor.DARK_RED
        ));

        showTitleToEveryone(
                Component.text("THE END BEGINS", NamedTextColor.DARK_RED),
                Component.text("IN 5 MINUTES", NamedTextColor.GRAY),
                Duration.ofSeconds(3)
        );

        showRedPlayerTitles();

        playSoundForEveryone(Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.7f);

        schedule(this::showFourMinuteWarning, 4 * 60 * SECOND);
        schedule(this::showOneMinuteWarning, 4 * 60 * SECOND + 45 * SECOND);
        schedule(() -> showCountdownTitle(10), 5 * 60 * SECOND - 10 * SECOND);
        schedule(() -> showCountdownTitle(5), 5 * 60 * SECOND - 5 * SECOND);
        schedule(() -> showCountdownTitle(4), 5 * 60 * SECOND - 4 * SECOND);
        schedule(() -> showCountdownTitle(3), 5 * 60 * SECOND - 3 * SECOND);
        schedule(() -> showCountdownTitle(2), 5 * 60 * SECOND - 2 * SECOND);
        schedule(() -> showCountdownTitle(1), 5 * 60 * SECOND - SECOND);

        countdownTask = Bukkit.getScheduler().runTaskLater(
                plugin,
                this::chooseBoogeyman,
                FIVE_MINUTES
        );

//        schedule(this::showFourMinuteWarning, 30 * SECOND);
//        schedule(this::showOneMinuteWarning, 45 * SECOND);
//
//        schedule(() -> showCountdownTitle(10), 50 * SECOND);
//        schedule(() -> showCountdownTitle(5), 55 * SECOND);
//        schedule(() -> showCountdownTitle(4), 56 * SECOND);
//        schedule(() -> showCountdownTitle(3), 57 * SECOND);
//        schedule(() -> showCountdownTitle(2), 58 * SECOND);
//        schedule(() -> showCountdownTitle(1), 59 * SECOND);
//
//        countdownTask = Bukkit.getScheduler().runTaskLater(
//                plugin,
//                this::chooseBoogeyman,
//                COUNTDOWN_DURATION
//        );
    }

    private void showFourMinuteWarning() {
        Bukkit.broadcast(Component.text(
                "The Boogeyman will be chosen in 1 minute...",
                NamedTextColor.RED
        ));

        playSoundForEveryone(
                Sound.ENTITY_WARDEN_HEARTBEAT,
                1.2f,
                0.65f
        );
    }

    private void showOneMinuteWarning() {
        Bukkit.broadcast(Component.text(
                "THE HUNT IS NEAR",
                NamedTextColor.RED
        ));

        showTitleToEveryone(
                Component.text("THE HUNT IS NEAR", NamedTextColor.RED),
                Component.text("THE BOOGEYMAN WILL BE CHOSEN SOON", NamedTextColor.GRAY),
                Duration.ofSeconds(3)
        );

        playSoundForEveryone(
                Sound.ENTITY_WARDEN_HEARTBEAT,
                1.5f,
                0.55f
        );
    }

    private void showCountdownTitle(int seconds) {
        Title title = Title.title(
                Component.text(seconds, NamedTextColor.DARK_RED),
                Component.text("THE BOOGEYMAN", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(100),
                        Duration.ofMillis(700),
                        Duration.ofMillis(200)
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(
                    player.getLocation(),
                    Sound.BLOCK_DISPENSER_DISPENSE,
                    1.0f,
                    seconds <= 3 ? 0.5f : 0.8f
            );
        }
    }

    private void chooseBoogeyman() {
        countdownActive = false;
        countdownTask = null;
        scheduledTasks.clear();

        if (!selectNewBoogeyman(null)) {
            return;
        }

        huntActive = true;
        playSelectionSequence();
    }

    private boolean selectNewBoogeyman(UUID excludedPlayer) {
        List<Player> eligiblePlayers = new ArrayList<>();
        List<Player> allEligiblePlayers = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (livesManager.getLives(player) <= 1) {
                continue;
            }

            allEligiblePlayers.add(player);

            if (!player.getUniqueId().equals(excludedPlayer)) {
                eligiblePlayers.add(player);
            }
        }

        if (eligiblePlayers.isEmpty()) {
            eligiblePlayers = allEligiblePlayers;
        }

        if (eligiblePlayers.isEmpty()) {
            boogeyman = null;
            huntActive = false;
            stopRevealCycle();
            stopProximityHeartbeat();
            return false;
        }

        Player selectedPlayer = eligiblePlayers.get(
                ThreadLocalRandom.current().nextInt(eligiblePlayers.size())
        );

        boogeyman = selectedPlayer.getUniqueId();
        return true;
    }

    private void playSelectionSequence() {
        Title lookUpTitle = Title.title(
                Component.text("LOOK UP", NamedTextColor.RED),
                Component.text("THE FINAL MOMENT", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(250),
                        Duration.ofSeconds(4),
                        Duration.ofMillis(500)
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(lookUpTitle);
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_HEARTBEAT,
                    1.4f,
                    0.55f
            );
        }

        schedule(this::showSkyOmen, 100L);
        schedule(this::showQuestionTitle, 170L);
        schedule(this::showBoogeymanReveal, 330L);
    }

    private void showSkyOmen() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            spawnLightningCircle(player, 8, 6L);
        }
    }

    private void spawnLightningCircle(
            Player player,
            int points,
            long delayBetweenPoints
    ) {
        final double radius = 3.5;

        for (int point = 0; point < points; point++) {
            long delay = point * delayBetweenPoints;
            double angle = (2 * Math.PI * point) / points;

            schedule(() -> {
                if (!player.isOnline()) {
                    return;
                }

                Location center = player.getLocation();
                Location lightningLocation = center.clone().add(
                        Math.cos(angle) * radius,
                        0,
                        Math.sin(angle) * radius
                );

                spawnVisualLightning(lightningLocation);
                player.getWorld().spawnParticle(
                        Particle.DUST,
                        lightningLocation.clone().add(0, 2, 0),
                        20,
                        0.5,
                        2.0,
                        0.5,
                        new Particle.DustOptions(Color.fromRGB(110, 0, 0), 1.2f)
                );

            }, delay);
        }
    }

    private void showQuestionTitle() {
        Title questionTitle = Title.title(
                Component.text("?", NamedTextColor.DARK_RED),
                Component.text("WHO IS THE BOOGEYMAN?", NamedTextColor.GRAY),
                Title.Times.times(
                        Duration.ofMillis(500),
                        Duration.ofSeconds(6),
                        Duration.ofMillis(1000)
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(questionTitle);
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_HEARTBEAT,
                    1.5f,
                    0.5f
            );
        }
    }

    private void showBoogeymanReveal() {
        Player target = getBoogeyman();

        if (target == null || !target.isOnline()) {
            return;
        }

        spawnVisualLightning(target.getLocation());
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().clone().add(0, 2, 0),
                100,
                1.0,
                2.0,
                1.0,
                new Particle.DustOptions(Color.fromRGB(180, 0, 0), 2.0f)
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                    0.55f,
                    0.7f
            );

            player.showTitle(Title.title(
                    Component.text("THE BOOGEYMAN", NamedTextColor.DARK_RED),
                    Component.text("HUNT THEM DOWN", NamedTextColor.RED),
                    Title.Times.times(
                            Duration.ofMillis(500),
                            Duration.ofSeconds(4),
                            Duration.ofSeconds(1)
                    )
            ));
        }

        scoreboardManager.setBoogeyman(target);

        startRevealCycle();
        startProximityHeartbeat();
    }

    private void showQuietBoogeymanReveal() {
        Player target = getBoogeyman();

        if (target == null || !target.isOnline()) {
            return;
        }

        spawnVisualLightning(target.getLocation());
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().clone().add(0, 2, 0),
                50,
                0.7,
                1.5,
                0.7,
                new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.5f)
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                    0.3f,
                    1.25f
            );
        }

        scoreboardManager.setBoogeyman(target);

        startRevealCycle();
        startProximityHeartbeat();
    }

    private void startRevealCycle() {
        stopRevealCycle();

        revealTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::revealBoogeyman,
                5 * SECOND,
                REVEAL_INTERVAL
        );
    }

    private void revealBoogeyman() {
        Player target = getBoogeyman();

        if (target == null || !target.isOnline()) {
            return;
        }

        target.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                REVEAL_DURATION,
                0,
                false,
                false,
                true
        ));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(target)) {
                continue;
            }

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_HEARTBEAT,
                    1.2f,
                    0.6f
            );

            player.sendActionBar(Component.text(
                    "THE BOOGEYMAN IS NEAR",
                    NamedTextColor.DARK_RED
            ));
        }
    }

    private void startProximityHeartbeat() {
        stopProximityHeartbeat();

        proximityTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                this::playProximityHeartbeat,
                10L,
                2L
        );
    }

    private void showRedPlayerTitles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (livesManager.getLives(player) == 1) {
                showRedPlayerTitles(player);
            }
        }
    }

    public void showRedPlayerTitles(Player player) {
        if (!player.isOnline()) {
            return;
        }

        player.playSound(
                player.getLocation(),
                Sound.ENTITY_WARDEN_HEARTBEAT,
                1.5f,
                0.45f
        );

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_BEACON_DEACTIVATE,
                1.2f,
                0.55f
        );

        player.showTitle(Title.title(
                Component.text("NO MORE LIVES", NamedTextColor.DARK_RED),
                Component.empty(),
                Title.Times.times(
                        Duration.ofMillis(700),
                        Duration.ofSeconds(3),
                        Duration.ofMillis(1000)
                )
        ));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                    1.3f,
                    0.7f
            );

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WITHER_SPAWN,
                    0.8f,
                    0.6f
            );

            player.showTitle(Title.title(
                    Component.text("NO MORE MERCY", NamedTextColor.RED),
                    Component.empty(),
                    Title.Times.times(
                            Duration.ofMillis(700),
                            Duration.ofSeconds(3),
                            Duration.ofMillis(1200)
                    )
            ));
        }, 75L);
    }

    public void checkWhetherOneWillLive() {
        if ((!countdownActive && !huntActive) || oneWillLiveShown) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (livesManager.getLives(player) > 1) {
                return;
            }
        }

        oneWillLiveShown = true;
        countdownActive = false;
        huntActive = false;

        cancelScheduledTasks();
        stopRevealCycle();
        stopProximityHeartbeat();

        showTitleToEveryone(
                Component.text("ONE WILL LIVE", NamedTextColor.DARK_RED),
                Component.text("THE FINAL SESSION", NamedTextColor.RED),
                Duration.ofSeconds(5)
        );
    }

    private void showTitleToEveryone(
            Component titleComponent,
            Component subtitleComponent,
            Duration stayDuration
    ) {
        Title title = Title.title(
                titleComponent,
                subtitleComponent,
                Title.Times.times(
                        Duration.ofMillis(500),
                        stayDuration,
                        Duration.ofMillis(1000)
                )
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
        }
    }

    private void playProximityHeartbeat() {
        Player target = getBoogeyman();

        if (target == null || !target.isOnline()) {
            heartbeatCooldowns.clear();
            return;
        }

        Set<UUID> nearbyPlayers = new HashSet<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.equals(target)) {
                continue;
            }

            if (!player.getWorld().equals(target.getWorld())) {
                continue;
            }

            double distanceSquared = player.getLocation().distanceSquared(
                    target.getLocation()
            );

            if (distanceSquared > PROXIMITY_RANGE * PROXIMITY_RANGE) {
                heartbeatCooldowns.remove(player.getUniqueId());
                continue;
            }

            UUID playerId = player.getUniqueId();
            nearbyPlayers.add(playerId);

            int cooldown = heartbeatCooldowns.getOrDefault(playerId, 0);

            if (cooldown > 0) {
                heartbeatCooldowns.put(playerId, cooldown - 2);
                continue;
            }

            double distance = Math.sqrt(distanceSquared);
            double closeness = 1.0 - distance / PROXIMITY_RANGE;

            // Far away: roughly one heartbeat per second.
            // Very close: roughly four heartbeats per second.
            int heartbeatInterval = (int) Math.round(
                    24.0 - closeness * 18.0
            );

            float volume = (float) (0.35 + closeness * 0.9);
            float pitch = (float) (0.45 + closeness * 0.1);

            player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_HEARTBEAT,
                    volume,
                    pitch
            );

            heartbeatCooldowns.put(playerId, heartbeatInterval);
        }

        heartbeatCooldowns.keySet().retainAll(nearbyPlayers);
    }

    public void handleKill(Player killer, Player victim) {
        if (!huntActive || rerollPending) {
            return;
        }

        boolean boogeymanWasKilled = isBoogeyman(victim);

        // The Boogeyman keeps the role after getting a kill. A new one is
        // selected only when the current Boogeyman is killed.
        if (!boogeymanWasKilled) {
            return;
        }

        rerollPending = true;

        Bukkit.getScheduler().runTask(plugin, () -> {
            rerollPending = false;

            if (!huntActive) {
                return;
            }

            UUID previousBoogeyman = boogeyman;

            if (!selectNewBoogeyman(previousBoogeyman)) {
                return;
            }

            Bukkit.broadcast(Component.text(
                    "A new Boogeyman will be chosen...",
                    NamedTextColor.DARK_RED
            ));

            schedule(this::showQuietBoogeymanReveal, 5 * SECOND);
        });
    }

    public void stopHunt() {
        cancelScheduledTasks();
        stopRevealCycle();
        stopProximityHeartbeat();

        Player target = getBoogeyman();

        if (target != null) {
            target.removePotionEffect(PotionEffectType.GLOWING);

            scoreboardManager.updatePlayerTeam(
                    target,
                    livesManager.getLives(target)
            );
        }

        boogeyman = null;
        countdownActive = false;
        huntActive = false;
        rerollPending = false;
        oneWillLiveShown = false;
    }

    public Player getBoogeyman() {
        if (boogeyman == null) {
            return null;
        }

        return Bukkit.getPlayer(boogeyman);
    }

    public boolean isBoogeyman(Player player) {
        return player != null
                && boogeyman != null
                && boogeyman.equals(player.getUniqueId());
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public boolean isHuntActive() {
        return huntActive;
    }

    private void schedule(Runnable action, long delay) {
        scheduledTasks.add(
                Bukkit.getScheduler().runTaskLater(plugin, action, delay)
        );
    }

    private void cancelScheduledTasks() {
        for (BukkitTask task : scheduledTasks) {
            task.cancel();
        }

        scheduledTasks.clear();

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void stopRevealCycle() {
        if (revealTask != null) {
            revealTask.cancel();
            revealTask = null;
        }
    }

    private void stopProximityHeartbeat() {
        if (proximityTask != null) {
            proximityTask.cancel();
            proximityTask = null;
        }

        heartbeatCooldowns.clear();
    }

    private void playSoundForEveryone(Sound sound, float volume, float pitch) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void spawnVisualLightning(Location location) {
        if (location.getWorld() == null) {
            return;
        }

        location.getWorld().strikeLightningEffect(location);
    }
}
