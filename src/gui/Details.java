package gui;

import java.io.InputStream;
import javax.microedition.lcdui.*;
import openwig.Engine;
import openwig.EventTable;
import openwig.Media;
import openwig.Thing;
import openwig.Task;
import openwig.Zone;

public class Details extends Form implements CommandListener, Pushable, Runnable {
	
	private static final Command CMD_ACTIONS = new Command("Actions", Command.SCREEN, 0);
	private static final Command CMD_NAVIGATE = new Command("Navigate", Command.SCREEN, 1);
	
	private StringItem description = new StringItem(null, null);
	private StringItem state = new StringItem("Stav: ", null);
	private StringItem distance = new StringItem("Vzd�l.: ", null);
	private StringItem direction = new StringItem("Sm�r: ", null);
	private ImageItem image = new ImageItem(null, null, ImageItem.LAYOUT_DEFAULT, null);
	private ImageItem compass = new ImageItem(null, null, ImageItem.LAYOUT_DEFAULT, null);
	//private Image media = null;
	
	static String[] taskStates = { "nespln�no", "hotovo", "ne�sp�ch" };
	
	private EventTable thing;
	private Things parent;
	
	public Details (EventTable t, Things where) {
		super(null);
		thing = t;
		parent = where;
		append(image);
		append(description);
		setCommandListener(this);
		addCommand(Midlet.CMD_BACK);
		
		Media m = (Media)t.table.rawget("Media");
		if (m != null) {
			image.setAltText(m.altText);
			try {
				InputStream is = Engine.mediaFile(m);
				Image i = Image.createImage(is);
				image.setImage(i);
			} catch (Exception e) { }
		}
		
		if (t instanceof Task) {
			append(state);
		} else if (t instanceof Zone) {
			append(state);
			append(distance);
			append(direction);
			append(compass);
		}
	}

	public void commandAction(Command cmd, Displayable disp) {
		if (cmd == CMD_ACTIONS) {
			Midlet.push(new Actions(thing.name, (Thing)thing, parent));
		} else if (cmd == CMD_NAVIGATE) {
			if (thing instanceof Zone) Midlet.push(new Navigation((Zone)thing));
			else if (thing.isLocated()) Midlet.push(new Navigation(thing.position));
		} else if (cmd == Midlet.CMD_BACK) {
			stop();
			Midlet.pop(this);
		}
	}

	public void prepare() {
		if (!thing.isVisible() || (thing instanceof Thing && parent != null && !parent.isPresent((Thing)thing))) {
			stop();
			Midlet.pop(this);
			return;
		}
		
		setTitle(thing.name);
		description.setText(thing.description);
		
		if (thing instanceof Thing) {
			Thing t = (Thing)thing;
			int actions = t.visibleActions() + Engine.instance.cartridge.visibleUniversalActions();
			if (actions > 0) addCommand(CMD_ACTIONS);
			else removeCommand(CMD_ACTIONS);
		} else if (thing instanceof Task) {
			Task t = (Task)thing;
			state.setText(taskStates[t.state()]);
		} else if (thing instanceof Zone) {
			updateNavi();
			start();
			//updateNavi();
		}
		
		if (thing.isLocated()) addCommand(CMD_NAVIGATE);
		else removeCommand(CMD_NAVIGATE);
	}
	
	private void updateNavi () {
		Zone z = (Zone)thing;
		String ss = "(nic)";
		switch (z.contain) {
			case Zone.DISTANT: ss = "distant"; break;
			case Zone.PROXIMITY: ss = "proximity"; break;
			case Zone.INSIDE: ss = "uvnit�"; break;
		}
		state.setText(ss);
		
		if (z.ncontain == Zone.INSIDE) { 
			distance.setText("0 m");
		} else {
			long part = (long)(z.distance * 1000);
			double d = part/1000.0;
			distance.setText(Double.toString(d)+" m");
		}
		
		// uhodnuti smeru
		double x = z.nearestX - Midlet.latitude;
		double y = z.nearestY - Midlet.longitude;
		double xx = x*2, yy = y*2;
		String s;
		if (x > 0 && x > Math.abs(yy)) s = "sever";
		else if (y > 0 && x > 0 && x <= yy && y <= xx) s = "severov�chod";
		else if (y > 0 && y > Math.abs(xx)) s = "v�chod";
		else if (y > 0 && x < 0 && -x <= yy && y <= -xx) s = "jihov�chod";
		else if (x < 0 && -x > Math.abs(yy)) s = "jih";
		else if (y < 0 && x < 0 && x >= yy && y >= xx) s = "jihoz�pad";
		else if (y < 0 && -y > Math.abs(xx)) s = "z�pad";
		else s = "severoz�pad";
		direction.setText(s+"\nnejbli��� bod: "+z.nearestX+","+z.nearestY);
	}
	
	private boolean running = false;
	synchronized private void start() {
		if (running) return;
		running = true;
		new Thread(this).start();
	}
	
	synchronized private void stop() {
		running = false;
	}
	
	public void run () {
		while (running) {
			try { Thread.sleep(1000); } catch (Exception e) { }
			updateNavi();
		}
	}
	
}
