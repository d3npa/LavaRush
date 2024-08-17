package jp.d3npa.lavarush;

import com.projectkorra.projectkorra.ProjectKorra;
import com.projectkorra.projectkorra.event.PlayerSwingEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LRListener implements Listener {
    public static void register() {
        ProjectKorra.plugin
                .getServer()
                .getPluginManager()
                .registerEvents(new LRListener(), ProjectKorra.plugin);
    }

    @EventHandler
    public void onSwing(PlayerSwingEvent event) {
        Player player = event.getPlayer();
        if (LavaRush.canUse(player)) {
            new LavaRush(player);
        }
    }
}
