package openwig;

import se.krka.kahlua.vm.*;
import gui.Midlet;

import java.io.*;
import java.util.Vector;

public class Timer extends EventTable {
	
	private static java.util.Timer globalTimer;
	private static boolean paused = true;
	private static Vector timers = new Vector();

	private static JavaFunction start = new JavaFunction() {
		public int call (LuaCallFrame callFrame, int nArguments) {
			Timer t = (Timer)callFrame.get(0);
			t.start();
			return 0;
		}
	};
	
	private static JavaFunction stop = new JavaFunction() {
		public int call (LuaCallFrame callFrame, int nArguments) {
			Timer t = (Timer)callFrame.get(0);
			t.stop();
			return 0;
		}
	};
	
	private static JavaFunction tick = new JavaFunction() {
		public int call (LuaCallFrame callFrame, int nArguments) {
			Timer t = (Timer)callFrame.get(0);
			//t.tick();
			t.callEvent("OnTick", null);
			return 0;
		}
	};
	
	private class TimerTask extends java.util.TimerTask {
		public boolean restart = false;
		public void run() {
			tick();
			Midlet.refresh();
			if (restart) {
				cancel();
				task = null;
				start();
			}
		}	
	}
	
	private TimerTask task = null;
	
	private static final int COUNTDOWN = 0;
	private static final int INTERVAL = 1;
	private int type = COUNTDOWN;
	
	private long duration = -1;
	private long lastTick = 0;

	private long remaining = 0;
	private boolean resume = false;
	
	public Timer () {
		if (globalTimer == null) globalTimer = new java.util.Timer();
		table.rawset("Start", start);
		table.rawset("Stop", stop);
		table.rawset("Tick", tick);
		timers.addElement(this);
	}
	
	protected void setItem (String key, Object value) {
		if ("Type".equals(key) && value instanceof String) {
			String v = (String)value;
			int t = type;
			if ("Countdown".equals(v)) {
				t = COUNTDOWN;
				if (t != type && task != null)
					task.restart = false;
					// we don't need task.restart here,
					// so make sure it's not set
			} else if ("Interval".equals(v)) {
				t = INTERVAL;
				if (t != type && task != null)
					task.restart = true;
			}
			type = t;
		} else if ("Duration".equals(key) && value instanceof Double) {
			long d = (long)LuaState.fromDouble(value);
			rawset("Remaining", value);
			duration = d * 1000;
		} else super.setItem(key, value);
	}
	
	public void start () {
		if (task != null) return;
		if (duration == 0) {
			// XXX this might be a problem if the timer is interval
			callEvent("OnStart", null);
			callEvent("OnTick", null);
			return;
		}
		task = new TimerTask();
		lastTick = System.currentTimeMillis();
		updateRemaining();
		callEvent("OnStart", null);
		schedule(duration);
	}

	private void schedule (long when) {
		switch (type) {
			case COUNTDOWN:
				globalTimer.schedule(task, when);
				break;
			case INTERVAL:
				globalTimer.scheduleAtFixedRate(task, when, duration);
				break;
		}
	}
	
	public void stop () {
		if (task != null) {
			task.cancel();
			task = null;
			callEvent("OnStop", null);
		}
	}
	
	public void tick () {
		Engine.callEvent(this, "OnTick", null);
		lastTick = System.currentTimeMillis();
		updateRemaining();
		if (type == COUNTDOWN) {
			task.cancel();
			task = null;
		}
		if (type == INTERVAL && task != null && !task.restart)
			Engine.callEvent(this, "OnStart", null);
			// the devices seem to do this. why, that's beyond me
		// else it will be restarted and OnStart called again anyway
	}
	
	public void updateRemaining () {
		long stm = System.currentTimeMillis();
		remaining = (duration/1000) - ((stm - lastTick)/1000);
		table.rawset("Remaining", LuaState.toDouble(remaining));
	}
	
	public static void kill() {
		if (globalTimer != null) globalTimer.cancel();
		globalTimer = null;
	}

	private void pause () {
		if (task != null) resume = true;
		updateRemaining();
		lastTick = System.currentTimeMillis() - lastTick;
	}

	private void resume () {
		lastTick += System.currentTimeMillis();
		updateRemaining();
		if (resume) {
			task = new TimerTask();
		}
	}

	public void serialize (DataOutput out) throws IOException {
		out.writeBoolean(resume);
		out.writeLong(lastTick);
		super.serialize(out);
	}

	public void deserialize (DataInput in) throws IOException {
		resume = in.readBoolean();
		lastTick = in.readLong();
		super.deserialize(in);
	}
}
