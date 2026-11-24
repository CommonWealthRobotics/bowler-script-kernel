package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.annotations.Expose;
import com.neuronrobotics.manifold3d.CSGManifold3d;
import eu.mihosoft.vrl.v3d.CSG;

/** Replaces selected geometry with independent connected components. */
public class BreakUp extends AbstractAddFrom {
	@Expose
	private List<String> names = new ArrayList<>();
	@Expose
	private Map<String, List<String>> outputNames = new LinkedHashMap<>();

	public BreakUp() {
		getName();
		na = new LinkedHashSet<>();
	}

	public BreakUp setNames(List<String> names) {
		this.names = new ArrayList<>(Objects.requireNonNull(names));
		return this;
	}

	@Override
	public String getType() {
		return "BreakUp";
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		na.clear();
		Set<String> selected = new HashSet<>(names);
		Set<String> removedGroups = new HashSet<>();
		for (CSG source : incoming)
			if (selected.contains(source.getName()) && source.isGroupResult())
				removedGroups.add(source.getName());
		// Nested group constituents belong to the replaced geometry too.
		boolean changed;
		do {
			changed = false;
			for (CSG source : incoming)
				if (source.isGroupResult() && belongsTo(source, removedGroups))
					changed |= removedGroups.add(source.getName());
		} while (changed);
		Set<String> occupied = new HashSet<>();
		for (CSG source : incoming)
			occupied.add(source.getName());
		Set<String> reserved = new HashSet<>();
		for (CSG source : incoming)
			reserved.add(source.getName());
		for (List<String> allocated : outputNames.values())
			reserved.addAll(allocated);
		List<CSG> result = new ArrayList<>();
		List<String> added = new ArrayList<>();
		for (CSG source : incoming) {
			if (belongsTo(source, removedGroups))
				continue;
			if (!selected.contains(source.getName())) {
				result.add(source);
				continue;
			}
			List<CSG> parts;
			try {
				parts = new CSGManifold3d().decompose(source);
			} catch (RuntimeException | Error e) {
				throw e;
			} catch (Throwable e) {
				throw new IllegalStateException("Break up failed for " + source.getName(), e);
			}
			List<String> allocated = outputNames.computeIfAbsent(source.getName(), key -> new ArrayList<>());
			for (int i = 0; i < parts.size(); i++) {
				if (i == allocated.size() || occupied.contains(allocated.get(i))) {
					int index = 1;
					String candidate;
					do {
						candidate = getName() + "_" + index++;
					} while (!reserved.add(candidate));
					if (i == allocated.size())
						allocated.add(candidate);
					else
						allocated.set(i, candidate);
				}
				String partName = allocated.get(i);
				occupied.add(partName);
				CSG part = parts.get(i);
				// Copy metadata only; exclude upstream parameter and regeneration callbacks
				part.getStorage().syncProperties(source.getStorage());
				part.setName(partName).setColor(source.getColor());
				part.setUserDefinedName("Break Up " + (added.size() + 1));
				part.getStorage().delete("groupMembership");
				part.getStorage().delete("GroupResult");
				part.setRegenerate(previous -> previous);
				result.add(part);
				added.add(partName);
			}
		}
		na.addAll(added);
		return result;
	}

	private static boolean belongsTo(CSG source, Set<String> groups) {
		for (String group : groups)
			if (source.checkGroupMembership(group))
				return true;
		return false;
	}

	@Override
	public File getFile() throws NoSuchFileException {
		throw new NoSuchFileException("BreakUp has no source file");
	}
}
