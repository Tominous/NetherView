package me.gorgeousone.netherview;

import me.gorgeousone.netherview.blocktype.BlockType;
import me.gorgeousone.netherview.bstats.Metrics;
import me.gorgeousone.netherview.cmdframework.command.ParentCommand;
import me.gorgeousone.netherview.cmdframework.handlers.CommandHandler;
import me.gorgeousone.netherview.commmands.EnableDebugCommand;
import me.gorgeousone.netherview.commmands.ListPortalsCommand;
import me.gorgeousone.netherview.commmands.PortalInfoCommand;
import me.gorgeousone.netherview.commmands.ReloadCommand;
import me.gorgeousone.netherview.handlers.PortalHandler;
import me.gorgeousone.netherview.handlers.ViewHandler;
import me.gorgeousone.netherview.listeners.BlockListener;
import me.gorgeousone.netherview.listeners.PlayerMoveListener;
import me.gorgeousone.netherview.listeners.TeleportListener;
import me.gorgeousone.netherview.portal.PortalLocator;
import me.gorgeousone.netherview.updatechecks.UpdateCheck;
import me.gorgeousone.netherview.updatechecks.VersionResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class NetherView extends JavaPlugin {
	
	private static final int resourceId = 78885;
	
	public final static String VIEW_PERM = "netherview.viewportals";
	public final static String LINK_PERM = "netherview.linkportals";
	public final static String RELOAD_PERM = "netherview.reload";
	public final static String INFO_PERM = "netherview.info";
	
	private boolean isLegacyServer;
	private Material portalMaterial;
	
	private PortalHandler portalHandler;
	private ViewHandler viewHandler;
	
	private Set<UUID> worldsWithPortalViewing;
	
	private int portalProjectionDist;
	private int portalDisplayRangeSquared;
	
	private boolean hidePortalBlocks;
	private boolean cancelTeleportWhenLinking;
	private boolean debugMessagesEnabled;
	
	private HashMap<World.Environment, BlockType> worldBorderBlockTypes;
	
	@Override
	public void onEnable() {
		
		Metrics metrics = new Metrics(this, 7571);
		registerTotalPortalsChart(metrics);
		registerPortalsOnline(metrics);
		
		loadServerVersion();
		BlockType.configureVersion(isLegacyServer);
		PortalLocator.configureVersion(portalMaterial);
		
		portalHandler = new PortalHandler(this);
		viewHandler = new ViewHandler(this, portalHandler);
		
		//do not register listeners or commands before creating handlers
		registerListeners();
		registerCommands();
		
		loadConfigData();
		
		checkForUpdates();
	}
	
	public void reload() {
		
		onDisable();
		loadConfigData();
		checkForUpdates();
	}
	
	@Override
	public void onDisable() {
		
		savePortalsToConfig();
		viewHandler.reset();
		portalHandler.reset();
	}
	
	public int getPortalProjectionDist() {
		return portalProjectionDist;
	}
	
	public int getPortalDisplayRangeSquared() {
		return portalDisplayRangeSquared;
	}
	
	public boolean hidePortalBlocks() {
		return hidePortalBlocks;
	}
	
	public boolean cancelTeleportWhenLinking() {
		return cancelTeleportWhenLinking;
	}
	
	public boolean canCreatePortalViews(World world) {
		return worldsWithPortalViewing.contains(world.getUID());
	}
	
	public BlockType getWorldBorderBlockType(World.Environment environment) {
		return worldBorderBlockTypes.get(environment);
	}
	
	public boolean debugMessagesEnabled() {
		return debugMessagesEnabled;
	}
	
	public boolean setDebugMessagesEnabled(boolean state) {
		
		if (debugMessagesEnabled != state) {
			
			debugMessagesEnabled = state;
			PortalLocator.setDebugMessagesEnabled(debugMessagesEnabled);
			getConfig().set("debug-messages", debugMessagesEnabled);
			saveConfig();
			return true;
		}
		
		return false;
	}
	
	private void loadServerVersion() {
		
		String version = getServer().getBukkitVersion();
		isLegacyServer =
				version.contains("1.8") ||
				version.contains("1.9") ||
				version.contains("1.10") ||
				version.contains("1.11") ||
				version.contains("1.12");
		
		portalMaterial = isLegacyServer ? Material.matchMaterial("PORTAL") : Material.NETHER_PORTAL;
	}
	
	private void registerCommands() {
		
		ParentCommand netherViewCommand = new ParentCommand("netherview", null, false, "just tab");
		netherViewCommand.addChild(new ReloadCommand(netherViewCommand, this));
		netherViewCommand.addChild(new EnableDebugCommand(netherViewCommand, this));
		netherViewCommand.addChild(new ListPortalsCommand(netherViewCommand, this, portalHandler));
		netherViewCommand.addChild(new PortalInfoCommand(netherViewCommand, portalHandler));
		
		CommandHandler cmdHandler = new CommandHandler(this);
		cmdHandler.registerCommand(netherViewCommand);
	}
	
	private void registerListeners() {
		
		PluginManager manager = Bukkit.getPluginManager();
		manager.registerEvents(new TeleportListener(this, portalHandler), this);
		manager.registerEvents(new PlayerMoveListener(this, viewHandler), this);
		manager.registerEvents(new BlockListener(this, portalHandler, viewHandler, portalMaterial), this);
	}
	
	private void loadConfigData() {
		
		reloadConfig();
		getConfig().options().copyDefaults(true);
		addVersionDependentDefaults();
		saveConfig();
		
		portalProjectionDist = getConfig().getInt("portal-projection-view-distance", 8);
		portalDisplayRangeSquared = (int) Math.pow(getConfig().getInt("portal-display-range", 32), 2);
		hidePortalBlocks = getConfig().getBoolean("hide-portal-blocks", true);
		cancelTeleportWhenLinking = getConfig().getBoolean("cancel-teleport-when-linking-portals", true);
		
		setDebugMessagesEnabled(getConfig().getBoolean("debug-messages", false));
		
		loadWorldBorderBlockTypes();
		loadWorldsWithPortalViewing();
		loadRegisteredPortals();
	}
	
	private void addVersionDependentDefaults() {
		
		if (isLegacyServer) {
			getConfig().addDefault("overworld-border", "stained_clay");
			getConfig().addDefault("nether-border", "stained_clay:14");
			getConfig().addDefault("end-border", "wool:15");
			
		} else {
			getConfig().addDefault("overworld-border", "white_terracotta");
			getConfig().addDefault("nether-border", "red_concrete");
			getConfig().addDefault("end-border", "black_concrete");
		}
	}
	
	private void loadWorldsWithPortalViewing() {
		
		worldsWithPortalViewing = new HashSet<>();
		
		List<String> worldNames = getConfig().getStringList("worlds-with-portal-viewing");
		
		for (String worldName : worldNames) {
			World world = Bukkit.getWorld(worldName);
			
			if (world == null) {
				getLogger().log(Level.WARNING, "World " + worldName + " could be found.");
			} else {
				worldsWithPortalViewing.add(world.getUID());
			}
		}
	}
	
	private void loadWorldBorderBlockTypes() {
		
		worldBorderBlockTypes = new HashMap<>();
		worldBorderBlockTypes.put(World.Environment.NORMAL, deserializeWorldBorderBlockType("overworld-border"));
		worldBorderBlockTypes.put(World.Environment.NETHER, deserializeWorldBorderBlockType("nether-border"));
		worldBorderBlockTypes.put(World.Environment.THE_END, deserializeWorldBorderBlockType("end-border"));
	}
	
	private BlockType deserializeWorldBorderBlockType(String configPath) {
		
		String configValue = getConfig().getString(configPath);
		String defaultValue = getConfig().getDefaults().getString(configPath);
		
		BlockType worldBorder;
		
		try {
			worldBorder = BlockType.of(configValue);
			
		} catch (Exception e) {
			getLogger().log(Level.WARNING, "'" + configValue + "' could not be interpreted as a block type. Using '" + defaultValue + "' instead.");
			return BlockType.of(defaultValue);
		}
		
		if (!worldBorder.isOccluding()) {
			getLogger().log(Level.WARNING, "'" + configValue + "' is not an occluding block. Using '" + defaultValue + "' instead.");
			return BlockType.of(defaultValue);
		}
		
		return worldBorder;
	}
	
	private void loadRegisteredPortals() {
		
		File portalConfigFile = new File(getDataFolder() + File.separator + "portals.yml");
		
		if (!portalConfigFile.exists()) { return; }
		
		YamlConfiguration portalConfig = YamlConfiguration.loadConfiguration(portalConfigFile);
		portalHandler.loadPortals(portalConfig);
		portalHandler.loadPortalLinks(portalConfig);
		
		try {
			portalConfig.save(portalConfigFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void savePortalsToConfig() {
		
		File portalConfigFile = new File(getDataFolder() + File.separator + "portals.yml");
		YamlConfiguration portalConfig = YamlConfiguration.loadConfiguration(portalConfigFile);
		portalHandler.savePortals(portalConfig);
		
		try {
			portalConfig.save(portalConfigFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void registerTotalPortalsChart(Metrics metrics) {
		metrics.addCustomChart(new Metrics.SingleLineChart("total_portals", () -> portalHandler.getTotalPortalCount()));
	}
	
	private void registerPortalsOnline(Metrics metrics) {
		metrics.addCustomChart(new Metrics.SingleLineChart("portals_online", () -> portalHandler.getRecentlyViewedPortalsCount()));
	}
	
	private void checkForUpdates() {
		
		new UpdateCheck(this, resourceId).handleResponse((versionResponse, newVersion) -> {
			
			if (versionResponse == VersionResponse.FOUND_NEW) {
				
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (player.isOp()) {
						player.sendMessage("A new version of NetherView is available: " + ChatColor.LIGHT_PURPLE + newVersion);
					}
				}
				
				getLogger().info("A new version of NetherView is available: " + newVersion);
				
			} else if (versionResponse == VersionResponse.UNAVAILABLE) {
				getLogger().info("Unable to check for new versions...");
			}
		}).check();
	}
}