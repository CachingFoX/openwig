package openwig;

import java.util.Vector;
import se.krka.kahlua.vm.*;

public class Cartridge extends EventTable {
	public Vector zones = new Vector();
	public Vector timers = new Vector();
	
	public Vector things = new Vector();
	public Vector universalActions = new Vector();
	
	public Vector tasks = new Vector();
	
	public LuaTable allZObjects = new LuaTable();
	
	private static class Method implements JavaFunction {
		public int call (LuaCallFrame frame, int n) {
			return 0;
		}
	}
	private static Method m = new Method();
	
	public static void register(LuaState state) {
		EventTable.register(state);
		state.setUserdataMetatable(Cartridge.class, metatable);
	}
	
	public Cartridge () {
		table.rawset("RequestSync", m);
		table.rawset("AllZObjects", allZObjects);
		Engine.tableInsert(allZObjects, this);
	}
		
	public void walk (ZonePoint zp) {		
		for (int i = 0; i < zones.size(); i++) {
			Zone z = (Zone)zones.elementAt(i);
			z.walk(zp);
		}
	}
	
	public void tick () {
		for (int i = 0; i < zones.size(); i++) {
			Zone z = (Zone)zones.elementAt(i);
			z.tick();
		}
		for (int i = 0; i < timers.size(); i++) {
			Timer t = (Timer)timers.elementAt(i);
			t.tick();
		}

	}
	
	public int visibleZones () {
		int count = 0;
		for (int i = 0; i < zones.size(); i++) {
			Zone z = (Zone)zones.elementAt(i);
			if (z.isVisible()) count++;
		}
		return count;
	}
	
	public int visibleThings () {
		int count = 0;
		for (int i = 0; i < zones.size(); i++) {
			Zone z = (Zone)zones.elementAt(i);
			count += z.visibleThings();
		}
		return count;
	}
	
	public LuaTable currentThings () {
		LuaTable ret = new LuaTable();
		for (int i = 0; i < zones.size(); i++) {
			Zone z = (Zone)zones.elementAt(i);
			z.collectThings(ret);
		}
		return ret;
	}
	
	public int visibleUniversalActions () {
		int count = 0;
		for (int i = 0; i < universalActions.size(); i++) {
			Action a = (Action)universalActions.elementAt(i);
			if (a.isEnabled()) count++;
		}
		return count;
	}
	
	public int visibleTasks () {
		int count = 0;
		for (int i = 0; i < tasks.size(); i++) {
			Task a = (Task)tasks.elementAt(i);
			if (a.isVisible()) count++;
		}
		return count;
	}
	
	public void addObject (Object o) {
		Engine.tableInsert(allZObjects, o);
		if (o instanceof Task) tasks.addElement(o);
		else if (o instanceof Zone) zones.addElement(o);
		else if (o instanceof Timer) timers.addElement(o);
		else if (o instanceof Thing) things.addElement(o);
	}
}
