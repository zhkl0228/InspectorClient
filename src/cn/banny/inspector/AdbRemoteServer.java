package cn.banny.inspector;

import com.android.ddmlib.IDevice;
import jline.console.ConsoleReader;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author zhkl0228
 *
 */
public class AdbRemoteServer extends AbstractRemoteServer {
	
	private final IDevice device;
	private final int port;

	public AdbRemoteServer(IDevice device, String processName, int port, ConsoleReader reader) {
		super(InspectorClient.getDeviceName(device), processName, reader);
		
		this.device = device;
		this.port = port;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteServer#connect()
	 */
	@Override
	public Socket connect() throws IOException {
		try {
			device.createForward(port, port);
		} catch (Throwable e) {
			throw new IOException(e);
		}
		return connectSocket(new InetSocketAddress("localhost", port), "");
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteServer#getKey()
	 */
	@Override
	public String getKey() {
		return "adb:" + port;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteServer#onDisconnect()
	 */
	@Override
	public void onDisconnect() {
	}

	@Override
	public boolean isAdb() {
		return true;
	}
}
