package com.neuronrobotics.bowlerstudio;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import com.neuronrobotics.bowlerstudio.scripting.ScriptingEngine;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.WaitResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.transport.DockerHttpClient;
import com.google.common.collect.Sets;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;

import java.io.File;

public class CoquiDockerManager implements ITTSEngine {

	private static HashMap<String, CoquiDockerManager> managers = new HashMap<>();

	private static List<String> options = Arrays.asList("tts_models/en/vctk/vits", "tts_models/en/jenny/jenny",
			"tts_models/en/ljspeech/tacotron2-DCA");
	private static List<String> speakerNeeded = Arrays.asList("tts_models/en/vctk/vits");
	private String voice;
	private DockerHttpClient client;

	private String ipAddress = "[::1]";

	private String id;

	private DockerClient dockerClient;
	private Container forMe = null;
	private int hostPort = 5002;
	private int containerPort = 5002;
	private boolean started=false;
	private CoquiDockerManager(String voice) throws InterruptedException, InvalidRemoteException, TransportException,
			GitAPIException, IOException, URISyntaxException {
		this.voice = voice;
		if (managers.get(voice) != null) {
			throw new RuntimeException("Only one manager allowed");
		}
		managers.put(voice, this);
		System.out.println("Coqui Voice " + voice);
		String[] parts = voice.split("/");
		String voiceSlug = "-" + parts[parts.length - 1];
		ScriptingEngine.cloneRepo("https://github.com/Halloween2020TheChild/CoquiDocker.git", null);
		ScriptingEngine.pull("https://github.com/Halloween2020TheChild/CoquiDocker.git");
		String dockerFilePath = ScriptingEngine
				.fileFromGit("https://github.com/Halloween2020TheChild/CoquiDocker.git", "Dockerfile")
				.getAbsolutePath(); // Replace with the actual path to your Dockerfile
		String imageName = "coqui-java"; // Replace with your desired image name

		// Create Docker client
		DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
		client = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
				.sslConfig(config.getSSLConfig()).maxConnections(100).connectionTimeout(Duration.ofSeconds(30))
				.responseTimeout(Duration.ofSeconds(45)).build();
		dockerClient = DockerClientBuilder.getInstance(config).withDockerHttpClient(client).build();

		// Build the Docker image
		String command = "python3 TTS/server/server.py --model_name " + voice;
		// Create the Docker container
		List<Container> allCOntainers = dockerClient.listContainersCmd().withShowAll(true).exec();
//        boolean containerExists=false;

		for (Container c : allCOntainers) {
			if (forMe != null)
				continue;
			System.out.println("Container: " + c);
			String[] names = c.getNames();
			for (String n : names) {

				String cs = "/" + imageName + voiceSlug;

				System.out.println("Checking " + n + " to " + cs);
				boolean PortMatch=false;
				ContainerPort[] conPorts = c.getPorts();
				for(ContainerPort cp:conPorts) {
					if(cp.getPublicPort()==hostPort)
						PortMatch=true;
				}
				if (n.contentEquals(cs) || PortMatch) {
					if (!c.getStatus().contains("Exited")) {
						try {
							dockerClient.killContainerCmd(c.getId()).exec();
						}catch(com.github.dockerjava.api.exception.ConflictException ex) {
							ex.printStackTrace();
						}
						//return;
					}
					dockerClient.removeContainerCmd(c.getId()).exec();
					
				}
			}

		}
		if (forMe == null) {
			File dockerfile = new File(dockerFilePath);

			dockerClient.buildImageCmd(dockerfile).withTags(Sets.newHashSet(imageName))
					.exec(new BuildImageResultCallback() {
						@Override
						public void onNext(BuildResponseItem item) {
							// Handle build output (optional)
							System.out.println(item.getStream());
							super.onNext(item);
						}
					}).awaitImageId();
			System.out.println("Docker image built successfully.");
			try {
				ExposedPort port = ExposedPort.tcp(hostPort);
				Ports portBindings = new Ports();
				portBindings.bind(port, Ports.Binding.bindPort(containerPort));
				CreateContainerResponse containerResponse = dockerClient.createContainerCmd(imageName)
						.withName(imageName + voiceSlug).withCmd(command.split(" ")).withTty(true)
						.withExposedPorts(port).withHostConfig(new HostConfig().withPortBindings(portBindings)).exec();
				id = containerResponse.getId();
				// Wait for the server to start inside the container
				dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback() {
					@Override
					public void onNext(WaitResponse item) {
						// Handle build output (optional)
						System.out.println(item.toString());
						super.onNext(item);
					}
				}).awaitCompletion();
				dockerClient.startContainerCmd(id).exec();
				LogContainerResultCallback logCallback=new LogContainerResultCallback() {
				    @Override
				    public void onNext(Frame item) {
				        String string = item.toString();
						System.out.println(string);
						if(string.contains("Press CTRL+C to quit")) {
							started=true;
						}
				    }
				};
				dockerClient.logContainerCmd(id).withStdOut(true).withStdErr(true).withFollowStream(true).exec(logCallback);


			} catch (com.github.dockerjava.api.exception.ConflictException ex) {
				// container exists
				ex.printStackTrace();

			}
		}
		while(!started) {
			Thread.sleep(1000);
			System.out.println("Waiting for server to start");
		}
//		Request request = Request.builder().method(Request.Method.GET).path("/_ping").build();
//
//		client.execute(request);
//		dockerClient.waitContainerCmd(id).exec(new WaitContainerResultCallback() {
//			@Override
//			public void onNext(WaitResponse item) {
//				// Handle build output (optional)
//				System.out.println(item.toString());
//				super.onNext(item);
//			}
//		}).awaitCompletion();

		// Get the container details
		// InspectContainerResponse container =
		// dockerClient.inspectContainerCmd(containerResponse.getId()).exec();

		// ipAddress = container.getNetworkSettings()

	}

	public void disconnect() {
		// TODO Auto-generated method stub
		dockerClient.killContainerCmd(id).exec();
	}

	public static CoquiDockerManager get(double doubleValue) throws InvalidRemoteException, TransportException,
			InterruptedException, GitAPIException, IOException, URISyntaxException {
		int value = (int) Math.round(doubleValue - 800);
		String voiceval = options.get(value);
		if (managers.get(voiceval) == null)
			return new CoquiDockerManager(voiceval);
		return managers.get(voiceval);
	}

	@Override
	public int speak(String text, float gainValue, boolean daemon, boolean join, ISpeakingProgress progress) {
		try {

			String content = URLEncoder.encode(text.trim(), StandardCharsets.UTF_8.toString()).replace("+", "%20");

			// String url = "http://"+ipAddress+":5002/;

			String string = "";//"&speaker_id=p304";
			for(String s:speakerNeeded) {
				if(s.contains(voice))
					string = "&speaker_id=p304";
			}
			String url = "http://[::1]:5002//api/tts?text=" + content
					+ string + "&style_wav=&language_id=HTTP/1.1\n"; // Replace
			// with your
			// URL

			Request request = new Request.Builder().url(url).get().build();

			Response response = new OkHttpClient().newCall(request).execute();
			// Handle the response as needed
			InputStream is = response.body().byteStream();
			AudioPlayer tts = new AudioPlayer();
			AudioInputStream audio = AudioSystem.getAudioInputStream(new BufferedInputStream(is));
			tts.setAudio(audio);
			tts.setGain(gainValue);
			tts.setDaemon(daemon);
			if (progress != null)
				tts.setSpeakProgress(progress);
			tts.start();
			if (join)
				tts.join();
		} catch (Exception e) {
			e.printStackTrace();
			return 1;
		}
		return 0;
	}

}
