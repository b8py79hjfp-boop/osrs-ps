package tp.runelitemcp;

import com.google.gson.JsonObject;

/** One MCP tool: name, description, JSON Schema for the arguments, and the handler. */
final class ToolDef
{
	/** Handler; may throw, which is reported to the caller as an {@code isError} result. */
	@FunctionalInterface
	interface Handler
	{
		ToolResult call(JsonObject args) throws Exception;
	}

	final String name;
	final String description;
	final JsonObject inputSchema;
	final Handler handler;

	ToolDef(String name, String description, JsonObject inputSchema, Handler handler)
	{
		this.name = name;
		this.description = description;
		this.inputSchema = inputSchema;
		this.handler = handler;
	}
}
