package tp.runelitemcp;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/** Settings of the MCP server plugin (RuneLite configuration panel, group "mcp"). */
@ConfigGroup(McpConfig.GROUP)
public interface McpConfig extends Config
{
	String GROUP = "mcp";

	@ConfigItem(
		keyName = "enabled",
		name = "Enable server",
		description = "Run the local MCP server while this plugin is on.",
		position = 1
	)
	default boolean enabled()
	{
		return true;
	}

	@Range(min = 1024, max = 65535)
	@ConfigItem(
		keyName = "port",
		name = "Port",
		description = "TCP port on 127.0.0.1 (loopback only). Default 8765.",
		position = 2
	)
	default int port()
	{
		return 8765;
	}

	@ConfigItem(
		keyName = "token",
		name = "Bearer token",
		description = "If not empty, every request must carry 'Authorization: Bearer <token>'.",
		secret = true,
		position = 3
	)
	default String token()
	{
		return "";
	}

	@Range(min = 100, max = 100000)
	@ConfigItem(
		keyName = "eventBufferSize",
		name = "Event buffer size",
		description = "How many client events get_events keeps (ring buffer). Chat messages keep their own 500.",
		position = 4
	)
	default int eventBufferSize()
	{
		return 2000;
	}
}
