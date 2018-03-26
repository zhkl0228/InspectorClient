package cn.banny.inspector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import com.android.ddmlib.IDevice;
import jline.console.ConsoleReader;

class SocketRemoteServer extends AbstractRemoteServer implements RemoteServer {
	
	private final InetSocketAddress addr;
	private final int clientCount;
	SocketRemoteServer(InetSocketAddress addr, String model,
					   int clientCount, String processName, ConsoleReader reader) {
		super(model, processName, reader);
		this.addr = addr;
		this.clientCount = clientCount;

		this.key = addr.getAddress().getHostAddress() + ':' + addr.getPort() + "[" + getProcessName() + ']';
	}
	
	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteSocket#connect()
	 */
	@Override
	public Socket connect() throws IOException {
		return connectSocket(addr, ", clients count is " + this.clientCount);
	}

	private final String key;
	
	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteSocket#getKey()
	 */
	@Override
	public String getKey() {
		return key;
	}

	@Override
	public void onDisconnect() {
	}

	@Override
	public boolean isAdb() {
		return false;
	}

	@Override
	public IDevice getDevice() {
		return null;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}
}