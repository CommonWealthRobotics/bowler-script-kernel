package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

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
		ScriptingEngine.setAutoupdate(true);
		org.kohsuke.github.GitHub github = ScriptingEngine.getGithub();
		while (github == null) {
			github = ScriptingEngine.getGithub();
			ThreadUtil.wait(2000);
			System.out.println("Waiting for github");
		}
		Map<String, GHOrganization> orgs = github.getMyOrganizations();
		for(String org:orgs.keySet()){
			System.out.println("Org: "+org);
			GHOrganization ghorg = orgs.get(org);
			Map<String, GHRepository> repos = ghorg.getRepositories();
			for(String orgRepo: repos.keySet()){
				System.out.println("\tRepo "+org+" "+orgRepo);
			}
		}
		Map<String, Set<GHTeam>> teams = github.getMyTeams();
		for (String team :teams.keySet()){
			System.out.println("Team "+team);
			Set<GHTeam> ghteam = teams.get(team);
			for(GHTeam ghT: ghteam){
				System.out.println("\tGHTeam "+ghT.getName());
				Map<String, GHRepository> repos = ghT.getRepositories();
				for(String repoName:repos.keySet()){
					System.out.println("\t\tGHTeam "+ghT.getName()+" repo "+repoName);
				}
			}
		}
		GHMyself self = github.getMyself();
		Map<String, GHRepository> myPublic = self.getAllRepositories();
		for (String myRepo :myPublic.keySet()){
			System.out.println("Repo "+myRepo);
			GHRepository ghrepo= myPublic.get(myRepo);
			//if(ghrepo.getOwnerName().contains("demo"))
			System.out.println("\tOwner: "+ghrepo.getOwnerName()+" "+myRepo);
		}
		PagedIterable<GHRepository> watching = self.listSubscriptions();
		for(GHRepository g:watching){
			System.out.println("Watching "+g.getOwnerName()+" "+g.getFullName());
		}
		ArrayList<String> listofFiles = ScriptingEngine.filesInGit("https://github.com/madhephaestus/clojure-utils.git", "master", null);
		if(listofFiles.size()==0)
			fail();
		for(String s: listofFiles){
			System.out.println("Files "+s);
		}
	}

}
