package com.neuronrobotics.bowlerstudio.threed;

import static org.junit.Assert.*;
import java.lang.reflect.*;
import java.util.*;
import org.junit.Test;
import eu.mihosoft.vrl.v3d.CSG;
import javafx.scene.paint.Color;

/** BSP boundary partitions are not necessarily an indexed, conforming triangle mesh. */
public class StepBoundaryPartitionTest {
    private static CSG tetra(double[] left, double[] right, double gap) {
        List<Double> p = new ArrayList<>(Arrays.asList(0.,0.,0., 9.,0.,0., 0.,5.,0., 0.,0.,4.));
        List<Long> t = new ArrayList<>();
        List<Integer> a = chain(p,left,0), b = chain(p,right,gap);
        for(int i=0;i<a.size()-1;i++) add(t,2,a.get(i+1),a.get(i));
        for(int i=0;i<b.size()-1;i++) add(t,3,b.get(i),b.get(i+1));
        add(t,1,2,3);add(t,2,0,3);
        return new CSG(p.stream().mapToDouble(Double::doubleValue).toArray(),t.stream().mapToLong(Long::longValue).toArray(),Color.GRAY);
    }
    private static List<Integer> chain(List<Double> p,double[] x,double gap) {
        List<Integer> c=new ArrayList<>();c.add(0);
        for(double v:x) {c.add(p.size()/3);p.add(v);p.add(gap);p.add(0.);}
        c.add(1);return c;
    }
    private static void add(List<Long> t,int a,int b,int c){t.add((long)a);t.add((long)b);t.add((long)c);}
    private static String point(double[] p){return Arrays.toString(p);}
    private static int unmatched(CSG c) {
        Map<String,Integer> e=new HashMap<>();
        for(StepFace f:StepBRepWrite.faces(c)) for(List<double[]> l:f.loops)
            for(int i=0;i<l.size();i++) e.merge(point(l.get(i))+">"+point(l.get((i+1)%l.size())),1,Integer::sum);
        int bad=0;for(String k:e.keySet()){int i=k.indexOf('>');String r=k.substring(i+1)+">"+k.substring(0,i);if(e.get(k)!=1||e.getOrDefault(r,0)!=1)bad++;}return bad;
    }
    @Test public void oneEdgeAgainstNineSegmentsClosesWithoutMovingSource() {
        CSG c=tetra(new double[]{},new double[]{1,2,3,4,5,6,7,8},0);
        double[] p=c.getVertices().clone();long[] t=c.getTriangles().clone();
        assertEquals(0,unmatched(c));assertArrayEquals(p,c.getVertices(),0);assertArrayEquals(t,c.getTriangles());
        StepEmitter e=new StepEmitter();StepBRepWrite.items(e,c,"nine pieces");
        assertTrue(e.text().contains("CLOSED_SHELL"));assertFalse(e.text().contains("OPEN_SHELL"));
    }
    @Test public void independentlyPartitionedSidesGetTheSameAtomicEdges() {
        assertEquals(0,unmatched(tetra(new double[]{1,4,7},new double[]{2,3,5,6,8},0)));
    }
    @Test public void aRealOffsetGapIsNotConformedOrCapped() {
        CSG c=tetra(new double[]{},new double[]{1,2,3,4,5,6,7,8},1e-8);
        assertTrue(unmatched(c)>0);
        // An unrelated coordinate must not inflate the local collinearity budget.
        double[] withDistantUnused=Arrays.copyOf(c.getVertices(),c.getVertices().length+3);
        withDistantUnused[withDistantUnused.length-3]=1e12;
        assertTrue(unmatched(new CSG(withDistantUnused,c.getTriangles(),Color.GRAY))>0);
        StepEmitter e=new StepEmitter();StepBRepWrite.items(e,c,"real gap");
        assertFalse(e.text().contains("CLOSED_SHELL"));assertTrue(e.text().contains("OPEN_SHELL"));
    }
    @Test public void anUncoveredIntervalRemainsAnOpenBoundary() {
        CSG complete=tetra(new double[]{},new double[]{1,2,3,4,5,6,7,8},0);
        long[] source=complete.getTriangles(), missing=new long[source.length-3];
        System.arraycopy(source,0,missing,0,12);
        System.arraycopy(source,15,missing,12,source.length-15);
        CSG c=new CSG(complete.getVertices(),missing,Color.GRAY);
        assertTrue(unmatched(c)>0);
        StepEmitter e=new StepEmitter();StepBRepWrite.items(e,c,"uncovered interval");
        assertTrue(e.text().contains("OPEN_SHELL"));
    }
    @Test public void retainedTessellationUsesTheSameConformingBoundaryContract() throws Exception {
        CSG.OptType previous=CSG.getDefaultOptionType();
        try {
            CSG.setDefaultOptType(CSG.OptType.CSG_BOUND);
            CSG c=tetra(new double[]{1,4,7},new double[]{2,3,5,6,8},0);
            STEPFile writer=new STEPFile(c);
            STEPFile reader=new STEPFile(new java.io.ByteArrayInputStream(writer.toStep().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            assertEquals(1,reader.getShapes().size());
            CSG mesh=reader.getShapes().get(0).getMesh();
            assertNotNull(mesh);
            assertArrayEquals(writer.getCsg().getVertices(),mesh.getVertices(),0);
            assertArrayEquals(writer.getCsg().getTriangles(),mesh.getTriangles());
            Map<Long,Integer> uses=new HashMap<>();long[] t=mesh.getTriangles();
            for(int i=0;i<t.length;i+=3)for(int j=0;j<3;j++)
                uses.merge((t[i+j]<<32)|(t[i+(j+1)%3]&0xffffffffL),1,Integer::sum);
            for(long edge:uses.keySet()) {
                assertEquals(Integer.valueOf(1),uses.get(edge));
                long reverse=((edge&0xffffffffL)<<32)|(edge>>>32);
                assertEquals(Integer.valueOf(1),uses.get(reverse));
            }
        } finally {CSG.setDefaultOptType(previous);}
    }
    @Test public void balancedTwoToTwoUsesAreStillRejected() throws Exception {
        Method watertight=StepBRepWrite.class.getDeclaredMethod("isWatertight",List.class);
        watertight.setAccessible(true);
        List<List<Integer>> face=List.of(List.of(0,1,2)),reverse=List.of(List.of(2,1,0));
        assertTrue((Boolean)watertight.invoke(null,List.of(face,reverse)));
        assertFalse("balanced multiplicity is not a manifold shell",
                (Boolean)watertight.invoke(null,List.of(face,reverse,face,reverse)));
    }
    private static Map<Long,Integer> edgeCounts(List<List<Integer>> loops) {
        Map<Long,Integer> edges=new HashMap<>();
        for(List<Integer> loop:loops) for(int i=0;i<loop.size();i++)
            edges.merge(((long)loop.get(i)<<32)|(loop.get((i+1)%loop.size())&0xffffffffL),1,Integer::sum);
        return edges;
    }
    private static int badUses(List<List<Integer>> loops) {
        Map<Long,Integer> edges=edgeCounts(loops);int bad=0;
        for(long edge:edges.keySet()) {
            long reverse=((edge&0xffffffffL)<<32)|(edge>>>32);
            if(edges.get(edge)!=1 || edges.getOrDefault(reverse,0)!=1)bad++;
        }
        return bad;
    }
    @Test public void individuallyFavorableFallbacksAreRecheckedAgainstAcceptedChanges() throws Exception {
        // An accounting counterexample, not a claim about the box/sphere geometry:
        // two replacements improve 13 -> 12 alone, but together worsen it to 14.
        int[][] triangles={{4,3,2},{3,4,1},{5,0,6},{2,0,1},{6,4,0}};
        List<List<Integer>> loops=List.of(List.of(4,6,3,2),List.of(3,6,4,0,5,1),
                List.of(1,5,2,3,0,6),List.of(2,0,3,5,1),List.of(6,1,4,0));
        Class<?> mesh=Class.forName(StepBRepWrite.class.getName()+"$Mesh");
        Constructor<?> ctor=mesh.getDeclaredConstructor(double[].class,long[].class);ctor.setAccessible(true);
        Object m=ctor.newInstance(new double[]{0,0,0, 1,0,0, 0,1,0, 1,1,0, 2,0,0, 0,2,0, 2,2,0},new long[]{0,1,2});
        Field ts=mesh.getDeclaredField("triangles");ts.setAccessible(true);
        List<int[]> raw=(List<int[]>)ts.get(m);raw.clear();raw.addAll(Arrays.asList(triangles));
        Class<?> planned=Class.forName(StepBRepWrite.class.getName()+"$Mesh$Planned");
        Constructor<?> pc=planned.getDeclaredConstructor(List.class,List.class);pc.setAccessible(true);
        List<Object> plan=new ArrayList<>();for(int i=0;i<loops.size();i++)plan.add(pc.newInstance(List.of(i),List.of(loops.get(i))));
        Method better=mesh.getDeclaredMethod("pairsBetterAsTriangles",planned,Map.class);better.setAccessible(true);
        assertEquals(13,badUses(loops));
        for(int i:new int[]{1,3}) {
            List<List<Integer>> one=new ArrayList<>(loops);
            one.set(i,Arrays.asList(triangles[i][0],triangles[i][1],triangles[i][2]));
            assertEquals(12,badUses(one));
            assertTrue((Boolean)better.invoke(m,plan.get(i),edgeCounts(loops)));
        }
        List<List<Integer>> both=new ArrayList<>(loops);
        for(int i:new int[]{1,3})both.set(i,Arrays.asList(triangles[i][0],triangles[i][1],triangles[i][2]));
        assertEquals(14,badUses(both));
        Method settle=mesh.getDeclaredMethod("pairedPlan",List.class);settle.setAccessible(true);
        List<?> result=(List<?>)settle.invoke(m,plan);
        Field regions=planned.getDeclaredField("region"),outlines=planned.getDeclaredField("loops");
        regions.setAccessible(true);outlines.setAccessible(true);
        List<Integer> accepted=new ArrayList<>();List<List<Integer>> actual=new ArrayList<>();
        for(Object face:result) {
            int i=((List<Integer>)regions.get(face)).get(0);
            if(outlines.get(face)==null) {accepted.add(i);actual.add(Arrays.asList(triangles[i][0],triangles[i][1],triangles[i][2]));}
            else actual.addAll((List<List<Integer>>)outlines.get(face));
        }
        assertEquals(List.of(1),accepted);
        assertEquals(12,badUses(actual));
    }
    @Test public void fallbackDoesNotCountTheFaceUnderComparisonTwice() throws Exception {
        Class<?> mesh=Class.forName(StepBRepWrite.class.getName()+"$Mesh");
        Constructor<?> ctor=mesh.getDeclaredConstructor(double[].class,long[].class);ctor.setAccessible(true);
        Object m=ctor.newInstance(new double[]{0,0,0, 0,2,0, 4,0,0, 2,0,0},new long[]{0,1,2});
        Field ts=mesh.getDeclaredField("triangles");ts.setAccessible(true);
        List<int[]> triangles=(List<int[]>)ts.get(m);triangles.clear();triangles.add(new int[]{0,1,2});
        Class<?> planned=Class.forName(StepBRepWrite.class.getName()+"$Mesh$Planned");
        Constructor<?> pc=planned.getDeclaredConstructor(List.class,List.class);pc.setAccessible(true);
        List<Integer> loop=List.of(0,1,2,3);
        Object face=pc.newInstance(List.of(0),List.of(loop));
        Map<Long,Integer> all=new HashMap<>();
        for(int i=0;i<loop.size();i++) {int a=loop.get(i),b=loop.get((i+1)%loop.size());all.put(((long)a<<32)|(b&0xffffffffL),1);all.put(((long)b<<32)|(a&0xffffffffL),1);}
        Method better=mesh.getDeclaredMethod("pairsBetterAsTriangles",planned,Map.class);better.setAccessible(true);
        assertFalse("a closed four-edge face must not become an unmatched triangle",(Boolean)better.invoke(m,face,all));
    }
}
