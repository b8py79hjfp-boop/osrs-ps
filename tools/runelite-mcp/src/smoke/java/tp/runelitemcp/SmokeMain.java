package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

/**
 * Isolated smoke test of the HTTP / JSON-RPC layer: starts {@link McpServer} with fake tools and drives it over a
 * real socket. Not part of the plugin jar. Run: {@code ./gradlew -p tools/runelite-mcp smoke}.
 * With {@code serve <port> [token]} it only starts the server and blocks, for manual curl.
 */
public final class SmokeMain
{
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static int failures;

	private SmokeMain()
	{
	}

	private static List<ToolDef> fakeTools()
	{
		return Arrays.asList(
			new ToolDef("echo", "Echoes its arguments.",
				Schema.object().string("msg", "Text.").require("msg").build(),
				a ->
				{
					JsonObject o = new JsonObject();
					o.add("echo", a.get("msg"));
					return ToolResult.json(o);
				}),
			new ToolDef("fail", "Always throws.", Schema.object().build(),
				a ->
				{
					throw new IllegalStateException("boom");
				}),
			new ToolDef("pixel", "Returns a 1x1 png.", Schema.object().build(),
				a -> new ToolResult().addImage(
					"iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
					"image/png")));
	}

	public static void main(String[] args) throws Exception
	{
		if (args.length >= 2 && "serve".equals(args[0]))
		{
			McpServer s = new McpServer("127.0.0.1", Integer.parseInt(args[1]), args.length > 2 ? args[2] : "",
				"smoke", "0", "smoke", fakeTools());
			s.start();
			System.out.println("serving on http://127.0.0.1:" + s.boundPort() + "/mcp");
			Thread.currentThread().join();
			return;
		}

		McpServer open = new McpServer("127.0.0.1", 0, "", "smoke", "0", "smoke", fakeTools());
		open.start();
		String url = "http://127.0.0.1:" + open.boundPort() + "/mcp";
		try
		{
			JsonObject init = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
				+ "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"t\","
				+ "\"version\":\"1\"}}}", 200);
			check("initialize protocol echo", "2025-06-18".equals(
				init.getAsJsonObject("result").get("protocolVersion").getAsString()));
			check("initialize tools capability", init.getAsJsonObject("result").getAsJsonObject("capabilities")
				.has("tools"));
			JsonObject old = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"initialize\",\"params\":"
				+ "{\"protocolVersion\":\"1999-01-01\"}}", 200);
			check("unknown protocol falls back", "2025-06-18".equals(
				old.getAsJsonObject("result").get("protocolVersion").getAsString()));

			HttpResponse<String> note = post(url, null,
				"{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}");
			check("notification -> 202 empty", note.statusCode() == 202 && note.body().isEmpty());

			JsonObject ping = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}", 200);
			check("ping", ping.has("result"));

			JsonObject list = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/list\"}", 200);
			JsonArray tools = list.getAsJsonObject("result").getAsJsonArray("tools");
			check("tools/list has 3 tools with schema", tools.size() == 3
				&& tools.get(0).getAsJsonObject().has("inputSchema"));

			JsonObject call = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"echo\",\"arguments\":{\"msg\":\"hi\"}}}", 200);
			JsonObject part = call.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject();
			check("tools/call text part", "text".equals(part.get("type").getAsString())
				&& part.get("text").getAsString().equals("{\"echo\":\"hi\"}")
				&& !call.getAsJsonObject("result").has("isError"));

			JsonObject img = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":6,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"pixel\"}}", 200);
			JsonObject ip = img.getAsJsonObject("result").getAsJsonArray("content").get(0).getAsJsonObject();
			check("tools/call image part", "image".equals(ip.get("type").getAsString())
				&& "image/png".equals(ip.get("mimeType").getAsString()));

			JsonObject fail = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":"
				+ "{\"name\":\"fail\"}}", 200);
			check("tool exception -> isError", fail.getAsJsonObject("result").get("isError").getAsBoolean());

			JsonObject unknown = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\","
				+ "\"params\":{\"name\":\"nope\"}}", 200);
			check("unknown tool -> -32602", unknown.getAsJsonObject("error").get("code").getAsInt() == -32602);

			JsonObject nomethod = rpc(url, null, "{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"foo\"}", 200);
			check("unknown method -> -32601", nomethod.getAsJsonObject("error").get("code").getAsInt() == -32601);

			HttpResponse<String> bad = post(url, null, "{not json");
			check("parse error -> 400 / -32700", bad.statusCode() == 400 && new JsonParser().parse(bad.body())
				.getAsJsonObject().getAsJsonObject("error").get("code").getAsInt() == -32700);

			HttpResponse<String> get = HTTP.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
				HttpResponse.BodyHandlers.ofString());
			check("GET -> 405", get.statusCode() == 405);

			HttpResponse<String> evil = HTTP.send(HttpRequest.newBuilder(URI.create(url))
				.header("Origin", "https://evil.example").header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
			check("foreign Origin -> 403", evil.statusCode() == 403);
		}
		finally
		{
			open.stop();
		}

		McpServer locked = new McpServer("127.0.0.1", 0, "s3cret", "smoke", "0", "smoke", fakeTools());
		locked.start();
		String lockedUrl = "http://127.0.0.1:" + locked.boundPort() + "/mcp";
		try
		{
			String ping = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";
			check("token: none -> 401", post(lockedUrl, null, ping).statusCode() == 401);
			check("token: wrong -> 401", post(lockedUrl, "Bearer nope", ping).statusCode() == 401);
			check("token: right -> 200", post(lockedUrl, "Bearer s3cret", ping).statusCode() == 200);
		}
		finally
		{
			locked.stop();
		}

		System.out.println(failures == 0 ? "SMOKE OK" : "SMOKE FAILED: " + failures);
		System.exit(failures == 0 ? 0 : 1);
	}

	private static HttpResponse<String> post(String url, String auth, String body) throws Exception
	{
		HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString(body));
		if (auth != null)
		{
			b.header("Authorization", auth);
		}
		return HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static JsonObject rpc(String url, String auth, String body, int expectedStatus) throws Exception
	{
		HttpResponse<String> r = post(url, auth, body);
		check("HTTP " + expectedStatus + " for " + body.substring(0, Math.min(60, body.length())),
			r.statusCode() == expectedStatus);
		check("content-type json", r.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
		return new JsonParser().parse(r.body()).getAsJsonObject();
	}

	private static void check(String what, boolean ok)
	{
		System.out.println((ok ? "  ok   " : "  FAIL ") + what);
		if (!ok)
		{
			failures++;
		}
	}
}
