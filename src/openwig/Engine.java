package openwig;

import gui.Midlet;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.stdlib.*;

import java.io.*;
import java.util.*;
import javax.microedition.lcdui.*;

public class Engine implements Runnable {
	
	private Vector queue = new Vector();
	public Cartridge cartridge;
	public Player player = new Player();
	
	private InputStream code;
	
	public static Engine instance;
	public static LuaState state;
	
	private boolean end = false;

	public Engine (InputStream code) {
		instance = this;
		this.code = code;
	}
	
	public void run() {
		state = new LuaState(System.out);
		
		BaseLib.register(state);
		MathLib.register(state);
		StringLib.register(state);
		CoroutineLib.register(state);

		LuaInterface.register(state);
		
		try {
			InputStream stdlib = getClass().getResourceAsStream("/openwig/stdlib.lbc");
			LuaClosure closure = LuaPrototype.loadByteCode(stdlib, state.environment);
			state.call(closure, null, null, null);
			stdlib.close(); stdlib = null;
			
			closure = LuaPrototype.loadByteCode(code, state.environment);
			state.call(closure, null, null, null);
			code.close(); code = null;
			closure = null;
			
			player.position = (ZonePoint)cartridge.table.rawget("StartingLocation");
			if (player.position == null) {
				player.position = new ZonePoint(Midlet.latitude, Midlet.longitude, Midlet.altitude);
			}
			origPos = new ZonePoint(player.position);
					
			Midlet.start();
			cartridge.callEvent("OnStart", null);
			
			while (! end ) {
				try { Thread.sleep(1000); } catch (Exception e) {}
/*				synchronized (queue) {
					if (queue.isEmpty()) {
						try { queue.wait(); } catch (InterruptedException e) { e.printStackTrace(); }
						continue;
					}

					player.position = (ZonePoint)queue.elementAt(0);
					queue.removeElementAt(0);
				}*/
				if (Midlet.latitude != player.position.latitude
					|| Midlet.longitude != player.position.longitude
					|| Midlet.altitude != player.position.altitude) {
					
					player.position.latitude = Midlet.latitude;
					player.position.longitude = Midlet.longitude;
					player.position.altitude = Midlet.altitude;
					
					cartridge.newPosition(player.position);
				}
			} 
			
		} catch (RuntimeException e) {
			e.printStackTrace();
			Midlet.error("Runtime Exception... "+e.toString());
			System.out.println(state.currentThread.stackTrace);
		} catch (IOException e) {
			Midlet.error("IOException... "+e.getMessage());
		}
	}

	public static void newPosition (ZonePoint z) {
		synchronized (instance.queue) {
			instance.queue.addElement(z);
			instance.queue.notify();
		}
	}
	
	public static void kill () {
		if (instance == null) return;
		synchronized (instance.queue) {
			instance.end = true;
			instance.queue.notify();
		}
	}
		
	public static void message(String text) {
		String[] texts = {text};
		dialog(texts);
	}
	
	public static void dialog(String[] texts) {
		Midlet.pushDialog(texts);
	}
	
	public static void input(LuaTable input) {
		Midlet.pushInput(input);
	}
	
	public static void callEvent(EventTable subject, String name, Object param) {
		EventCaller ec = new EventCaller(subject, name, param);
		ec.start();
	}
	
	private static ZonePoint origPos;
	public static ZonePoint diff;
	public static boolean shifted = false;
	
	public static void reposition(double lat, double lon, double alt) {
		if (origPos == null) return;
		if (shifted) return;
		shifted = true;
		diff = new ZonePoint(origPos.latitude - lat, origPos.longitude - lon, 0);
		instance.cartridge.reposition(diff);
	}
}
