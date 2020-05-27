package me.gorgeousone.netherview.threedstuff.viewfrustum;

import me.gorgeousone.netherview.blocktype.Axis;
import me.gorgeousone.netherview.portal.Portal;
import me.gorgeousone.netherview.threedstuff.AxisAlignedRect;
import me.gorgeousone.netherview.threedstuff.Line;
import me.gorgeousone.netherview.threedstuff.Plane;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class ViewingFrustumFactory {
	
	private ViewingFrustumFactory() {}
	
	/**
	 * Returns a viewing frustum with a near plane precisely representing the area the player can see through a portal.
	 *
	 * @param viewPoint   eye location of a player
	 * @param cameraShake player's velocity used for expanding viewing frustum, which stabilizes the portal animation
	 */
	public static ViewingFrustum createFrustum(Vector viewPoint,
	                                           Vector cameraShake,
	                                           AxisAlignedRect portalRect,
	                                           double frustumLength) {
		
		boolean isPlayerBehindPortal = isPlayerBehindPortal(viewPoint, portalRect);
		
		Vector portalNormal = portalRect.getPlane().getNormal();
		Vector playerFacingToPortal = portalNormal.clone().multiply(isPlayerBehindPortal ? 1 : -1);
		
		//this will become near plane of the viewing frustum. It will be cropped to fit the actual player view through the portal
		AxisAlignedRect viewingRect = portalRect.clone().translate(playerFacingToPortal.clone().multiply(0.5));
		
		Vector viewingRectMin = viewingRect.getMin(); //.subtract(threshold);
		Vector viewingRectMax = viewingRect.getMax(); //.add(threshold);
		
		//depending on which portal frame blocks will block the view, the viewing rect bounds are contracted by casting rays along the block edges
		Plane nearPlane = viewingRect.getPlane();
		Axis portalAxis = portalRect.getAxis();
		
		adjustNearPlaneCornerYs(viewingRectMin, viewingRectMax, viewPoint, playerFacingToPortal, nearPlane);
		
		if (portalAxis == Axis.X) {
			adjustNearPlaneCornerXs(viewingRectMin, viewingRectMax, viewPoint, playerFacingToPortal, nearPlane);
		} else {
			adjustNearPlaneCornerZs(viewingRectMin, viewingRectMax, viewPoint, playerFacingToPortal, nearPlane);
		}
		
		Vector viewingRectSize = viewingRectMax.clone().subtract(viewingRectMin);
		double rectWidth = portalAxis == Axis.X ? viewingRectSize.getX() : viewingRectSize.getZ();
		double rectHeight = viewingRectSize.getY();
		
		if (rectWidth < 0) {
			return null;
		}
		
		AxisAlignedRect actualViewingRect = new AxisAlignedRect(viewingRect.getAxis(), viewingRectMin, rectWidth, rectHeight);
		return new ViewingFrustum(viewPoint, actualViewingRect, frustumLength);
	}
	
	/**
	 * Performs some kind of ray casting to adjust the y-coordinates of the "viewing rectangle" corners to the actual visible area.
	 */
	private static void adjustNearPlaneCornerYs(
			Vector viewingRectMin,
			Vector viewingRectMax,
			Vector viewPoint,
			Vector playerFacingToPortal,
			Plane nearPlane) {
		
		//the lower corner will be moved a bit upwards if the player is located lower than the portal
		if (viewPoint.getY() < viewingRectMin.getY()) {
			
			Vector closeRectMin = viewingRectMin.clone().subtract(playerFacingToPortal);
			Vector newRectMin = nearPlane.getIntersection(new Line(viewPoint, closeRectMin));
			viewingRectMin.setY(newRectMin.getY());
			
		//the upper corner will be moved downwards a bit if the player is higher lower than the portal
		} else if (viewPoint.getY() > viewingRectMax.getY()) {
			
			Vector closeRectMax = viewingRectMax.clone().subtract(playerFacingToPortal);
			Vector newRectMax = nearPlane.getIntersection(new Line(viewPoint, closeRectMax));
			viewingRectMax.setY(newRectMax.getY());
		}
	}
	
	private static void adjustNearPlaneCornerXs(
			Vector viewingRectMin,
			Vector viewingRectMax,
			Vector viewPoint,
			Vector playerFacingToPortal,
			Plane nearPlane) {
		
		if (viewPoint.getX() < viewingRectMin.getX()) {
			
			Vector closeRectMin = viewingRectMin.clone().subtract(playerFacingToPortal);
			Vector newRectMin = nearPlane.getIntersection(new Line(viewPoint, closeRectMin));
			viewingRectMin.setX(newRectMin.getX());
			
		} else if (viewPoint.getX() > viewingRectMax.getX()) {
			
			Vector closeRectMax = viewingRectMax.clone().subtract(playerFacingToPortal);
			Vector newRectMax = nearPlane.getIntersection(new Line(viewPoint, closeRectMax));
			viewingRectMax.setX(newRectMax.getX());
		}
	}
	
	private static void adjustNearPlaneCornerZs(
			Vector viewingRectMin,
			Vector viewingRectMax,
			Vector viewPoint,
			Vector playerFacingToPortal,
			Plane nearPlane) {
		
		if (viewPoint.getZ() < viewingRectMin.getZ()) {
			
			Vector closeRectMin = viewingRectMin.clone().subtract(playerFacingToPortal);
			Vector newRectMin = nearPlane.getIntersection(new Line(viewPoint, closeRectMin));
			viewingRectMin.setZ(newRectMin.getZ());
			
		} else if (viewPoint.getZ() > viewingRectMax.getZ()) {
			
			Vector closeRectMax = viewingRectMax.clone().subtract(playerFacingToPortal);
			Vector newRectMax = nearPlane.getIntersection(new Line(viewPoint, closeRectMax));
			viewingRectMax.setZ(newRectMax.getZ());
		}
	}
	public static boolean isPlayerBehindPortal(Player player, Portal portal) {
		return isPlayerBehindPortal(player.getEyeLocation().toVector(), portal.getPortalRect());
	}
	
	public static boolean isPlayerBehindPortal(Vector viewPoint, AxisAlignedRect portalRect) {
		
		Vector portalPos = portalRect.getMin();
		
		return portalRect.getAxis() == Axis.X ?
				viewPoint.getZ() < portalPos.getZ() :
				viewPoint.getX() < portalPos.getX();
	}
}
