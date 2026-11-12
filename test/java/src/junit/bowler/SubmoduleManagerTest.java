package junit.bowler;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.junit.Test;
import org.junit.After;
import org.junit.Ignore;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.bowlerstudio.scripting.SubmoduleManager;

/**
 * Behavior tests for converting repos nested inside a repo into submodules
 * and for the recursive checkout/commit/push of the submodule tree. All
 * repositories are local, no network needed.
 */
public class SubmoduleManagerTest {

	private static final String FILE_CONTENT = "hello submodule\n";
	private static final List<Repository> OPEN_REPOS = new ArrayList<>();

	private static File tmpDir(String name) throws IOException {
		File dir = Files.createTempDirectory("submodule-test-" + name).toFile();
		dir.deleteOnExit();
		return dir;
	}

	/** create a repo with one commit and an origin remote pointing at origin */
	private static File newRepo(File parent, String name, File origin) throws Exception {
		File dir = new File(parent, name);
		File remoteDir = new File(new File(origin, "remotes"), parent.getName() + "-" + name);
		remoteDir.mkdirs();
		// the origin remote is itself a bare-ish clone target; init it first
		try (Git o = Git.init().setDirectory(remoteDir).call()) {
			Files.write(new File(remoteDir, "seed.txt").toPath(), FILE_CONTENT.getBytes("UTF-8"));
			o.add().addFilepattern("seed.txt").call();
			o.commit().setMessage("seed commit").call();
		}
		try (Git g = Git.cloneRepository().setURI(remoteDir.getAbsolutePath()).setDirectory(dir).call()) {
		} // clones with an origin remote pointing at remoteDir
		return dir;
	}

	/**
	 * Open a repo work tree for reading, following the .git gitfile of an
	 * absorbed submodule (Git.open can not do that).
	 */
	private static Git openAbsorbed(File dir) throws IOException {
		Repository repo = new RepositoryBuilder().setWorkTree(dir).build();
		OPEN_REPOS.add(repo);
		return Git.wrap(repo);
	}

	private static void commitAll(Git git, String message) throws Exception {
		git.add().addFilepattern(".").call();
		git.commit().setMessage(message).call();
	}

	@After
	public void tearDown() throws Exception {
		for (Repository repo : OPEN_REPOS)
			repo.close();
		OPEN_REPOS.clear();
	}

	@Test
	public void testNestedRepoBecomesSubmodule() throws Exception {
		File root = tmpDir("root");
		File superp = newRepo(root, "super", root);
		File nested = newRepo(superp, "nested", root);
		Files.write(new File(nested, "change.txt").toPath(), FILE_CONTENT.getBytes("UTF-8"));
		try (Git g = Git.open(nested)) {
			commitAll(g, "nested change before conversion");
		}

		assertTrue(SubmoduleManager.isTopLevelGitRepo(superp));
		assertFalse("a repo inside another repo is by definition not top level",
				SubmoduleManager.isTopLevelGitRepo(nested));

		List<File> converted = ScriptingEngine.convertNestedReposToSubmodules(superp);
		assertEquals("the nested repo should have been converted", 1, converted.size());
		assertEquals(nested.getAbsoluteFile(), converted.get(0));

		// .git of nested repo is now a gitfile into super/.git/modules
		File nestedDotGit = new File(nested, Constants.DOT_GIT);
		assertTrue("nested .git should be a gitfile after absorption", nestedDotGit.isFile());
		String gitdir = new String(Files.readAllBytes(nestedDotGit.toPath()), "UTF-8").trim();
		assertTrue(gitdir.startsWith("gitdir: "));

		// gitlink is staged in the super repo index and .gitmodules written
		try (Git sup = openAbsorbed(superp)) {
			assertTrue("gitlink should be staged",
					FileMode.GITLINK.equals(sup.getRepository().readDirCache().getEntry("nested").getFileMode()));
			assertTrue(new File(superp, ".gitmodules").exists());
		}
		// the JGit infra can still resolve the absorbed repo through the gitfile
		try (Repository nestedRepo = new RepositoryBuilder().setWorkTree(nested).build()) {
			assertNotNull("absorbed nested repo must resolve HEAD", nestedRepo.resolve(Constants.HEAD));
		}
	}

	@Test
	public void testAlreadySubmoduleNotReconverted() throws Exception {
		File root = tmpDir("root");
		File superp = newRepo(root, "super", root);
		File nested = newRepo(superp, "nested", root);
		ScriptingEngine.convertNestedReposToSubmodules(superp);
		// a second run must not touch anything
		List<File> converted = ScriptingEngine.convertNestedReposToSubmodules(superp);
		assertTrue("already registered submodules must not be reconverted", converted.isEmpty());
	}

	@Test
	public void testRecursiveCommitOfSubmodules() throws Exception {
		File root = tmpDir("root");
		File superp = newRepo(root, "super", root);
		File nested = newRepo(superp, "nested", root);
		ScriptingEngine.convertNestedReposToSubmodules(superp);
		// commit the .gitmodules + gitlink registration
		try (Git sup = Git.open(superp)) {
			commitAll(sup, "add submodule");
		}

		// change inside the submodule
		Files.write(new File(nested, "inside.txt").toPath(), FILE_CONTENT.getBytes("UTF-8"));
		// commit through the engine's commit path used by pushCodeToGit
		try (Git sup = openAbsorbed(superp)) {
			SubmoduleManager.commitSubmodulesRecursive(sup, "recursive submodule commit");
		}
		// submodule should now be clean, with the message committed
		try (Git nestedGit = openAbsorbed(nested)) {
			assertTrue("submodule changes should have been committed", nestedGit.status().call().isClean());
			assertEquals("recursive submodule commit",
					nestedGit.log().setMaxCount(1).call().iterator().next().getShortMessage());
		}
		// the super project gitlink should be dirty, pointing at the new id
		try (Git sup = openAbsorbed(superp)) {
			assertFalse("super repo should see the moved gitlink", sup.status().call().isClean());
		}
	}

	@Test
	public void testDeeplyNestedReposAllConvert() throws Exception {
		File root = tmpDir("root");
		File superp = newRepo(root, "super", root);
		File mid = newRepo(superp, "mid", root);
		File deep = newRepo(mid, "deep", root);
		List<File> converted = ScriptingEngine.convertNestedReposToSubmodules(superp);
		assertEquals("mid and deep should both convert", 2, converted.size());
		// mid belongs to super; deep belongs to mid
		try (Git sup = Git.open(superp)) {
			assertTrue(FileMode.GITLINK.equals(sup.getRepository().readDirCache().getEntry("mid").getFileMode()));
		}
		try (Git midGit = openAbsorbed(mid)) {
			assertTrue(FileMode.GITLINK.equals(midGit.getRepository().readDirCache().getEntry("deep").getFileMode()));
		}
	}

	@Test
	public void testCheckoutRecursivelyUpdatesSubmodules() throws Exception {
		File root = tmpDir("root");
		File superp = newRepo(root, "super", root);
		File nested = newRepo(superp, "nested", root);
		ScriptingEngine.convertNestedReposToSubmodules(superp);
		try (Git sup = Git.open(superp)) {
			commitAll(sup, "add submodule");
		}
		// blow away the submodule working tree, then update via the manager
		Files.walk(nested.toPath()).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
		assertFalse(new File(nested, Constants.DOT_GIT).exists());

		try (Git sup = Git.open(superp)) {
			SubmoduleManager.updateSubmodulesRecursive(sup);
		}
		assertTrue("submodule working tree should be restored", new File(nested, "seed.txt").exists());
	}

	@Test
	public void testNotARepoTopLevelThrows() throws Exception {
		File dir = tmpDir("plain");
		try {
			ScriptingEngine.convertNestedReposToSubmodules(dir);
			fail("expected IllegalArgumentException for a non-repo directory");
		} catch (RuntimeException e) {
			// expected: plain directory is not a git repo top level
		}
	}

	/**
	 * End to end test against the real CaDoodle-Application repository, a
	 * three deep submodule chain:
	 * CaDoodle-Application -&gt; BowlerStudio -&gt; bowler-script-kernel -&gt;
	 * java-bowler/JCSG/GithubPasswordManager.
	 */
	@Test
	@Ignore
	public void testCaDoodleRecursiveSubmodules() throws Exception {
		File root = tmpDir("cadoodle");
		File cloneDir = new File(root, "CaDoodle-Application");
		try (Git g = Git.cloneRepository().setURI("https://github.com/CommonWealthRobotics/CaDoodle-Application.git")
				.setDirectory(cloneDir).call()) {
		}
		try (Git cadoodle = openAbsorbed(cloneDir)) {
			SubmoduleManager.updateSubmodulesRecursive(cadoodle);
			// every level of the chain is checked out
			File bowlerStudio = new File(cloneDir, "BowlerStudio");
			File kernel = new File(bowlerStudio, "libraries/bowler-script-kernel");
			File javaBowler = new File(kernel, "java-bowler");
			assertTrue("BowlerStudio submodule checked out", new File(bowlerStudio, ".gitmodules").exists());
			assertTrue("bowler-script-kernel submodule checked out", new File(kernel, ".gitmodules").exists());
			assertTrue("java-bowler submodule checked out", new File(javaBowler, "src").exists());

			// bowler-script-kernel's build.gradle embeds java-bowler's sources
			// as a plain directory, so the scan must convert it
			List<File> converted = ScriptingEngine.convertNestedReposToSubmodules(cloneDir);
			assertTrue("a nested repo inside the chain should be converted",
					converted.stream().allMatch(f -> f.getAbsolutePath().startsWith(cloneDir.getAbsolutePath())));
			for (File f : converted) {
				File dotGit = new File(f, Constants.DOT_GIT);
				assertTrue("converted repo " + f + " should be a gitfile link", dotGit.isFile());
			}
			// the gitlinks must be visible in the indexes of their parents
			for (File f : converted) {
				File parent = f.getParentFile();
				while (!new File(parent, Constants.DOT_GIT).exists())
					parent = parent.getParentFile();
				try (Git p = openAbsorbed(parent)) {
					String path = parent.toPath().relativize(f.toPath()).toString().replace('\\', '/');
					assertTrue("gitlink for " + path + " staged in " + parent,
							FileMode.GITLINK.equals(p.getRepository().readDirCache().getEntry(path).getFileMode()));
				}
			}
		}
	}
}
