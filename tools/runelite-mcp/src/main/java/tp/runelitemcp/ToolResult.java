package tp.runelitemcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Result of an MCP tool call: a list of content parts plus the error flag. */
final class ToolResult
{
	private static final Gson GSON = new Gson();

	private final JsonArray content = new JsonArray();
	private boolean error;

	/** A text part holding {@code value} serialised as compact JSON. */
	static ToolResult json(JsonElement value)
	{
		ToolResult r = new ToolResult();
		r.addText(GSON.toJson(value));
		return r;
	}

	static ToolResult text(String text)
	{
		ToolResult r = new ToolResult();
		r.addText(text);
		return r;
	}

	static ToolResult error(String message)
	{
		ToolResult r = text(message);
		r.error = true;
		return r;
	}

	ToolResult addText(String text)
	{
		JsonObject part = new JsonObject();
		part.addProperty("type", "text");
		part.addProperty("text", text);
		content.add(part);
		return this;
	}

	ToolResult addImage(String base64, String mimeType)
	{
		JsonObject part = new JsonObject();
		part.addProperty("type", "image");
		part.addProperty("data", base64);
		part.addProperty("mimeType", mimeType);
		content.add(part);
		return this;
	}

	/** The MCP {@code CallToolResult} object. */
	JsonObject toJson()
	{
		JsonObject o = new JsonObject();
		o.add("content", content);
		if (error)
		{
			o.addProperty("isError", true);
		}
		return o;
	}
}
