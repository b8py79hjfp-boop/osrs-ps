package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Canvas;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GroundObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WallObject;
import net.runelite.api.WidgetNode;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.Text;

/** All MCP tools. Everything that reads or writes client state runs on the client thread via {@link #onClient}. */
final class ClientTools
{
	/** Timeout for a client-thread round trip; the client thread runs roughly every 20 ms (one client tick). */
	private static final long CLIENT_THREAD_TIMEOUT_MS = 5000;
	/** Timeout for the next rendered frame (screenshot). */
	private static final long FRAME_TIMEOUT_MS = 8000;
	/** Size of a scene in tiles (RuneLite {@code Constants.SCENE_SIZE}). */
	private static final int SCENE_SIZE = 104;
	private static final int MAX_NEARBY_RADIUS = 50;
	private static final int DEFAULT_NEARBY_RADIUS = 12;
	private static final int PER_TYPE_LIMIT = 150;
	/** Upper bound when walking the components of one interface group (groups are far smaller). */
	private static final int MAX_COMPONENTS_PER_GROUP = 4096;
	private static final int INVENTORY_CONTAINER = 93;
	private static final int EQUIPMENT_CONTAINER = 94;
	/** Delay between synthetic key events, so the client's key queue is drained at least once per few events. */
	private static final long KEY_GAP_MS = 8;
	/** Highest op index that still uses CC_OP; higher ops use CC_OP_LOW_PRIORITY (MenuEntrySwapperPlugin). */
	private static final int LAST_NORMAL_PRIORITY_OP = 5;
	/** chat_sendpublic type used by the chatbox for public chat (ChatboxInput javadoc: 0 = public). */
	private static final int CHAT_TYPE_PUBLIC = 0;

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final DrawManager drawManager;
	private final EventLog events;
	private final EventLog chat;

	ClientTools(Client client, ClientThread clientThread, ItemManager itemManager, DrawManager drawManager,
		EventLog events, EventLog chat)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.drawManager = drawManager;
		this.events = events;
		this.chat = chat;
	}

	List<ToolDef> build()
	{
		List<ToolDef> t = new ArrayList<>();
		t.add(new ToolDef("get_state",
			"Game state, player name/world/location (x, y, plane), tick count, run energy and run flag, hitpoints, "
				+ "prayer, all skill levels, open top-level and sub interfaces (with names), selected widget, camera.",
			Schema.object().build(), a -> ToolResult.json(onClient(this::state))));
		t.add(new ToolDef("get_inventory",
			"Inventory contents (container 93): slot, item id, quantity, name. Optional container_id for any other "
				+ "item container (e.g. 95 = bank, only while loaded).",
			Schema.object().integer("container_id", "Item container id; default 93 (inventory).").build(),
			a -> ToolResult.json(onClient(() -> container(intArg(a, "container_id", INVENTORY_CONTAINER))))));
		t.add(new ToolDef("get_equipment",
			"Worn equipment (container 94): slot name, item id, quantity, name.",
			Schema.object().build(), a -> ToolResult.json(onClient(() -> container(EQUIPMENT_CONTAINER)))));
		t.add(new ToolDef("get_nearby",
			"NPCs, players, ground items and objects within a radius (Chebyshev tiles) of the local player: id, name, "
				+ "world location, scene location (the p0/p1 for menu_action), actions.",
			Schema.object()
				.integer("radius", "Tiles, default " + DEFAULT_NEARBY_RADIUS + ", max " + MAX_NEARBY_RADIUS + ".")
				.strings("types", "Subset of npcs, players, ground_items, objects. Default: all.")
				.string("name", "Only entries whose name contains this (case-insensitive).")
				.bool("include_unnamed_objects", "Include objects named 'null' (decorations). Default false.")
				.build(),
			a -> ToolResult.json(onClient(() -> nearby(a)))));
		t.add(new ToolDef("get_widget",
			"Components of an interface group (or one component): packed id, type, text, name, actions, hidden, "
				+ "bounds, item id, child counts.",
			Schema.object()
				.integer("group", "Interface group id (e.g. 12 = bank). Needed unless widget is given.")
				.integer("child", "Component index in the group; omit to list the whole group.")
				.integer("widget", "Packed component id ((group << 16) | child), alternative to group/child.")
				.bool("visible_only", "Skip hidden components. Default false.")
				.bool("children", "Also list dynamic and static children of each component (inventory slots, list "
					+ "rows, ...). Default false.")
				.integer("limit", "Max entries returned. Default 300.")
				.build(),
			a -> ToolResult.json(onClient(() -> getWidget(a)))));
		t.add(new ToolDef("find_widgets",
			"Search every loaded interface for components whose text/name contains a string, whose actions contain a "
				+ "string, or that show an item id. Returns packed ids to feed click_widget.",
			Schema.object()
				.string("text", "Case-insensitive substring of the component text or name (colour tags ignored).")
				.string("action", "Case-insensitive substring of any action (menu option).")
				.integer("item_id", "Components showing this item id.")
				.integers("groups", "Restrict to these group ids.")
				.bool("visible_only", "Skip hidden components. Default false.")
				.integer("limit", "Max results. Default 100.")
				.build(),
			a -> ToolResult.json(onClient(() -> findWidgets(a)))));
		t.add(new ToolDef("click_widget",
			"Perform a widget menu action exactly like a real click (client.menuAction with CC_OP for IF3 "
				+ "components), so the server receives the same IF_BUTTON packet. Identify the component by packed id "
				+ "or group+child, optionally a dynamic child index, and the op by number (1-based) or option text.",
			Schema.object()
				.integer("widget", "Packed component id ((group << 16) | child).")
				.integer("group", "Interface group id (alternative to widget).")
				.integer("child", "Component index in the group (alternative to widget).")
				.integer("index", "Dynamic child index under that component (e.g. inventory slot, bank item).")
				.integer("op", "1-based op number. Default 1, or resolved from option.")
				.string("option", "Option text to look up in the component's actions (case-insensitive substring).")
				.integer("item_id", "Item id to send; default the component's own item id (-1 if none).")
				.string("action", "Override the MenuAction name (e.g. WIDGET_CONTINUE, WIDGET_TARGET).")
				.bool("dry_run", "Only report what would be sent.")
				.build(),
			a -> ToolResult.json(onClient(() -> clickWidget(a)))));
		t.add(new ToolDef("menu_action",
			"Raw client.menuAction(p0, p1, action, id, item_id, option, target) for anything else: walk, NPC/object/"
				+ "ground item/player ops. Scene coordinates (p0/p1) come from get_nearby. action is a MenuAction name "
				+ "(e.g. NPC_FIRST_OPTION, GAME_OBJECT_FIRST_OPTION, GROUND_ITEM_THIRD_OPTION) or its numeric id.",
			Schema.object()
				.integer("p0", "Menu param0 (scene x, or child index for widget actions). Default 0.")
				.integer("p1", "Menu param1 (scene y, or packed widget id). Default 0.")
				.string("action", "MenuAction enum name or numeric id.")
				.integer("id", "Menu identifier (npc index, object id, item id, op number ...). Default 0.")
				.integer("item_id", "Item id, -1 if none. Default -1.")
				.string("option", "Option text, informational. Default empty.")
				.string("target", "Target text, informational. Default empty.")
				.require("action").build(),
			a -> ToolResult.json(onClient(() -> menuAction(a)))));
		t.add(new ToolDef("walk_to",
			"Walk to a world tile (sends a WALK menu action, like a map click). Target must be inside the loaded "
				+ "scene.",
			Schema.object()
				.integer("x", "World x.").integer("y", "World y.")
				.require("x", "y").build(),
			a -> ToolResult.json(onClient(() -> walkTo(a)))));
		t.add(new ToolDef("send_chat",
			"Send a public chat line. Default method 'script' runs the chatbox send script (5517) like RuneLite's "
				+ "own ChatInputManager. A text starting with '::' is typed as key events (method 'keys') because "
				+ "commands are dispatched by the chatbox key handler.",
			Schema.object()
				.string("text", "Chat line.")
				.string("method", "'script' (default for plain text) or 'keys' (synthetic AWT key events).")
				.bool("press_enter_first", "keys method: press Enter before typing (when 'Press Enter to Chat' "
					+ "is on). Default false.")
				.require("text").build(),
			this::sendChat));
		t.add(new ToolDef("run_command",
			"Run a server command such as '::tele 3222 3218'. Types it with synthetic key events into the game "
				+ "canvas and presses Enter. The '::' prefix is added if missing. Check get_messages afterwards.",
			Schema.object()
				.string("command", "Command text, with or without leading '::'.")
				.bool("press_enter_first", "Press Enter before typing. Default false.")
				.require("command").build(),
			a ->
			{
				String c = strArg(a, "command", "").trim();
				JsonObject b = new JsonObject();
				b.addProperty("text", c.startsWith("::") ? c : "::" + c);
				b.addProperty("method", "keys");
				if (a.has("press_enter_first"))
				{
					b.add("press_enter_first", a.get("press_enter_first"));
				}
				return sendChat(b);
			}));
		t.add(new ToolDef("get_messages",
			"Recent chat/game messages (ring buffer of 500): seq, tick, type, name, sender, message, text, timestamp.",
			Schema.object()
				.integer("limit", "Newest N messages. Default 50, max 500.")
				.integer("since_seq", "Only messages with seq greater than this.")
				.strings("types", "ChatMessageType names to keep (GAMEMESSAGE, PUBLICCHAT, ...).")
				.build(),
			a -> ToolResult.json(messages(a))));
		t.add(new ToolDef("get_events",
			"Ring buffer of client events with tick numbers: MenuOptionClicked (what the client sent), VarbitChanged "
				+ "(varp/varbit + value), WidgetLoaded/WidgetClosed, GameStateChanged, StatChanged, "
				+ "ItemContainerChanged, ChatMessage, SoundEffectPlayed/AreaSoundEffectPlayed (sound id), HitsplatApplied, "
				+ "ActorDeath, InteractingChanged and AnimationChanged (local player and its target), OverheadTextChanged. "
				+ "Poll with since_seq = latest_seq of the previous call.",
			Schema.object()
				.integer("since_seq", "Only events with seq greater than this. Default 0.")
				.strings("types", "Event type names to keep, e.g. MenuOptionClicked, VarbitChanged.")
				.integer("limit", "Newest N matches. Default 200, max 5000.")
				.build(),
			a -> ToolResult.json(eventsTool(a))));
		t.add(new ToolDef("get_varps",
			"Values of given varp ids and/or varbit ids; with all_nonzero, every non-zero varp.",
			Schema.object()
				.integers("varps", "Varp ids.")
				.integers("varbits", "Varbit ids.")
				.bool("all_nonzero", "Also return all varps whose value is not 0.")
				.build(),
			a -> ToolResult.json(onClient(() -> varps(a)))));
		t.add(new ToolDef("screenshot",
			"PNG of the game canvas (next rendered frame), base64.",
			Schema.object()
				.number("scale", "Downscale factor 0.05..1. Default 1.")
				.integer("max_width", "Downscale so the width is at most this. Applied after scale.")
				.build(),
			this::screenshot));
		return t;
	}

	// ---- client thread plumbing ----------------------------------------------------------------------------------

	private <T> T onClient(Callable<T> task) throws Exception
	{
		CompletableFuture<T> future = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				future.complete(task.call());
			}
			catch (Throwable e)
			{
				future.completeExceptionally(e);
			}
		});
		try
		{
			return future.get(CLIENT_THREAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e)
		{
			future.cancel(false);
			throw new IllegalStateException("client thread did not respond within " + CLIENT_THREAD_TIMEOUT_MS + " ms");
		}
		catch (ExecutionException e)
		{
			Throwable c = e.getCause();
			if (c instanceof Exception)
			{
				throw (Exception) c;
			}
			throw new IllegalStateException(String.valueOf(c), c);
		}
	}

	// ---- argument helpers ----------------------------------------------------------------------------------------

	private static int intArg(JsonObject a, String name, int def)
	{
		JsonElement e = a.get(name);
		return e == null || e.isJsonNull() ? def : e.getAsInt();
	}

	private static Integer optInt(JsonObject a, String name)
	{
		JsonElement e = a.get(name);
		return e == null || e.isJsonNull() ? null : e.getAsInt();
	}

	private static int reqInt(JsonObject a, String name)
	{
		Integer v = optInt(a, name);
		if (v == null)
		{
			throw new IllegalArgumentException("missing argument: " + name);
		}
		return v;
	}

	private static String strArg(JsonObject a, String name, String def)
	{
		JsonElement e = a.get(name);
		return e == null || e.isJsonNull() ? def : e.getAsString();
	}

	private static boolean boolArg(JsonObject a, String name, boolean def)
	{
		JsonElement e = a.get(name);
		return e == null || e.isJsonNull() ? def : e.getAsBoolean();
	}

	private static Set<String> stringSet(JsonObject a, String name)
	{
		Set<String> out = new LinkedHashSet<>();
		JsonElement e = a.get(name);
		if (e != null && e.isJsonArray())
		{
			for (JsonElement x : e.getAsJsonArray())
			{
				out.add(x.getAsString());
			}
		}
		return out;
	}

	private static JsonArray strings(String[] values)
	{
		JsonArray arr = new JsonArray();
		if (values != null)
		{
			for (String s : values)
			{
				if (s != null)
				{
					arr.add(s);
				}
			}
		}
		return arr;
	}

	private static JsonObject point(int x, int y, int plane)
	{
		JsonObject o = new JsonObject();
		o.addProperty("x", x);
		o.addProperty("y", y);
		o.addProperty("plane", plane);
		return o;
	}

	// ---- get_state -----------------------------------------------------------------------------------------------

	private JsonObject state()
	{
		JsonObject o = new JsonObject();
		GameState gs = client.getGameState();
		o.addProperty("game_state", gs.name());
		o.addProperty("tick", client.getTickCount());
		o.addProperty("world", client.getWorld());
		Player me = client.getLocalPlayer();
		if (me != null)
		{
			o.addProperty("name", me.getName());
			o.addProperty("player_index", me.getId());
			o.addProperty("combat_level", me.getCombatLevel());
			WorldPoint wp = me.getWorldLocation();
			if (wp != null)
			{
				o.add("location", point(wp.getX(), wp.getY(), wp.getPlane()));
			}
			LocalPoint lp = me.getLocalLocation();
			if (lp != null)
			{
				JsonObject scene = new JsonObject();
				scene.addProperty("x", lp.getSceneX());
				scene.addProperty("y", lp.getSceneY());
				o.add("scene_location", scene);
			}
			o.addProperty("animation", me.getAnimation());
			o.addProperty("pose_animation", me.getPoseAnimation());
			if (me.getInteracting() != null)
			{
				o.addProperty("interacting", me.getInteracting().getName());
			}
		}
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null)
		{
			JsonObject scene = new JsonObject();
			scene.addProperty("base_x", wv.getBaseX());
			scene.addProperty("base_y", wv.getBaseY());
			scene.addProperty("plane", wv.getPlane());
			scene.addProperty("instance", wv.isInstance());
			o.add("world_view", scene);
		}
		// getEnergy() is in 1/100 of a percent (Client javadoc)
		o.addProperty("run_energy_percent", client.getEnergy() / 100.0);
		o.addProperty("run_enabled", client.getVarpValue(VarPlayerID.OPTION_RUN) == 1);
		o.addProperty("weight", client.getWeight());
		o.add("hitpoints", levelPair(Skill.HITPOINTS));
		o.add("prayer", levelPair(Skill.PRAYER));
		JsonObject skills = new JsonObject();
		for (Skill s : Skill.values())
		{
			if ("OVERALL".equals(s.name()))
			{
				continue;
			}
			JsonObject sk = levelPair(s);
			sk.addProperty("xp", client.getSkillExperience(s));
			skills.add(s.getName(), sk);
		}
		o.add("skills", skills);

		JsonObject interfaces = new JsonObject();
		int top = client.getTopLevelInterfaceId();
		interfaces.addProperty("top_level", top);
		interfaces.addProperty("top_level_name", GameNames.group(top));
		JsonArray subs = new JsonArray();
		for (WidgetNode n : client.getComponentTable())
		{
			int parent = (int) n.getHash();
			JsonObject s = new JsonObject();
			s.addProperty("parent_widget", parent);
			s.addProperty("parent_group", parent >>> 16);
			s.addProperty("parent_child", parent & 0xFFFF);
			s.addProperty("parent_name", GameNames.component(parent));
			s.addProperty("group", n.getId());
			s.addProperty("name", GameNames.group(n.getId()));
			s.addProperty("modal_mode", n.getModalMode());
			subs.add(s);
		}
		interfaces.add("sub_interfaces", subs);
		o.add("interfaces", interfaces);

		if (client.isWidgetSelected())
		{
			Widget sel = client.getSelectedWidget();
			if (sel != null)
			{
				JsonObject s = new JsonObject();
				s.addProperty("widget", sel.getId());
				s.addProperty("index", sel.getIndex());
				s.addProperty("item_id", sel.getItemId());
				s.addProperty("name", sel.getName());
				o.add("selected_widget", s);
			}
		}
		JsonObject cam = new JsonObject();
		cam.addProperty("x", client.getCameraX());
		cam.addProperty("y", client.getCameraY());
		cam.addProperty("z", client.getCameraZ());
		cam.addProperty("pitch", client.getCameraPitch());
		cam.addProperty("yaw", client.getCameraYaw());
		cam.addProperty("focal_x", client.getCameraFocalPointX());
		cam.addProperty("focal_y", client.getCameraFocalPointY());
		cam.addProperty("focal_z", client.getCameraFocalPointZ());
		o.add("camera", cam);
		JsonObject canvas = new JsonObject();
		canvas.addProperty("width", client.getCanvasWidth());
		canvas.addProperty("height", client.getCanvasHeight());
		o.add("canvas", canvas);
		return o;
	}

	private JsonObject levelPair(Skill s)
	{
		JsonObject o = new JsonObject();
		o.addProperty("current", client.getBoostedSkillLevel(s));
		o.addProperty("base", client.getRealSkillLevel(s));
		return o;
	}

	// ---- containers ----------------------------------------------------------------------------------------------

	private JsonObject container(int containerId)
	{
		JsonObject o = new JsonObject();
		o.addProperty("container_id", containerId);
		ItemContainer c = client.getItemContainer(containerId);
		if (c == null)
		{
			o.addProperty("loaded", false);
			o.add("items", new JsonArray());
			return o;
		}
		o.addProperty("loaded", true);
		o.addProperty("size", c.size());
		JsonArray items = new JsonArray();
		Item[] arr = c.getItems();
		for (int slot = 0; slot < arr.length; slot++)
		{
			Item it = arr[slot];
			if (it == null || it.getId() < 0 || (it.getId() == 0 && it.getQuantity() == 0))
			{
				continue;
			}
			JsonObject j = new JsonObject();
			j.addProperty("slot", slot);
			if (containerId == EQUIPMENT_CONTAINER)
			{
				for (EquipmentInventorySlot es : EquipmentInventorySlot.values())
				{
					if (es.getSlotIdx() == slot)
					{
						j.addProperty("slot_name", es.name());
					}
				}
			}
			j.addProperty("id", it.getId());
			j.addProperty("quantity", it.getQuantity());
			j.addProperty("name", itemName(it.getId()));
			items.add(j);
		}
		o.add("items", items);
		o.addProperty("count", items.size());
		return o;
	}

	private String itemName(int id)
	{
		try
		{
			ItemComposition ic = itemManager.getItemComposition(id);
			return ic == null ? null : ic.getName();
		}
		catch (RuntimeException e)
		{
			return null;
		}
	}

	// ---- get_nearby ----------------------------------------------------------------------------------------------

	private JsonObject nearby(JsonObject a)
	{
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || wv == null || client.getGameState() != GameState.LOGGED_IN)
		{
			throw new IllegalStateException("not logged in");
		}
		int radius = Math.max(1, Math.min(MAX_NEARBY_RADIUS, intArg(a, "radius", DEFAULT_NEARBY_RADIUS)));
		Set<String> types = stringSet(a, "types");
		String nameFilter = strArg(a, "name", "").toLowerCase(Locale.ROOT);
		boolean unnamed = boolArg(a, "include_unnamed_objects", false);
		WorldPoint here = me.getWorldLocation();
		JsonObject o = new JsonObject();
		o.add("center", point(here.getX(), here.getY(), here.getPlane()));
		o.addProperty("radius", radius);

		if (types.isEmpty() || types.contains("npcs"))
		{
			JsonArray arr = new JsonArray();
			for (NPC npc : wv.npcs())
			{
				if (npc == null || arr.size() >= PER_TYPE_LIMIT)
				{
					continue;
				}
				NPCComposition comp = npc.getTransformedComposition();
				if (comp == null)
				{
					comp = npc.getComposition();
				}
				String name = comp == null ? npc.getName() : comp.getName();
				JsonObject j = actorBase(npc, here, radius, name, nameFilter);
				if (j == null)
				{
					continue;
				}
				j.addProperty("index", npc.getIndex());
				j.addProperty("id", comp == null ? npc.getId() : comp.getId());
				j.addProperty("combat_level", npc.getCombatLevel());
				if (comp != null)
				{
					j.add("actions", strings(comp.getActions()));
					j.addProperty("size", comp.getSize());
				}
				arr.add(j);
			}
			o.add("npcs", arr);
		}
		if (types.isEmpty() || types.contains("players"))
		{
			JsonArray arr = new JsonArray();
			for (Player p : wv.players())
			{
				if (p == null || arr.size() >= PER_TYPE_LIMIT)
				{
					continue;
				}
				JsonObject j = actorBase(p, here, radius, p.getName(), nameFilter);
				if (j == null)
				{
					continue;
				}
				j.addProperty("index", p.getId());
				j.addProperty("combat_level", p.getCombatLevel());
				j.addProperty("local", p == me);
				arr.add(j);
			}
			o.add("players", arr);
		}
		boolean wantItems = types.isEmpty() || types.contains("ground_items");
		boolean wantObjects = types.isEmpty() || types.contains("objects");
		if (wantItems || wantObjects)
		{
			Scene scene = wv.getScene();
			Tile[][] tiles = scene.getTiles()[wv.getPlane()];
			LocalPoint lp = me.getLocalLocation();
			int sx = lp.getSceneX();
			int sy = lp.getSceneY();
			JsonArray items = new JsonArray();
			JsonArray objects = new JsonArray();
			Set<Long> seenObjects = new HashSet<>();
			for (int x = Math.max(0, sx - radius); x <= Math.min(SCENE_SIZE - 1, sx + radius); x++)
			{
				for (int y = Math.max(0, sy - radius); y <= Math.min(SCENE_SIZE - 1, sy + radius); y++)
				{
					Tile tile = tiles[x][y];
					if (tile == null)
					{
						continue;
					}
					if (wantItems && items.size() < PER_TYPE_LIMIT)
					{
						addGroundItems(items, tile, nameFilter);
					}
					if (wantObjects && objects.size() < PER_TYPE_LIMIT)
					{
						addObjects(objects, seenObjects, tile, nameFilter, unnamed);
					}
				}
			}
			if (wantItems)
			{
				o.add("ground_items", items);
			}
			if (wantObjects)
			{
				o.add("objects", objects);
			}
		}
		return o;
	}

	/** Common actor fields, or {@code null} if the actor is outside the radius or filtered out by name. */
	private JsonObject actorBase(net.runelite.api.Actor actor, WorldPoint here, int radius, String name,
		String nameFilter)
	{
		WorldPoint wp = actor.getWorldLocation();
		if (wp == null || wp.getPlane() != here.getPlane())
		{
			return null;
		}
		int dist = Math.max(Math.abs(wp.getX() - here.getX()), Math.abs(wp.getY() - here.getY()));
		if (dist > radius || (!nameFilter.isEmpty() && (name == null
			|| !name.toLowerCase(Locale.ROOT).contains(nameFilter))))
		{
			return null;
		}
		JsonObject j = new JsonObject();
		j.addProperty("name", name);
		j.addProperty("x", wp.getX());
		j.addProperty("y", wp.getY());
		j.addProperty("plane", wp.getPlane());
		LocalPoint lp = actor.getLocalLocation();
		if (lp != null)
		{
			j.addProperty("scene_x", lp.getSceneX());
			j.addProperty("scene_y", lp.getSceneY());
		}
		j.addProperty("distance", dist);
		j.addProperty("animation", actor.getAnimation());
		if (actor.getInteracting() != null)
		{
			j.addProperty("interacting", actor.getInteracting().getName());
		}
		if (actor.getHealthScale() > 0)
		{
			j.addProperty("health_ratio", actor.getHealthRatio());
			j.addProperty("health_scale", actor.getHealthScale());
		}
		return j;
	}

	private void addGroundItems(JsonArray out, Tile tile, String nameFilter)
	{
		List<TileItem> list = tile.getGroundItems();
		if (list == null)
		{
			return;
		}
		for (TileItem ti : list)
		{
			String name = itemName(ti.getId());
			if (!nameFilter.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(nameFilter)))
			{
				continue;
			}
			JsonObject j = new JsonObject();
			j.addProperty("id", ti.getId());
			j.addProperty("name", name);
			j.addProperty("quantity", ti.getQuantity());
			WorldPoint wp = tile.getWorldLocation();
			j.addProperty("x", wp.getX());
			j.addProperty("y", wp.getY());
			j.addProperty("plane", wp.getPlane());
			j.addProperty("scene_x", tile.getSceneLocation().getX());
			j.addProperty("scene_y", tile.getSceneLocation().getY());
			out.add(j);
		}
	}

	private void addObjects(JsonArray out, Set<Long> seen, Tile tile, String nameFilter, boolean unnamed)
	{
		GameObject[] gos = tile.getGameObjects();
		if (gos != null)
		{
			for (GameObject go : gos)
			{
				if (go != null && seen.add(go.getHash()))
				{
					addObject(out, "game", go.getId(), go.getWorldLocation(), tile, nameFilter, unnamed);
				}
			}
		}
		WallObject wo = tile.getWallObject();
		if (wo != null)
		{
			addObject(out, "wall", wo.getId(), wo.getWorldLocation(), tile, nameFilter, unnamed);
		}
		GroundObject gr = tile.getGroundObject();
		if (gr != null)
		{
			addObject(out, "ground", gr.getId(), gr.getWorldLocation(), tile, nameFilter, unnamed);
		}
		DecorativeObject dec = tile.getDecorativeObject();
		if (dec != null)
		{
			addObject(out, "decorative", dec.getId(), dec.getWorldLocation(), tile, nameFilter, unnamed);
		}
	}

	private void addObject(JsonArray out, String kind, int id, WorldPoint wp, Tile tile, String nameFilter,
		boolean unnamed)
	{
		ObjectComposition comp = client.getObjectDefinition(id);
		if (comp != null && comp.getImpostorIds() != null)
		{
			ObjectComposition imp = comp.getImpostor();
			if (imp != null)
			{
				comp = imp;
			}
		}
		String name = comp == null ? null : comp.getName();
		boolean isUnnamed = name == null || name.equals("null");
		if ((isUnnamed && !unnamed)
			|| (!nameFilter.isEmpty() && (name == null || !name.toLowerCase(Locale.ROOT).contains(nameFilter))))
		{
			return;
		}
		JsonObject j = new JsonObject();
		j.addProperty("kind", kind);
		j.addProperty("id", id);
		j.addProperty("name", name);
		if (comp != null)
		{
			j.add("actions", strings(comp.getActions()));
			j.addProperty("size_x", comp.getSizeX());
			j.addProperty("size_y", comp.getSizeY());
		}
		j.addProperty("x", wp.getX());
		j.addProperty("y", wp.getY());
		j.addProperty("plane", wp.getPlane());
		// scene coordinates of the tile holding the object, used as menu p0/p1 for object ops
		j.addProperty("scene_x", tile.getSceneLocation().getX());
		j.addProperty("scene_y", tile.getSceneLocation().getY());
		out.add(j);
	}

	// ---- widgets -------------------------------------------------------------------------------------------------

	private JsonObject widgetJson(Widget w)
	{
		JsonObject j = new JsonObject();
		int id = w.getId();
		j.addProperty("id", id);
		j.addProperty("group", id >>> 16);
		j.addProperty("child", id & 0xFFFF);
		j.addProperty("index", w.getIndex());
		String constant = GameNames.component(id);
		if (constant != null)
		{
			j.addProperty("constant", constant);
		}
		j.addProperty("type", w.getType());
		j.addProperty("content_type", w.getContentType());
		j.addProperty("text", w.getText());
		j.addProperty("name", w.getName());
		j.add("actions", strings(w.getActions()));
		j.addProperty("hidden", w.isHidden());
		j.addProperty("self_hidden", w.isSelfHidden());
		Rectangle b = w.getBounds();
		if (b != null)
		{
			JsonObject bo = new JsonObject();
			bo.addProperty("x", b.x);
			bo.addProperty("y", b.y);
			bo.addProperty("w", b.width);
			bo.addProperty("h", b.height);
			j.add("bounds", bo);
		}
		j.addProperty("item_id", w.getItemId());
		j.addProperty("item_quantity", w.getItemQuantity());
		j.addProperty("sprite_id", w.getSpriteId());
		j.addProperty("model_id", w.getModelId());
		j.addProperty("parent_id", w.getParentId());
		j.addProperty("if3", w.isIf3());
		JsonObject kids = new JsonObject();
		kids.addProperty("static", len(w.getStaticChildren()));
		kids.addProperty("dynamic", len(w.getDynamicChildren()));
		kids.addProperty("nested", len(w.getNestedChildren()));
		j.add("child_counts", kids);
		return j;
	}

	private static int len(Object[] a)
	{
		return a == null ? 0 : a.length;
	}

	private JsonObject getWidget(JsonObject a)
	{
		boolean visibleOnly = boolArg(a, "visible_only", false);
		boolean withChildren = boolArg(a, "children", false);
		int limit = Math.max(1, intArg(a, "limit", 300));
		Integer packed = optInt(a, "widget");
		int group = packed != null ? packed >>> 16 : reqInt(a, "group");
		Integer child = packed != null ? Integer.valueOf(packed & 0xFFFF) : optInt(a, "child");

		JsonObject o = new JsonObject();
		o.addProperty("group", group);
		o.addProperty("group_name", GameNames.group(group));
		JsonArray arr = new JsonArray();
		boolean truncated = false;
		if (child != null)
		{
			Widget w = client.getWidget(group, child);
			if (w == null)
			{
				throw new IllegalArgumentException("no such component " + group + ":" + child + " (not loaded?)");
			}
			truncated = addWidget(arr, w, visibleOnly, withChildren, limit);
		}
		else
		{
			for (int i = 0; i < MAX_COMPONENTS_PER_GROUP; i++)
			{
				Widget w = client.getWidget(group, i);
				if (w == null)
				{
					break;
				}
				if (addWidget(arr, w, visibleOnly, withChildren, limit))
				{
					truncated = true;
					break;
				}
			}
			o.addProperty("loaded", arr.size() > 0 || client.getWidget(group, 0) != null);
		}
		o.add("components", arr);
		o.addProperty("count", arr.size());
		o.addProperty("truncated", truncated);
		return o;
	}

	/** Adds {@code w} (and optionally its children); returns true when the limit was hit. */
	private boolean addWidget(JsonArray out, Widget w, boolean visibleOnly, boolean withChildren, int limit)
	{
		if (!(visibleOnly && w.isHidden()))
		{
			if (out.size() >= limit)
			{
				return true;
			}
			out.add(widgetJson(w));
		}
		if (withChildren)
		{
			for (Widget[] kids : new Widget[][]{w.getStaticChildren(), w.getDynamicChildren()})
			{
				if (kids == null)
				{
					continue;
				}
				for (Widget k : kids)
				{
					if (k == null || (visibleOnly && k.isHidden()))
					{
						continue;
					}
					if (out.size() >= limit)
					{
						return true;
					}
					out.add(widgetJson(k));
				}
			}
		}
		return false;
	}

	private Set<Integer> loadedGroups()
	{
		Set<Integer> groups = new LinkedHashSet<>();
		int top = client.getTopLevelInterfaceId();
		if (top > 0)
		{
			groups.add(top);
		}
		for (WidgetNode n : client.getComponentTable())
		{
			groups.add(n.getId());
		}
		Widget[] roots = client.getWidgetRoots();
		if (roots != null)
		{
			for (Widget r : roots)
			{
				if (r != null)
				{
					groups.add(r.getId() >>> 16);
				}
			}
		}
		return groups;
	}

	private JsonObject findWidgets(JsonObject a)
	{
		String text = strArg(a, "text", "").toLowerCase(Locale.ROOT);
		String action = strArg(a, "action", "").toLowerCase(Locale.ROOT);
		Integer itemId = optInt(a, "item_id");
		boolean visibleOnly = boolArg(a, "visible_only", false);
		int limit = Math.max(1, intArg(a, "limit", 100));
		if (text.isEmpty() && action.isEmpty() && itemId == null)
		{
			throw new IllegalArgumentException("give at least one of text, action, item_id");
		}
		Set<Integer> groups = new LinkedHashSet<>();
		JsonElement ge = a.get("groups");
		if (ge != null && ge.isJsonArray())
		{
			for (JsonElement e : ge.getAsJsonArray())
			{
				groups.add(e.getAsInt());
			}
		}
		else
		{
			groups = loadedGroups();
		}
		JsonArray found = new JsonArray();
		boolean truncated = false;
		outer:
		for (int g : groups)
		{
			for (int i = 0; i < MAX_COMPONENTS_PER_GROUP; i++)
			{
				Widget w = client.getWidget(g, i);
				if (w == null)
				{
					break;
				}
				List<Widget> candidates = new ArrayList<>();
				candidates.add(w);
				addAll(candidates, w.getStaticChildren());
				addAll(candidates, w.getDynamicChildren());
				for (Widget c : candidates)
				{
					if (matches(c, text, action, itemId, visibleOnly))
					{
						if (found.size() >= limit)
						{
							truncated = true;
							break outer;
						}
						found.add(widgetJson(c));
					}
				}
			}
		}
		JsonObject o = new JsonObject();
		o.add("searched_groups", intArray(groups));
		o.add("results", found);
		o.addProperty("count", found.size());
		o.addProperty("truncated", truncated);
		return o;
	}

	private static JsonArray intArray(Set<Integer> values)
	{
		JsonArray arr = new JsonArray();
		for (int v : values)
		{
			arr.add(v);
		}
		return arr;
	}

	private static void addAll(List<Widget> out, Widget[] kids)
	{
		if (kids == null)
		{
			return;
		}
		for (Widget k : kids)
		{
			if (k != null)
			{
				out.add(k);
			}
		}
	}

	private static boolean matches(Widget w, String text, String action, Integer itemId, boolean visibleOnly)
	{
		if (visibleOnly && w.isHidden())
		{
			return false;
		}
		if (itemId != null && w.getItemId() != itemId)
		{
			return false;
		}
		if (!text.isEmpty())
		{
			String t = w.getText() == null ? "" : Text.removeTags(w.getText()).toLowerCase(Locale.ROOT);
			String n = w.getName() == null ? "" : Text.removeTags(w.getName()).toLowerCase(Locale.ROOT);
			if (!t.contains(text) && !n.contains(text))
			{
				return false;
			}
		}
		if (!action.isEmpty())
		{
			boolean any = false;
			String[] acts = w.getActions();
			if (acts != null)
			{
				for (String s : acts)
				{
					if (s != null && Text.removeTags(s).toLowerCase(Locale.ROOT).contains(action))
					{
						any = true;
						break;
					}
				}
			}
			if (!any)
			{
				return false;
			}
		}
		return true;
	}

	// ---- actions -------------------------------------------------------------------------------------------------

	private JsonObject clickWidget(JsonObject a)
	{
		Integer packed = optInt(a, "widget");
		Widget w;
		if (packed != null)
		{
			w = client.getWidget(packed);
		}
		else
		{
			w = client.getWidget(reqInt(a, "group"), reqInt(a, "child"));
		}
		if (w == null)
		{
			throw new IllegalArgumentException("component not found (interface not loaded?)");
		}
		Integer index = optInt(a, "index");
		if (index != null)
		{
			Widget c = w.getChild(index);
			if (c == null)
			{
				throw new IllegalArgumentException("component has no dynamic child " + index);
			}
			w = c;
		}
		String[] actions = w.getActions();
		int op;
		String option = strArg(a, "option", null);
		if (option != null)
		{
			op = -1;
			String want = option.toLowerCase(Locale.ROOT);
			for (int i = 0; actions != null && i < actions.length; i++)
			{
				if (actions[i] != null && Text.removeTags(actions[i]).toLowerCase(Locale.ROOT).contains(want))
				{
					op = i + 1;
					break;
				}
			}
			if (op < 0)
			{
				throw new IllegalArgumentException("no action containing '" + option + "' in "
					+ strings(actions));
			}
		}
		else
		{
			op = intArg(a, "op", 1);
		}
		MenuAction type;
		String override = strArg(a, "action", null);
		if (override != null)
		{
			type = parseMenuAction(override);
		}
		else if (w.isIf3())
		{
			type = op > LAST_NORMAL_PRIORITY_OP ? MenuAction.CC_OP_LOW_PRIORITY : MenuAction.CC_OP;
		}
		else
		{
			// IF1 widgets: WIDGET_FIRST_OPTION..WIDGET_FIFTH_OPTION are consecutive ids (39..43)
			if (op < 1 || op > LAST_NORMAL_PRIORITY_OP)
			{
				throw new IllegalArgumentException("op must be 1..5 for a non-IF3 widget");
			}
			type = MenuAction.of(MenuAction.WIDGET_FIRST_OPTION.getId() + op - 1);
		}
		int p0 = w.getIndex();
		int p1 = w.getId();
		int itemId = intArg(a, "item_id", w.getItemId());
		String optionText = actions != null && op >= 1 && op <= actions.length && actions[op - 1] != null
			? actions[op - 1] : "";
		String target = w.getName() == null ? "" : w.getName();

		JsonObject sent = new JsonObject();
		sent.addProperty("p0", p0);
		sent.addProperty("p1", p1);
		sent.addProperty("action", type.name());
		sent.addProperty("action_id", type.getId());
		sent.addProperty("id", op);
		sent.addProperty("item_id", itemId);
		sent.addProperty("option", optionText);
		sent.addProperty("target", target);
		boolean dry = boolArg(a, "dry_run", false);
		if (!dry)
		{
			client.menuAction(p0, p1, type, op, itemId, optionText, target);
		}
		JsonObject o = new JsonObject();
		o.addProperty("sent", !dry);
		o.add("menu_action", sent);
		o.addProperty("tick", client.getTickCount());
		return o;
	}

	private static MenuAction parseMenuAction(String s)
	{
		try
		{
			MenuAction byId = MenuAction.of(Integer.parseInt(s.trim()));
			if (byId == MenuAction.UNKNOWN)
			{
				throw new IllegalArgumentException("unknown MenuAction id " + s);
			}
			return byId;
		}
		catch (NumberFormatException e)
		{
			try
			{
				return MenuAction.valueOf(s.trim().toUpperCase(Locale.ROOT));
			}
			catch (IllegalArgumentException ex)
			{
				throw new IllegalArgumentException("unknown MenuAction: " + s);
			}
		}
	}

	private JsonObject menuAction(JsonObject a)
	{
		MenuAction type = parseMenuAction(strArg(a, "action", ""));
		int p0 = intArg(a, "p0", 0);
		int p1 = intArg(a, "p1", 0);
		int id = intArg(a, "id", 0);
		int itemId = intArg(a, "item_id", -1);
		String option = strArg(a, "option", "");
		String target = strArg(a, "target", "");
		client.menuAction(p0, p1, type, id, itemId, option, target);
		JsonObject o = new JsonObject();
		o.addProperty("sent", true);
		o.addProperty("action", type.name());
		o.addProperty("tick", client.getTickCount());
		return o;
	}

	private JsonObject walkTo(JsonObject a)
	{
		int x = reqInt(a, "x");
		int y = reqInt(a, "y");
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || client.getGameState() != GameState.LOGGED_IN)
		{
			throw new IllegalStateException("not logged in");
		}
		LocalPoint lp = LocalPoint.fromWorld(wv, x, y);
		if (lp == null)
		{
			throw new IllegalArgumentException("(" + x + ", " + y + ") is outside the loaded scene");
		}
		client.menuAction(lp.getSceneX(), lp.getSceneY(), MenuAction.WALK, 0, -1, "Walk here", "");
		JsonObject o = new JsonObject();
		o.addProperty("sent", true);
		o.addProperty("scene_x", lp.getSceneX());
		o.addProperty("scene_y", lp.getSceneY());
		o.addProperty("tick", client.getTickCount());
		return o;
	}

	// ---- chat ----------------------------------------------------------------------------------------------------

	private ToolResult sendChat(JsonObject a) throws Exception
	{
		String text = strArg(a, "text", "");
		if (text.isEmpty())
		{
			throw new IllegalArgumentException("text is empty");
		}
		String method = strArg(a, "method", text.startsWith("::") ? "keys" : "script");
		JsonObject o = new JsonObject();
		o.addProperty("method", method);
		if ("script".equals(method))
		{
			if (text.startsWith("::"))
			{
				throw new IllegalArgumentException("'::' commands are not handled by the send script; use method keys");
			}
			onClient(() ->
			{
				// Same call as RuneLite's ChatInputManager.sendChatboxInput: args are
				// (text, chatType, clanTarget, 0, -1) for script 5517.
				client.createScriptEventBuilder(ScriptID.CHAT_SEND, text, CHAT_TYPE_PUBLIC, 0, 0, -1)
					.build()
					.setCanSendPackets(true)
					.run();
				return null;
			});
		}
		else if ("keys".equals(method))
		{
			Canvas canvas = onClient(client::getCanvas);
			if (canvas == null)
			{
				throw new IllegalStateException("client canvas not available");
			}
			if (onClient(client::getGameState) != GameState.LOGGED_IN)
			{
				throw new IllegalStateException("not logged in");
			}
			typeKeys(canvas, text, boolArg(a, "press_enter_first", false));
		}
		else
		{
			throw new IllegalArgumentException("method must be 'script' or 'keys'");
		}
		o.addProperty("sent", true);
		o.addProperty("text", text);
		return ToolResult.json(o);
	}

	/** Dispatches key events straight to the canvas listeners, as AWT would for a human typing. */
	private static void typeKeys(Canvas canvas, String text, boolean enterFirst) throws InterruptedException
	{
		if (enterFirst)
		{
			key(canvas, KeyEvent.VK_ENTER, '\n');
		}
		for (char c : text.toCharArray())
		{
			int code = KeyEvent.getExtendedKeyCodeForChar(c);
			int mods = Character.isUpperCase(c) || ":~!@#$%^&*()_+{}|\"<>?".indexOf(c) >= 0
				? KeyEvent.SHIFT_DOWN_MASK : 0;
			long now = System.currentTimeMillis();
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, mods, code, c));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_TYPED, now, mods, KeyEvent.VK_UNDEFINED, c));
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, mods, code, c));
			Thread.sleep(KEY_GAP_MS);
		}
		key(canvas, KeyEvent.VK_ENTER, '\n');
	}

	private static void key(Canvas canvas, int code, char c) throws InterruptedException
	{
		long now = System.currentTimeMillis();
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, 0, code, c));
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_TYPED, now, 0, KeyEvent.VK_UNDEFINED, c));
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, now, 0, code, c));
		Thread.sleep(KEY_GAP_MS);
	}

	// ---- logs ----------------------------------------------------------------------------------------------------

	private JsonObject messages(JsonObject a)
	{
		int limit = Math.max(1, Math.min(500, intArg(a, "limit", 50)));
		long since = optInt(a, "since_seq") == null ? 0 : optInt(a, "since_seq");
		JsonObject o = new JsonObject();
		o.add("messages", chat.query(since, stringSet(a, "types"), limit));
		o.addProperty("latest_seq", chat.latestSeq());
		return o;
	}

	private JsonObject eventsTool(JsonObject a)
	{
		int limit = Math.max(1, Math.min(5000, intArg(a, "limit", 200)));
		long since = optInt(a, "since_seq") == null ? 0 : optInt(a, "since_seq");
		JsonObject o = new JsonObject();
		o.add("events", events.query(since, stringSet(a, "types"), limit));
		o.addProperty("latest_seq", events.latestSeq());
		return o;
	}

	private JsonObject varps(JsonObject a)
	{
		JsonObject o = new JsonObject();
		JsonElement ve = a.get("varps");
		if (ve != null && ve.isJsonArray())
		{
			JsonObject m = new JsonObject();
			for (JsonElement e : ve.getAsJsonArray())
			{
				m.addProperty(String.valueOf(e.getAsInt()), client.getVarpValue(e.getAsInt()));
			}
			o.add("varps", m);
		}
		JsonElement be = a.get("varbits");
		if (be != null && be.isJsonArray())
		{
			JsonObject m = new JsonObject();
			for (JsonElement e : be.getAsJsonArray())
			{
				m.addProperty(String.valueOf(e.getAsInt()), client.getVarbitValue(e.getAsInt()));
			}
			o.add("varbits", m);
		}
		if (boolArg(a, "all_nonzero", false))
		{
			JsonObject m = new JsonObject();
			int[] all = client.getVarps();
			for (int i = 0; all != null && i < all.length; i++)
			{
				if (all[i] != 0)
				{
					m.addProperty(String.valueOf(i), all[i]);
				}
			}
			o.add("nonzero_varps", m);
			o.addProperty("varp_count", all == null ? 0 : all.length);
		}
		return o;
	}

	// ---- screenshot ----------------------------------------------------------------------------------------------

	private ToolResult screenshot(JsonObject a) throws Exception
	{
		CompletableFuture<Image> frame = new CompletableFuture<>();
		drawManager.requestNextFrameListener(frame::complete);
		Image img;
		try
		{
			img = frame.get(FRAME_TIMEOUT_MS, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e)
		{
			throw new IllegalStateException("no frame rendered within " + FRAME_TIMEOUT_MS
				+ " ms (client minimised?)");
		}
		BufferedImage src = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_RGB);
		Graphics2D g = src.createGraphics();
		g.drawImage(img, 0, 0, null);
		g.dispose();

		double scale = a.has("scale") && !a.get("scale").isJsonNull() ? a.get("scale").getAsDouble() : 1.0;
		scale = Math.max(0.05, Math.min(1.0, scale));
		int maxWidth = intArg(a, "max_width", 0);
		int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
		if (maxWidth > 0 && w > maxWidth)
		{
			w = maxWidth;
		}
		int h = Math.max(1, (int) Math.round(src.getHeight() * ((double) w / src.getWidth())));
		BufferedImage out = src;
		if (w != src.getWidth())
		{
			out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
			Graphics2D og = out.createGraphics();
			og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			og.drawImage(src, 0, 0, w, h, null);
			og.dispose();
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bytes);
		return new ToolResult()
			.addImage(Base64.getEncoder().encodeToString(bytes.toByteArray()), "image/png")
			.addText("{\"width\":" + out.getWidth() + ",\"height\":" + out.getHeight() + ",\"source_width\":"
				+ src.getWidth() + ",\"source_height\":" + src.getHeight() + "}");
	}
}
