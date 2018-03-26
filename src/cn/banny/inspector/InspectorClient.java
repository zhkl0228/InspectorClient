package cn.banny.inspector;

import cn.banny.auxiliary.Inspector;
import cn.banny.inspector.adb.AndroidDebugBridgeManager;
import cn.banny.inspector.adb.BootCompleteListener;
import cn.banny.inspector.completer.ClientCompleter;
import cn.banny.inspector.completer.FileNameCompleter;
import cn.banny.inspector.completer.ServerCommandCompleter;
import com.android.ddmlib.*;
import com.android.ddmlib.logcat.LogCatMessage;
import jline.console.ConsoleReader;
import jline.console.completer.CandidateListCompletionHandler;
import jline.console.history.FileHistory;
import jline.console.history.History.Entry;
import jline.console.history.PersistentHistory;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * 使用方法：<br>
 * 利用Halo增加字节码如下<br>
 * 
 * <code>aload_1</code><br>
 * <code>iconst_1</code>表示发送数据，<code>iconst_0</code>表示接收数据<br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/inspect([BZ)V</code>
 * 
 * 带label侦察
 * <code>aload_1</code><br>
 * <code>ldc "label: "</code><br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/inspect([BLjava/lang/String;)V</code>
 * 
 * type使用方法：<br>
 * 利用Halo增加字节码如下<br>
 * 
 * <code>iconst_1</code><br>
 * <code>aload_1</code><br>
 * <code>iconst_1</code>表示发送数据，<code>iconst_0</code>表示接收数据<br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/inspect(I[BZ)V</code>
 * 
 * 
 * 识别对象类型：<br>
 * 利用Halo增加字节码如下<br>
 * <code>aload_1</code><br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/objectType(Ljava/lang/Object;)V</code>
 * 
 * 打印数值
 * <code>ldc "label: "</code><br>
 * <code>iconst_1</code><br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/inspect(Ljava/lang/String;I)V</code>
 * 
 * 手动引发错误
 * <code>invokestatic cn/banny/auxiliary/Inspector/throwError()V</code>
 * 
 * 根据值比较引发错误
 * <code>iconst_1</code>常量值<br>
 * <code>iconst_1</code>测试值<br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/throwError(II)V</code>
 * 
 * 根据值查看执行位置
 * <code>iconst_1</code>常量值<br>
 * <code>iconst_1</code>测试值<br>
 * <code>invokestatic cn/banny/auxiliary/Inspector/where(II)V</code>
 * 
 * 
 * @author Banny
 * 
 */
public class InspectorClient implements Runnable, BootCompleteListener {

	private static final Log log = LogFactory.getLog(InspectorClient.class);
	
	private final ConsoleReader reader;
	
	private InspectorClient(ConsoleReader reader) {
		super();
		this.reader = reader;
	}

	public ConsoleReader getConsoleReader() {
		return reader;
	}

	private File outDir;
	private PrintWriter logWriter;
	
	private void setOutDir(File outDir) {
		this.outDir = outDir;
		
		File logFile = new File(outDir, "inspector.log");
		try {
			logWriter = new PrintWriter(new FileWriter(logFile, true), true);
		} catch(IOException e) {
			log.warn(e.getMessage(), e);
		}
	}

	public void logWrite(LogCatMessage msg) {
		if(logWriter != null) {
			logWriter.println(msg);
		}
	}
	
	private void resetLog() {
		if(outDir == null) {
			return;
		}

		IOUtils.closeQuietly(logWriter);
		if(!new File(outDir, "inspector.log").delete()) {
			setOutDir(outDir);
			System.err.println("changeLogFile failed!");
			return;
		}
		
		setOutDir(outDir);
		System.out.println("changeLogFile successfully!");
	}
	
	private static boolean isEmpty(String in) {
		return in == null || in.trim().length() < 1;
	}
	
	private ClientCompleter clientCompleter;
	
	private ClientCompleter getClientCompleter() {
		if(clientCompleter != null) {
			return clientCompleter;
		}
		clientCompleter = new ClientCompleter(this);
		return clientCompleter;
	}

	/**
	 * Inspector test
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		File outDir = null;
		PersistentHistory history = null;
		if(args.length > 0) {
			outDir = new File(args[0]);
			FileUtils.forceMkdir(outDir);
			
			history = new FileHistory(new File(outDir, "inspector_history"));
		}
		
		System.out.println("Inspector as client mode.");
		String cmd;
		
		AndroidDebugBridge.init(false);
		
		ConsoleReader reader = new ConsoleReader();
		if(outDir != null) {
			reader.setHistory(history);
		}
		
		final InspectorClient inspector = new InspectorClient(reader);
		inspector.setOutDir(outDir);
		
		// reader.setPrompt("> ");
		if (reader.getCompletionHandler() instanceof CandidateListCompletionHandler) {
			CandidateListCompletionHandler.class.cast(reader.getCompletionHandler()).setPrintSpaceAfterFullCompletion(false);
		}
		
		reader.addCompleter(inspector.getClientCompleter());
		reader.addCompleter(new FileNameCompleter(inspector, outDir, "lua", "pcap"));
		reader.addCompleter(new FileNameCompleter(null, outDir, "apk"));

		Thread thread = new Thread(inspector, "Socket client");
		thread.setDaemon(true);
		thread.start();
		AndroidDebugBridgeManager manager = new AndroidDebugBridgeManager(inspector, inspector);
		inspector.manager = manager;
		AndroidDebugBridge.addDebugBridgeChangeListener(manager);
		AndroidDebugBridge.addDeviceChangeListener(manager);
		AndroidDebugBridge.createBridge();
		while((cmd = reader.readLine()) != null) {
			cmd = cmd.trim();
			
			if("clear".equals(cmd)) {
				reader.clearScreen();
				reader.flush();
				continue;
			}
			
			if("quit".equals(cmd) ||
					"exit".equals(cmd)) {
				break;
			}
			
			if("resetLog".equalsIgnoreCase(cmd)) {
				inspector.resetLog();
				continue;
			}
			
			String[] tokens = cmd.split("\\s+");
			if(tokens.length > 0 && "logcat".equalsIgnoreCase(tokens[0])) {
				String tag = "";
				if(tokens.length > 1) {
					tag = tokens[1].trim();
				}

				manager.setLogcatTag(tag);
				continue;
			}
			if(tokens.length > 0 &&
					"history".equalsIgnoreCase(tokens[0])) {
				String kw = "";
				if(tokens.length > 1) {
					kw = tokens[1].trim();
				}
				for(Entry entry : reader.getHistory()) {
					if(entry.value().toString().contains(kw)) {
						System.out.println(String.format("%d: %s", entry.index() + 1, entry.value()));
					}
				}
				continue;
			}
			
			if("reboot".equalsIgnoreCase(cmd) && inspector.adb != null) {
				for(IDevice device : inspector.adb.getDevices()) {
					if(device.isOnline()) {
						try {
							device.reboot(null);
						} catch (Throwable e) {
							log.warn(e.getMessage(), e);
						}
					}
				}
				continue;
			}
			
			if("screenShot".equalsIgnoreCase(cmd) && inspector.adb != null &&
					inspector.outDir != null) {
				DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
				for(IDevice device : inspector.adb.getDevices()) {
					try {
						BufferedImage image = screenShot(device);
						if(image == null) {
							continue;
						}
						
						File save = new File(inspector.outDir, "screenshot_" + device.getName() + "_" + dateFormat.format(new Date()) + ".png");
						ImageIO.write(image, "png", save);
						System.out.println("screenshot saved: " + save);
					} catch(Throwable t) {
						t.printStackTrace();
					}
				}
				continue;
			}
			
			if(isEmpty(cmd)) {
				cmd = "eval";
			}
			
			DataOutputStream writer = inspector.writer;
			if(writer == null) {
				if("help".equalsIgnoreCase(cmd)) {
					System.out.println("quit|exit");
					System.out.println("resetLog");
					System.out.println("clear");
					if(inspector.adb != null && inspector.adb.getDevices().length == 1) {
						System.out.println("reboot");
						System.out.println("screenShot");
						// System.out.println("list");
						System.out.println("logcat TAG");
					}
					System.out.println("reset");
					System.out.println("close");
					System.out.println("connect ...");
					System.out.println("adb TCP_ADB");
					continue;
				}

				if("apk".equalsIgnoreCase(FilenameUtils.getExtension(cmd)) && outDir != null) {
					String file = cmd.trim();
					if(file.startsWith("~/")) {
						file = file.substring(2);
					}
					File apk = new File(outDir, file);
					if(apk.canRead() && apk.length() > 0) {
						inspector.installApks.add(apk);
						System.err.println("Add auto install apk: " + apk.getAbsolutePath());
						continue;
					}
				}

				int index;
				if (cmd.startsWith("adb") && (index = cmd.indexOf(' ')) != -1 && inspector.adb != null) {
					String tcp = cmd.substring(index + 1).trim();
					String[] ips = tcp.split(",");
					final List<String> list = new ArrayList<>();
					for (String ip : ips) {
						if (ip.indexOf(':') == -1) {
							ip += ":50001";
						}
						list.add(ip);
					}
					Thread connectThread = new Thread(() -> {
                        System.out.println("Begin auto connect adb: " + list);
                        try {
                            org.apache.commons.exec.Executor executor = new DefaultExecutor();
                            executor.setStreamHandler(new PumpStreamHandler());
                            for (String ip : list) {
                                while (inspector.adb.getDevices().length > 0) {
                                    Thread.sleep(1000);
                                }

                                executor.execute(new CommandLine("adb").addArgument("connect").addArgument(ip));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
					connectThread.setDaemon(true);
					connectThread.start();
					continue;
				}
				
				RemoteServer server = createRemoteServer(inspector, cmd);
				if(server != null) {
					inspector.currentServer = server;
					continue;
				}
				if(cmd.startsWith("connect ") &&
						(index = cmd.indexOf(':', 9)) != -1) {
					String host = cmd.substring(8, index).trim();
					int port = Integer.parseInt(cmd.substring(index + 1).trim());
					InetSocketAddress socketAddress = new InetSocketAddress(host, port);
					inspector.currentServer = new SocketRemoteServer(socketAddress, "custom", 1, "custom", reader);
				}
				continue;
			}
			
			if("reset".equalsIgnoreCase(cmd)) {
				inspector.serverMap.clear();
				manager.reset();
				if(inspector.socket != null) {
					inspector.socket.close();
				}
				inspector.lastProcessName = null;
				continue;
			}
			
			if("close".equalsIgnoreCase(cmd)) {
				inspector.serverMap.clear();
				manager.reset();
				if(inspector.socket != null) {
					inspector.socket.close();
				}
				continue;
			}
			
			if("lua".equalsIgnoreCase(FilenameUtils.getExtension(cmd)) && outDir != null &&
					writeFile(0x1, cmd, outDir, writer)) {
				continue;
			}

			if("pcap".equalsIgnoreCase(FilenameUtils.getExtension(cmd)) && outDir != null &&
					writeFile(0x2, cmd, outDir, writer)) {
				continue;
			}
			
			cmd = cmd.replaceAll("fzp", "FZInspector.print");
			cmd = cmd.replaceAll("fzi", "FZInspector.inspect");
			
			if(cmd.startsWith(":")) {
				cmd = "inspector" + cmd;
			}
			
			writer.writeShort(0x0);
			writer.writeUTF(cmd);
			writer.flush();
			
			if(inspector.logWriter != null) {
				inspector.logWriter.println(DATE_FORMAT.format(new Date()) + "[OUT]" + cmd);
			}
		}

		manager.stop();
		
		if(history != null) {
			history.flush();
		}
		IOUtils.closeQuietly(reader);
		
		if(inspector.logWriter != null) {
			inspector.logWriter.close();
		}

		AndroidDebugBridge.terminate();
	}

	private static boolean writeFile(int command, String cmd, File outDir, DataOutputStream writer) throws IOException {
		String filename = cmd.trim();
		if(filename.startsWith("~/")) {
			filename = filename.substring(2);
		}
		File file = new File(outDir, filename);
		if(file.canRead() && file.length() > 0) {
			writer.writeShort(command);
			writer.writeInt(Long.valueOf(file.length()).intValue());
			InputStream inputStream = null;
			try {
				inputStream = new FileInputStream(file);
				IOUtils.copy(inputStream, writer);
			} finally {
				IOUtils.closeQuietly(inputStream);
			}
			return true;
		}
		return false;
	}

	private static RemoteServer createRemoteServer(InspectorClient inspector, String cmd) {
		RemoteServer server = inspector.serverMap.get(cmd);
		if(server != null) {
			return server;
		}
		
		for(RemoteServer check : inspector.serverMap.values()) {
			if(cmd.equals(check.getProcessName())) {
				return check;
			}
		}
		
		int index = cmd.indexOf(':');
		if(index == -1) {
			return null;
		}
		
		try {
			String host = cmd.substring(0, index).trim();
			int port = Integer.parseInt(cmd.substring(index + 1).trim());
			return new SocketRemoteServer(new InetSocketAddress(host, port), "FakeModel", 0, "Inspector", inspector.reader);
		} catch(Throwable t) {
			return null;
		}
	}

	@Override
	public void run() {
		executeClient();
	}
	
	private DataOutputStream writer;
	
	public boolean isConnected() {
		return writer != null;
	}
	
	private final Map<String, RemoteServer> serverMap = new LinkedHashMap<>();
	private RemoteServer currentServer;
	private String lastProcessName;
	private Socket socket;

	public String getLastProcessName() {
		return lastProcessName;
	}

	public Collection<RemoteServer> getServers() {
		return serverMap.values();
	}
	
	private static final DateFormat DATE_FORMAT = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]");

	private AndroidDebugBridgeManager manager;

	private void executeClient() {
		boolean connected = false;
		long lastReconnectTime = 0;
		while(true) {
			RemoteServer remoteServer = null;
			
			try {
				if(connected) {
					System.err.println("Connection to device lost.");
					reader.setPrompt("");
					writer = null;
					connected = false;
					serverMap.clear();
					if (manager != null) {
						manager.reset();
					}
				}
				
				remoteServer = discoverServer();
				if(remoteServer == null ||
						this.currentServer != null) {
					remoteServer = this.currentServer;
					if(remoteServer == null) {
						Thread.sleep(1000);
						continue;
					}
					
					this.lastProcessName = remoteServer.getProcessName();
					this.currentServer = null;
					socket = remoteServer.connect();
					connected = true;
					
					closeDatagramSocket();
					
					InputStream inputStream = socket.getInputStream();
					DataInputStream in = new DataInputStream(inputStream);
					writer = new DataOutputStream(socket.getOutputStream());
					
					while(true) {
						int type = in.readUnsignedShort();
						boolean zip = (type & 0x8000) != 0;
						type &= 0x7FFF;
						
						DataInputStream reader;
						if(zip) {
							byte[] data = new byte[in.readInt()];
							in.readFully(data);
							GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data));
							reader = new DataInputStream(gzip);
						} else {
							reader = in;
						}
						
						switch (type) {
						case 0x1000:
							String msg = reader.readUTF();
							System.out.print(msg);
							
							if(logWriter != null) {
								logWriter.print(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x1002:
							msg = reader.readUTF();
							System.err.print(msg);
							
							if(logWriter != null) {
								logWriter.print(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x1100:
							int length = reader.readInt();
							byte[] data = new byte[length];
							reader.readFully(data);
							msg = new String(data, "UTF-8");
							System.out.print(msg);
							
							if(logWriter != null) {
								logWriter.print(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x1102:
							length = reader.readInt();
							data = new byte[length];
							reader.readFully(data);
							msg = new String(data, "UTF-8");
							System.err.print(msg);
							
							if(logWriter != null) {
								logWriter.print(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x1001:
							String name = reader.readUTF();
							data = new byte[reader.readInt()];
							reader.readFully(data);
							saveData(name, data);
							break;
						case 0x2000:
							Date date = new Date(reader.readLong());
							String label = reader.readUTF();
							if(reader.readBoolean()) {
								data = new byte[reader.readInt()];
								reader.readFully(data);
							} else {
								data = null;
							}
							int mode = reader.readInt();
							msg = Inspector.inspectString(date, label, data, mode);
							System.out.println(msg);
							
							if(logWriter != null) {
								logWriter.println(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x2001:
							date = new Date(reader.readLong());
							label = reader.readUTF();
							short[] shortData;
							if(reader.readBoolean()) {
								shortData = new short[reader.readInt()];
								for(int i = 0; i < shortData.length; i++) {
									shortData[i] = reader.readShort();
								}
							} else {
								shortData = null;
							}
							mode = reader.readInt();
							msg = Inspector.inspectString(date, label, shortData, mode);
							System.out.println(msg);
							
							if(logWriter != null) {
								logWriter.println(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						case 0x2002:
							String prefix = reader.readUTF();
							ServerCommandCompleter commandCompleter = clientCompleter.createCommandCompleter(prefix);
							int count = reader.readUnsignedShort();
							for(int m = 0; m < count; m++) {
								String command = reader.readUTF();
								int cs = reader.readUnsignedByte();
								String[] help = new String[cs];
								for(int n = 0; n < cs; n++) {
									help[n] = reader.readUTF();
								}
								commandCompleter.addCommandHelp(command, help);
							}
							break;
						case 0x3000:
							date = new Date(reader.readLong());
							length = reader.readInt();
							byte[] labelData = new byte[length];
							reader.readFully(labelData);
							label = new String(labelData);
							if(reader.readBoolean()) {
								data = new byte[reader.readInt()];
								reader.readFully(data);
							} else {
								data = null;
							}
							mode = reader.readInt();
							msg = Inspector.inspectString(date, label, data, mode);
							System.out.println(msg);
							
							if(logWriter != null) {
								logWriter.println(DATE_FORMAT.format(new Date()) + msg);
							}
							break;
						default:
							System.err.println("No handler for type: 0x" + Integer.toHexString(type).toUpperCase());
							break;
						}

						System.out.flush();
						System.err.flush();
					}
				}
				
				String key = remoteServer.getKey();
				if(serverMap.put(key, remoteServer) == null) {
					PrintStream out = remoteServer.isAdb() ? System.err : System.out;
					out.println("Discover “" + remoteServer.getModel() + "” id=" + key + "[" + remoteServer.getProcessName() + ']');
				}

				long currentTimeMillis = System.currentTimeMillis();
				boolean isProcessId = false;
				try {
					isProcessId = Integer.parseInt(this.lastProcessName) > 0;
				} catch(NumberFormatException ignored) {}
				if(currentTimeMillis - lastReconnectTime > TimeUnit.SECONDS.toMillis(5) && (isProcessId || (this.lastProcessName != null &&
						this.lastProcessName.equals(remoteServer.getProcessName())))) {
					this.currentServer = remoteServer;
					lastReconnectTime = currentTimeMillis;
					System.out.println("Try auto connect “" + remoteServer.getModel() + "” id=" + key + "[" + remoteServer.getProcessName() + ']');
				}
			} catch(Exception e) {
				log.debug(e.getMessage(), e);
				
				if(socket != null) {
					try { socket.close(); } catch(IOException ignored) {}
					socket = null;
				}
				if(remoteServer != null) {
					remoteServer.onDisconnect();
				}
				if(clientCompleter != null) {
					clientCompleter.clearServerCommands();
				}
			}
		}
	}

	private void closeDatagramSocket() {
		if(datagramSocket != null) {
			datagramSocket.close();
			datagramSocket = null;
		}
	}

	private void saveData(String name, byte[] data) {
		if(outDir == null) {
			Inspector.inspect(data, "md5=" + DigestUtils.md5Hex(data) + ", dump file: " + name);
			return;
		}
		
		try {
			File out = new File(outDir, name);
			FileUtils.writeByteArrayToFile(out, data);
			System.out.println("md5=" + DigestUtils.md5Hex(data) + ", file saved to: " + out + ", size=" + data.length);
		} catch(IOException e) {
			log.warn(e.getMessage(), e);
		}
	}

	private static final int UDP_PORT = 20000;
	
	protected static int readUnsignedShort(byte[] data, int startIndex) {
		int ch1 = data[startIndex] & 0xFF;
		int ch2 = data[startIndex + 1] & 0xFF;
        return (ch1 << 8) + ch2;
	}
	
	protected static int readInt(byte[] data, int startIndex) {
		int ch1 = data[startIndex] & 0xFF;
		int ch2 = data[startIndex + 1] & 0xFF;
		int ch3 = data[startIndex + 2] & 0xFF;
		int ch4 = data[startIndex + 3] & 0xFF;
		return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
	}
	
	private DatagramSocket datagramSocket;
	private DatagramPacket datagramPacket;

	private synchronized RemoteServer discoverServer() {
		Queue<RemoteServer> queue = this.adbRemoteServerQueue;
		if(!queue.isEmpty()) {
			return queue.poll();
		}
		
		ByteArrayInputStream bais = null;
		DataInputStream dis = null;
		try {
			if(datagramSocket == null) {
				datagramSocket = new DatagramSocket(UDP_PORT);
				datagramSocket.setSoTimeout(5000);
				datagramPacket = new DatagramPacket(new byte[256], 256);
			}
			
			datagramSocket.receive(datagramPacket);
			
			byte[] data = datagramPacket.getData();
			bais = new ByteArrayInputStream(data);
			dis = new DataInputStream(bais);
			int port = dis.readUnsignedShort();
			String model = dis.readUTF();
			int clientCount = dis.readUnsignedByte();
			int processId = -1;
			String processName = null;
			if(dis.available() >= 4) {
				processId = dis.readInt();
				processName = dis.readUTF();
			}
			return new SocketRemoteServer(new InetSocketAddress(datagramPacket.getAddress(), port), model, clientCount + 1, processName, reader);
		} catch (IOException e) {
			// e.printStackTrace();
		} finally {
			close(dis);
			close(bais);
		}
		
		return queue.poll();
	}
	
	private static void close(InputStream is) {
		if(is == null) {
			return;
		}
		
		try {
			is.close();
		} catch(Exception ignored) {}
	}
	protected static void close(OutputStream os) {
		if(os == null) {
			return;
		}
		
		try {
			os.close();
		} catch(Exception ignored) {}
	}
	
	private AndroidDebugBridge adb;

	public AndroidDebugBridge getAdb() {
		return adb;
	}

	public void setAdb(AndroidDebugBridge adb) {
		this.adb = adb;
	}
	
	private Queue<RemoteServer> adbRemoteServerQueue = new LinkedBlockingQueue<>();

	public void addAdbRemoteServer(AdbRemoteServer adbRemoteServer) {
		adbRemoteServerQueue.offer(adbRemoteServer);
		closeDatagramSocket();
	}

	public static String getDeviceName(IDevice device) {
		try {
			return getDeviceManufacturer(device) + '/' + getDeviceModel(device);
		} catch (InterruptedException | ExecutionException e) {
			return null;
		}
	}
	private static String getDeviceModel(IDevice device) throws InterruptedException, ExecutionException {
		return device.getSystemProperty(IDevice.PROP_DEVICE_MODEL).get();
	}
	private static String getDeviceManufacturer(IDevice device) throws InterruptedException, ExecutionException {
		return device.getSystemProperty(IDevice.PROP_DEVICE_MANUFACTURER).get();
	}
	
	private static final int IDA_DEBUG_PORT = 23946;

	private final Set<File> installApks = new LinkedHashSet<>();
	private final Set<String> lastInstallSerialNumber = new HashSet<>();

	@Override
	public void onBootComplete(AndroidDebugBridgeManager manager, IDevice device) {
		try {
			if (!installApks.isEmpty() && !lastInstallSerialNumber.contains(device.getSerialNumber())) {
				installApks(device, 1);
			} else if(device.isOnline()) {
				manager.setLogInstall(false);

				device.createForward(IDA_DEBUG_PORT, IDA_DEBUG_PORT);
				System.err.println("Execute  “" + getDeviceName(device) + "” ida debug port[" + IDA_DEBUG_PORT + "] forward successfully!");
			}
		} catch(Exception e) {
			log.warn(e.getMessage(), e);
		}
	}

	private void installApks(IDevice device, int tryCount) throws Exception {
		InstallException installException = null;
		try {
			for (File installApk : installApks) {
				System.err.println("Begin install " + installApk.getName() + " into " + device.getName());
				device.installPackage(installApk.getAbsolutePath(), false, "-r");
				System.err.println("Install " + installApk.getName() + " into “" + device.getName() + "” successfully!");

				if (installApk.getName().toLowerCase().contains("xposed")) {
					System.err.println("Request start de.robv.android.xposed.installer.WelcomeActivity.");
					device.executeShellCommand("am start -n de.robv.android.xposed.installer/.WelcomeActivity", new NullOutputReceiver());
				}
			}
		} catch (InstallException e) {
			installException = e;
		}
		if (installException != null) {
			if (tryCount > 3) {
				System.err.println("Install apk failed into “" + device.getName() + "”: " + installException.getMessage());
			} else {
				System.err.println("Install apk failed into “" + device.getName() + "”: " + installException.getMessage() + ", retry...");
				installApks(device, tryCount + 1);
			}
		} else {
			if (installApks.size() == 1) { // only one apk
				device.reboot(null);
			}
			lastInstallSerialNumber.add(device.getSerialNumber());
			org.apache.commons.exec.Executor executor = new DefaultExecutor();
			executor.setStreamHandler(new PumpStreamHandler());
			executor.execute(new CommandLine("adb").addArgument("disconnect"));
		}
	}

	@Override
	public void onDeviceConnected(AndroidDebugBridgeManager manager, IDevice device) {
		manager.setLogInstall(installApks.size() > 1);
	}

	@Override
	public void onDeviceDisconnect(AndroidDebugBridgeManager manager, IDevice device) {
		lastInstallSerialNumber.remove(device.getSerialNumber());
	}

	private static BufferedImage screenShot(IDevice device) throws TimeoutException, AdbCommandRejectedException, IOException {
		RawImage rawImage = device.getScreenshot();

		// device/adb not available?
		if (rawImage == null)
			return null;

		// convert raw data to an Image
		BufferedImage image = new BufferedImage(rawImage.width, rawImage.height, BufferedImage.TYPE_INT_ARGB);

		int index = 0;
		int IndexInc = rawImage.bpp >> 3;
		for (int y = 0; y < rawImage.height; y++) {
			for (int x = 0; x < rawImage.width; x++) {
				int value = rawImage.getARGB(index);
				index += IndexInc;
				image.setRGB(x, y, value);
			}
		}
		return image;
	}

}
