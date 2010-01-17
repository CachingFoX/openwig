package gwc;

import java.io.*;
import javax.microedition.io.*;
import javax.microedition.io.file.FileConnection;

public class CartridgeFile {
	
	private static final byte[] CART_ID = { 0x02, 0x0a, 0x43, 0x41, 0x52, 0x54, 0x00 };
			// 02 0a CART 00
	
	private GwcInput source;
	private String connectionUrl;
	private FileConnection file;

	private Savegame savegame;
	
	private int files;
	private int[] offsets;
	private int[] ids;
	
	public byte[] bytecode;
	
	public double latitude, longitude;
	public String type, member, name, description, startdesc, version, author, url, device, code;
	public int iconId, splashId;

	public String filename;
	
	private CartridgeFile() { }
	
	private void resetSource() 
	throws IOException {
		if (source != null) source.close();
		if (file != null) 
			source = new GwcInput(file.openInputStream());
		else
			source = new GwcInput(getClass().getResourceAsStream(connectionUrl));
	}
	
	private boolean fileOk () throws IOException {
		byte[] buf = new byte[CART_ID.length];
		source.read(buf);
		for (int i = 0; i < buf.length; i++) if (buf[i]!=CART_ID[i]) return false;
		return true;
	}
	
	public static CartridgeFile read (String what)
	throws IOException {
		CartridgeFile cf = new CartridgeFile();
		if (what.startsWith("resource:")) {
			String url = what.substring(9);
			if (cf.getClass().getResourceAsStream(url) == null)
				throw new IOException("resource not found");
			cf.connectionUrl = url;
		} else if (what.startsWith("file:")) {
			cf.file = (FileConnection)Connector.open(what, Connector.READ);
		} else {
			throw new IllegalArgumentException("invalid connection string");
		}
		
		cf.resetSource();
		if (!cf.fileOk()) throw new IOException("invalid cartridge file");
		
		cf.scanOffsets();
		cf.scanHeader();
			
		return cf;
	}
	
	private void scanOffsets () throws IOException {
		files = source.readShort();
		offsets = new int[files];
		ids = new int[files];
		for (int i = 0; i < files; i++) {
			ids[i] = source.readShort();
			offsets[i] = source.readInt();
		}
	}
	
	private void scanHeader () throws IOException {
		int headerlen = source.readInt();
		byte[] header = new byte[headerlen];
		source.read(header);
		
		GwcInput dis = new GwcInput(new ByteArrayInputStream(header));
		latitude = dis.readDouble();
		longitude = dis.readDouble();
		dis.skip(8); // zeroes
		dis.skip(4+4); // unknown long values
		iconId = dis.readShort();
		splashId = dis.readShort();
		type = dis.readString();
		member = dis.readString();
		dis.skip(4+4); // unknown long values
		name = dis.readString();
		dis.readString(); // GUID
		description = dis.readString();
		startdesc = dis.readString();
		version = dis.readString();
		author = dis.readString();
		url = dis.readString();
		device = dis.readString();
		dis.skip(4); // unknown long value
		code = dis.readString();
	}
	
	public byte[] getBytecode () throws IOException {
		if (source.position() > offsets[0]) resetSource();
		source.pseudoSeek(offsets[0]);
		int len = source.readInt();
		byte[] ffile = new byte[len];
		source.read(ffile);
		return ffile;
	}

	private int lastId = -1;
	private byte[] lastFile = null;

	private int idToIndex (int id) {
		for (int i = 0; i < ids.length; i++)
			if (ids[i] == id) return i;
		return -1;
	}

	private void pseudoSeek (long target) throws IOException {
		if (source.position() > target) resetSource();
		source.pseudoSeek(target);
	}

	private int preseek = 0;
	private boolean preseekPresent = false;;

	public boolean isPresent (int id) throws IOException {
		if (id == preseek) return preseekPresent;
		if (id < 1) return false;
		id = idToIndex(id);
		pseudoSeek(offsets[id]);
		int a = source.read();
		if (a != 1) return false;
		return true;
	}
	
	public byte[] getFile (int id) throws IOException {
		if (id == lastId) return lastFile;

		if (!isPresent(id)) return null;
		// relying on side-effect - we are now at proper position

		int ttype = source.readInt(); // we don't need this?
		int len = source.readInt();
		byte[] ffile = new byte[len];
		source.read(ffile);
		
		lastId = id;
		lastFile = ffile;
		return ffile;
	}
	
	public Savegame getSavegame () throws IOException {
		if (savegame == null) savegame = new Savegame(gui.Midlet.browser.getSyncFile());
		return savegame;
	}

}
