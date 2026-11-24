package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.manifold3d.MeshProcessing;
import eu.mihosoft.vrl.v3d.CSG;
import eu.mihosoft.vrl.v3d.parametrics.LengthParameter;

/** Selected-object mesh processing; nonpositive method controls disable their stage. */
public class Simplify extends CaDoodleOperation {
	@Expose
	private String name = RandomStringFactory.generateRandomString();
	@Expose
	private List<String> names = new ArrayList<>();
	@Expose
	private Map<String, String> outputNames = new LinkedHashMap<>();
	private final List<String> addedNames = new ArrayList<>();
	private final Map<String, LengthParameter> parameters = new LinkedHashMap<>();
	@Expose
	private double simplify = 0.5;
	@Expose
	private int calculateNormals = 0;
	@Expose
	private int smoothByNormals = 0;
	@Expose
	private double smoothOut = 0;
	@Expose
	private double normalAngle = 60;
	@Expose
	private double smoothness = 0;
	@Expose
	private int refine = 0;
	@Expose
	private double refineToLength = 0;
	@Expose
	private double refineToTolerance = 0;

	@Override
	public String getType() {
		return "Simplify";
	}

	public static boolean isParameterKey(String key) {
		return key.contains("_CaDoodle_Simplify_");
	}

	public boolean ownsParameterKey(String key) {
		return key.startsWith(name + "_CaDoodle_Simplify_");
	}

	@Override
	public void setCaDoodleFile(CaDoodleFile file) {
		if (file != getCaDoodleFile())
			parameters.clear();
		super.setCaDoodleFile(file);
	}

	public String getName() {
		return name;
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		return new ArrayList<>(addedNames);
	}

	public Simplify setNames(List<String> names) {
		this.names = new ArrayList<>(Objects.requireNonNull(names));
		return this;
	}

	private LengthParameter parameter(String key, double value, double lower, double upper) {
		return parameters.computeIfAbsent(key, k -> new LengthParameter(getDb(), name + "_CaDoodle_Simplify_" + k,
				value, new ArrayList<>(Arrays.asList(lower, upper))));
	}

	private LengthParameter integerParameter(String key, int value, Double... options) {
		return parameters.computeIfAbsent(key, k -> new LengthParameter(getDb(), name + "_CaDoodle_Simplify_" + k,
				(double) value, new ArrayList<>(Arrays.asList(options))));
	}

	private void update(String key, double value) {
		if (parameters.containsKey(key))
			parameters.get(key).setMM(value);
	}

	private static int integer(LengthParameter parameter) {
		double value = parameter.getMM();
		if (!Double.isFinite(value) || value != Math.rint(value) || value > Integer.MAX_VALUE
				|| value < Integer.MIN_VALUE)
			throw new IllegalArgumentException(parameter.getName() + " must be an integer");
		return (int) value;
	}

	public MeshProcessing.Settings getSettings() {
		return new MeshProcessing.Settings(simplifyParameter().getMM(), integer(calculateNormalsParameter()),
				integer(smoothByNormalsParameter()), normalAngleParameter().getMM(), smoothOutParameter().getMM(),
				smoothnessParameter().getMM(), integer(refineParameter()), refineToLengthParameter().getMM(),
				refineToToleranceParameter().getMM());
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		addedNames.clear();
		Set<String> occupied = new HashSet<>();
		for (CSG source : incoming)
			occupied.add(source.getName());
		Set<String> reserved = new HashSet<>(occupied);
		reserved.addAll(outputNames.values());
		List<CSG> result = new ArrayList<>(incoming.size());
		for (CSG source : incoming) {
			if (!names.contains(source.getName())) {
				result.add(source);
				continue;
			}
			if (!outputNames.containsKey(source.getName()) || occupied.contains(outputNames.get(source.getName()))) {
				int index = 1;
				String candidate;
				do {
					candidate = name + "_" + index++;
				} while (!reserved.add(candidate));
				outputNames.put(source.getName(), candidate);
			}
			String outputName = outputNames.get(source.getName());
			occupied.add(outputName);
			result.add(processSelected(source, outputName, addedNames.size() + 1));
			addedNames.add(outputName);
		}
		return result;
	}

	private CSG processSelected(CSG source, String outputName, int outputIndex) {
		try {
			CSG result = applyGeometry(source, getSettings());
			// Disabled stages may return the incoming mesh; keep its snapshot independent
			if (result == source)
				result = source.cloneShallow();
			result.getStorage().syncProperties(source.getStorage());
			result.setUserDefinedNameIfMissing("Simplify " + outputIndex);
			result.setName(outputName).setColor(source.getColor());
			for (LengthParameter parameter : parameters.values())
				result.setParameter(getDb(), parameter);
			return result.setRegenerate(previous -> processSelected(source, outputName, outputIndex));
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Throwable e) {
			throw new IllegalStateException("Mesh processing failed for " + source.getName(), e);
		}
	}

	protected CSG applyGeometry(CSG source, MeshProcessing.Settings settings) throws Throwable {
		return MeshProcessing.apply(source, settings);
	}

	public LengthParameter simplifyParameter() {
		return parameter("simplify", simplify, 0.0, 10.0);
	}

	public double getSimplify() {
		return parameters.containsKey("simplify") ? parameters.get("simplify").getMM() : simplify;
	}

	public Simplify setSimplify(double value) {
		simplify = value;
		update("simplify", value);
		return this;
	}

	public LengthParameter calculateNormalsParameter() {
		return integerParameter("calculateNormals", calculateNormals, 0.0, 1.0, 2.0, 3.0);
	}

	public int getCalculateNormals() {
		return parameters.containsKey("calculateNormals")
				? integer(parameters.get("calculateNormals"))
				: calculateNormals;
	}

	public Simplify setCalculateNormals(int value) {
		calculateNormals = value;
		update("calculateNormals", value);
		return this;
	}

	public LengthParameter smoothByNormalsParameter() {
		return integerParameter("smoothByNormals", smoothByNormals, 0.0, 1.0, 2.0, 3.0);
	}

	public int getSmoothByNormals() {
		return parameters.containsKey("smoothByNormals") ? integer(parameters.get("smoothByNormals")) : smoothByNormals;
	}

	public Simplify setSmoothByNormals(int value) {
		smoothByNormals = value;
		update("smoothByNormals", value);
		return this;
	}

	public LengthParameter smoothOutParameter() {
		return parameter("smoothOut", smoothOut, 0.0, 180.0);
	}

	public double getSmoothOut() {
		return parameters.containsKey("smoothOut") ? parameters.get("smoothOut").getMM() : smoothOut;
	}

	public Simplify setSmoothOut(double value) {
		smoothOut = value;
		update("smoothOut", value);
		return this;
	}

	public LengthParameter smoothnessParameter() {
		return parameter("smoothness", smoothness, 0.0, 1.0);
	}

	public double getSmoothness() {
		return parameters.containsKey("smoothness") ? parameters.get("smoothness").getMM() : smoothness;
	}

	public Simplify setSmoothness(double value) {
		smoothness = value;
		update("smoothness", value);
		return this;
	}

	public LengthParameter refineParameter() {
		return integerParameter("refine", refine, 0.0, 1.0, 2.0, 3.0, 4.0, 8.0);
	}

	public int getRefine() {
		return parameters.containsKey("refine") ? integer(parameters.get("refine")) : refine;
	}

	public Simplify setRefine(int value) {
		refine = value;
		update("refine", value);
		return this;
	}

	public LengthParameter refineToLengthParameter() {
		return parameter("refineToLength", refineToLength, 0.0, 100.0);
	}

	public double getRefineToLength() {
		return parameters.containsKey("refineToLength") ? parameters.get("refineToLength").getMM() : refineToLength;
	}

	public Simplify setRefineToLength(double value) {
		refineToLength = value;
		update("refineToLength", value);
		return this;
	}

	public LengthParameter refineToToleranceParameter() {
		return parameter("refineToTolerance", refineToTolerance, 0.0, 10.0);
	}

	public double getRefineToTolerance() {
		return parameters.containsKey("refineToTolerance")
				? parameters.get("refineToTolerance").getMM()
				: refineToTolerance;
	}

	public Simplify setRefineToTolerance(double value) {
		refineToTolerance = value;
		update("refineToTolerance", value);
		return this;
	}

	public LengthParameter normalAngleParameter() {
		return parameter("normalAngle", normalAngle, 0.0, 180.0);
	}

	public double getNormalAngle() {
		return parameters.containsKey("normalAngle") ? parameters.get("normalAngle").getMM() : normalAngle;
	}

	public Simplify setNormalAngle(double value) {
		normalAngle = value;
		update("normalAngle", value);
		return this;
	}
}
