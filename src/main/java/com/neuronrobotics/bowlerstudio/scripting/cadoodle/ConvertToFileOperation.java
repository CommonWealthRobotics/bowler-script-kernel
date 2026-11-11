package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;

import eu.mihosoft.vrl.v3d.CSG;

/**
 * Converts the selected objects into a single external model file placed in the
 * doodle directory, removes the selected objects, and adds the new file with the
 * normal {@link AddFromFile} procedure. The file is only written when it does
 * not exist yet, so re-running or re-loading the timeline keeps the file.
 */
public abstract class ConvertToFileOperation extends AddFromFile {
	@Expose(serialize = true, deserialize = true)
	private List<String> names = new ArrayList<String>();

	/** The file extension this conversion writes, without the dot. */
	public abstract String getExtension();

	/**
	 * Writes the selected CSGs into {@code target}, which does not exist yet.
	 */
	protected abstract void writeFile(File target, List<CSG> selected) throws IOException;

	@Override
	public String getType() {
		return "ConvertTo" + getExtension();
	}

	@Override
	public List<CSG> process(List<CSG> incoming) {
		// remove the selected objects, they are replaced by the converted file
		ArrayList<CSG> back = new ArrayList<CSG>();
		back.addAll(incoming);
		List<CSG> selected = new ArrayList<CSG>();
		for (String n : names) {
			try {
				CSG byName = CaDoodleFile.getByName(incoming, n);
				back.remove(byName);
				selected.add(byName);
			} catch (NameMissingException e) {
				com.neuronrobotics.sdk.common.Log.error(e);
			}
		}
		// create the file in the doodle directory when it is not there yet
		File target = newTargetFile();
		if (!target.exists()) {
			if (selected.size() == 0)
				throw new RuntimeException("No selected objects to convert to " + getExtension());
			try {
				writeFile(target, selected);
			} catch (IOException e) {
				com.neuronrobotics.sdk.common.Log.error(e);
				throw new RuntimeException(e);
			}
		}
		if (!target.exists())
			throw new RuntimeException("Conversion to " + getExtension() + " failed: " + target);

		// add the file with the normal AddFromFile procedure
		setFilenamePart(target.getName().split("\\.")[0]);
		return super.process(back);
	}

	@Override
	public File getFile() throws java.nio.file.NoSuchFileException {
		return newTargetFile();
	}

	private File newTargetFile() {
		File self = getCaDoodleFile().getSelf();
		File doodleDir = new File(self.getAbsolutePath()).getParentFile();
		return new File(doodleDir, getName() + "." + getExtension());
	}

	@Override
	public List<String> getNamesAddedInThisOperation() {
		List<String> namesAdded = super.getNamesAddedInThisOperation();
		// the added file's objects plus the removed selection are all touched
		ArrayList<String> back = new ArrayList<String>();
		back.addAll(namesAdded);
		back.addAll(names);
		return back;
	}

	public List<String> getNames() {
		return names;
	}

	public ConvertToFileOperation setNames(List<String> names) {
		this.names = names;
		return this;
	}
}
