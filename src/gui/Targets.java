package gui;

import java.util.Vector;
import openwig.Engine;
import openwig.Thing;
import openwig.Action;
import se.krka.kahlua.vm.LuaTable;

public class Targets extends ListOfStuff {

	private Action action;
	private Things parent;

	public Targets(String title, Action what, Things parent) {
		super(title);
		action = what;
		this.parent = parent;
	}

	protected void callStuff(Object what) {
		Midlet.pop(this);
		Thing t = (Thing) what;
		String eventName = "On"+action.getName();
		Engine.callEvent(action.getActor(), eventName, t);
	}

	protected boolean stillValid() {
		return parent == null || parent.isPresent(action.getActor());
	}

	protected Vector getValidStuff() {
		LuaTable current = Engine.instance.cartridge.currentThings();
		int size = current.len() + Engine.instance.player.inventory.len();
		Vector newtargets = new Vector(size);
		Object key = null;
		while ((key = current.next(key)) != null)
			newtargets.addElement(current.rawget(key));
		while ((key = Engine.instance.player.inventory.next(key)) != null)
			newtargets.addElement(Engine.instance.player.inventory.rawget(key));
		
		for (int i = 0; i < newtargets.size(); i++) {
			Thing t = (Thing)newtargets.elementAt(i);
			if (! t.isVisible() || ! action.isTarget(t)) {
				newtargets.removeElementAt(i--);
			}
		}
		
		return newtargets;
	}

	protected String getStuffName(Object what) {
		Thing t = (Thing)what;
		return t.name;
	}
}
