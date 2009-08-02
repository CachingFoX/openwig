package openwig;

import gui.Midlet;
import gwc.*;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.stdlib.TableLib;

import java.io.*;
import java.util.*;
import javax.microedition.io.file.FileConnection;

interface Caller {
	void call();
}

class EventCaller implements Caller {
	private EventTable target = null;
	private String event;
	private Object param;
	
	public EventCaller (EventTable target, String event, Object param) {
		this.target = target;
		this.event = event;
		this.param = param;
	}
	
	public void call() {
		target.callEvent(event, param);
	}
	
}

class SyncCaller implements Caller {
	public void call() {
		Engine.instance.store();
	}
}

class CallbackCaller implements Caller {
	private LuaClosure callback;
	private Object value;
	
	public CallbackCaller (LuaClosure callback, Object value) {
		this.callback = callback;
		this.value = value;
	}
	
	public void call () {
		Engine.log("BTTN: " + (value == null ? "(cancel)" : value.toString()) + " pressed");
		Engine.state.call(callback, value, null, null);
		Engine.log("BTTN END");
	}
}

public class Engine implements Runnable {

	public Cartridge cartridge;
	public Player player = new Player();

	private String codeUrl;
	private CartridgeFile gwcfile;
	public Savegame savegame = null;
	private PrintStream log;
	public static boolean logProperties = false;
	
	private Vector eventQueue;
	private class EventQueue extends Thread {		
		public void run () {
			boolean events;
			while (!end) {
				events = false;
				while (!eventQueue.isEmpty()) {
					events = true;
					Caller c = (Caller)eventQueue.firstElement();
					eventQueue.removeElementAt(0);
					try {
						c.call();
					} catch (Throwable t) {
						stacktrace(t);
					}
				}
				if (events) Midlet.refresh();
				synchronized (this) {
					if (!eventQueue.isEmpty()) continue;
					try { wait(); } catch (InterruptedException e) { }
				}
			}
		}
		
		synchronized public void addCall (Caller c) {
			eventQueue.addElement(c);
			notify();
		}
	}
	private static EventQueue eventRunner;

	public static Engine instance;
	public static LuaState state;

	private boolean end = false;

	// **** utility for displaying status on loading screen
	private StringBuffer stdout = new StringBuffer("Creating engine...\n");
	private void write (String s) {
		stdout.append(s);
		Midlet.engineOutput.setText(stdout.toString());
	}

	public Engine (String codeUrl) {
		instance = this;
		this.codeUrl = codeUrl;
	}

	public Engine (CartridgeFile cf) {
		instance = this;
		gwcfile = cf;
	}

	public Engine (CartridgeFile cf, OutputStream out) {
		instance = this;
		gwcfile = cf;
		if (out != null) log = new PrintStream(out);
	}

	public Engine (CartridgeFile cf, FileConnection sv, OutputStream out) {
		this(cf, out);
		if (sv != null) savegame = new Savegame(sv);
	}

	public void run () {
		try {
			write("Creating state...\n");
			state = new LuaState(System.out);

			/*		write("Registering base libs...\n");
			BaseLib.register(state);
			MathLib.register(state);
			StringLib.register(state);
			CoroutineLib.register(state);
			OsLib.register(state);*/

			write("Loading stdlib...");
			InputStream stdlib = getClass().getResourceAsStream("/openwig/stdlib.lbc");
			LuaClosure closure = LuaPrototype.loadByteCode(stdlib, state.getEnvironment());
			write("calling...\n");
			state.call(closure, null, null, null);
			stdlib.close(); stdlib = null;

			write("Registering WIG libs...\n");
			LuaInterface.register(state);
				
			write("Building event queue...\n");
			eventQueue = new Vector(10);
			eventRunner = new EventQueue();

			if (savegame == null) {
				// starting game normally
				write("Loading gwc...");
				if (gwcfile == null) gwcfile = CartridgeFile.read(codeUrl);
				if (gwcfile == null) throw new Exception("invalid cartridge file");
				write("loading code...");
				byte[] lbc = gwcfile.getBytecode();

				write("parsing...");
				closure = LuaPrototype.loadByteCode(new ByteArrayInputStream(lbc), state.getEnvironment());
				write("calling...\n");
				state.call(closure, null, null, null);
				lbc = null;
				closure = null;

				write("Setting remaining properties...\n");
				player.rawset("CompletionCode", gwcfile.code);
				player.rawset("Name", gwcfile.member);

			} else if (savegame != null) {
				write("Restoring saved state...");
				restore();
			}
			logProperties = true;

			write("Starting game...\n");
			Midlet.start();

			if (log != null) log.println("-------------------\ncartridge " + cartridge.toString() + " started\n-------------------");
			player.refreshLocation();
			if (savegame == null) {
				cartridge.callEvent("OnStart", null);
			} else {
				cartridge.callEvent("OnRestore", null);
			}
			Midlet.refresh();
			eventRunner.start();

			while (!end) {
				try {
					if (Midlet.gps.getLatitude() != player.position.latitude
					|| Midlet.gps.getLongitude() != player.position.longitude
					|| Midlet.gps.getAltitude() != player.position.altitude.value) {
						player.refreshLocation();
					}
					cartridge.tick();
				} catch (Exception e) {
					stacktrace(e);
				}
				
				try { Thread.sleep(1000); } catch (InterruptedException e) { }
			}
			if (log != null) log.close();
		} catch (Throwable t) {
			Midlet.end();
			Engine.stacktrace(t);
		} finally {
			instance = null;
			state = null;
			eventRunner = null;
		}
	}

	public static void stacktrace (Throwable e) {
		e.printStackTrace();
		String msg;
		if (state != null) {
			System.out.println(state.currentThread.stackTrace);
			msg = e.toString() + "\n\nstack trace: " + state.currentThread.stackTrace;
		} else {
			msg = e.toString();
		}
		log(msg);
		Midlet.error(msg);
	}

	public static void kill () {
		if (instance == null) return;
		Timer.kill();
		instance.end = true;
	}

	public static void message (LuaTable message) {
		String[] texts = {removeHtml((String)message.rawget("Text"))};
		log("CALL: MessageBox - " + texts[0].substring(0, Math.min(100,texts[0].length())));
		Media[] media = {(Media)message.rawget("Media")};
		String button1 = null, button2 = null;
		LuaTable buttons = (LuaTable)message.rawget("Buttons");
		if (buttons != null) {
			button1 = (String)buttons.rawget(new Double(1));
			button2 = (String)buttons.rawget(new Double(2));
		}
		LuaClosure callback = (LuaClosure)message.rawget("Callback");
		Midlet.pushDialog(texts, media, button1, button2, callback);
	}

	public static void dialog (String[] texts, Media[] media) {
		if (texts.length > 0) {
			log("CALL: Dialog - " + texts[0].substring(0, Math.min(100,texts[0].length())));
		}
		Midlet.pushDialog(texts, media, null, null, null);
	}

	public static void input (EventTable input) {
		log("CALL: GetInput - "+input.name);
		Midlet.pushInput(input);
	}

	public static void callEvent (EventTable subject, String name, Object param) {
		if (!subject.hasEvent(name)) return;
		EventCaller ec = new EventCaller(subject, name, param);
		eventRunner.addCall(ec);
	}

	public static void invokeCallback (LuaClosure callback, Object value) {
		CallbackCaller cc = new CallbackCaller(callback, value);
		eventRunner.addCall(cc);
	}

	public static byte[] mediaFile (Media media) throws Exception {
		/*String filename = media.jarFilename();
		return media.getClass().getResourceAsStream("/media/"+filename);*/
		return instance.gwcfile.getFile(media.id);
	}

	public static void log (String s) {
		if (instance == null || instance.log == null) return;
		synchronized (instance.log) {
		Calendar now = Calendar.getInstance();
		instance.log.print(now.get(Calendar.HOUR_OF_DAY));
		instance.log.print(':');
		instance.log.print(now.get(Calendar.MINUTE));
		instance.log.print(':');
		instance.log.print(now.get(Calendar.SECOND));
		instance.log.print('|');
		instance.log.print((int)(Midlet.gps.getLatitude() * 10000 + 0.5) / 10000.0);
		instance.log.print('|');
		instance.log.print((int)(Midlet.gps.getLongitude() * 10000 + 0.5) / 10000.0);
		instance.log.print('|');
		instance.log.print(Midlet.gps.getAltitude());
		instance.log.print('|');
		instance.log.print(Midlet.gps.getPrecision());
		instance.log.print("|:: ");
		instance.log.println(s);
		instance.log.flush();
		}
	}

	public static String removeHtml (String s) {
		if (s == null) return null;
		StringBuffer sb = new StringBuffer(s.length());
		int pos = 0;
		while (pos < s.length()) {
			int np = s.indexOf("<BR>", pos);
			if (np == -1) break;
			sb.append(s.substring(pos, np));
			pos = np + 4;
		}
		sb.append(s.substring(pos));
		s = sb.toString(); pos = 0; sb.delete(0, sb.length());
		while (pos < s.length()) {
			int np = s.indexOf("&nbsp;", pos);
			if (np == -1) break;
			sb.append(s.substring(pos, np));
			sb.append(' ');
			pos = np + 6;
		}
		sb.append(s.substring(pos));
		return sb.toString();
	}

	public void store () {
		// perform the actual sync
		try {
			Midlet.setStatusText("saving...");
			if (savegame == null)
				savegame = new Savegame(Midlet.browser.getSyncFile());
			savegame.store(state.getEnvironment());
		} catch (IOException e) {
			Midlet.error("Sync failed.\n"+e.getMessage());
		} finally {
			Midlet.setStatusText(null);
		}
	}

	private void restore () {
		if (savegame == null) return;
		try {
			savegame.restore(state.getEnvironment());
		} catch (IOException e) {
			Midlet.error("Restore failed.\n"+e.getMessage());
		}
	}

	public static void requestSync () {
		eventRunner.addCall(new SyncCaller());
	}

	public static void tableInsert (LuaTable table, int position, Object item) {
		TableLib.insert(state, table, position, item);
	}
	public static void tableInsert (LuaTable table, Object item) {
		TableLib.insert(state, table, item);
	}
	public static Object tableRemove (LuaTable table, int position) {
		return TableLib.remove(state, table, position);
	}
	public static Object tableRemove (LuaTable table) {
		return TableLib.remove(state, table);
	}
}