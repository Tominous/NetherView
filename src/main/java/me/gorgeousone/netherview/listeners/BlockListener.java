package me.gorgeousone.netherview.listeners;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.BlockPosition;
import me.gorgeousone.netherview.NetherView;
import me.gorgeousone.netherview.blockcache.BlockCache;
import me.gorgeousone.netherview.blockcache.BlockCacheFactory;
import me.gorgeousone.netherview.blocktype.BlockType;
import me.gorgeousone.netherview.handlers.PortalHandler;
import me.gorgeousone.netherview.handlers.ViewHandler;
import me.gorgeousone.netherview.portal.Portal;
import me.gorgeousone.netherview.threedstuff.BlockVec;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.HashSet;
import java.util.Map;

public class BlockListener implements Listener {
	
	private NetherView main;
	private PortalHandler portalHandler;
	private ViewHandler viewHandler;
	private Material portalMaterial;
	
	public BlockListener(NetherView main,
	                     PortalHandler portalHandler,
	                     ViewHandler viewHandler,
	                     Material portalMaterial) {
		
		this.main = main;
		this.portalHandler = portalHandler;
		this.viewHandler = viewHandler;
		this.portalMaterial = portalMaterial;
		addBlockUpdateInterceptor();
	}
	
	private void addBlockUpdateInterceptor() {
		
		ProtocolLibrary.getProtocolManager().addPacketListener(
				
				new PacketAdapter(main, ListenerPriority.HIGHEST, PacketType.Play.Server.BLOCK_CHANGE) {
					
					@Override
					public void onPacketSending(PacketEvent event) {
						
						if (event.isCancelled() || event.getPacketType() != PacketType.Play.Server.BLOCK_CHANGE) {
							return;
						}
						
						Player player = event.getPlayer();
						
						if (!viewHandler.hasViewSession(player)) {
							return;
						}
						
						BlockPosition blockPos = event.getPacket().getBlockPositionModifier().getValues().get(0);
						BlockVec blockPosVec = new BlockVec(blockPos);
						
						//cannot limit location check to projection cache size because the portal itself is not included in that area, yet needed
//						if (!projection.contains(blockPosVec)) {
//							return;
//						}
						
						Map<BlockVec, BlockType> viewSession = viewHandler.getViewSession(player);
						
						if (viewSession.containsKey(blockPosVec)) {
							event.getPacket().getBlockData().write(0, viewSession.get(blockPosVec).getWrapped());
						}
					}
				}
		);
	}
	
	private void removeDamagedPortals(Block block) {
		
		World blockWorld = block.getWorld();
		
		if (!portalHandler.hasPortals(blockWorld)) {
			return;
		}
		
		BlockVec blockLoc = new BlockVec(block);
		
		for (Portal portal : new HashSet<>(portalHandler.getPortals(blockWorld))) {
			
			if (portal.contains(blockLoc)) {
				viewHandler.removePortal(portal);
				portalHandler.removePortal(portal);
			}
		}
	}
	
	private void updateBlockCaches(Block block, BlockType newBlockType, boolean blockWasOccluding) {
		
		World blockWorld = block.getWorld();
		
		if (!portalHandler.hasPortals(blockWorld)) {
			return;
		}
		
		BlockVec blockPos = new BlockVec(block);
		
		for (BlockCache cache : portalHandler.getBlockCaches(blockWorld)) {
			
			if (!cache.contains(blockPos)) {
				continue;
			}
			
			Map<BlockVec, BlockType> updatedCopies = BlockCacheFactory.updateBlockInCache(cache, block, newBlockType, blockWasOccluding);
			
			if (!updatedCopies.isEmpty()) {
				viewHandler.updateProjections(cache, updatedCopies);
			}
		}
	}
	
	@EventHandler(priority = EventPriority.LOWEST)
	public void onBlockInteract(PlayerInteractEvent event) {
		
		if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
			return;
		}
		
		Player player = event.getPlayer();
		
		if (!viewHandler.hasViewSession(player)) {
			return;
		}
		
		Map<BlockVec, BlockType> viewSession = viewHandler.getViewSession(player);
		BlockVec blockPos = new BlockVec(event.getClickedBlock());
		
		if (viewSession.containsKey(blockPos)) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent event) {
		
		Block block = event.getBlock();
		Material blockType = block.getType();
		
		updateBlockCaches(block, BlockType.of(Material.AIR), block.getType().isOccluding());
		
		if (blockType == Material.OBSIDIAN || blockType == portalMaterial) {
			removeDamagedPortals(block);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent event) {
		
		Block block = event.getBlock();
		updateBlockCaches(block, BlockType.of(block), false);
		
		Player player = event.getPlayer();
		
		if (!viewHandler.hasViewSession(player)) {
			return;
		}
		
		Map<BlockVec, BlockType> viewSession = viewHandler.getViewSession(player);
		BlockVec blockPos = new BlockVec(block);
		
		if (viewSession.containsKey(blockPos)) {
			event.setCancelled(true);
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		
		for (Block block : event.blockList()) {
			if (block.getType() == portalMaterial) {
				removeDamagedPortals(block);
			}
		}
		
		for (Block block : event.blockList())
			updateBlockCaches(block, BlockType.of(Material.AIR), block.getType().isOccluding());
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockExplode(EntityExplodeEvent event) {
		
		for (Block block : event.blockList()) {
			if (block.getType() == portalMaterial) {
				removeDamagedPortals(block);
			}
		}
		
		for (Block block : event.blockList())
			updateBlockCaches(block, BlockType.of(Material.AIR), block.getType().isOccluding());
	}
	
	//water, lava, dragon eggs
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockSpill(BlockFromToEvent event) {
		Block block = event.getToBlock();
		updateBlockCaches(block, BlockType.of(event.getBlock()), false);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBurn(BlockBurnEvent event) {
		Block block = event.getBlock();
		updateBlockCaches(block, BlockType.of(Material.AIR), block.getType().isOccluding());
	}
	
	private void onAnyGrowEvent(BlockGrowEvent event) {
		Block block = event.getBlock();
		updateBlockCaches(block, BlockType.of(event.getNewState()), block.getType().isOccluding());
	}
	
	//pumpkin/melon growing
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockGrow(BlockGrowEvent event) {
		onAnyGrowEvent(event);
	}
	
	//grass, mycelium spreading
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockSpread(BlockSpreadEvent event) {
		onAnyGrowEvent(event);
	}
	
	//obsidian, concrete
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockForm(BlockFormEvent event) {
		onAnyGrowEvent(event);
	}
	
	//ice melting
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockFade(BlockFadeEvent event) {
		Block block = event.getBlock();
		updateBlockCaches(block, BlockType.of(event.getNewState()), block.getType().isOccluding());
	}
	
	//falling sand and maybe endermen (actually also sheeps but that doesn't work)
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityChangeBlock(EntityChangeBlockEvent event) {
		Block block = event.getBlock();
		//TODO check what 1.8 uses instead of event.getBlockData()
		updateBlockCaches(block, BlockType.of(event.getBlock()), false);
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onPlantGrow(StructureGrowEvent event) {
		
		for (BlockState state : event.getBlocks()) {
			updateBlockCaches(state.getBlock(), BlockType.of(state), false);
		}
	}
}