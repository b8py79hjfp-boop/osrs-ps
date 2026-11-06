package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Tiny builder for the flat JSON Schema objects used as tool {@code inputSchema}. */
final class Schema
{
	private final JsonObject properties = new JsonObject();
	private final JsonArray required = new JsonArray();

	static Schema object()
	{
		return new Schema();
	}

	Schema integer(String name, String description)
	{
		return prop(name, "integer", description);
	}

	Schema number(String name, String description)
	{
		return prop(name, "number", description);
	}

	Schema string(String name, String description)
	{
		return prop(name, "string", description);
	}

	Schema bool(String name, String description)
	{
		return prop(name, "boolean", description);
	}

	Schema integers(String name, String description)
	{
		return array(name, "integer", description);
	}

	Schema strings(String name, String description)
	{
		return array(name, "string", description);
	}

	Schema require(String... names)
	{
		for (String n : names)
		{
			required.add(n);
		}
		return this;
	}

	JsonObject build()
	{
		JsonObject o = new JsonObject();
		o.addProperty("type", "object");
		o.add("properties", properties);
		if (required.size() > 0)
		{
			o.add("required", required);
		}
		return o;
	}

	private Schema prop(String name, String type, String description)
	{
		JsonObject p = new JsonObject();
		p.addProperty("type", type);
		p.addProperty("description", description);
		properties.add(name, p);
		return this;
	}

	private Schema array(String name, String itemType, String description)
	{
		JsonObject items = new JsonObject();
		items.addProperty("type", itemType);
		JsonObject p = new JsonObject();
		p.addProperty("type", "array");
		p.add("items", items);
		p.addProperty("description", description);
		properties.add(name, p);
		return this;
	}
}
