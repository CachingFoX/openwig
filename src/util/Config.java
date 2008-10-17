package util;

import java.util.Enumeration;
import java.util.Hashtable;
import javax.microedition.rms.*;

public class Config {

	private Hashtable values = new Hashtable();
	private RecordStore store;

	public void loadDefaults() {
	}

	public Config(String storename) {
		try {
			store = RecordStore.openRecordStore(storename, true);
			if (store.getSize() > 0) {
				fetchValues();
			}
		} catch (RecordStoreException e) {
			// display a warning? eh, why bother
		}
	}

	private void fetchValues() {
		if (store == null) return;
		try {
			byte[] r = store.getRecord(0);
			String record = new String(r);
			int si = 0;
			int idx;
			while ((idx = record.indexOf('\n', si)) > -1) {
				int sep = record.indexOf(':', si);
				try {
					values.put(record.substring(si, sep), record.substring(sep + 1, idx));
				} catch (IndexOutOfBoundsException e) { /* oh well */ }
				si = idx + 1;
			}
		} catch (RecordStoreException e) {
			// sorry guys, no cake for you today
		}
	}

	public void commit() {
		if (store == null) return;
		try {
			StringBuffer rep = new StringBuffer();
			Enumeration k = values.keys();
			while (k.hasMoreElements()) {
				String key = (String) k.nextElement();
				rep.append(key);
				rep.append(':');
				rep.append((String) values.get(key));
				rep.append('\n');
			}
			byte[] data = rep.toString().getBytes();
			if (store.getSize() > 0) {
				store.setRecord(0, data, 0, data.length);
			} else {
				store.addRecord(data, 0, data.length);
			}
		} catch (RecordStoreException e) {
			// bad luck, bye
		}
	}

	public void set(String key, String value) {
		values.put(key, value);
		commit();
	}

	public String get(String key) {
		return (String) values.get(key);
	}
}