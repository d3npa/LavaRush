package jp.d3npa.lavarush;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.ability.AddonAbility;
import com.projectkorra.projectkorra.ability.LavaAbility;
import com.projectkorra.projectkorra.configuration.ConfigManager;
import org.bukkit.Location;
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

    private BendingPlayer bendingPlayer;
    private Location origin;
    private Vector direction;


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
        player.sendMessage("New LavaRush Instance");
    }

    @Override
    public void progress() {

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
        return 0;
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
}
