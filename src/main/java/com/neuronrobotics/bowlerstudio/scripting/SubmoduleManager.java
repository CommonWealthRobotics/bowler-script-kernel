package com.neuronrobotics.bowlerstudio.scripting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.SubmoduleInitCommand;
import org.eclipse.jgit.api.SubmoduleUpdateCommand;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.submodule.SubmoduleWalk;

/**
 * Recursive git submodule support. Repositories found nested inside another
 * git repository are absorbed as submodules of the repo they reside in, and
 * the submodule tree is checked out, committed and pushed recursively. All
 * Git objects are acquired and released through the ScriptingEngine openGit
 * accessor callbacks.
 */
public final class SubmoduleManager {

	private SubmoduleManager() {
	}

	/**
	 * Check that repoDir is a directory at the top level of a git repository,
	 * then recursively scan its contents for other git repositories. Any
	 * nested repository that is not yet a submodule of the repository it
	 * resides within is converted into one: its git data is absorbed under
	 * the parent's .git/modules, a .gitmodules entry is written and the
	 * gitlink is staged (not committed). Already-registered submodules are
	 * left untouched.
	 *
	 * @param repoDir the top level directory of a git repository
	 * @return the nested repositories that were converted to submodules
	 */
	public static List<File> convertNestedReposToSubmodules(File repoDir) {
		if (!isTopLevelGitRepo(repoDir))
			throw new IllegalArgumentException("Not a directory at the top level of a git repository: " + repoDir);
		ArrayList<File> converted = new ArrayList<>();
		try {
			scanForNestedRepos(repoDir, repoDir, converted);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return converted;
	}

	/**
	 * True if dir is a directory containing a .git entry and no ancestor
	 * directory is a git repository.
	 */
	public static boolean isTopLevelGitRepo(File dir) {
		if (dir == null || !dir.isDirectory())
			return false;
		if (!new File(dir, Constants.DOT_GIT).exists())
			return false;
		File parent = dir.getParentFile();
		while (parent != null) {
			if (new File(parent, Constants.DOT_GIT).exists())
				return false;
			parent = parent.getParentFile();
		}
		try (Repository repo = openRepository(dir)) {
			return repo != null;
		} catch (IOException e) {
			return false;
		}
	}

	/**
	 * Walk the contents of dir. Every nested repository found is registered
	 * as a submodule of containingRepo, the repository it resides within.
	 */
	private static void scanForNestedRepos(File containingRepo, File dir, List<File> converted) throws Exception {
		File[] children = dir.listFiles();
		if (children == null)
			return;
		for (File child : children) {
			if (!child.isDirectory())
				continue;
			if (Constants.DOT_GIT.equals(child.getName()))
				continue;
			if (new File(child, Constants.DOT_GIT).exists()) {
				// child is a repository inside containingRepo
				if (registerSubmodule(containingRepo, child))
					converted.add(child.getAbsoluteFile());
				// deeper repositories belong to child, not containingRepo
				scanForNestedRepos(child, child, converted);
			} else {
				scanForNestedRepos(containingRepo, child, converted);
			}
		}
	}

	/**
	 * Absorb nestedRepo as a submodule of parentRepo if it is not one already.
	 *
	 * @return true if the submodule was newly registered
	 */
	private static boolean registerSubmodule(File parentRepo, File nestedRepo) throws Exception {
		final String path = relativePath(parentRepo, nestedRepo);
		if (path.isEmpty() || path.startsWith("..")) {
			com.neuronrobotics.sdk.common.Log.error("Can not register " + nestedRepo + " as a submodule of "
					+ parentRepo + " because it is not contained within it");
			return false;
		}
		boolean[] registered = new boolean[]{false};
		try (Repository repo = openRepository(parentRepo)) {
			ScriptingEngine.openGit(repo, git -> {
				DirCache index = git.getRepository().readDirCache();
				DirCacheEntry existing = index.getEntry(path);
				if (existing != null && FileMode.GITLINK.equals(existing.getFileMode()))
					return;// already a submodule of this repo
				try (Repository nested = openRepository(nestedRepo)) {
					if (nested.resolve(Constants.HEAD) == null) {
						com.neuronrobotics.sdk.common.Log
								.error("Can not register " + nestedRepo + " as a submodule because it has no commits");
						return;
					}
				}
				absorbIntoParent(git.getRepository(), nestedRepo, path);
				String url = originUrlOf(nestedRepo);
				// .gitmodules entry and the parent's submodule config
				FileBasedConfig modulesConfig = new FileBasedConfig(
						new File(git.getRepository().getWorkTree(), Constants.DOT_GIT_MODULES),
						git.getRepository().getFS());
				modulesConfig.load();
				modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_PATH,
						path);
				modulesConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_URL,
						url);
				modulesConfig.save();
				StoredConfig parentConfig = git.getRepository().getConfig();
				parentConfig.setString(ConfigConstants.CONFIG_SUBMODULE_SECTION, path, ConfigConstants.CONFIG_KEY_URL,
						url);
				parentConfig.save();
				removeStaleNestedEntries(git.getRepository(), path);
				// stage .gitmodules and the gitlink at the nested repo's HEAD
				git.add().addFilepattern(Constants.DOT_GIT_MODULES).call();
				git.add().addFilepattern(path).call();
				registered[0] = true;
				com.neuronrobotics.sdk.common.Log
						.debug("Registered " + nestedRepo + " as submodule " + path + " of " + parentRepo);
			});
		}
		return registered[0];
	}

	/**
	 * Move the nested repo's git data under the parent's .git/modules and
	 * leave a .git gitfile in its place, the way git submodule absorbgitdirs
	 * does. A nested repo that is already a gitfile link is left alone.
	 */
	private static void absorbIntoParent(Repository parentRepo, File nestedRepo, String path)
			throws IOException, ConfigInvalidException {
		File nestedDotGit = new File(nestedRepo, Constants.DOT_GIT);
		if (nestedDotGit.isFile())
			return;// already linked into some git dir
		File moduleDir = new File(new File(parentRepo.getDirectory(), Constants.MODULES), path);
		if (moduleDir.exists())
			throw new IOException("Git module data already exists at " + moduleDir);
		Files.createDirectories(moduleDir.getParentFile().toPath());
		Files.move(nestedDotGit.toPath(), moduleDir.toPath());
		Files.write(nestedDotGit.toPath(),
				("gitdir: " + relativePath(nestedRepo, moduleDir) + "\n").getBytes(StandardCharsets.UTF_8));
		// the git dir no longer sits next to the work tree, point back at it
		FileBasedConfig moduleConfig = new FileBasedConfig(new File(moduleDir, Constants.CONFIG), parentRepo.getFS());
		moduleConfig.load();
		moduleConfig.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_WORKTREE,
				relativePath(moduleDir, nestedRepo));
		moduleConfig.save();
	}

	/**
	 * A repo-in-repo that was committed before conversion leaves plain file
	 * entries in the index under the submodule path; drop them so the
	 * directory can be recorded as a gitlink.
	 */
	private static void removeStaleNestedEntries(Repository repo, String path) throws IOException {
		DirCache snapshot = repo.readDirCache();
		boolean stale = false;
		for (int i = 0; i < snapshot.getEntryCount(); i++) {
			if (snapshot.getEntry(i).getPathString().startsWith(path + "/")) {
				stale = true;
				break;
			}
		}
		if (!stale)
			return;
		DirCache index = repo.lockDirCache();
		ArrayList<DirCacheEntry> keep = new ArrayList<>();
		for (int i = 0; i < index.getEntryCount(); i++) {
			DirCacheEntry e = index.getEntry(i);
			if (!e.getPathString().startsWith(path + "/"))
				keep.add(e);
		}
		DirCacheBuilder builder = index.builder();
		for (DirCacheEntry e : keep)
			builder.add(e);
		builder.commit();
	}

	/**
	 * Recursively initialize and update (clone when missing, otherwise check
	 * out the recorded commit) every submodule of the repository behind git.
	 */
	public static void updateSubmodulesRecursive(Git git) throws Exception {
		Repository repo = git.getRepository();
		// re link absorbed modules whose work tree is gone before updating,
		// so the update can not try to clone over the existing git data
		ArrayList<String> paths = new ArrayList<>();
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
			while (walk.next()) {
				File subDir = walk.getDirectory();
				if (subDir == null)
					continue;
				repairMissingWorkTree(repo, walk.getPath(), subDir);
				paths.add(walk.getPath());
			}
		}
		if (paths.isEmpty())
			return;
		try {
			new SubmoduleInitCommand(repo).call();
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		SubmoduleUpdateCommand update = new SubmoduleUpdateCommand(repo);
		update.setProgressMonitor(
				ScriptingEngine.getProgressMoniter("Submodule update ", repo.getWorkTree().getAbsolutePath()));
		String url = repo.getConfig().getString("remote", "origin", "url");
		if (url != null && url.startsWith("git@"))
			update.setTransportConfigCallback(ScriptingEngine.getTransportConfigCallback());
		else
			update.setCredentialsProvider(PasswordManager.getCredentialProvider());
		try {
			update.call();
		} catch (Exception e) {
			com.neuronrobotics.sdk.common.Log.error(e);
		}
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
			while (walk.next()) {
				File subDir = walk.getDirectory();
				if (new File(subDir, Constants.DOT_GIT).exists()) {
					try (Repository sub = openRepository(subDir)) {
						ScriptingEngine.openGit(sub, g -> updateSubmodulesRecursive(g));
					}
				}
			}
		}
	}

	/**
	 * Re link a submodule whose working tree was deleted while its git data
	 * is still absorbed under the parent's .git/modules, so a plain clone can
	 * not collide with the existing module dir.
	 */
	private static void repairMissingWorkTree(Repository parentRepo, String path, File subDir)
			throws IOException, ConfigInvalidException {
		File moduleDir = new File(new File(parentRepo.getDirectory(), Constants.MODULES), path);
		if (!moduleDir.isDirectory())
			return;// no absorbed data to reattach
		if (!subDir.exists())
			subDir.mkdirs();
		File dotGit = new File(subDir, Constants.DOT_GIT);
		if (dotGit.exists())
			return;
		Files.write(dotGit.toPath(),
				("gitdir: " + relativePath(subDir, moduleDir) + "\n").getBytes(StandardCharsets.UTF_8));
		FileBasedConfig moduleConfig = new FileBasedConfig(new File(moduleDir, Constants.CONFIG), parentRepo.getFS());
		moduleConfig.load();
		moduleConfig.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_WORKTREE,
				relativePath(moduleDir, subDir));
		moduleConfig.save();
		com.neuronrobotics.sdk.common.Log.debug("Re linked submodule " + path + " to " + moduleDir);
	}

	/**
	 * Recursively commit pending changes in the submodules of git with the
	 * given message, innermost repositories first, so that each parent
	 * records the new commit ids of its submodules when it commits.
	 */
	public static void commitSubmodulesRecursive(Git git, String message) throws GitAPIException, IOException {
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(git.getRepository())) {
			while (walk.next()) {
				File subDir = walk.getDirectory();
				if (subDir == null || !new File(subDir, Constants.DOT_GIT).exists())
					continue;
				try (Repository sub = openRepository(subDir)) {
					ScriptingEngine.openGit(sub, g -> {
						commitSubmodulesRecursive(g, message);
						if (!g.status().call().isClean()) {
							g.add().addFilepattern(".").call();
							g.commit().setMessage(message).call();
							com.neuronrobotics.sdk.common.Log.debug("Committed submodule changes in " + subDir);
						}
					});
				}
			}
		}
	}

	/**
	 * Recursively push every submodule of git, innermost repositories first,
	 * so their commits exist on their remotes before the parent references
	 * them.
	 */
	public static void pushSubmodulesRecursive(Git git) throws Exception {
		try (SubmoduleWalk walk = SubmoduleWalk.forIndex(git.getRepository())) {
			while (walk.next()) {
				File subDir = walk.getDirectory();
				if (subDir == null || !new File(subDir, Constants.DOT_GIT).exists())
					continue;
				try (Repository sub = openRepository(subDir)) {
					ScriptingEngine.openGit(sub, g -> {
						pushSubmodulesRecursive(g);
						pushToOrigin(g);
					});
				}
			}
		}
	}

	private static void pushToOrigin(Git git) throws Exception {
		Repository repo = git.getRepository();
		String url = repo.getConfig().getString("remote", "origin", "url");
		if (url == null) {
			com.neuronrobotics.sdk.common.Log.debug("No remote to push " + repo.getWorkTree());
			return;
		}
		if (url.startsWith("git@"))
			git.push().setTransportConfigCallback(ScriptingEngine.getTransportConfigCallback())
					.setProgressMonitor(ScriptingEngine.getProgressMoniter("Pushing ", url)).call();
		else
			git.push().setCredentialsProvider(PasswordManager.getCredentialProvider())
					.setProgressMonitor(ScriptingEngine.getProgressMoniter("Pushing ", url)).call();
	}

	// helpers

	/**
	 * Open the repository whose work tree is dir, following a .git gitfile
	 * link when the repository is an absorbed submodule.
	 */
	private static Repository openRepository(File dir) throws IOException {
		File dotGit = new File(dir.getAbsoluteFile(), Constants.DOT_GIT);
		if (dotGit.isDirectory())
			return new FileRepository(dotGit);
		return new RepositoryBuilder().setWorkTree(dir.getAbsoluteFile()).build();
	}

	private static String originUrlOf(File repoDir) throws IOException {
		try (Repository repo = openRepository(repoDir)) {
			String url = repo.getConfig().getString("remote", "origin", "url");
			return url != null ? url : repoDir.getAbsoluteFile().getAbsolutePath();
		}
	}

	private static String relativePath(File from, File to) {
		try {
			return from.getAbsoluteFile().toPath().relativize(to.getAbsoluteFile().toPath()).toString().replace('\\',
					'/');
		} catch (IllegalArgumentException e) {
			// different roots; fall back to the absolute target
			return to.getAbsoluteFile().getAbsolutePath();
		}
	}
}
