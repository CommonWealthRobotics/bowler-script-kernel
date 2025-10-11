package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.Bounds;
import eu.mihosoft.vrl.v3d.Vector3d;

public class StoragbeBounds {
    /**
     * The x coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	minx;

    /**
     * The y coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	miny;

    /**
     * The z coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	minz;
    /**
     * The x coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	maxx;

    /**
     * The y coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	maxy;

    /**
     * The z coordinate.
     */
	@Expose (serialize = true, deserialize = true)
    public	double	maxz;
	public StoragbeBounds(Bounds b) {
		minx=b.getMin().x;
		miny=b.getMin().y;
		minz=b.getMin().z;
		maxx=b.getMax().x;
		maxy=b.getMax().y;
		maxz=b.getMax().z;
	}
	public Bounds getBounds() {
		Vector3d min = new Vector3d(minx, miny, minz);
		Vector3d max = new Vector3d(maxx, maxy, maxz);
		return new Bounds(min, max);
	}
	
}
