package me.gorgeousone.netherview.threedstuff;

import org.bukkit.util.Vector;

public class Plane {
	
	private Vector origin;
	private Vector normal;
	
	public Plane(Vector origin, Vector normal) {
		
		this.origin = origin.clone();
		this.normal = normal.clone().normalize();
		
		if (normal.lengthSquared() == 0) {
			throw new IllegalArgumentException("normal cannot be 0");
		}
	}
	
	public Vector getOrigin() {
		return origin.clone();
	}
	
	public Vector getNormal() {
		return normal.clone();
	}
	
	public boolean contains(Vector point) {
		
		if (point == null) {
			return false;
		}
		
		Vector relPoint = getOrigin().subtract(point);
		return Math.abs(getNormal().dot(relPoint)) < 0.0001;
	}
	
	public Vector getIntersection(Line line) {
		
		Vector normal = getNormal();
		
		double d = getOrigin().subtract(line.getOrigin()).dot(normal) / line.getDirection().dot(normal);
		Vector intersection = line.getPoint(d);
		
		return contains(intersection) ? intersection : null;
	}
	
	public void translate(Vector delta) {
		origin.add(delta);
	}
}
