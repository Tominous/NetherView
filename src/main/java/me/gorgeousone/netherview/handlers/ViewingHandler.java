package me.gorgeousone.netherview.handlers;

import me.gorgeousone.netherview.blockcache.BlockCache;
import me.gorgeousone.netherview.blockcache.BlockCacheFactory;
import me.gorgeousone.netherview.blockcache.BlockCopy;
import me.gorgeousone.netherview.blockcache.BlockVec;
import me.gorgeousone.netherview.portal.PortalSide;
import me.gorgeousone.netherview.portal.Portal;
import me.gorgeousone.netherview.threedstuff.AxisUtils;
import me.gorgeousone.netherview.threedstuff.Transform;
import me.gorgeousone.netherview.threedstuff.ViewingFrustum;
import me.gorgeousone.netherview.threedstuff.ViewingFrustumFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ViewingHandler {
	
	private PortalHandler portalHandler;
	
	private Map<Portal, BlockCache> netherCaches;
	private Map<UUID, Set<BlockCopy>> playerViews;
	
	public ViewingHandler(PortalHandler portalHandler) {
		
		this.portalHandler = portalHandler;
	
		netherCaches = new HashMap<>();
		playerViews = new HashMap<>();
	}
	
	public Set<BlockCopy> getViewSession(Player player) {
		
		UUID uuid = player.getUniqueId();
		
		if(!playerViews.containsKey(uuid))
			playerViews.put(uuid, new HashSet<>());
		
		return playerViews.get(uuid);
	}
	
	public void displayPortal(Player player, Portal portal) {
		
		if (portal.getWorld().getEnvironment() != World.Environment.NORMAL)
			return;
		
		Portal netherPortal = portalHandler.getLinkedNetherPortal(portal);
		
//		if (!netherCaches.containsKey(netherPortal))
//			netherCaches.put(netherPortal, BlockCacheFactory.createBlockCache(portalHandler.getLinkedNetherPortal(portal), 10));
		
		Location playerEyeLoc = player.getEyeLocation();
		
		boolean playerIsRelativelyNegativeToPortal = isPlayerRelativelyNegativeToPortal(playerEyeLoc, portal);
		ViewingFrustum playerViewingFrustum = ViewingFrustumFactory.createViewingFrustum(playerEyeLoc.toVector(), portal);
		
//		PortalSide portalSideToDisplay = playerIsRelativelyNegativeToPortal ? PortalSide.POSITIVE : PortalSide.NEGATIVE;
		
//		BlockCache netherBlockCache = getCachedBlocks(netherPortal, portalSideToDisplay);
//		Set<BlockCopy> visibleBlocks = detectBlocksInView(playerViewingFrustum, netherBlockCache, portalHandler.getLinkTransform(portal));
//		renderNetherBLocks(player, portal, visibleBlocks);
	}
	
	private BlockCache getCachedBlocks(Portal portal, PortalSide portalSideToDisplay) {
		return netherCaches.get(portal);
	}
	
	private boolean isPlayerRelativelyNegativeToPortal(Location playerLoc, Portal portal) {
		
		Vector portalDist = portal.getLocation().toVector().subtract(playerLoc.toVector());
		Vector portalFacing = AxisUtils.getAxisPlaneNormal(portal.getAxis());
		
		return portalFacing.dot(portalDist) > 0;
	}
	
	private Set<BlockCopy> detectBlocksInView(ViewingFrustum viewingFrustum, BlockCache netherCache, Transform transform) {
		
		World world = Bukkit.getWorld("world");
		
		Set<BlockCopy> blocksInCone = new HashSet<>();
		
		BlockVec min = netherCache.getCopyMin();
		BlockVec max = netherCache.getCopyMax();
		
		for (int x = min.getX(); x < max.getX(); x++) {
			for (int y = min.getY(); y < max.getY(); y++) {
				for (int z = min.getZ(); z < max.getZ(); z++) {
					
					BlockVec transformedCorner = transform.getTransformed(new BlockVec(x, y, z));
					
//					if (viewingFrustum.contains(transformedCorner.toVector())) {
//
//						for (BlockVec block : getBlocksAroundCorner(new BlockVec(x, y, z))) {
//							BlockCopy copy = netherCache.getCopyAt(block);
//
//							if(copy == null)
//								continue;
//
//							blocksInCone.add(copy.clone().setPosition(transform.getTransformed(copy.getPosition())));
//						}
//					}
					
					if (viewingFrustum.contains(transformedCorner.toVector())) {

						for (BlockCopy blockCopy : netherCache.getCopiesAround(new BlockVec(x, y, z))) {
							BlockCopy copy = blockCopy.clone();
							copy.setPosition(transform.getTransformed(copy.getPosition()));
							
							blocksInCone.add(copy);
//							if(blocksInCone.add(copy))
//								Bukkit.broadcastMessage(copy.getPosition().toString());

//							blocksInCone.add(blockCopy.clone().setPosition(transform.getTransformed(blockCopy.getPosition())));
						}
					}
				}
			}
		}
		
		return blocksInCone;
	}
	
	private Set<BlockVec> getBlocksAroundCorner(BlockVec blockCorner) {
		
		Set<BlockVec> blocksAroundCorner = new HashSet<>();
		
		int x = blockCorner.getX();
		int y = blockCorner.getY();
		int z = blockCorner.getZ();
		
		blocksAroundCorner.add(new BlockVec(x, y, z));
		blocksAroundCorner.add(new BlockVec(x + 1, y, z));
		blocksAroundCorner.add(new BlockVec(x, y, z + 1));
		blocksAroundCorner.add(new BlockVec(x + 1, y, z + 1));
		blocksAroundCorner.add(new BlockVec(x, y -1, z));
		blocksAroundCorner.add(new BlockVec(x + 1, y -1, z));
		blocksAroundCorner.add(new BlockVec(x, y -1, z + 1));
		blocksAroundCorner.add(new BlockVec(x + 1, y -1, z + 1));
		
		return blocksAroundCorner;
	}
	
	private void renderNetherBLocks(Player player, Portal portal, Set<BlockCopy> visibleBlocks) {
		
		System.out.println("view " + visibleBlocks.size() + " blocks");
		World playerWorld = player.getWorld();
		Set<BlockCopy> viewSession = getViewSession(player);
		
		for (Block block : portal.getPortalBlocks())
			player.sendBlockChange(block.getLocation(), Material.AIR.createBlockData());
		
		for (Block block : portal.getFrameBlocks())
			player.sendBlockChange(block.getLocation(), Material.MAGENTA_STAINED_GLASS.createBlockData());
		
		Iterator<BlockCopy> iterator = viewSession.iterator();

		while (iterator.hasNext()) {
			BlockCopy nextCopy = iterator.next();

			if (!visibleBlocks.contains(nextCopy)) {
				refreshBlock(player, nextCopy);
				iterator.remove();
			}
		}
		
		for (BlockCopy copy : visibleBlocks) {
			if(viewSession.add(copy))
				player.sendBlockChange(copy.getPosition().toLocation(playerWorld), copy.getBlockData());
		}
	}
	
	private void refreshBlock(Player player, BlockCopy blockCopy) {
		Location blockLoc = blockCopy.getPosition().toLocation(player.getWorld());
		player.sendBlockChange(blockLoc, blockLoc.getBlock().getBlockData());
	}
}