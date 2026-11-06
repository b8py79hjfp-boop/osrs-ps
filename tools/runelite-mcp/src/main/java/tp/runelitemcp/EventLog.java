package tp.runelitemcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayDeque;
import java.util.Set;

/**
 * Thread-safe ring buffer of JSON events with a monotonically increasing sequence number. Producers are the client
 * thread (RuneLite event subscribers); consumers are the HTTP threads.
 */
final class EventLog
{
	private final ArrayDeque<JsonObject> entries = new ArrayDeque<>();
	private int capacity;
	private long nextSeq = 1;

	EventLog(int capacity)
	{
		this.capacity = Math.max(1, capacity);
	}

	synchronized void setCapacity(int capacity)
	{
		this.capacity = Math.max(1, capacity);
		while (entries.size() > this.capacity)
		{
			entries.removeFirst();
		}
	}

	/** Appends an event; {@code data} gets {@code seq}, {@code tick}, {@code time} and {@code type} added. */
	synchronized long add(String type, int tick, JsonObject data)
	{
		long seq = nextSeq++;
		JsonObject entry = new JsonObject();
		entry.addProperty("seq", seq);
		entry.addProperty("tick", tick);
		entry.addProperty("time", System.currentTimeMillis());
		entry.addProperty("type", type);
		for (var e : data.entrySet())
		{
			entry.add(e.getKey(), e.getValue());
		}
		entries.addLast(entry);
		while (entries.size() > capacity)
		{
			entries.removeFirst();
		}
		return seq;
	}

	synchronized long latestSeq()
	{
		return nextSeq - 1;
	}

	/**
	 * Entries with {@code seq > sinceSeq}, optionally filtered by type, oldest first. If more than {@code limit}
	 * match, the newest {@code limit} are returned.
	 */
	synchronized JsonArray query(long sinceSeq, Set<String> types, int limit)
	{
		JsonArray matching = new JsonArray();
		for (JsonObject e : entries)
		{
			if (e.get("seq").getAsLong() <= sinceSeq)
			{
				continue;
			}
			if (types != null && !types.isEmpty() && !types.contains(e.get("type").getAsString()))
			{
				continue;
			}
			matching.add(e);
		}
		if (matching.size() <= limit)
		{
			return matching;
		}
		JsonArray tail = new JsonArray();
		for (int i = matching.size() - limit; i < matching.size(); i++)
		{
			tail.add(matching.get(i));
		}
		return tail;
	}
}
