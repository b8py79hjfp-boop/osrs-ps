package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a local MCP server (JSON-RPC over HTTP, loopback only) so a coding agent can observe and drive this client.
 * Intended for developing the tickperfect private server; sideloaded in RuneLite developer mode.
 */
@PluginDescriptor(
	name = "MCP Server",
	description = "Local MCP server to observe and drive the client (development tool)",
	tags = {"mcp", "claude", "debug", "automation"},
	developerPlugin = true,
	enabledByDefault = false
)
public class McpPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(McpPlugin.class);

	/** Bind address: loopback only, never configurable. */
	private static final String BIND_HOST = "127.0.0.1";
	/** Chat ring buffer size (task requirement). */
	private static final int CHAT_BUFFER_SIZE = 500;
	private static final String VERSION = "0.1.0";
	private static final String INSTRUCTIONS = "Observe and drive a RuneLite client connected to a tickperfect "
		+ "(OSRS rev 242) private server. Start with get_state; use get_events after an action to see the "
		+ "MenuOptionClicked the client sent and the varp/widget changes the server made.";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ItemManager itemManager;

	@Inject
	private DrawManager drawManager;

	@Inject
	private McpConfig config;

	private EventLog events;
	private EventLog chat;
	private McpServer server;

	@Provides
	McpConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(McpConfig.class);
	}

	@Override
	protected void startUp()
	{
		events = new EventLog(config.eventBufferSize());
		chat = new EventLog(CHAT_BUFFER_SIZE);
		startServer();
	}

	@Override
	protected void shutDown()
	{
		stopServer();
		events = null;
		chat = null;
	}

	private void startServer()
	{
		if (!config.enabled())
		{
			log.info("MCP server disabled in config");
			return;
		}
		ClientTools tools = new ClientTools(client, clientThread, itemManager, drawManager, events, chat);
		List<ToolDef> defs = tools.build();
		McpServer s = new McpServer(BIND_HOST, config.port(), config.token(), "runelite-mcp", VERSION,
			INSTRUCTIONS, defs);
		try
		{
			s.start();
			server = s;
			log.info("MCP server listening on http://{}:{}/mcp ({} tools, token {})", BIND_HOST, config.port(),
				defs.size(), config.token().isEmpty() ? "not required" : "required");
		}
		catch (IOException | RuntimeException e)
		{
			log.error("MCP server failed to start on {}:{}", BIND_HOST, config.port(), e);
		}
	}

	private void stopServer()
	{
		if (server != null)
		{
			server.stop();
			server = null;
			log.info("MCP server stopped");
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!McpConfig.GROUP.equals(e.getGroup()) || events == null)
		{
			return;
		}
		if ("eventBufferSize".equals(e.getKey()))
		{
			events.setCapacity(config.eventBufferSize());
		}
		else
		{
			stopServer();
			startServer();
		}
	}

	// ---- event capture (all on the client thread) ----------------------------------------------------------------

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		MenuAction action = e.getMenuAction();
		o.addProperty("option", e.getMenuOption());
		o.addProperty("target", e.getMenuTarget());
		o.addProperty("action", action.name());
		o.addProperty("action_id", action.getId());
		o.addProperty("id", e.getId());
		o.addProperty("param0", e.getParam0());
		o.addProperty("param1", e.getParam1());
		o.addProperty("item_id", e.getItemId());
		if (action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY
			|| action == MenuAction.WIDGET_TARGET || action == MenuAction.WIDGET_TARGET_ON_WIDGET
			|| (action.getId() >= MenuAction.WIDGET_FIRST_OPTION.getId()
			&& action.getId() <= MenuAction.WIDGET_FIFTH_OPTION.getId()))
		{
			o.addProperty("widget_group", e.getParam1() >>> 16);
			o.addProperty("widget_child", e.getParam1() & 0xFFFF);
			String name = GameNames.component(e.getParam1());
			if (name != null)
			{
				o.addProperty("widget_constant", name);
			}
		}
		o.addProperty("consumed", e.isConsumed());
		events.add("MenuOptionClicked", client.getTickCount(), o);
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("varp_id", e.getVarpId());
		o.addProperty("varbit_id", e.getVarbitId());
		o.addProperty("value", e.getValue());
		events.add("VarbitChanged", client.getTickCount(), o);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("group", e.getGroupId());
		o.addProperty("name", GameNames.group(e.getGroupId()));
		events.add("WidgetLoaded", client.getTickCount(), o);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("group", e.getGroupId());
		o.addProperty("name", GameNames.group(e.getGroupId()));
		o.addProperty("modal_mode", e.getModalMode());
		o.addProperty("unload", e.isUnload());
		events.add("WidgetClosed", client.getTickCount(), o);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("game_state", e.getGameState().name());
		events.add("GameStateChanged", client.getTickCount(), o);
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("skill", e.getSkill().getName());
		o.addProperty("xp", e.getXp());
		o.addProperty("level", e.getLevel());
		o.addProperty("boosted_level", e.getBoostedLevel());
		events.add("StatChanged", client.getTickCount(), o);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("container_id", e.getContainerId());
		ItemContainer c = e.getItemContainer();
		JsonArray items = new JsonArray();
		if (c != null)
		{
			Item[] arr = c.getItems();
			for (int slot = 0; slot < arr.length; slot++)
			{
				Item it = arr[slot];
				if (it != null && it.getId() >= 0 && it.getQuantity() > 0)
				{
					JsonObject j = new JsonObject();
					j.addProperty("slot", slot);
					j.addProperty("id", it.getId());
					j.addProperty("quantity", it.getQuantity());
					items.add(j);
				}
			}
		}
		o.add("items", items);
		events.add("ItemContainerChanged", client.getTickCount(), o);
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (chat == null || events == null)
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("chat_type", e.getType().name());
		o.addProperty("name", e.getName());
		o.addProperty("sender", e.getSender());
		o.addProperty("message", e.getMessage());
		o.addProperty("text", Text.removeTags(e.getMessage()));
		o.addProperty("timestamp", e.getTimestamp());
		int tick = client.getTickCount();
		// messages filter on "type", which for the chat buffer is the ChatMessageType name
		JsonObject asChat = o.deepCopy();
		asChat.addProperty("type", e.getType().name());
		chat.add(e.getType().name(), tick, asChat);
		events.add("ChatMessage", tick, o);
	}
}
