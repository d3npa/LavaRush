package jp.d3npa.lavarush;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class LavaRush extends LavaAbility implements AddonAbility {
    final static String NAME = "LavaRush";
    final static String VERSION = "3.0a";
    final static String AUTHOR = "d3npa";
    final static String DESCRIPTION = "An offensive Lavabending ability. To use, left-click in any direction. " +
            "A wave of lava will erupt from the ground, swallowing up anything in its way.";

    static ArrayList<LavaRush> instances = new ArrayList<>();
    static long cooldown = 10000;

    private Player player;
    private BendingPlayer bendingPlayer;
    private Location origin;
    private Vector direction;
    private int spam = 0;
    private int interval = 100;
    private long lastProgress;
    private LRState state;

    /* Blocks are added in groups with their neighbors. Blocks within a group are animated at the same time. */
    private ArrayList<ArrayList<Location>> wave = new ArrayList<>();
    private int depth;

    public static void createConfig() {
        FileConfiguration languageConfig = ConfigManager.languageConfig.get();
        languageConfig.addDefault("Abilities.Earth.LavaRush.Description", DESCRIPTION);
        ConfigManager.languageConfig.save();
    }

    public static boolean canUse(Player player) {
        BendingPlayer bendingPlayer = BendingPlayer.getBendingPlayer(player);
        String selectedAbility = bendingPlayer.getBoundAbilityName();

        boolean abilitySelected = selectedAbility.equals(NAME);
        boolean onCooldown = bendingPlayer.isOnCooldown(NAME);

        return abilitySelected && !onCooldown;
    }

    public LavaRush(Player player) {
        super(player);

        this.player = player;
        bendingPlayer = BendingPlayer.getBendingPlayer(player);
        direction = player
                .getEyeLocation()
                .getDirection()
                .setY(0)
                .normalize();

        ProjectKorra.log.info(String.format("Normalized Direction: %s", direction.toString()));

        origin = player
                .getLocation()
                .add(new Vector(0, -1, 0)) // in the ground
                .add(direction.clone().multiply(2)); // start 2 blocks in front of player

        bendingPlayer.addCooldown(this);
        lastProgress = System.currentTimeMillis();
        calculateWave();
        state = LRState.ANIMATING;
        player.sendMessage("New LavaRush Instance");
        this.start();
    }

    @Override
    public void progress() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastProgress = currentTime - lastProgress;

        if (timeSinceLastProgress > interval) {
            lastProgress = currentTime;

            /* LavaRush has 3 phases: casting, resting, cleanup. */
            switch (state) {
                case ANIMATING -> {
                    ArrayList<Location> currentLayer = wave.get(depth++);
                    for (Location loc : currentLayer) {
                        TempBlock tempBlock = new TempBlock(loc.getBlock(), Material.MAGMA_BLOCK);
                    }

                    if (depth == wave.size()) {
                        // reached end of wave
                        depth = 0;
                        state = LRState.RESTING;
                    }
                }
                case RESTING -> {
                    if (++spam == 30) {
                        state = LRState.CLEANUP;
                    }
                }
                case CLEANUP -> {
                    ArrayList<Location> currentLayer = wave.get(depth++);
                    for (Location loc : currentLayer) {
                        TempBlock tempBlock = TempBlock.get(loc.getBlock());
                        tempBlock.revertBlock();
                    }

                    if (depth == wave.size()) {
                        this.remove();
                    }
                }
            }
        }
    }

    @Override
    public boolean isSneakAbility() {
        return true;
    }

    @Override
    public boolean isHarmlessAbility() {
        return false;
    }

    @Override
    public long getCooldown() {
        return cooldown;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Location getLocation() {
        return null;
    }

    @Override
    public void load() {
        LRListener.register();
        LavaRush.createConfig();
        ProjectKorra.log.info(String.format("Loaded %s by %s", NAME, AUTHOR));
    }

    @Override
    public void stop() {
        // TODO: cleanup all instances
    }

    @Override
    public String getAuthor() {
        return AUTHOR;
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    public void calculateWave() {
        /* use player position to precalculate a list of all blocks transformed by the wave */
        final int range = 8;
        depth = 0;

        ProjectKorra.log.info(String.format("Origin: %s", origin.toString()));
        ProjectKorra.log.info(String.format("Direction: %s", direction.toString()));

        for (int i = 0; i < range; i++) {
            // get the next blocks in the wave

            Vector modifiedDirection = direction.clone().multiply(i);
            Location middle = origin.clone().add(modifiedDirection);
            ArrayList<Location> layer = new ArrayList<>();
            layer.add(middle);
            wave.add(layer);
        }

    }
}
