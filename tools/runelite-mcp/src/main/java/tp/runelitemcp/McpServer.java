package tp.runelitemcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal MCP "Streamable HTTP" server: {@code POST /mcp} carrying JSON-RPC 2.0, replies as
 * {@code application/json} (no SSE, no sessions). Binds to the loopback address only. This class depends on the JDK
 * and Gson only, so it can be exercised without RuneLite (see the {@code smoke} source set).
 */
final class McpServer
{
	/** Protocol versions we accept from the client; the first is answered when the client's is unknown. */
	private static final List<String> SUPPORTED_PROTOCOLS = Arrays.asList("2025-06-18", "2025-03-26", "2024-11-05");
	/** Upper bound for a request body; tool arguments are tiny. */
	private static final int MAX_BODY_BYTES = 1 << 20;
	private static final int WORKER_THREADS = 4;
	private static final int ERR_PARSE = -32700;
	private static final int ERR_INVALID_REQUEST = -32600;
	private static final int ERR_METHOD_NOT_FOUND = -32601;
	private static final int ERR_INVALID_PARAMS = -32602;
	private static final int ERR_INTERNAL = -32603;

	private static final Gson GSON = new Gson();

	private final String host;
	private final int port;
	private final String token;
	private final String serverName;
	private final String serverVersion;
	private final String instructions;
	private final List<ToolDef> tools;
	private HttpServer http;
	private ExecutorService pool;

	McpServer(String host, int port, String token, String serverName, String serverVersion, String instructions,
		List<ToolDef> tools)
	{
		this.host = host;
		this.port = port;
		this.token = token == null ? "" : token;
		this.serverName = serverName;
		this.serverVersion = serverVersion;
		this.instructions = instructions;
		this.tools = new ArrayList<>(tools);
	}

	/** Actual bound port (differs from the requested one only when 0 was requested). */
	int boundPort()
	{
		return http.getAddress().getPort();
	}

	void start() throws IOException
	{
		http = HttpServer.create(new InetSocketAddress(InetAddress.getByName(host), port), 0);
		AtomicInteger n = new AtomicInteger();
		pool = Executors.newFixedThreadPool(WORKER_THREADS, r ->
		{
			Thread t = new Thread(r, "runelite-mcp-http-" + n.incrementAndGet());
			t.setDaemon(true);
			return t;
		});
		http.setExecutor(pool);
		http.createContext("/mcp", this::handle);
		http.start();
	}

	void stop()
	{
		if (http != null)
		{
			http.stop(0);
			http = null;
		}
		if (pool != null)
		{
			pool.shutdownNow();
			pool = null;
		}
	}

	@SuppressWarnings("deprecation")
	private void handle(HttpExchange ex) throws IOException
	{
		try
		{
			if (!isLoopback(ex) || !hostAndOriginOk(ex))
			{
				send(ex, 403, "text/plain", "forbidden");
				return;
			}
			if (!authorized(ex))
			{
				ex.getResponseHeaders().set("WWW-Authenticate", "Bearer");
				send(ex, 401, "text/plain", "unauthorized");
				return;
			}
			if (!"POST".equals(ex.getRequestMethod()))
			{
				// GET would open an SSE stream and DELETE would end a session; we support neither.
				ex.getResponseHeaders().set("Allow", "POST");
				send(ex, 405, "text/plain", "method not allowed");
				return;
			}
			byte[] body = readBody(ex.getRequestBody());
			if (body == null)
			{
				send(ex, 413, "text/plain", "request too large");
				return;
			}
			JsonElement parsed;
			try
			{
				parsed = new JsonParser().parse(new String(body, StandardCharsets.UTF_8)); // parseString needs Gson 2.8.6
			}
			catch (RuntimeException e)
			{
				sendJson(ex, 400, errorResponse(null, ERR_PARSE, "parse error"));
				return;
			}
			if (parsed.isJsonArray())
			{
				JsonArray out = new JsonArray();
				for (JsonElement e : parsed.getAsJsonArray())
				{
					JsonObject r = handleMessage(e);
					if (r != null)
					{
						out.add(r);
					}
				}
				if (out.size() == 0)
				{
					accepted(ex);
				}
				else
				{
					sendJson(ex, 200, out);
				}
				return;
			}
			JsonObject response = handleMessage(parsed);
			if (response == null)
			{
				accepted(ex);
			}
			else
			{
				sendJson(ex, 200, response);
			}
		}
		catch (RuntimeException e)
		{
			send(ex, 500, "text/plain", "internal error");
		}
		finally
		{
			ex.close();
		}
	}

	/** Returns the JSON-RPC response, or {@code null} for a notification (no reply). */
	private JsonObject handleMessage(JsonElement message)
	{
		if (!message.isJsonObject())
		{
			return errorResponse(null, ERR_INVALID_REQUEST, "invalid request");
		}
		JsonObject req = message.getAsJsonObject();
		JsonElement id = req.get("id");
		JsonElement methodEl = req.get("method");
		if (methodEl == null || !methodEl.isJsonPrimitive())
		{
			// A response from the client (we never send requests) or garbage: nothing to answer.
			return id == null ? null : errorResponse(id, ERR_INVALID_REQUEST, "missing method");
		}
		String method = methodEl.getAsString();
		boolean notification = id == null || id.isJsonNull();
		JsonObject params = req.get("params") != null && req.get("params").isJsonObject()
			? req.getAsJsonObject("params") : new JsonObject();
		if (notification)
		{
			return null; // notifications/initialized, notifications/cancelled, ...
		}
		try
		{
			switch (method)
			{
				case "initialize":
					return okResponse(id, initialize(params));
				case "ping":
					return okResponse(id, new JsonObject());
				case "tools/list":
					return okResponse(id, listTools());
				case "tools/call":
					return callTool(id, params);
				default:
					return errorResponse(id, ERR_METHOD_NOT_FOUND, "method not found: " + method);
			}
		}
		catch (RuntimeException e)
		{
			return errorResponse(id, ERR_INTERNAL, "internal error: " + e);
		}
	}

	private JsonObject initialize(JsonObject params)
	{
		String requested = params.has("protocolVersion") && params.get("protocolVersion").isJsonPrimitive()
			? params.get("protocolVersion").getAsString() : "";
		JsonObject result = new JsonObject();
		result.addProperty("protocolVersion",
			SUPPORTED_PROTOCOLS.contains(requested) ? requested : SUPPORTED_PROTOCOLS.get(0));
		JsonObject capabilities = new JsonObject();
		JsonObject toolsCap = new JsonObject();
		toolsCap.addProperty("listChanged", false);
		capabilities.add("tools", toolsCap);
		result.add("capabilities", capabilities);
		JsonObject info = new JsonObject();
		info.addProperty("name", serverName);
		info.addProperty("version", serverVersion);
		result.add("serverInfo", info);
		result.addProperty("instructions", instructions);
		return result;
	}

	private JsonObject listTools()
	{
		JsonArray arr = new JsonArray();
		for (ToolDef t : tools)
		{
			JsonObject o = new JsonObject();
			o.addProperty("name", t.name);
			o.addProperty("description", t.description);
			o.add("inputSchema", t.inputSchema);
			arr.add(o);
		}
		JsonObject result = new JsonObject();
		result.add("tools", arr);
		return result;
	}

	private JsonObject callTool(JsonElement id, JsonObject params)
	{
		JsonElement nameEl = params.get("name");
		if (nameEl == null || !nameEl.isJsonPrimitive())
		{
			return errorResponse(id, ERR_INVALID_PARAMS, "missing tool name");
		}
		ToolDef tool = null;
		for (ToolDef t : tools)
		{
			if (t.name.equals(nameEl.getAsString()))
			{
				tool = t;
				break;
			}
		}
		if (tool == null)
		{
			return errorResponse(id, ERR_INVALID_PARAMS, "unknown tool: " + nameEl.getAsString());
		}
		JsonObject args = params.get("arguments") != null && params.get("arguments").isJsonObject()
			? params.getAsJsonObject("arguments") : new JsonObject();
		ToolResult result;
		try
		{
			result = tool.handler.call(args);
		}
		catch (Exception e)
		{
			String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			result = ToolResult.error(e.getClass().getSimpleName() + ": " + msg);
		}
		catch (LinkageError e)
		{
			// e.g. a RuneLite API that differs from what we compiled against; report instead of killing the worker
			result = ToolResult.error("LinkageError: " + e);
		}
		return okResponse(id, result.toJson());
	}

	private static JsonObject okResponse(JsonElement id, JsonElement result)
	{
		JsonObject o = new JsonObject();
		o.addProperty("jsonrpc", "2.0");
		o.add("id", id);
		o.add("result", result);
		return o;
	}

	private static JsonObject errorResponse(JsonElement id, int code, String message)
	{
		JsonObject o = new JsonObject();
		o.addProperty("jsonrpc", "2.0");
		o.add("id", id == null ? com.google.gson.JsonNull.INSTANCE : id);
		JsonObject err = new JsonObject();
		err.addProperty("code", code);
		err.addProperty("message", message);
		o.add("error", err);
		return o;
	}

	// ---- HTTP plumbing -------------------------------------------------------------------------------------------

	private static boolean isLoopback(HttpExchange ex)
	{
		return ex.getRemoteAddress().getAddress().isLoopbackAddress();
	}

	/** DNS-rebinding defence (MCP transport spec): the Host and any Origin must name a loopback host. */
	private static boolean hostAndOriginOk(HttpExchange ex)
	{
		Headers h = ex.getRequestHeaders();
		String hostHeader = h.getFirst("Host");
		if (hostHeader != null && !isLoopbackName(hostOf(hostHeader)))
		{
			return false;
		}
		String origin = h.getFirst("Origin");
		if (origin != null)
		{
			try
			{
				return isLoopbackName(URI.create(origin).getHost());
			}
			catch (IllegalArgumentException e)
			{
				return false;
			}
		}
		return true;
	}

	private static String hostOf(String hostHeader)
	{
		if (hostHeader.startsWith("["))
		{
			int end = hostHeader.indexOf(']');
			return end < 0 ? hostHeader : hostHeader.substring(0, end + 1);
		}
		int colon = hostHeader.lastIndexOf(':');
		return colon < 0 ? hostHeader : hostHeader.substring(0, colon);
	}

	private static boolean isLoopbackName(String name)
	{
		if (name == null)
		{
			return false;
		}
		String n = name.toLowerCase(Locale.ROOT);
		return n.equals("127.0.0.1") || n.equals("localhost") || n.equals("[::1]");
	}

	private boolean authorized(HttpExchange ex)
	{
		if (token.isEmpty())
		{
			return true;
		}
		String header = ex.getRequestHeaders().getFirst("Authorization");
		if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7))
		{
			return false;
		}
		byte[] given = header.substring(7).trim().getBytes(StandardCharsets.UTF_8);
		return MessageDigest.isEqual(given, token.getBytes(StandardCharsets.UTF_8));
	}

	/** Reads the body, or returns {@code null} if it exceeds {@link #MAX_BODY_BYTES}. */
	private static byte[] readBody(InputStream in) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int n;
		while ((n = in.read(buf)) > 0)
		{
			out.write(buf, 0, n);
			if (out.size() > MAX_BODY_BYTES)
			{
				return null;
			}
		}
		return out.toByteArray();
	}

	private static void accepted(HttpExchange ex) throws IOException
	{
		ex.sendResponseHeaders(202, -1);
	}

	private static void sendJson(HttpExchange ex, int status, JsonElement body) throws IOException
	{
		send(ex, status, "application/json", GSON.toJson(body));
	}

	private static void send(HttpExchange ex, int status, String contentType, String body) throws IOException
	{
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", contentType);
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = ex.getResponseBody())
		{
			os.write(bytes);
		}
	}
}
