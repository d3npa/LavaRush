package jp.d3npa.lavarush;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import com.projectkorra.projectkorra.util.DamageHandler;
import com.projectkorra.projectkorra.util.TempBlock;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.ArrayList;

public class LavaRush extends LavaAbility implements AddonAbility {
    final static String NAME = "LavaRush";
    final static String VERSION = "3.0a";
    final static String AUTHOR = "d3npa";
    final static String DESCRIPTION = "An offensive Lavabending ability. To use, left-click in any direction. " +
            "A wave of lava will erupt from the ground, swallowing up anything in its way.";
    final static int MIN_LENGTH = 4;
    final static int DAMAGE = 4;

    static ArrayList<LavaRush> instances = new ArrayList<>();
    static long cooldown = 10000;
    static int range = 10;

    private BendingPlayer bendingPlayer;
    private Location origin;
    private Vector direction;
    private int spam = 0;
    private int interval = 20;
    private long lastProgress;
    private LRState state;

    /* Blocks are added in groups with their neighbors. Blocks within a group are animated at the same time. */
    private ArrayList<ArrayList<Block>> wave = new ArrayList<>();
    private int depth;

    private BlockIterator line;
    private ArrayList<TempBlock> previousWaveHead = new ArrayList<>();

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

        bendingPlayer = BendingPlayer.getBendingPlayer(player);
        direction = player
                .getEyeLocation()
                .getDirection()
                .setY(0)
                .normalize();

        origin = player
                .getLocation()
                .add(new Vector(0, -1, 0)) // in the ground
                .add(direction.clone().multiply(3)); // start 3 blocks in front of player

        /* make sure origin is not in the air */
        int counter = 0;
        while (GeneralMethods.isTransparent(origin.getBlock())) {
            origin.setY(origin.getY() - 1);
            if (++counter == 3) {
                this.remove();
                return;
            }
        }

        lastProgress = System.currentTimeMillis();
        line = new BlockIterator(origin.getWorld(), origin.toVector(), direction, 0, range);
        calculateWave();

        if (wave.size() >= MIN_LENGTH) {
            state = LRState.ANIMATING;
            bendingPlayer.addCooldown(this);
            this.start();
        }
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
                    // replace previous wave "head" with air
                    while (!previousWaveHead.isEmpty()) {
                        TempBlock tempBlock = previousWaveHead.removeFirst();
                        // set to low lava
                        Levelled lavaData = (Levelled) tempBlock.getBlockData();
                        lavaData.setLevel(1); // range is 0 (very low) to 15 (full)
                        tempBlock.setType(lavaData);
                    }

                    if (depth == wave.size()) {
                        // reached end of wave
                        depth = 0;
                        state = LRState.RESTING;
                        break;
                    }

                    // turn ground to lava and create new wave "head"
                    ArrayList<Block> currentLayer = wave.get(depth++);

                    for (Block block : currentLayer) {
                        Block above = block.getRelative(BlockFace.UP);
                        TempBlock tempBlock = new TempBlock(block, Material.LAVA);
                        previousWaveHead.add(new TempBlock(above, Material.LAVA));
                        for (Entity entity : GeneralMethods.getEntitiesAroundPoint(block.getLocation(), 1.0)) {
                            DamageHandler.damageEntity(entity, DAMAGE, this);
                        }
                    }

                    if (!currentLayer.isEmpty()) {
                        playEarthbendingSound(currentLayer.getFirst().getLocation());
                    }
                }
                case RESTING -> {
                    if (++spam == 60) {
                        state = LRState.CLEANUP;
                    }
                }
                case CLEANUP -> {
                    ArrayList<Block> currentLayer = wave.get(depth++);
                    for (Block block : currentLayer) {
                        TempBlock tempBlock = TempBlock.get(block);
                        if (tempBlock != null) {
                            tempBlock.revertBlock();
                        }

                        TempBlock above = TempBlock.get(block.getRelative(BlockFace.UP));
                        if (above != null) {
                            above.revertBlock();
                        }
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
        return false;
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
        /* precalculate a list of all blocks transformed by the wave */
        int slopeLimit = 1;
        while (line.hasNext()) {
            BlockFace directionAsBlockFace = GeneralMethods.getCardinalDirection(direction);

            Block middle = line.next();
            Block left = middle.getRelative(getLeftBlockFace(directionAsBlockFace));
            Block right = middle.getRelative(getLeftBlockFace(directionAsBlockFace).getOppositeFace());

            ArrayList<Block> layer = new ArrayList<>();
            for (Block block : new Block[] { left, middle, right }) {
                Block lavabendable = getLavabendableBlock(block, slopeLimit);
                if (lavabendable != null) {
                    layer.add(lavabendable);
                    if (lavabendable.getY() < block.getY()) {
                        slopeLimit++;
                    }
                }
            }

            if (layer.isEmpty()) {
                return;
            }

            wave.add(layer);
        }
    }


    public BlockFace getLeftBlockFace(BlockFace forward)
    {
        return switch (forward) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            case EAST -> BlockFace.NORTH;
            case NORTH_WEST -> BlockFace.SOUTH_WEST;
            case NORTH_EAST -> BlockFace.NORTH_WEST;
            case SOUTH_WEST -> BlockFace.SOUTH_EAST;
            case SOUTH_EAST -> BlockFace.NORTH_EAST;
            default -> null;
        };
    }

    public Block getLavabendableBlock(Block block, int slopeLimit) {
        /* some checks must be made on the block before adding
         *  - is there space above?
         *  - if it is air, is there earth below it? (slight slope)
         *  - if the block above is not air, does that one have air above and is earthbendable?
         *  - is it earthbendable?
         */

        /* when air or water, torch etc, use the block beneath and perform more checks */
        int counter = 0;
        while (GeneralMethods.isTransparent(block)) {
            if (counter++ == Math.max(slopeLimit, 0)) {
                return null;
            }

            block = block.getRelative(BlockFace.DOWN);
        }

        Block above = block.getRelative(BlockFace.UP);
        if (!GeneralMethods.isTransparent(above)) {
            Block aboveTwice = above.getRelative(BlockFace.UP);
            if (GeneralMethods.isTransparent(aboveTwice)) {
                block = above;
            } else {
                return null;
            }
        }

        if (!isEarthbendable(block)) {
            return null;
        }

        return block;
    }
}
