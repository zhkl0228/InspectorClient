package cn.banny.inspector;

import java.io.IOException;
import java.net.Socket;

public interface RemoteServer {

	String getProcessName();

	String getModel();

	Socket connect() throws IOException;

	String getKey();
	
	void onDisconnect();

	boolean isAdb();

}