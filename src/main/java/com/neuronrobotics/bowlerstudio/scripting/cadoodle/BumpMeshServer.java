package com.neuronrobotics.bowlerstudio.scripting.cadoodle;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

public class BumpMeshServer {

	private static final int WS_PORT = 3742;
	private static final int HTTP_PORT = 8080;
	private static final String STATUS_PATH = "/api/status";
	private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

	private final File meshFile;
	private final File configFile;
	private final File webDir;
	private Server jetty;

	public BumpMeshServer(File meshFile, File configFile, File webDir) throws Exception {
		this.meshFile = meshFile;
		this.configFile = configFile;
		this.webDir = webDir;
		startHttp();
		startWatcher();
		startWebSocket();
	}

	private synchronized void startHttp() throws Exception {
		if (jetty != null)
			jetty.stop();
		jetty = new Server(HTTP_PORT);
		ResourceHandler rh = new ResourceHandler();
		rh.setDirectoriesListed(true);
		rh.setResourceBase(webDir.getAbsolutePath());
		HandlerList handlers = new HandlerList();
		// Status handler is checked first so it answers /api/status even
		// though it isn't a file in webDir.
		handlers.setHandlers(new Handler[]{new StatusHandler(), rh, new DefaultHandler()});
		jetty.setHandler(handlers);
		jetty.start();
	}

	/**
	 * Serves GET /api/status as a small JSON object reporting whether the mesh file
	 * and config file currently exist on disk, e.g.: {"mesh":true,"config":false}
	 */
	private class StatusHandler extends AbstractHandler {
		@Override
		public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
				throws IOException, ServletException {
			if (!STATUS_PATH.equals(target))
				return;

			String json = "{\"mesh\":" + meshFile.exists() + ",\"config\":" + configFile.exists() + "}";

			response.setContentType("application/json");
			response.setCharacterEncoding("UTF-8");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().write(json);
			baseRequest.setHandled(true);
		}
	}

	private void startWatcher() throws IOException {
		WatchService ws = FileSystems.getDefault().newWatchService();
		webDir.toPath().register(ws, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY,
				StandardWatchEventKinds.ENTRY_DELETE);
		Thread t = new Thread(() -> {
			while (true) {
				WatchKey key;
				try {
					key = ws.take();
				} catch (InterruptedException e) {
					return;
				}
				key.pollEvents();
				try {
					startHttp();
				} catch (Exception e) {
					e.printStackTrace();
				}
				key.reset();
			}
		}, "webdir-watcher");
		t.setDaemon(true);
		t.start();
	}

	private void startWebSocket() {
		Thread t = new Thread(() -> {
			try (ServerSocket ss = new ServerSocket(WS_PORT)) {
				while (true) {
					try (Socket s = ss.accept()) {
						handleClient(s);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}, "ws-server");
		t.setDaemon(true);
		t.start();
	}

	private void handleClient(Socket socket) throws Exception {
		InputStream in = socket.getInputStream();
		OutputStream out = socket.getOutputStream();

		String requestLine = readLine(in);
		if (requestLine == null)
			return;
		String path = requestLine.split(" ").length > 1 ? requestLine.split(" ")[1] : "/";

		String line, key = null;
		while ((line = readLine(in)) != null && !line.isEmpty()) {
			if (line.toLowerCase(Locale.ROOT).startsWith("sec-websocket-key:")) {
				key = line.substring(line.indexOf(':') + 1).trim();
			}
		}
		if (key == null)
			return;

		// Route based on the request path. Defaults to the mesh file if
		// the client doesn't specify /config explicitly.
		File target = path.toLowerCase(Locale.ROOT).contains("config") ? configFile : meshFile;

		String accept = Base64.getEncoder().encodeToString(
				MessageDigest.getInstance("SHA-1").digest((key + WS_MAGIC).getBytes(StandardCharsets.UTF_8)));

		out.write(("HTTP/1.1 101 Switching Protocols\r\n" + "Upgrade: websocket\r\n" + "Connection: Upgrade\r\n"
				+ "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
		out.flush();

		File tmp = File.createTempFile("upload", ".tmp", target.getAbsoluteFile().getParentFile());
		boolean completed = false;

		try (FileOutputStream fos = new FileOutputStream(tmp)) {
			frameLoop : while (true) {
				int b1 = in.read();
				if (b1 == -1)
					break; // socket closed without a close frame
				int b2 = in.read();
				if (b2 == -1)
					break;

				int opcode = b1 & 0x0F;
				boolean masked = (b2 & 0x80) != 0;
				long len = b2 & 0x7F;

				if (len == 126) {
					len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
				} else if (len == 127) {
					len = 0;
					for (int i = 0; i < 8; i++)
						len = (len << 8) | (in.read() & 0xFF);
				}

				byte[] mask = new byte[4];
				if (masked) {
					int r = 0;
					while (r < 4) {
						int rr = in.read(mask, r, 4 - r);
						if (rr == -1)
							break frameLoop;
						r += rr;
					}
				}

				byte[] payload = new byte[(int) len];
				int off = 0;
				while (off < payload.length) {
					int r = in.read(payload, off, payload.length - off);
					if (r == -1)
						break frameLoop;
					off += r;
				}
				if (masked) {
					for (int i = 0; i < payload.length; i++)
						payload[i] ^= mask[i % 4];
				}

				switch (opcode) {
					case 8 : // close frame — client is done sending
						completed = true;
						break frameLoop;
					case 2 : // binary
					case 0 : // continuation
						fos.write(payload);
						break;
					default :
						// ignore ping/pong/text frames
				}
			}
		}

		if (completed) {
			Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} else {
			tmp.delete(); // connection dropped mid-upload; discard partial data
		}

		try {
			out.write(new byte[]{(byte) 0x88, 0x00}); // close frame back to client
			out.flush();
		} catch (IOException ignored) {
		}
	}

	// Reads a CRLF-terminated line directly off the raw stream (no buffering)
	// so the stream position is exact when we switch to binary frame reading.
	private static String readLine(InputStream in) throws IOException {
		ByteArrayOutputStream buf = new ByteArrayOutputStream();
		int b, last = -1;
		while ((b = in.read()) != -1) {
			if (last == '\r' && b == '\n') {
				byte[] arr = buf.toByteArray();
				return new String(arr, 0, arr.length - 1, StandardCharsets.UTF_8);
			}
			buf.write(b);
			last = b;
		}
		return buf.size() == 0 ? null : buf.toString(StandardCharsets.UTF_8.name());
	}

	public static void main(String[] args) throws Exception {
		new BumpMeshServer(new File(args[0]), new File(args[1]), new File(args[2]));
		Thread.currentThread().join();
	}
}
