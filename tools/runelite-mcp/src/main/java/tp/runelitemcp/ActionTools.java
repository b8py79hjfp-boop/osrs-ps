package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Canvas;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.Text;

/**
 * Higher-level tools for driving the client in a play-through: act on things by name and option text, use items on
 * things, read and answer dialogues, type into input prompts, press keys, log in and out, and wait for conditions.
 * Every action goes through {@code client.menuAction} or canvas key events, so the server sees exactly what a player's
 * click or key press sends.
 */
final class ActionTools
{
	private static final long CLIENT_THREAD_TIMEOUT_MS = 5000;
	private static final int SCENE_SIZE = 104;
	private static final int DEFAULT_RADIUS = 20;
	private static final int MAX_RADIUS = 50;
	private static final long KEY_GAP_MS = 8;
	/** Inventory side panel items (interface 149, component 0): the widget for use-item and inventory ops. */
	private static final int INVENTORY_ITEMS_WIDGET = 149 << 16;
	private static final int INVENTORY_CONTAINER = 93;
	/** Chatbox dialogue interfaces (gameval names): NPC chat, player chat, options, message box, item/double/level-up boxes. */
	private static final int[] DIALOG_GROUPS = {231, 217, 219, 229, 193, 11, 233, 162};
	private static final int OPTIONS_GROUP = 219;
	/** Upper bound and stop rule when walking a group's component ids (dialogue groups have well under 100). */
	private static final int MAX_GROUP_COMPONENTS = 600;
	private static final int MISSING_RUN = 8;
	private static final long POLL_MS = 100;
	/** The ground ops of an item without its own (OSRS default): op 3 "Take". */
	private static final String[] DEFAULT_GROUND_ACTIONS = {null, null, "Take", null, null};
	private static final long DEFAULT_WAIT_MS = 10_000;
	private static final long MAX_WAIT_MS = 120_000;

	private static final MenuAction[] NPC_OPS = {MenuAction.NPC_FIRST_OPTION, MenuAction.NPC_SECOND_OPTION,
		MenuAction.NPC_THIRD_OPTION, MenuAction.NPC_FOURTH_OPTION, MenuAction.NPC_FIFTH_OPTION};
	private static final MenuAction[] OBJECT_OPS = {MenuAction.GAME_OBJECT_FIRST_OPTION,
		MenuAction.GAME_OBJECT_SECOND_OPTION, MenuAction.GAME_OBJECT_THIRD_OPTION, MenuAction.GAME_OBJECT_FOURTH_OPTION,
		MenuAction.GAME_OBJECT_FIFTH_OPTION};
	private static final MenuAction[] GROUND_OPS = {MenuAction.GROUND_ITEM_FIRST_OPTION,
		MenuAction.GROUND_ITEM_SECOND_OPTION, MenuAction.GROUND_ITEM_THIRD_OPTION, MenuAction.GROUND_ITEM_FOURTH_OPTION,
		MenuAction.GROUND_ITEM_FIFTH_OPTION};
	private static final MenuAction[] PLAYER_OPS = {MenuAction.PLAYER_FIRST_OPTION, MenuAction.PLAYER_SECOND_OPTION,
		MenuAction.PLAYER_THIRD_OPTION, MenuAction.PLAYER_FOURTH_OPTION, MenuAction.PLAYER_FIFTH_OPTION,
		MenuAction.PLAYER_SIXTH_OPTION, MenuAction.PLAYER_SEVENTH_OPTION, MenuAction.PLAYER_EIGHTH_OPTION};

	private final Client client;
	private final ClientThread clientThread;
	private final EventLog chat;

	ActionTools(Client client, ClientThread clientThread, EventLog chat)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chat = chat;
	}

	List<ToolDef> build()
	{
		List<ToolDef> t = new ArrayList<>();
		t.add(new ToolDef("interact",
			"Act on the nearest NPC, object, ground item or player by name (or id) and option text, like a right-click "
				+ "menu choice: e.g. {name:'Banker', option:'Bank'}, {name:'Grouping Board', option:'Inspect'}, "
				+ "{kind:'ground_item', name:'Bones', option:'Take'}. Without option the first action is used. The player "
				+ "walks there first, as with a real click.",
			Schema.object()
				.string("name", "Case-insensitive substring of the target's name (exact match preferred).")
				.integer("id", "NPC/object/item id instead of a name.")
				.string("kind", "npc, object, ground_item or player. Default: search all kinds.")
				.string("option", "Option text, case-insensitive (exact match preferred, then substring).")
				.integer("op", "1-based op slot instead of option text (e.g. a ground item's op 4).")
				.integer("x", "Only the target on this world tile (with y).")
				.integer("y", "Only the target on this world tile (with x).")
				.integer("radius", "Search radius in tiles, default " + DEFAULT_RADIUS + ", max " + MAX_RADIUS + ".")
				.bool("dry_run", "Only report the target and the menu action.")
				.build(),
			a -> ToolResult.json(onClient(() -> interact(a)))));
		t.add(new ToolDef("use_item_on",
			"Use an inventory item on something (the 'Use' then click flow): another inventory item (target_item_id or "
				+ "target_slot), or the nearest NPC/object/ground item/player by name or id (target_name/target_id, "
				+ "target_kind).",
			Schema.object()
				.integer("item_id", "Inventory item to use (first slot holding it).")
				.integer("slot", "Inventory slot to use instead of item_id.")
				.integer("target_item_id", "Inventory item to use it on.")
				.integer("target_slot", "Inventory slot to use it on.")
				.string("target_name", "Name of the NPC/object/ground item/player to use it on.")
				.integer("target_id", "Id of the NPC/object/ground item to use it on.")
				.string("target_kind", "npc, object, ground_item or player. Default: search all kinds.")
				.integer("radius", "Search radius, default " + DEFAULT_RADIUS + ".")
				.build(),
			this::useItemOn));
		t.add(new ToolDef("get_dialog",
			"The open chatbox dialogue, if any: speaker, text, the numbered options of an options menu, whether "
				+ "'Click here to continue' is shown, and any open input prompt (amount / text).",
			Schema.object().build(),
			a -> ToolResult.json(onClient(this::dialog))));
		t.add(new ToolDef("continue_dialog",
			"Answer the open dialogue: without arguments press 'Click here to continue' (space); with option (1-based "
				+ "number or option text) choose that line of an options menu.",
			Schema.object()
				.string("option", "Option number (1-based) or a case-insensitive substring of the option text.")
				.build(),
			this::continueDialog));
		t.add(new ToolDef("type_input",
			"Type into an open input prompt (an amount, a name, a search box) and press Enter: e.g. after 'Enter "
				+ "amount:'. Not for '::' commands (use run_command).",
			Schema.object()
				.string("text", "Text or number to type.")
				.bool("enter", "Press Enter afterwards. Default true.")
				.require("text").build(),
			this::typeInput));
		t.add(new ToolDef("press_key",
			"Press one key on the game canvas: a key name (ESCAPE, ENTER, SPACE, BACK_SPACE, TAB, UP, DOWN, LEFT, "
				+ "RIGHT, F1..F12, DELETE, HOME, END, PAGE_UP, PAGE_DOWN) or a single character. Arrow keys can be held "
				+ "for hold_ms (camera).",
			Schema.object()
				.string("key", "Key name or one character.")
				.integer("hold_ms", "Hold the key this long before releasing. Default 0.")
				.require("key").build(),
			this::pressKey));
		t.add(new ToolDef("login",
			"From the login screen: log in with username and password (legacy login), then wait for LOGGED_IN.",
			Schema.object()
				.string("username", "Account name.")
				.string("password", "Password.")
				.integer("timeout_ms", "How long to wait for LOGGED_IN. Default 20000.")
				.require("username", "password").build(),
			this::login));
		t.add(new ToolDef("logout",
			"Log out with the logout tab's button (the same click a player makes), then wait for the login screen.",
			Schema.object().integer("timeout_ms", "How long to wait. Default 10000.").build(),
			this::logout));
		t.add(new ToolDef("wait",
			"Wait for game ticks or a condition, then return the state that ended the wait. Conditions (any given must "
				+ "all hold): message (a new chat/game message containing this), widget_group (interface loaded and "
				+ "visible), x+y (player on this tile), idle (no animation and not moving), varp/varbit with value. "
				+ "Returns timed_out=true if the time ran out.",
			Schema.object()
				.integer("ticks", "Wait this many game ticks (0.6 s each).")
				.string("message", "Substring of a new chat/game message (tags removed, case-insensitive).")
				.integer("widget_group", "Interface group id that must be open and visible.")
				.integer("x", "Player world x (with y).")
				.integer("y", "Player world y (with x).")
				.bool("idle", "Player not animating and not moving.")
				.integer("varp", "Varp id (with value).")
				.integer("varbit", "Varbit id (with value).")
				.integer("value", "Expected varp/varbit value.")
				.integer("timeout_ms", "Default 10000, max 120000.")
				.build(),
			this::waitFor));
		return t;
	}

	// ---- interact ------------------------------------------------------------------------------------------------

	/** A resolved menu entry for a scene target. */
	private static final class Target
	{
		String kind;
		String name;
		int id;
		int p0;
		int p1;
		int menuId;
		String[] actions;
		MenuAction[] ops;
		WorldPoint where;
		int distance;
	}

	private JsonObject interact(JsonObject a)
	{
		Target t = find(a, "name", "id", "kind");
		String option = strArg(a, "option", null);
		Integer slot = optInt(a, "op");
		int op = slot != null ? slot - 1 : chooseOp(t.actions, option);
		if (op < 0 || op >= t.ops.length)
		{
			throw new IllegalArgumentException(t.kind + " '" + t.name + "' has no option "
				+ (option == null ? "" : "'" + option + "' ") + "among " + actionsJson(t.actions));
		}
		MenuAction type = t.ops[op];
		String opText = t.actions != null && op < t.actions.length && t.actions[op] != null ? t.actions[op] : "";
		JsonObject sent = describe(t, type, opText);
		if (!boolArg(a, "dry_run", false))
		{
			client.menuAction(t.p0, t.p1, type, t.menuId, -1, opText, t.name);
		}
		sent.addProperty("sent", !boolArg(a, "dry_run", false));
		sent.addProperty("tick", client.getTickCount());
		return sent;
	}

	private JsonObject describe(Target t, MenuAction type, String opText)
	{
		JsonObject o = new JsonObject();
		o.addProperty("kind", t.kind);
		o.addProperty("name", t.name);
		o.addProperty("id", t.id);
		o.addProperty("x", t.where.getX());
		o.addProperty("y", t.where.getY());
		o.addProperty("plane", t.where.getPlane());
		o.addProperty("distance", t.distance);
		o.addProperty("option", opText);
		o.addProperty("action", type.name());
		o.add("actions", actionsJson(t.actions));
		return o;
	}

	/** Index of [option] in [actions]: exact (tags removed) first, then substring; the first non-null if none given. */
	private static int chooseOp(String[] actions, String option)
	{
		if (actions == null)
		{
			return -1;
		}
		if (option == null)
		{
			for (int i = 0; i < actions.length; i++)
			{
				if (actions[i] != null && !actions[i].isEmpty())
				{
					return i;
				}
			}
			return -1;
		}
		String want = option.toLowerCase(Locale.ROOT).trim();
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && Text.removeTags(actions[i]).toLowerCase(Locale.ROOT).equals(want))
			{
				return i;
			}
		}
		for (int i = 0; i < actions.length; i++)
		{
			if (actions[i] != null && Text.removeTags(actions[i]).toLowerCase(Locale.ROOT).contains(want))
			{
				return i;
			}
		}
		return -1;
	}

	/** The nearest target matching the name/id/kind arguments (keys given, so use_item_on can reuse this). */
	private Target find(JsonObject a, String nameKey, String idKey, String kindKey)
	{
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || wv == null || client.getGameState() != GameState.LOGGED_IN)
		{
			throw new IllegalStateException("not logged in");
		}
		String name = strArg(a, nameKey, null);
		Integer id = optInt(a, idKey);
		if (name == null && id == null)
		{
			throw new IllegalArgumentException("give " + nameKey + " or " + idKey);
		}
		String want = name == null ? null : name.toLowerCase(Locale.ROOT).trim();
		String kind = strArg(a, kindKey, null);
		Integer tx = optInt(a, "x");
		Integer ty = optInt(a, "y");
		int radius = Math.max(1, Math.min(MAX_RADIUS, intArg(a, "radius", DEFAULT_RADIUS)));
		WorldPoint here = me.getWorldLocation();
		List<Target> found = new ArrayList<>();

		if (kind == null || kind.equals("npc"))
		{
			for (NPC npc : wv.npcs())
			{
				if (npc == null)
				{
					continue;
				}
				NPCComposition comp = npc.getTransformedComposition();
				if (comp == null)
				{
					continue;
				}
				Target t = new Target();
				t.kind = "npc";
				t.name = comp.getName();
				t.id = comp.getId();
				t.menuId = npc.getIndex();
				t.actions = comp.getActions();
				t.ops = NPC_OPS;
				t.where = npc.getWorldLocation();
				found.add(t);
			}
		}
		if (kind == null || kind.equals("player"))
		{
			String[] options = client.getPlayerOptions();
			for (Player p : wv.players())
			{
				if (p == null || p == me)
				{
					continue;
				}
				Target t = new Target();
				t.kind = "player";
				t.name = p.getName();
				t.id = p.getId();
				t.menuId = p.getId();
				t.actions = options;
				t.ops = PLAYER_OPS;
				t.where = p.getWorldLocation();
				found.add(t);
			}
		}
		if (kind == null || kind.equals("object") || kind.equals("ground_item"))
		{
			Scene scene = wv.getScene();
			Tile[][] tiles = scene.getTiles()[wv.getPlane()];
			LocalPoint lp = me.getLocalLocation();
			for (int x = Math.max(0, lp.getSceneX() - radius); x <= Math.min(SCENE_SIZE - 1, lp.getSceneX() + radius); x++)
			{
				for (int y = Math.max(0, lp.getSceneY() - radius); y <= Math.min(SCENE_SIZE - 1, lp.getSceneY() + radius); y++)
				{
					Tile tile = tiles[x][y];
					if (tile == null)
					{
						continue;
					}
					if (kind == null || kind.equals("object"))
					{
						addObjects(found, tile);
					}
					if (kind == null || kind.equals("ground_item"))
					{
						addItems(found, tile);
					}
				}
			}
		}

		Target best = null;
		boolean bestExact = false;
		for (Target t : found)
		{
			if (t.name == null || t.where == null || t.where.getPlane() != here.getPlane())
			{
				continue;
			}
			if (id != null && t.id != id)
			{
				continue;
			}
			String n = Text.removeTags(t.name).toLowerCase(Locale.ROOT);
			boolean exact = want == null || n.equals(want);
			if (want != null && !exact && !n.contains(want))
			{
				continue;
			}
			if (tx != null && ty != null && (t.where.getX() != tx || t.where.getY() != ty))
			{
				continue;
			}
			t.distance = Math.max(Math.abs(t.where.getX() - here.getX()), Math.abs(t.where.getY() - here.getY()));
			if (t.distance > radius)
			{
				continue;
			}
			if (best == null || (exact && !bestExact) || (exact == bestExact && t.distance < best.distance))
			{
				best = t;
				bestExact = exact;
			}
		}
		if (best == null)
		{
			throw new IllegalArgumentException("nothing named/with id " + (name != null ? "'" + name + "'" : id)
				+ (kind == null ? "" : " of kind " + kind) + " within " + radius + " tiles");
		}
		if (best.kind.equals("npc") || best.kind.equals("player"))
		{
			best.p0 = 0;
			best.p1 = 0;
		}
		return best;
	}

	private void addObjects(List<Target> out, Tile tile)
	{
		List<TileObject> objs = new ArrayList<>();
		GameObject[] gos = tile.getGameObjects();
		if (gos != null)
		{
			for (GameObject go : gos)
			{
				// A multi-tile object appears on each of its tiles; keep it once, on its south-west tile.
				if (go != null && go.getSceneMinLocation() != null && go.getSceneMinLocation().equals(tile.getSceneLocation()))
				{
					objs.add(go);
				}
			}
		}
		if (tile.getWallObject() != null)
		{
			objs.add(tile.getWallObject());
		}
		if (tile.getGroundObject() != null)
		{
			objs.add(tile.getGroundObject());
		}
		if (tile.getDecorativeObject() != null)
		{
			objs.add(tile.getDecorativeObject());
		}
		for (TileObject o : objs)
		{
			ObjectComposition comp = client.getObjectDefinition(o.getId());
			if (comp == null)
			{
				continue;
			}
			ObjectComposition shown = comp.getImpostorIds() != null && comp.getImpostor() != null ? comp.getImpostor() : comp;
			if (shown.getName() == null || shown.getName().equals("null"))
			{
				continue;
			}
			Target t = new Target();
			t.kind = "object";
			t.name = shown.getName();
			t.id = o.getId();
			t.menuId = o.getId();
			t.actions = shown.getActions();
			t.ops = OBJECT_OPS;
			t.where = o.getWorldLocation();
			t.p0 = tile.getSceneLocation().getX();
			t.p1 = tile.getSceneLocation().getY();
			out.add(t);
		}
	}

	private void addItems(List<Target> out, Tile tile)
	{
		List<TileItem> items = tile.getGroundItems();
		if (items == null)
		{
			return;
		}
		for (TileItem ti : items)
		{
			ItemComposition comp = client.getItemDefinition(ti.getId());
			Target t = new Target();
			t.kind = "ground_item";
			t.name = comp.getName();
			t.id = ti.getId();
			t.menuId = ti.getId();
			// RuneLite 1.13.6 has no ground-op accessor: the default ground ops are "Take" in slot 3 (use op for others).
			t.actions = DEFAULT_GROUND_ACTIONS;
			t.ops = GROUND_OPS;
			t.where = tile.getWorldLocation();
			t.p0 = tile.getSceneLocation().getX();
			t.p1 = tile.getSceneLocation().getY();
			out.add(t);
		}
	}

	// ---- use item on -----------------------------------------------------------------------------------------------

	private ToolResult useItemOn(JsonObject a) throws Exception
	{
		int[] used = onClient(() -> inventorySlot(optInt(a, "slot"), optInt(a, "item_id")));
		JsonObject result = new JsonObject();
		result.addProperty("slot", used[0]);
		result.addProperty("item_id", used[1]);
		// Step 1: select the item ("Use"), as the inventory op WIDGET_TARGET does.
		onClient(() ->
		{
			client.menuAction(used[0], INVENTORY_ITEMS_WIDGET, MenuAction.WIDGET_TARGET, 0, used[1], "Use", "");
			return null;
		});
		Thread.sleep(POLL_MS);
		// Step 2: click the target while the item is selected.
		JsonObject target = onClient(() ->
		{
			JsonObject o = new JsonObject();
			if (a.has("target_item_id") || a.has("target_slot"))
			{
				int[] on = inventorySlot(optInt(a, "target_slot"), optInt(a, "target_item_id"));
				client.menuAction(on[0], INVENTORY_ITEMS_WIDGET, MenuAction.WIDGET_TARGET_ON_WIDGET, 0, on[1], "Use", "");
				o.addProperty("kind", "item");
				o.addProperty("slot", on[0]);
				o.addProperty("item_id", on[1]);
				return o;
			}
			Target t = find(a, "target_name", "target_id", "target_kind");
			MenuAction type;
			switch (t.kind)
			{
				case "npc":
					type = MenuAction.WIDGET_TARGET_ON_NPC;
					break;
				case "player":
					type = MenuAction.WIDGET_TARGET_ON_PLAYER;
					break;
				case "ground_item":
					type = MenuAction.WIDGET_TARGET_ON_GROUND_ITEM;
					break;
				default:
					type = MenuAction.WIDGET_TARGET_ON_GAME_OBJECT;
			}
			client.menuAction(t.p0, t.p1, type, t.menuId, -1, "Use", t.name);
			return describe(t, type, "Use");
		});
		result.add("target", target);
		result.addProperty("sent", true);
		return ToolResult.json(result);
	}

	/** {slot, itemId} of [slot] or of the first slot holding [itemId]. */
	private int[] inventorySlot(Integer slot, Integer itemId)
	{
		ItemContainer inv = client.getItemContainer(INVENTORY_CONTAINER);
		if (inv == null)
		{
			throw new IllegalStateException("inventory not loaded");
		}
		Item[] items = inv.getItems();
		if (slot != null)
		{
			if (slot < 0 || slot >= items.length || items[slot].getId() <= 0)
			{
				throw new IllegalArgumentException("inventory slot " + slot + " is empty");
			}
			return new int[]{slot, items[slot].getId()};
		}
		if (itemId == null)
		{
			throw new IllegalArgumentException("give slot or item_id");
		}
		for (int i = 0; i < items.length; i++)
		{
			if (items[i].getId() == itemId)
			{
				return new int[]{i, itemId};
			}
		}
		throw new IllegalArgumentException("item " + itemId + " is not in the inventory");
	}

	// ---- dialogues -------------------------------------------------------------------------------------------------

	private JsonObject dialog()
	{
		JsonObject o = new JsonObject();
		JsonArray texts = new JsonArray();
		JsonArray options = new JsonArray();
		boolean canContinue = false;
		JsonArray groups = new JsonArray();
		for (int g : DIALOG_GROUPS)
		{
			// Every component of the group (the root layer is not always component 0: 219's is 1).
			List<Widget> all = groupWidgets(g);
			boolean visible = false;
			for (Widget w : all)
			{
				visible |= !w.isHidden();
			}
			if (!visible)
			{
				continue;
			}
			if (g != 162)
			{
				groups.add(g);
			}
			for (Widget w : all)
			{
				if (w.isHidden() || w.getText() == null)
				{
					continue;
				}
				String text = Text.removeTags(w.getText().replace("<br>", " ")).trim();
				if (text.isEmpty())
				{
					continue;
				}
				if (text.toLowerCase(Locale.ROOT).contains("click here to continue"))
				{
					canContinue = true;
					continue;
				}
				if (g == OPTIONS_GROUP && w.getId() == (OPTIONS_GROUP << 16 | 1) && w.getIndex() > 0)
				{
					options.add(text);
				}
				else if (g != 162 || w.getId() == (162 << 16 | 42) || text.endsWith(":") || text.endsWith("*"))
				{
					// The chatbox (162) only for its prompt/input lines (amount, name, search).
					texts.add(text);
				}
			}
		}
		o.add("open_dialog_groups", groups);
		o.add("text", texts);
		o.add("options", options);
		o.addProperty("can_continue", canContinue);
		return o;
	}

	/** All components of [group] with their children, walking component ids until a run of missing ones. */
	private List<Widget> groupWidgets(int group)
	{
		List<Widget> out = new ArrayList<>();
		int missing = 0;
		for (int c = 0; c < MAX_GROUP_COMPONENTS && missing < MISSING_RUN; c++)
		{
			Widget w = client.getWidget(group, c);
			if (w == null)
			{
				missing++;
				continue;
			}
			missing = 0;
			collect(out, w, group);
		}
		return out;
	}

	private static void collect(List<Widget> out, Widget w, int group)
	{
		if (w == null)
		{
			return;
		}
		out.add(w);
		addKids(out, w.getStaticChildren(), group);
		addKids(out, w.getDynamicChildren(), group);
		addKids(out, w.getNestedChildren(), group);
	}

	private static void addKids(List<Widget> out, Widget[] kids, int group)
	{
		if (kids == null)
		{
			return;
		}
		for (Widget k : kids)
		{
			if (k != null && !out.contains(k))
			{
				collect(out, k, group);
			}
		}
	}

	private ToolResult continueDialog(JsonObject a) throws Exception
	{
		String option = strArg(a, "option", null);
		JsonObject before = onClient(this::dialog);
		JsonObject o = new JsonObject();
		o.add("dialog", before);
		if (option == null)
		{
			if (!before.get("can_continue").getAsBoolean())
			{
				throw new IllegalStateException("no 'Click here to continue' is shown");
			}
			pressOnCanvas(KeyEvent.VK_SPACE, ' ', 0);
			o.addProperty("pressed", "SPACE");
			return ToolResult.json(o);
		}
		JsonArray options = before.getAsJsonArray("options");
		int n;
		try
		{
			n = Integer.parseInt(option.trim());
		}
		catch (NumberFormatException e)
		{
			n = -1;
			String want = option.toLowerCase(Locale.ROOT);
			for (int i = 0; i < options.size(); i++)
			{
				if (options.get(i).getAsString().toLowerCase(Locale.ROOT).contains(want))
				{
					n = i + 1;
					break;
				}
			}
		}
		if (n < 1 || n > options.size())
		{
			throw new IllegalArgumentException("no option '" + option + "' among " + options);
		}
		char c = (char) ('0' + n);
		pressOnCanvas(KeyEvent.getExtendedKeyCodeForChar(c), c, 0);
		o.addProperty("chose", options.get(n - 1).getAsString());
		o.addProperty("pressed", String.valueOf(c));
		return ToolResult.json(o);
	}

	// ---- keys ------------------------------------------------------------------------------------------------------

	private ToolResult typeInput(JsonObject a) throws Exception
	{
		String text = strArg(a, "text", "");
		Canvas canvas = canvas();
		for (char c : text.toCharArray())
		{
			int mods = Character.isUpperCase(c) || ":~!@#$%^&*()_+{}|\"<>?".indexOf(c) >= 0 ? KeyEvent.SHIFT_DOWN_MASK : 0;
			dispatch(canvas, KeyEvent.getExtendedKeyCodeForChar(c), c, mods, 0);
		}
		if (boolArg(a, "enter", true))
		{
			dispatch(canvas, KeyEvent.VK_ENTER, '\n', 0, 0);
		}
		JsonObject o = new JsonObject();
		o.addProperty("typed", text);
		return ToolResult.json(o);
	}

	private ToolResult pressKey(JsonObject a) throws Exception
	{
		String key = strArg(a, "key", "");
		int hold = Math.max(0, Math.min(5000, intArg(a, "hold_ms", 0)));
		int code;
		char c = KeyEvent.CHAR_UNDEFINED;
		if (key.length() == 1)
		{
			c = key.charAt(0);
			code = KeyEvent.getExtendedKeyCodeForChar(c);
		}
		else
		{
			try
			{
				code = KeyEvent.class.getField("VK_" + key.toUpperCase(Locale.ROOT)).getInt(null);
			}
			catch (NoSuchFieldException e)
			{
				throw new IllegalArgumentException("unknown key '" + key + "'");
			}
			if (code == KeyEvent.VK_ENTER)
			{
				c = '\n';
			}
			else if (code == KeyEvent.VK_SPACE)
			{
				c = ' ';
			}
			else if (code == KeyEvent.VK_ESCAPE)
			{
				c = 27;
			}
			else if (code == KeyEvent.VK_BACK_SPACE)
			{
				c = '\b';
			}
		}
		pressOnCanvas(code, c, hold);
		JsonObject o = new JsonObject();
		o.addProperty("pressed", key);
		return ToolResult.json(o);
	}

	private void pressOnCanvas(int code, char c, int holdMs) throws Exception
	{
		dispatch(canvas(), code, c, 0, holdMs);
	}

	private Canvas canvas() throws Exception
	{
		Canvas canvas = onClient(client::getCanvas);
		if (canvas == null)
		{
			throw new IllegalStateException("client canvas not available");
		}
		return canvas;
	}

	private static void dispatch(Canvas canvas, int code, char c, int mods, int holdMs) throws InterruptedException
	{
		long now = System.currentTimeMillis();
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_PRESSED, now, mods, code, c));
		if (c != KeyEvent.CHAR_UNDEFINED)
		{
			canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_TYPED, now, mods, KeyEvent.VK_UNDEFINED, c));
		}
		if (holdMs > 0)
		{
			Thread.sleep(holdMs);
		}
		canvas.dispatchEvent(new KeyEvent(canvas, KeyEvent.KEY_RELEASED, System.currentTimeMillis(), mods, code, c));
		Thread.sleep(KEY_GAP_MS);
	}

	// ---- login / logout ------------------------------------------------------------------------------------------

	private ToolResult login(JsonObject a) throws Exception
	{
		String user = strArg(a, "username", "");
		String pass = strArg(a, "password", "");
		long timeout = Math.max(1000, Math.min(MAX_WAIT_MS, intArg(a, "timeout_ms", 20_000)));
		GameState state = onClient(client::getGameState);
		if (state == GameState.LOGGED_IN)
		{
			throw new IllegalStateException("already logged in");
		}
		if (state != GameState.LOGIN_SCREEN)
		{
			throw new IllegalStateException("not on the login screen (" + state + ")");
		}
		onClient(() ->
		{
			client.setUsername(user);
			client.setPassword(pass);
			client.setGameState(GameState.LOGGING_IN);
			return null;
		});
		GameState end = waitState(GameState.LOGGED_IN, timeout);
		JsonObject o = new JsonObject();
		o.addProperty("game_state", end.name());
		o.addProperty("logged_in", end == GameState.LOGGED_IN);
		if (end != GameState.LOGGED_IN)
		{
			o.addProperty("hint", "still " + end + ": check the login screen message (screenshot)");
		}
		return ToolResult.json(o);
	}

	private ToolResult logout(JsonObject a) throws Exception
	{
		long timeout = Math.max(1000, Math.min(MAX_WAIT_MS, intArg(a, "timeout_ms", 10_000)));
		JsonObject clicked = onClient(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				throw new IllegalStateException("not logged in");
			}
			// The logout tab (182) and the world switcher's logout (69) both have a "Logout" op.
			for (int g : new int[]{182, 69})
			{
				Widget root = client.getWidget(g, 0);
				if (root == null)
				{
					continue;
				}
				List<Widget> all = new ArrayList<>();
				collect(all, root, g);
				for (Widget w : all)
				{
					String[] acts = w.getActions();
					for (int i = 0; acts != null && i < acts.length; i++)
					{
						if (acts[i] != null && Text.removeTags(acts[i]).equalsIgnoreCase("Logout"))
						{
							client.menuAction(w.getIndex(), w.getId(), MenuAction.CC_OP, i + 1, -1, acts[i], "");
							JsonObject o = new JsonObject();
							o.addProperty("widget", w.getId());
							return o;
						}
					}
				}
			}
			throw new IllegalStateException("no Logout button loaded");
		});
		GameState end = waitState(GameState.LOGIN_SCREEN, timeout);
		clicked.addProperty("game_state", end.name());
		clicked.addProperty("logged_out", end == GameState.LOGIN_SCREEN);
		return ToolResult.json(clicked);
	}

	private GameState waitState(GameState want, long timeoutMs) throws Exception
	{
		long end = System.currentTimeMillis() + timeoutMs;
		GameState s = onClient(client::getGameState);
		while (s != want && System.currentTimeMillis() < end)
		{
			Thread.sleep(POLL_MS * 2);
			s = onClient(client::getGameState);
		}
		return s;
	}

	// ---- wait ------------------------------------------------------------------------------------------------------

	private ToolResult waitFor(JsonObject a) throws Exception
	{
		long timeout = Math.max(POLL_MS, Math.min(MAX_WAIT_MS, intArg(a, "timeout_ms", (int) DEFAULT_WAIT_MS)));
		Integer ticks = optInt(a, "ticks");
		String message = strArg(a, "message", null);
		Integer group = optInt(a, "widget_group");
		Integer x = optInt(a, "x");
		Integer y = optInt(a, "y");
		boolean idle = boolArg(a, "idle", false);
		Integer varp = optInt(a, "varp");
		Integer varbit = optInt(a, "varbit");
		Integer value = optInt(a, "value");
		int startTick = onClient(client::getTickCount);
		long startSeq = chat.latestSeq();
		long deadline = System.currentTimeMillis() + timeout;
		String matched = null;
		while (true)
		{
			if (message != null && matched == null)
			{
				String want = message.toLowerCase(Locale.ROOT);
				for (JsonElement e : chat.query(startSeq, null, 500))
				{
					JsonObject m = e.getAsJsonObject();
					String text = m.has("text") ? m.get("text").getAsString() : "";
					if (text.toLowerCase(Locale.ROOT).contains(want))
					{
						matched = text;
						break;
					}
				}
			}
			final String matchedNow = matched;
			boolean done = onClient(() ->
			{
				if (ticks != null && client.getTickCount() - startTick < ticks)
				{
					return false;
				}
				if (message != null && matchedNow == null)
				{
					return false;
				}
				if (group != null)
				{
					Widget w = client.getWidget(group, 0);
					if (w == null || w.isHidden())
					{
						return false;
					}
				}
				Player me = client.getLocalPlayer();
				if (x != null && y != null)
				{
					if (me == null || me.getWorldLocation().getX() != x || me.getWorldLocation().getY() != y)
					{
						return false;
					}
				}
				if (idle && (me == null || me.getAnimation() != -1 || me.getPoseAnimation() != me.getIdlePoseAnimation()))
				{
					return false;
				}
				if (varp != null && value != null && client.getVarpValue(varp) != value)
				{
					return false;
				}
				return varbit == null || value == null || client.getVarbitValue(varbit) == value;
			});
			if (done || System.currentTimeMillis() >= deadline)
			{
				JsonObject o = new JsonObject();
				o.addProperty("timed_out", !done);
				o.addProperty("ticks_waited", onClient(client::getTickCount) - startTick);
				if (matched != null)
				{
					o.addProperty("message", matched);
				}
				Player me = onClient(client::getLocalPlayer);
				if (me != null)
				{
					WorldPoint wp = onClient(me::getWorldLocation);
					o.addProperty("x", wp.getX());
					o.addProperty("y", wp.getY());
					o.addProperty("plane", wp.getPlane());
				}
				return ToolResult.json(o);
			}
			Thread.sleep(POLL_MS);
		}
	}

	// ---- plumbing --------------------------------------------------------------------------------------------------

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

	private static JsonArray actionsJson(String[] actions)
	{
		JsonArray arr = new JsonArray();
		if (actions != null)
		{
			for (String s : actions)
			{
				if (s != null)
				{
					arr.add(Text.removeTags(s));
				}
			}
		}
		return arr;
	}

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
}
