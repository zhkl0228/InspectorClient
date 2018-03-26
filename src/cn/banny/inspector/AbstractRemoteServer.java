package cn.banny.inspector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

import jline.console.ConsoleReader;

public abstract class AbstractRemoteServer implements RemoteServer {

	private final String model;
	private final String processName;
	private final ConsoleReader reader;

	AbstractRemoteServer(String model, String processName, ConsoleReader reader) {
		super();
		
		this.model = model;
		this.processName = processName;
		this.reader = reader;
	}
	
	private Socket createSocket() throws SocketException {
		Socket socket = new Socket();
		socket.setKeepAlive(true);
		// socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(60));
		return socket;
	}
	
	final Socket connectSocket(InetSocketAddress addr, String tip) throws IOException {
		Socket socket = createSocket();
		socket.connect(addr, (int) TimeUnit.SECONDS.toMillis(10));
		System.err.println("Connection to device [" + this.model + "][" + this.processName + "] success" + tip);
		if(reader != null) {
			reader.setPrompt(this.processName + '@' + this.model + "> ");
		}
		return socket;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteSocket#getProcessName()
	 */
	@Override
	public String getProcessName() {
		return processName;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.RemoteSocket#getModel()
	 */
	@Override
	public String getModel() {
		return model;
	}

}