package openwig;

import se.krka.kahlua.vm.*;

public class EventTable implements LuaTable {

	public LuaTable table = new LuaTableImpl();
	
	public String name, description;
	public ZonePoint position = null;
	protected boolean visible = false;
	
	public boolean isVisible() { return visible; }

	public void setPosition(ZonePoint location) {
		position = location;
		table.rawset("ObjectLocation", location);
	}
	
	public boolean isLocated() {
		return position != null;
	}

	protected void setItem(String key, Object value) {
		if ("Name".equals(key)) {
			name = (String)value;
		} else if ("Description".equals(key)) {
			description = Engine.removeHtml((String)value);
		} else if ("Visible".equals(key)) {
			visible = LuaState.boolEval(value);
		} else if ("ObjectLocation".equals(key)) {
			position = ZonePoint.copy((ZonePoint)value);
		}
	}
	
	public void setTable (LuaTable table) {
		Object n = null;
		while ((n = table.next(n)) != null) {
			Object val = table.rawget(n);
			this.table.rawset(n, val);
			if (n instanceof String) setItem((String)n, val);
		}
	}
	
	public void callEvent(String name, Object param) {
		try {
			Object o = table.rawget(name);
			if (o instanceof LuaClosure) {
				Engine.log("EVNT: " + toString() + "." + name + (param!=null ? " (" + param.toString() + ")" : ""));
				LuaClosure event = (LuaClosure) o;
				Engine.state.call(event, this, param, null);
				Engine.log("EEND: " + toString() + "." + name);
			}
		} catch (Throwable t) {
			Engine.stacktrace(t);
		}
	}
	
	public boolean hasEvent(String name) {
		return (table.rawget(name)) instanceof LuaClosure;
	}
	
	public String toString()  {
		return (name == null ? "(unnamed)" : name);
	}

	public void rawset(Object key, Object value) {
		if (key instanceof String) {
			setItem((String) key, value);
		}
		table.rawset(key, value);
		Engine.log("PROP: " + toString() + "." + key + " is set to " + (value == null ? "nil" : value.toString()));
	}

	public void setMetatable (LuaTable metatable) { table.setMetatable(metatable); }

	public LuaTable getMetatable () { return table.getMetatable(); }

	public Object rawget (Object key) { return table.rawget(key); }

	public Object next (Object key) { return table.next(key); }

	public int len () { return table.len(); }
	
	public void updateWeakSettings (boolean weakKeys, boolean weakValues) { table.updateWeakSettings(weakKeys, weakValues); }
}
