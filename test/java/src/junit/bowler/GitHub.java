package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.lib.Ref;
import org.junit.Test;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTeam;
import org.kohsuke.github.PagedIterable;

import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.neuronrobotics.sdk.util.ThreadUtil;

public class GitHub {

  @Test
  public void test() throws Exception {
		//ScriptingEngine.login();
//		String remoteURI = "https://github.com/madhephaestusdemo/WalkTest_madhephaestusdemo.git";
//		com.neuronrobotics.sdk.common.Log.error(ScriptingEngine.getRepositoryCloneDirectory(remoteURI));
//		ScriptingEngine.pull(remoteURI);
		//ScriptingEngine.fork("https://github.com/madhephaestus/6dofServoArm.git", "forktest6dof", "testing yo!");	
		
		/*
		ScriptingEngine.runLogin();
		try {
			if (ScriptingEngine.getLoginID() == null) {
				return;
			}
			ScriptingEngine.setAutoupdate(true);
		} catch (Exception ex) {
			com.neuronrobotics.sdk.common.Log.error("User not logged in, test can not run");
		}
		org.kohsuke.github.GitHub github = ScriptingEngine.getGithub();
		while (github == null) {
			github = ScriptingEngine.getGithub();
			ThreadUtil.wait(2000);
			com.neuronrobotics.sdk.common.Log.error("Waiting for github");
		}
		Map<String, GHOrganization> orgs = github.getMyOrganizations();
		for (String org : orgs.keySet()) {
			com.neuronrobotics.sdk.common.Log.error("Org: " + org);
			GHOrganization ghorg = orgs.get(org);
			Map<String, GHRepository> repos = ghorg.getRepositories();
			for (String orgRepo : repos.keySet()) {
				com.neuronrobotics.sdk.common.Log.error("\tRepo " + org + " " + orgRepo);
			}
		}
		Map<String, Set<GHTeam>> teams = github.getMyTeams();
		for (String team : teams.keySet()) {
			com.neuronrobotics.sdk.common.Log.error("Team " + team);
			Set<GHTeam> ghteam = teams.get(team);
			for (GHTeam ghT : ghteam) {
				com.neuronrobotics.sdk.common.Log.error("\tGHTeam " + ghT.getName());
				Map<String, GHRepository> repos = ghT.getRepositories();
				for (String repoName : repos.keySet()) {
					com.neuronrobotics.sdk.common.Log.error("\t\tGHTeam " + ghT.getName() + " repo " + repoName);
				}
			}
		}
		GHMyself self = github.getMyself();
		Map<String, GHRepository> myPublic = self.getAllRepositories();
		for (String myRepo : myPublic.keySet()) {
			com.neuronrobotics.sdk.common.Log.error("Repo " + myRepo);
			GHRepository ghrepo = myPublic.get(myRepo);
			// if(ghrepo.getOwnerName().contains("demo"))
			com.neuronrobotics.sdk.common.Log.error("\tOwner: " + ghrepo.getOwnerName() + " " + myRepo);
		}
		PagedIterable<GHRepository> watching = self.listSubscriptions();
		for (GHRepository g : watching) {
			com.neuronrobotics.sdk.common.Log.error("Watching " + g.getOwnerName() + " " + g.getFullName());
		}
		String gitURL ="https://github.com/madhephaestus/clojure-utils.git";
		ArrayList<String> listofFiles = ScriptingEngine.filesInGit(gitURL,
				ScriptingEngine.getFullBranch(gitURL), null);
		if (listofFiles.size() == 0)
			fail();
		for (String s : listofFiles) {
			com.neuronrobotics.sdk.common.Log.error("Files " + s);
		}
		String asstsRepo="https://github.com/madhephaestus/BowlerStudioImageAssets.git";
		
		// https://github.com/madhephaestus/BowlerStudioImageAssets.git
		ScriptingEngine.deleteRepo(asstsRepo);
		List<Ref> call = ScriptingEngine.listBranches(asstsRepo);
		com.neuronrobotics.sdk.common.Log.error("Branches # " + call.size());
		if (call.size() > 0) {
			for (Ref ref : call) {
				com.neuronrobotics.sdk.common.Log.error("Branch: Ref= " + ref + " name= " + ref.getName() + " ID = " + ref.getObjectId().getName());			}
		} else {
			fail();
		}
		
		ScriptingEngine.checkout(asstsRepo, call.get(0).getName());
		call = ScriptingEngine.listLocalBranches(asstsRepo);
		com.neuronrobotics.sdk.common.Log.error("Local Branches # " + call.size());
		if (call.size() > 0) {
			for (Ref ref : call) {
				com.neuronrobotics.sdk.common.Log.error("Branch: Ref= " + ref + " name= " + ref.getName() + " ID = " + ref.getObjectId().getName());
			}
		} else {
			fail();
		}
		//com.neuronrobotics.sdk.common.Log.error("Creating branch # " );
//		ScriptingEngine.newBranch(asstsRepo, "0.20.0");
//		try{
//			ScriptingEngine.deleteBranch(asstsRepo, "0.20.0");
//		}catch(Exception e){
//			e.printStackTrace();
//		}		
		com.neuronrobotics.sdk.common.Log.error("Current Branch # " +  ScriptingEngine.getFullBranch(asstsRepo));
		*/
  }

}
