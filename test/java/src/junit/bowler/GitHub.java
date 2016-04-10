package junit.bowler;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.Map;

import org.junit.Test;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;

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
		
	}

}
