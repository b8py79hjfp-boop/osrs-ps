package tp.runelitemcp;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import com.google.gson.JsonObject;
import java.util.function.IntSupplier;
import org.slf4j.LoggerFactory;

/**
 * Copies the client's WARN and ERROR log lines (RuneLite's logback root logger, which also carries the game client's own
 * errors as {@code injected-client}) into an {@link EventLog}, so a client-script failure or a broken plugin shows up in
 * {@code get_client_log} next to the action that caused it. Logback is RuneLite's logging backend (1.2.9 in 1.13.6).
 */
final class ClientLogCapture extends AppenderBase<ILoggingEvent>
{
	private static final int MAX_MESSAGE = 2000;

	private final EventLog log;
	private final IntSupplier tick;

	ClientLogCapture(EventLog log, IntSupplier tick)
	{
		this.log = log;
		this.tick = tick;
		setName("runelite-mcp-capture");
	}

	void attach()
	{
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		setContext(root.getLoggerContext());
		start();
		root.addAppender(this);
	}

	void detach()
	{
		Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		root.detachAppender(this);
		stop();
	}

	@Override
	protected void append(ILoggingEvent e)
	{
		if (!e.getLevel().isGreaterOrEqual(Level.WARN) || e.getLoggerName().startsWith("tp.runelitemcp"))
		{
			return;
		}
		String raw = e.getFormattedMessage();
		// RuneLite's EventBus cannot build fast lambdas for a sideloaded plugin's handlers and falls back to reflection
		// (events still arrive); one warning per handler of this plugin is noise.
		if (raw != null && raw.startsWith("Unable to create lambda") && raw.contains("tp.runelitemcp"))
		{
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("level", e.getLevel().toString());
		o.addProperty("logger", e.getLoggerName());
		o.addProperty("thread", e.getThreadName());
		String msg = e.getFormattedMessage();
		o.addProperty("message", msg == null ? "" : msg.length() > MAX_MESSAGE ? msg.substring(0, MAX_MESSAGE) : msg);
		IThrowableProxy t = e.getThrowableProxy();
		if (t != null)
		{
			o.addProperty("exception", t.getClassName() + ": " + t.getMessage());
		}
		int now;
		try
		{
			now = tick.getAsInt();
		}
		catch (RuntimeException ex)
		{
			now = -1;
		}
		log.add("ClientLog", now, o);
	}
}
