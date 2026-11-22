package junit.bowler;

import java.util.Objects;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.neuronrobotics.bowlerstudio.threed.ManifoldBRepPlan;
import com.neuronrobotics.bowlerstudio.threed.ManifoldBRepPlan.Face;
import com.neuronrobotics.bowlerstudio.threed.ManifoldBRepPlan.Plan;

public class ManifoldBRepPlanTest {
	private static final class Mesh {
		private final double[] positions;
		private final long[] triangles;

		private Mesh(double[] positions, long[] triangles) {
			this.positions = positions;
			this.triangles = triangles;
		}

		public double[] positions() {
			return positions;
		}

		public long[] triangles() {
			return triangles;
		}

		@Override
		public boolean equals(Object other) {
			if (this == other)
				return true;
			if (!(other instanceof Mesh))
				return false;
			Mesh that = (Mesh) other;
			return Objects.equals(positions, that.positions) && Objects.equals(triangles, that.triangles);
		}

		@Override
		public int hashCode() {
			int result = 0;
			result = 31 * result + Objects.hashCode(positions);
			result = 31 * result + Objects.hashCode(triangles);
			return result;
		}

		@Override
		public String toString() {
			return "Mesh[positions=" + positions + ", triangles=" + triangles + "]";
		}

	}

	@Test
	public void pointsRetainValueEqualityIncludingFloatingPointEdges() {
		ManifoldBRepPlan.Point point = new ManifoldBRepPlan.Point(1, 2, 3);
		ManifoldBRepPlan.Point equal = new ManifoldBRepPlan.Point(1, 2, 3);
		assertEquals(point, equal);
		assertEquals(point.hashCode(), equal.hashCode());
		Map<ManifoldBRepPlan.Point, String> values = new HashMap<>();
		values.put(point, "found");
		assertEquals("found", values.get(equal));
		assertFalse(new ManifoldBRepPlan.Point(0.0, 0, 0).equals(new ManifoldBRepPlan.Point(-0.0, 0, 0)));
		assertEquals(new ManifoldBRepPlan.Point(Double.NaN, 0, 0),
				new ManifoldBRepPlan.Point(Double.longBitsToDouble(0x7ff8000000000001L), 0, 0));
	}

	@Test
	public void plansRetainValueEqualityAndImmutableDefensiveCopies() {
		ManifoldBRepPlan.Point origin = new ManifoldBRepPlan.Point(0, 0, 0);
		ManifoldBRepPlan.Point normal = new ManifoldBRepPlan.Point(0, 0, 1);
		List<Integer> loop = new ArrayList<>(Arrays.asList(0, 1, 2));
		List<List<Integer>> loops = new ArrayList<>();
		loops.add(loop);
		List<Integer> triangles = new ArrayList<>(Arrays.asList(0));
		Face face = new Face(origin, normal, loops, triangles);
		Face equal = new Face(origin, normal, loops, triangles);
		List<Face> faces = new ArrayList<>(Arrays.asList(face));
		Plan plan = new Plan(faces, 1, 0, 0);
		Plan equalPlan = new Plan(faces, 1, 0, 0);
		loop.clear();
		loops.clear();
		triangles.clear();
		faces.clear();
		assertEquals(Arrays.asList(0, 1, 2), face.loops().get(0));
		assertEquals(1, face.triangleCount());
		assertEquals(1, plan.faces().size());
		assertEquals(face, equal);
		assertEquals(face.hashCode(), equal.hashCode());
		assertEquals(plan, equalPlan);
		assertEquals(plan.hashCode(), equalPlan.hashCode());
		for (List<?> immutable : Arrays.asList(face.loops(), face.loops().get(0), face.triangleIndices(),
				plan.faces())) {
			try {
				immutable.clear();
				fail("plan collections must be immutable");
			} catch (UnsupportedOperationException expected) {
			}
		}
	}

	private static Mesh extrude(double[] xy, int[] top, double height) {
		int n = xy.length / 2;
		double[] p = new double[n * 6];
		for (int i = 0; i < n; i++)
			for (int layer = 0; layer < 2; layer++) {
				p[3 * (i + layer * n)] = xy[2 * i];
				p[3 * (i + layer * n) + 1] = xy[2 * i + 1];
				p[3 * (i + layer * n) + 2] = layer * height;
			}
		List<Long> t = new ArrayList<>();
		Map<String, int[]> boundary = new HashMap<>();
		for (int i = 0; i < top.length; i += 3) {
			add(t, top[i + 2], top[i + 1], top[i]);
			add(t, top[i] + n, top[i + 1] + n, top[i + 2] + n);
			for (int c = 0; c < 3; c++) {
				int a = top[i + c], b = top[i + (c + 1) % 3];
				String key = Math.min(a, b) + ":" + Math.max(a, b);
				if (boundary.remove(key) == null)
					boundary.put(key, new int[]{a, b});
			}
		}
		for (int[] e : boundary.values()) {
			add(t, e[0], e[1], e[1] + n);
			add(t, e[0], e[1] + n, e[0] + n);
		}
		return new Mesh(p, t.stream().mapToLong(Long::longValue).toArray());
	}

	private static void add(List<Long> list, int a, int b, int c) {
		list.add((long) a);
		list.add((long) b);
		list.add((long) c);
	}

	private static Mesh cube(double height) {
		return extrude(new double[]{0, 0, 1, 0, 1, 1, 0, 1}, new int[]{0, 1, 2, 0, 2, 3}, height);
	}

	private static Plan verify(Mesh mesh) {
		double[] original = mesh.positions.clone();
		long[] originalTriangles = mesh.triangles.clone();
		Plan plan = ManifoldBRepPlan.plan(mesh.positions, mesh.triangles);
		assertArrayEquals(original, mesh.positions, 0);
		assertArrayEquals(originalTriangles, mesh.triangles);
		int[] coverage = new int[mesh.triangles.length / 3];
		Map<String, Integer> edges = new HashMap<>();
		for (Face face : plan.faces()) {
			for (int t : face.triangleIndices())
				coverage[t]++;
			int loopIndex = 0;
			for (List<Integer> loop : face.loops()) {
				double area = 0;
				for (int c = 0; c < loop.size(); c++) {
					int a = loop.get(c), b = loop.get((c + 1) % loop.size()), o = loop.get(0);
					double ax = mesh.positions[3 * a] - mesh.positions[3 * o];
					double ay = mesh.positions[3 * a + 1] - mesh.positions[3 * o + 1];
					double az = mesh.positions[3 * a + 2] - mesh.positions[3 * o + 2];
					double bx = mesh.positions[3 * b] - mesh.positions[3 * o];
					double by = mesh.positions[3 * b + 1] - mesh.positions[3 * o + 1];
					double bz = mesh.positions[3 * b + 2] - mesh.positions[3 * o + 2];
					area += (ay * bz - az * by) * face.normal().x() + (az * bx - ax * bz) * face.normal().y()
							+ (ax * by - ay * bx) * face.normal().z();
				}
				assertTrue("outer positive; holes negative", loopIndex++ == 0 ? area > 0 : area < 0);
				assertEquals(loop.size(), new java.util.HashSet<>(loop).size());
				for (int c = 0; c < loop.size(); c++) {
					int a = loop.get(c), b = loop.get((c + 1) % loop.size());
					edges.merge(a + ":" + b, 1, Integer::sum);
				}
			}
			for (int t : face.triangleIndices())
				for (int c = 0; c < 3; c++) {
					int v = (int) mesh.triangles[3 * t + c];
					double distance = (mesh.positions[3 * v] - face.origin().x()) * face.normal().x()
							+ (mesh.positions[3 * v + 1] - face.origin().y()) * face.normal().y()
							+ (mesh.positions[3 * v + 2] - face.origin().z()) * face.normal().z();
					assertEquals(0, distance, 1e-7);
				}
		}
		for (int times : coverage)
			assertEquals(1, times);
		assertEquals(coverage.length, plan.triangleCount());
		for (String edge : edges.keySet()) {
			String[] ends = edge.split(":");
			assertEquals(Integer.valueOf(1), edges.get(edge));
			assertEquals(Integer.valueOf(1), edges.get(ends[1] + ":" + ends[0]));
		}
		return plan;
	}

	@Test
	public void cubeHasSixRegions() {
		Plan plan = verify(cube(1));
		assertEquals(6, plan.faces().size());
		assertEquals(0, plan.fallbackTriangleCount());
		for (Face face : plan.faces())
			assertEquals(2, face.triangleCount());
	}

	@Test
	public void throughHoleHasOneOuterAndOneInnerOnEachCap() {
		Mesh mesh = extrude(new double[]{0, 0, 4, 0, 4, 4, 0, 4, 1, 1, 3, 1, 3, 3, 1, 3},
				new int[]{0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5, 2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7}, 2);
		Plan plan = verify(mesh);
		assertEquals(10, plan.faces().size());
		assertEquals(2, plan.faces().stream().filter(f -> f.loops().size() == 2).count());
		assertEquals(0, plan.fallbackTriangleCount());
	}

	@Test
	public void adjacentSliversAreNotDiscarded() {
		Mesh mesh = extrude(new double[]{0, 0, 1, 0, 1, 1e-10, 0, 1e-10}, new int[]{0, 1, 2, 0, 2, 3}, 1);
		Plan plan = verify(mesh);
		assertEquals(6, plan.faces().size());
		assertEquals(0, plan.fallbackTriangleCount());
	}

	@Test
	public void nearbyParallelPlanesRemainSeparate() {
		Plan plan = verify(cube(2e-7));
		assertEquals(6, plan.faces().size());
		assertEquals(0, plan.fallbackTriangleCount());
	}

	@Test
	public void seedPlaneDoesNotDriftAcrossBentCap() {
		Mesh mesh = cube(1);
		mesh.positions[3 * 6 + 2] += 5e-7;
		Plan plan = verify(mesh);
		assertEquals(7, plan.faces().size());
	}

	@Test
	public void pinchedPlanarPatchFallsBackWithoutLosingTopology() {
		int size = 7;
		double[] xy = new double[size * size * 2];
		List<Long> top = new ArrayList<>();
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++) {
				int a = y * size + x;
				xy[2 * a] = x;
				xy[2 * a + 1] = y;
				if (x + 1 < size && y + 1 < size) {
					add(top, a, a + 1, a + size + 1);
					add(top, a, a + size + 1, a + size);
				}
			}
		Mesh mesh = extrude(xy, top.stream().mapToInt(Long::intValue).toArray(), 1);
		mesh.positions[3 * (size * size + 3 * size + 2) + 2] = 2;
		mesh.positions[3 * (size * size + 3 * size + 4) + 2] = 2;
		Plan plan = verify(mesh);
		assertTrue("pinched boundary must not become a cyclic face", plan.fallbackRegionCount() > 0);
		assertTrue(plan.fallbackTriangleCount() > 1);
	}

	@Test
	public void crossingBoundaryUsesExactTriangleFallback() {
		double[] xy = new double[12];
		List<Long> top = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			double angle = 4 * Math.PI * i / 5;
			xy[2 * i] = Math.cos(angle);
			xy[2 * i + 1] = Math.sin(angle);
			add(top, 5, i, (i + 1) % 5);
		}
		Plan plan = verify(extrude(xy, top.stream().mapToInt(Long::intValue).toArray(), 1));
		assertEquals(2, plan.fallbackRegionCount());
		assertEquals(10, plan.fallbackTriangleCount());
	}

	@Test
	public void rejectsOpenMisOrientedAndOverSubscribedEdges() {
		Mesh mesh = cube(1);
		reject(mesh.positions, Arrays.copyOf(mesh.triangles, mesh.triangles.length - 3));
		long[] reversed = mesh.triangles.clone();
		long a = reversed[0];
		reversed[0] = reversed[1];
		reversed[1] = a;
		reject(mesh.positions, reversed);
		long[] extra = Arrays.copyOf(mesh.triangles, mesh.triangles.length + 3);
		System.arraycopy(mesh.triangles, 0, extra, mesh.triangles.length, 3);
		reject(mesh.positions, extra);
	}

	@Test
	public void rejectsInvalidCoordinatesIndicesAndDegeneracy() {
		Mesh mesh = cube(1);
		double[] invalid = mesh.positions.clone();
		invalid[0] = Double.NaN;
		reject(invalid, mesh.triangles);
		long[] indices = mesh.triangles.clone();
		indices[0] = Long.MAX_VALUE;
		reject(mesh.positions, indices);
		indices[0] = indices[1];
		reject(mesh.positions, indices);
		reject(new double[]{1}, new long[0]);
	}

	private static void reject(double[] p, long[] t) {
		try {
			ManifoldBRepPlan.plan(p, t);
			fail("Expected invalid mesh rejection");
		} catch (IllegalArgumentException expected) {
			assertNotNull(expected.getMessage());
		}
	}
}
