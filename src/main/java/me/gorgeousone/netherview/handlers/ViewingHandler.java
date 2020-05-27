package me.gorgeousone.netherview.handlers;

import me.gorgeousone.netherview.DisplayUtils;
import me.gorgeousone.netherview.NetherView;
import me.gorgeousone.netherview.blockcache.BlockCache;
import me.gorgeousone.netherview.blockcache.ProjectionCache;
import me.gorgeousone.netherview.blockcache.Transform;
import me.gorgeousone.netherview.blocktype.Axis;
import me.gorgeousone.netherview.blocktype.BlockType;
import me.gorgeousone.netherview.portal.Portal;
import me.gorgeousone.netherview.threedstuff.AxisAlignedRect;
import me.gorgeousone.netherview.threedstuff.BlockVec;
import me.gorgeousone.netherview.threedstuff.viewfrustum.ViewingFrustum;
import me.gorgeousone.netherview.threedstuff.viewfrustum.ViewingFrustumFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ViewingHandler {
	
	private NetherView main;
	private PortalHandler portalHandler;
	
	private Map<UUID, Portal> viewedPortals;
	private Map<UUID, ProjectionCache> viewedProjections;
	private Map<UUID, Map<BlockVec, BlockType>> playerViewSessions;
	
	public ViewingHandler(NetherView main, PortalHandler portalHandler) {
		
		this.main = main;
		this.portalHandler = portalHandler;
		
		viewedProjections = new HashMap<>();
		playerViewSessions = new HashMap<>();
		viewedPortals = new HashMap<>();
	}
	
	public void reset() {
		
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (hasViewSession(player)) {
				hideViewSession(player);
			}
		}
		
		viewedPortals.clear();
		viewedProjections.clear();
		playerViewSessions.clear();
	}
	
	/**
	 * Returns a Map of BlockTypes linked to their location that are currently displayed with fake blocks
	 * to a player.
	 */
	public Map<BlockVec, BlockType> getViewSession(Player player) {
		
		UUID uuid = player.getUniqueId();
		
		playerViewSessions.putIfAbsent(uuid, new HashMap<>());
		return playerViewSessions.get(uuid);
	}
	
	public boolean hasViewSession(Player player) {
		return playerViewSessions.containsKey(player.getUniqueId());
	}
	
	/**
	 * Removes the players view session and removes all sent fake blocks.
	 */
	public void hideViewSession(Player player) {
		DisplayUtils.removeFakeBlocks(player, getViewSession(player));
		removeVieSession(player);
	}
	
	/**
	 * Only removes the player reference.
	 */
	public void removeVieSession(Player player) {
		playerViewSessions.remove(player.getUniqueId());
		viewedPortals.remove(player.getUniqueId());
	}
	
	/**
	 * Locates the nearest portal to a player and displays a portal animation to them with fake blocks (if portal in view range).
	 * @param playerMovement current movement of the player used for improving consistency of portal animation
	 */
	public void displayNearestPortalTo(Player player, Vector playerMovement) {
		
		Location playerEyeLoc = player.getEyeLocation().add(playerMovement);
		Portal portal = portalHandler.getNearestPortal(playerEyeLoc, true);
		
		if (portal == null) {
			hideViewSession(player);
			removeVieSession(player);
			return;
		}
		
		Vector portalDistance = portal.getLocation().subtract(playerEyeLoc).toVector();
		
		if (portalDistance.lengthSquared() > main.getPortalDisplayRangeSquared()) {
			hideViewSession(player);
			removeVieSession(player);
			return;
		}
		
		AxisAlignedRect portalRect = portal.getPortalRect();
		
		//display the portal totally normal if the player is not standing next to or in the portal
		if (getDistanceToPortal(playerEyeLoc, portalRect) > 0.5) {
			displayPortalTo(player, playerEyeLoc, playerMovement, portal, true, main.hidePortalBlocks());
			
			//keep portal blocks hidden (if requested) if the player is standing next to the portal to avoid light flickering
		} else if (!portalRect.contains(playerEyeLoc.toVector())) {
			displayPortalTo(player, playerEyeLoc, playerMovement, portal, false, main.hidePortalBlocks());
			
			//if the player is standing inside the portal projection should be dropped
		} else {
			hideViewSession(player);
			removeVieSession(player);
		}
	}
	
	private double getDistanceToPortal(Location playerEyeLoc, AxisAlignedRect portalRect) {
		
		double distanceToPortal;
		
		if (portalRect.getAxis() == Axis.X) {
			distanceToPortal = portalRect.getMin().getZ() - playerEyeLoc.getZ();
		} else {
			distanceToPortal = portalRect.getMin().getX() - playerEyeLoc.getX();
		}
		
		return Math.abs(distanceToPortal);
	}
	
	public void displayPortalTo(Player player,
	                            Location playerEyeLoc,
	                            Vector playerMovement,
	                            Portal portal,
	                            boolean displayFrustum,
	                            boolean hidePortalBlocks) {
		
		if (!portal.isLinked()) {
			return;
		}
		
		if (!portal.projectionsAreLoaded()) {
			portalHandler.loadProjectionCachesOf(portal);
		} else {
			portalHandler.updatePortalDataExpirationTime(portal);
		}
		
		ProjectionCache projection = ViewingFrustumFactory.isPlayerBehindPortal(player, portal) ? portal.getFrontProjection() : portal.getBackProjection();
		ViewingFrustum playerFrustum = ViewingFrustumFactory.createFrustum(playerEyeLoc.toVector(), playerMovement, portal.getPortalRect(), projection.getCacheLength());
		
		viewedPortals.put(player.getUniqueId(), portal);
		viewedProjections.put(player.getUniqueId(), projection);
		
		Map<BlockVec, BlockType> visibleBlocks = new HashMap<>();
		
		if (playerFrustum != null && displayFrustum) {
			visibleBlocks.putAll(getBlocksInFrustum(projection, playerFrustum));
		}
		
		if (hidePortalBlocks) {
			for (Block portalBlock : portal.getPortalBlocks())
				visibleBlocks.put(new BlockVec(portalBlock), BlockType.of(Material.AIR));
		}
		
		displayBlocks(player, visibleBlocks);
	}
	
	private Map<BlockVec, BlockType> getBlocksInFrustum(ProjectionCache projection, ViewingFrustum playerFrustum) {
		
		BlockVec min = projection.getMin();
		BlockVec max = projection.getMax();
		
		//reduce the iterated area of the block cache to what the frustum can actually reach
		//TODO put that in a new method
		AxisAlignedRect nearPlaneRect = playerFrustum.getNearPlaneRect();
		AxisAlignedRect farPlaneRect = playerFrustum.getFarPlaneRect();
		
		if (farPlaneRect.getAxis() == Axis.X) {
			
			double newMinX = Math.min(nearPlaneRect.getMin().getX(), farPlaneRect.getMin().getX());
			double newMaxX = Math.max(nearPlaneRect.getMax().getX(), farPlaneRect.getMax().getX());
			
			if (newMinX > min.getX()) {
				min.setX((int) Math.floor(newMinX));
			}
			if (newMaxX < max.getX()) {
				max.setX((int) Math.ceil(newMaxX));
			}
			
		} else {
			
			double newMinZ = Math.min(nearPlaneRect.getMin().getZ(), farPlaneRect.getMin().getZ());
			double newMaxZ = Math.max(nearPlaneRect.getMax().getZ(), farPlaneRect.getMax().getZ());
			
			if (newMinZ > min.getZ()) {
				min.setZ((int) Math.floor(newMinZ));
			}
			if (newMaxZ < max.getZ()) {
				max.setZ((int) Math.ceil(newMaxZ));
			}
		}
		
		Map<BlockVec, BlockType> blocksInFrustum = new HashMap<>();
		
		for (int x = min.getX(); x <= max.getX(); x++) {
			for (int y = min.getY(); y <= max.getY(); y++) {
				for (int z = min.getZ(); z <= max.getZ(); z++) {
					
					BlockVec blockPos = new BlockVec(x, y, z);
					
					if (playerFrustum.contains(blockPos.toVector())) {
						blocksInFrustum.putAll(projection.getBlockTypesAround(new BlockVec(x, y, z)));
					}
				}
			}
		}
		
		return blocksInFrustum;
	}
	
	/**
	 * Forwards the changes from a block cache to all the linked projection caches. This also live-updates what the players see
	 */
	public void updateProjections(BlockCache cache, Map<BlockVec, BlockType> updatedCopies) {
		
		for (ProjectionCache linkedProjection : portalHandler.getProjectionsLinkedTo(cache)) {
			
			Map<BlockVec, BlockType> projectionUpdates = updateBlocksInProjection(linkedProjection, updatedCopies);
			
			//TODO stop iterating through all players for each projection?
			for (Map.Entry<UUID, ProjectionCache> viewedProjection : viewedProjections.entrySet()) {
				
				if (viewedProjection.getValue() != linkedProjection) {
					continue;
				}
				
				UUID playerID = viewedProjection.getKey();
				Player player = Bukkit.getPlayer(playerID);
				Portal portal = viewedPortals.get(playerID);
				
				ViewingFrustum playerFrustum = ViewingFrustumFactory.createFrustum(
						player.getEyeLocation().toVector(),
						new Vector(),
						portal.getPortalRect(),
						linkedProjection.getCacheLength());
				
				if (playerFrustum == null) {
					continue;
				}
				
				Map<BlockVec, BlockType> blockChangesInFrustum = updateBlocksInFrustum(player, projectionUpdates, playerFrustum);
				DisplayUtils.displayFakeBlocks(player, blockChangesInFrustum);
			}
		}
	}
	
	/**
	 * Transfers block changes from a block cache into a connected projection cache.
	 * @param projection the projection cache to be updated
	 * @param changedBlocks info about blocks types changed in the block cache
	 * @return a map with the blocks changed types in the projection
	 */
	private Map<BlockVec, BlockType> updateBlocksInProjection(ProjectionCache projection, Map<BlockVec, BlockType> changedBlocks) {
		
		Map<BlockVec, BlockType> projectionUpdates = new HashMap<>();
		
		for (Map.Entry<BlockVec, BlockType> updatedCopy : changedBlocks.entrySet()) {
			
			Transform blockTransform = projection.getTransform();
			BlockVec projectionBlockPos = blockTransform.transformVec(updatedCopy.getKey().clone());
			BlockType projectionBlockType = updatedCopy.getValue().clone().rotate(blockTransform.getQuarterTurns());
			
			projection.setBlockTypeAt(projectionBlockPos, projectionBlockType);
			projectionUpdates.put(projectionBlockPos, projectionBlockType);
		}
		
		return projectionUpdates;
	}
	
	/**
	 * Forwards block changes from a projection cache to the actual view session of a player, but only the ones that are actually visible in the player's visible frustum.
	 * @return all blocks that are confirmed to be visible to the player and need to be sent as new fake blocks
	 */
	private Map<BlockVec, BlockType> updateBlocksInFrustum(Player player, Map<BlockVec, BlockType> projectionUpdates, ViewingFrustum playerFrustum) {
		
		Map<BlockVec, BlockType> frustumUpdates = new HashMap<>();
		Map<BlockVec, BlockType> viewSession = getViewSession(player);
		
		for (Map.Entry<BlockVec, BlockType> entry : projectionUpdates.entrySet()) {
			
			BlockVec blockPos = entry.getKey();
			BlockType blockType = entry.getValue();
			
			if (blockType == null) {
				blockType = BlockType.of(blockPos.toBlock(player.getWorld()));
			}
			
			if (playerFrustum.containsBlock(blockPos.toVector())) {
				frustumUpdates.put(blockPos, blockType);
				viewSession.put(blockPos, blockType);
			}
		}
		
		return frustumUpdates;
	}
	
	/**
	 * Adding new blocks to the portal animation for a player.
	 * But first redundant blocks are filtered out and outdated blocks are refreshed for the player.
	 */
	private void displayBlocks(Player player, Map<BlockVec, BlockType> blocksToDisplay) {
		
		Map<BlockVec, BlockType> viewSession = getViewSession(player);
		
		Map<BlockVec, BlockType> removedBlocks = new HashMap<>();
		Iterator<BlockVec> iterator = viewSession.keySet().iterator();
		
		while (iterator.hasNext()) {
			
			BlockVec blockPos = iterator.next();
			
			if (!blocksToDisplay.containsKey(blockPos)) {
				removedBlocks.put(blockPos, viewSession.get(blockPos));
				iterator.remove();
			}
		}
		
		iterator = blocksToDisplay.keySet().iterator();
		
		while (iterator.hasNext()) {
			
			if (viewSession.containsKey(iterator.next())) {
				iterator.remove();
			}
		}
		
		viewSession.putAll(blocksToDisplay);
		DisplayUtils.removeFakeBlocks(player, removedBlocks);
		DisplayUtils.displayFakeBlocks(player, blocksToDisplay);
	}

	/**
	 * Removes a portal and related portal animations.
	 */
	public void removePortal(Portal portal) {
		
		Set<Portal> affectedPortals = portalHandler.getPortalsLinkedTo(portal);
		affectedPortals.add(portal);
		Iterator<Map.Entry<UUID, Portal>> iter = viewedPortals.entrySet().iterator();
		
		while (iter.hasNext()) {
			
			Map.Entry<UUID, Portal> playerView = iter.next();
			
			if (!affectedPortals.contains(playerView.getValue())) {
				continue;
			}
			
			iter.remove();
			hideViewSession(Bukkit.getPlayer(playerView.getKey()));
		}
	}
}