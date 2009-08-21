package openwig;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Hashtable;
import se.krka.kahlua.vm.*;
import se.krka.kahlua.stdlib.BaseLib;

public class Distance implements LuaTable, Serializable {
	public double value; // value in metres

	public void serialize (DataOutputStream out) throws IOException {
		out.writeDouble(value);
	}

	public void deserialize (DataInputStream in) throws IOException {
		value = in.readDouble();
	}
		
	public static Hashtable conversions = new Hashtable(6);
	static {
		conversions.put("feet", new Double(0.3048));
		conversions.put("ft", new Double(0.3048));
		conversions.put("miles", new Double(1609.344));
		conversions.put("meters", new Double(1));
		conversions.put("kilometers", new Double(1000));
		conversions.put("nauticalmiles", new Double(1852));
	}
	
	protected static LuaTable metatable;
	protected static JavaFunction getValue = new JavaFunction() {
		public int call (LuaCallFrame frame, int n) {
			BaseLib.luaAssert(n >= 2, "not enough parameters");
			Distance z = (Distance) frame.get(0);
			String unit = (String) frame.get(1);
			frame.push(LuaState.toDouble(z.getValue(unit)));
			return 1;
		}
	};

	public Distance () {
		// for deserialize
	}
	
	public Distance (double value, String unit)
	{
		setValue(value, unit);
	}
	
	public void setValue (double value, String unit) {
		if (conversions.containsKey(unit)) {
			this.value = value * ((Double)conversions.get(unit)).doubleValue();
		} else {
			this.value = value;
			// XXX exception
		}
	}
	
	public double getValue (String unit) {
		if (unit != null && conversions.containsKey(unit)) {
			return value / ((Double)conversions.get(unit)).doubleValue();
		} else {
			return value;
			// XXX exception
		}		
	}

	public void setMetatable (LuaTable metatable) { }
	public LuaTable getMetatable () { return null; }

	public void rawset (Object key, Object value) {	}

	public Object rawget (Object key) {
		if (key == null) return null;
		if ("value".equals(key)) return LuaState.toDouble(value);
		if ("GetValue".equals(key)) return getValue;
		return null;
	}

	public int len () { return 3; }

	public void updateWeakSettings (boolean weakKeys, boolean weakValues) {	}
	public Object next (Object key) { return null; }
}
