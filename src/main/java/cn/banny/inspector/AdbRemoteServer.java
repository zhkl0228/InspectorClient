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
	private final boolean hasLabel;

	public AdbRemoteServer(IDevice device, String model, String processName, int port, ConsoleReader reader, boolean hasLabel) {
		super(model, processName, reader);
		
		this.device = device;
		this.port = port;
		this.hasLabel = hasLabel;

		this.key = "adb:" + port + "[" + getProcessName() + ']';
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

	private final String key;

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteServer#getKey()
	 */
	@Override
	public String getKey() {
		return key;
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

	@Override
	public IDevice getDevice() {
		return device;
	}

	@Override
	public boolean hasLabel() {
		return hasLabel;
	}
}
