package gui;

import gps.GpsParser;
import openwig.Engine;
import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;

import javax.bluetooth.*;

import java.io.*;
import java.util.Vector;

import net.benhui.btgallery.bluelet.BLUElet;
import se.krka.kahlua.stdlib.*;
import se.krka.kahlua.vm.*;
import openwig.Engine;

public class Midlet extends MIDlet implements CommandListener {
	
	public static Coordinates coordinates;
	public static Zones zones;
	public static Things inventory;
	public static MainMenu mainMenu;
	public static Form mainForm;
	private static StringItem si;
	
	private static BLUElet bluelet;
	public static GpsParser gpsParser;
	private static final int GPS_OFF = 0;
	private static final int GPS_SEARCHING = 1;
	private static final int GPS_LOCKING = 2;
	private static final int GPS_ON = 3;
	private static int gpsStatus = 0;
	
	private static List baseMenu = new List("menu", List.IMPLICIT);
	private static final int MNU_START = 0;
	private static final int MNU_GPS = 1;
	private static final int MNU_END = 2;
	
	public static final Command CMD_EXIT = new Command("Konec", Command.EXIT, 10);
	public static final Command CMD_SELECT = new Command("Vybrat", Command.ITEM, 0);
	public static final Command CMD_NEXT = new Command("Dal��", Command.OK, 1);
	public static final Command CMD_OK = new Command("OK", Command.OK, 1);
	public static final Command CMD_CANCEL = new Command("Zru�it", Command.BACK, 2);
	public static final Command CMD_BACK = new Command("Zp�t", Command.BACK, 2);
	
	public static Midlet instance;
	public static Display display;
	
	private static Dialog currentDialog = null;
	private static Vector screens = new Vector();
	private static Displayable currentScreen = null;
	
	public void startApp() {
		instance = this;
		display = Display.getDisplay(this);
		
		baseMenu.append("Start", null);
		baseMenu.append("GPS", null);
		baseMenu.append("Konec", null);
		baseMenu.setSelectCommand(CMD_SELECT);
		baseMenu.setCommandListener(this);
		
		push(baseMenu);
	}

	public void pauseApp() {
	}

	public void destroyApp(boolean unconditional) {
		Engine.kill();
		notifyDestroyed();
	}

	public void commandAction(Command cmd, Displayable disp) {
		if (disp == baseMenu) {
			if (cmd == CMD_SELECT) {
				switch (baseMenu.getSelectedIndex()) {
					case MNU_START:
						if (gpsStatus != GPS_ON) {
							display.setCurrent(new Alert("gps","po�kej a� bude� m�t funk�n� gpsku", null, AlertType.ERROR), baseMenu);
							break;
						}
						mainForm = new Form("splash");
						si = new StringItem(null, "Loading LUA...");
						mainForm.append(si);
						mainForm.addCommand(CMD_EXIT);
						mainForm.setCommandListener(this);
						Display.getDisplay(this).setCurrent(mainForm);

						InputStream luacode = getClass().getResourceAsStream("/openwig/luac.out");
						Thread t = new Thread(new Engine(luacode));
						t.start();

						break;
					case MNU_GPS:
						if (gpsStatus == GPS_OFF) {
							bluelet = new BLUElet(this, this);
							bluelet.startApp();
							push(bluelet.getUI());
							bluelet.startInquiry(DiscoveryAgent.GIAC, new UUID[]{new UUID(0x1101)});
						// launch gps selector, or not.
						}
						break;
					case MNU_END:
						destroyApp(false);
						break;
				}
			}
		} else if (bluelet != null && disp == bluelet.getUI()) {
			if (cmd == BLUElet.COMPLETED) {
				ServiceRecord serviceRecord = bluelet.getFirstDiscoveredService();
				pop(bluelet.getUI());
				bluelet = null;
				String url = serviceRecord.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
				gpsStatus = GPS_SEARCHING;
				gpsParser = new GpsParser(url, GpsParser.BLUETOOTH);
				gpsParser.open();
				baseMenu.set(MNU_GPS, "p�ipojuji...", null);
			} else if (cmd == BLUElet.BACK) {
				pop(bluelet.getUI());
				bluelet = null;
			}
		} else if (cmd.getCommandType() == Command.EXIT) {
			destroyApp(false);
		}
	}
	
	public static void gpsConnected () {
		gpsStatus = GPS_LOCKING;
		baseMenu.set(MNU_GPS, "hled�m polohu...", null);
	}
	
	public static void gpsFixChanged(boolean fix) {
		if (!fix) gpsConnected();
		else {
			gpsStatus = GPS_ON;
			baseMenu.set(MNU_GPS, "Sou�adnice", null);
		}
	}
	
	public static void state (String text) {
		si.setText(text);
		display.setCurrent(mainForm);
	}
	
	synchronized public static void error (String text) {
		Alert a = new Alert("chyba",text,null,AlertType.ERROR);
		a.setTimeout(Alert.FOREVER);
		display.setCurrent(a, display.getCurrent());
	}
	
	synchronized public static void pushDialog(String[] texts) {
		Dialog d = new Dialog(texts);

		//display.flashBacklight(500);
		//display.vibrate(500);
		
		if (currentDialog != null) pop(currentDialog);
		currentDialog = d;
		push(d);
	}
	
	synchronized public static void popDialog(Dialog d) {
		pop(d);
		if (currentDialog == d) currentDialog = null;
	}
	
	synchronized public static void push (Displayable d) {
		if (d instanceof Pushable) ((Pushable)d).prepare();
		screens.addElement(d);
		currentScreen = d;
		display.setCurrent(d);
	}
	
	synchronized public static void pop (Displayable disp) {
		int ss = screens.size();
		for (int i = ss-1; i >= 0; i--) {
			Object o = screens.elementAt(i);
			if (o == disp) {
				screens.removeElementAt(i);
				ss--;
				break;
			}
		}
		if (ss == 0) {
			screens.addElement(baseMenu);
			ss = 1;
		}
		
		display.setCurrent(currentScreen = (Displayable)screens.elementAt(ss-1));
	}
	
	public static void start () {
		mainMenu = new MainMenu();
		coordinates = new Coordinates();
		zones = new Zones();
		inventory = new Things("Invent��", Engine.instance.player);
		Engine.reposition(gpsParser.getLatitude(), gpsParser.getLongitude(), gpsParser.getAltitude());
		push(mainMenu);
	}
	
	synchronized public static void refresh () {
		int ss = screens.size();
		for (int i = 0; i < ss; i++) {
			Object d = screens.elementAt(i);
			if (d instanceof Pushable) ((Pushable)d).prepare();
		}
	}
}
