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
		/*
		ScriptingEngine.runLogin();
		try {
			if (ScriptingEngine.getLoginID() == null) {
				return;
			}
			ScriptingEngine.setAutoupdate(true);
		} catch (Exception ex) {
			System.out.println("User not logged in, test can not run");
		}
		org.kohsuke.github.GitHub github = ScriptingEngine.getGithub();
		while (github == null) {
			github = ScriptingEngine.getGithub();
			ThreadUtil.wait(2000);
			System.out.println("Waiting for github");
		}
		Map<String, GHOrganization> orgs = github.getMyOrganizations();
		for (String org : orgs.keySet()) {
			System.out.println("Org: " + org);
			GHOrganization ghorg = orgs.get(org);
			Map<String, GHRepository> repos = ghorg.getRepositories();
			for (String orgRepo : repos.keySet()) {
				System.out.println("\tRepo " + org + " " + orgRepo);
			}
		}
		Map<String, Set<GHTeam>> teams = github.getMyTeams();
		for (String team : teams.keySet()) {
			System.out.println("Team " + team);
			Set<GHTeam> ghteam = teams.get(team);
			for (GHTeam ghT : ghteam) {
				System.out.println("\tGHTeam " + ghT.getName());
				Map<String, GHRepository> repos = ghT.getRepositories();
				for (String repoName : repos.keySet()) {
					System.out.println("\t\tGHTeam " + ghT.getName() + " repo " + repoName);
				}
			}
		}
		GHMyself self = github.getMyself();
		Map<String, GHRepository> myPublic = self.getAllRepositories();
		for (String myRepo : myPublic.keySet()) {
			System.out.println("Repo " + myRepo);
			GHRepository ghrepo = myPublic.get(myRepo);
			// if(ghrepo.getOwnerName().contains("demo"))
			System.out.println("\tOwner: " + ghrepo.getOwnerName() + " " + myRepo);
		}
		PagedIterable<GHRepository> watching = self.listSubscriptions();
		for (GHRepository g : watching) {
			System.out.println("Watching " + g.getOwnerName() + " " + g.getFullName());
		}
		String gitURL ="https://github.com/madhephaestus/clojure-utils.git";
		ArrayList<String> listofFiles = ScriptingEngine.filesInGit(gitURL,
				ScriptingEngine.getFullBranch(gitURL), null);
		if (listofFiles.size() == 0)
			fail();
		for (String s : listofFiles) {
			System.out.println("Files " + s);
		}
		String asstsRepo="https://github.com/madhephaestus/BowlerStudioImageAssets.git";
		
		// https://github.com/madhephaestus/BowlerStudioImageAssets.git
		ScriptingEngine.deleteRepo(asstsRepo);
		List<Ref> call = ScriptingEngine.listBranches(asstsRepo);
		System.out.println("Branches # " + call.size());
		if (call.size() > 0) {
			for (Ref ref : call) {
				System.out.println("Branch: Ref= " + ref + " name= " + ref.getName() + " ID = " + ref.getObjectId().getName());			}
		} else {
			fail();
		}
		
		ScriptingEngine.checkout(asstsRepo, call.get(0).getName());
		call = ScriptingEngine.listLocalBranches(asstsRepo);
		System.out.println("Local Branches # " + call.size());
		if (call.size() > 0) {
			for (Ref ref : call) {
				System.out.println("Branch: Ref= " + ref + " name= " + ref.getName() + " ID = " + ref.getObjectId().getName());
			}
		} else {
			fail();
		}
		//System.out.println("Creating branch # " );
//		ScriptingEngine.newBranch(asstsRepo, "0.20.0");
//		try{
//			ScriptingEngine.deleteBranch(asstsRepo, "0.20.0");
//		}catch(Exception e){
//			e.printStackTrace();
//		}		
		System.out.println("Current Branch # " +  ScriptingEngine.getFullBranch(asstsRepo));
		*/
  }

}
