package cn.banny.inspector.adb;

import cn.banny.inspector.AdbRemoteServer;
import cn.banny.inspector.InspectorClient;
import com.android.ddmlib.*;
import com.android.ddmlib.TimeoutException;
import com.android.ddmlib.logcat.LogCatFilter;
import com.android.ddmlib.logcat.LogCatListener;
import com.android.ddmlib.logcat.LogCatMessage;
import com.android.ddmlib.logcat.LogCatReceiverTask;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * adb device listener
 * Created by zhkl0228 on 2017/3/18.
 */
public class AndroidDebugBridgeManager implements AndroidDebugBridge.IDebugBridgeChangeListener, AndroidDebugBridge.IDeviceChangeListener, Runnable {

    private static final org.apache.commons.logging.Log log = LogFactory.getLog(AndroidDebugBridgeManager.class);

    private final InspectorClient inspectorClient;
    private final Thread thread;
    private final BootCompleteListener bootCompleteListener;
    private final ExecutorService executorService;

    public AndroidDebugBridgeManager(InspectorClient inspectorClient, BootCompleteListener bootCompleteListener) {
        super();

        this.inspectorClient = inspectorClient;
        this.bootCompleteListener = bootCompleteListener;
        this.executorService = Executors.newCachedThreadPool();

        thread = new Thread(this, getClass().getSimpleName());
        thread.start();
    }

    private boolean canStop;

    public void stop() throws InterruptedException {
        canStop = true;

        synchronized (this) {
            this.notifyAll();
        }

        thread.join();
    }

    private final Map<String, IDevice> pendingConnectDevice = new ConcurrentHashMap<>(5);
    private final Map<String, IDevice> connectedDevice = new ConcurrentHashMap<>(5);
    private final Map<String, Date> lastModifiedMap = new ConcurrentHashMap<>(5);

    public void reset() {
        lastModifiedMap.clear();

        synchronized (this) {
            this.notifyAll();
        }
    }

    @Override
    public void run() {
        while (!canStop) {
            try {
                if (!pendingConnectDevice.isEmpty()) {
                    checkPendingConnectDevice();
                }
                if (!connectedDevice.isEmpty()) {
                    checkConnectedDevice();
                }
                synchronized (this) {
                    this.wait(1000);
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
                try { Thread.sleep(1000); } catch(InterruptedException ignored) {}
            }
        }
    }

    private FileListingService.FileEntry proc, inspector;

    private void checkConnectedDevice() {
        for (IDevice device : connectedDevice.values()) {
            SyncService syncService = null;
            try {
                if (!device.isOnline()) {
                    continue;
                }

                FileListingService fileListingService = device.getFileListingService();
                if (proc == null || inspector == null) {
                    FileListingService.FileEntry root = fileListingService.getRoot();
                    fileListingService.getChildrenSync(root);
                    FileListingService.FileEntry sdcard = root.findChild(FileListingService.DIRECTORY_SDCARD);
                    if (sdcard == null) {
                        System.err.println("sdcard is null");
                        continue;
                    }

                    proc = root.findChild("proc");
                    if (proc == null) {
                        System.err.println("proc is null");
                        continue;
                    }

                    fileListingService.getChildrenSync(sdcard);
                    inspector = sdcard.findChild("inspector");
                    if (inspector == null) {
                        // System.err.println("inspector is null");
                        continue;
                    }
                }

                syncService = device.getSyncService();
                if (syncService == null) {
                    continue;
                }

                SyncService.FileStat stat = syncService.statFile(inspector.getFullPath());
                Date lastModified = lastModifiedMap.get(device.getSerialNumber());
                if (lastModified != null && stat != null && lastModified.equals(stat.getLastModified())) {
                    continue;
                }

                FileListingService.FileEntry[] pids = fileListingService.getChildrenSync(inspector);
                for (FileListingService.FileEntry pid : pids) {
                    checkDevicePid(device, syncService, fileListingService, proc, pid);
                }
                if (stat != null) {
                    lastModifiedMap.put(device.getSerialNumber(), stat.getLastModified());
                }
            } catch (TimeoutException | IOException | AdbCommandRejectedException | ShellCommandUnresponsiveException | SyncException e) {
                log.debug(e.getMessage(), e);
            } finally {
                if (syncService != null) {
                    syncService.close();
                }
            }
        }
    }

    private void checkDevicePid(IDevice device, SyncService syncService, FileListingService fileListingService, FileListingService.FileEntry proc, FileListingService.FileEntry pid) throws IOException, TimeoutException, SyncException, AdbCommandRejectedException, ShellCommandUnresponsiveException {
        if (!device.isOnline()) {
            return;
        }

        fileListingService.getChildrenSync(proc);
        if (proc.findChild(pid.getName()) == null) {
            device.executeShellCommand("rm -fr " + pid.getFullEscapedPath(), new NullOutputReceiver());
            return;
        }

        FileListingService.FileEntry[] ports = fileListingService.getChildrenSync(pid);
        for (FileListingService.FileEntry portEntry : ports) {
            File local = File.createTempFile(pid.getName(), "pid");
            try {
                syncService.pullFile(portEntry, local.getAbsolutePath(), SyncService.getNullProgressMonitor());
                int port = Integer.parseInt(portEntry.getName());
                String processName = FileUtils.readFileToString(local);
                inspectorClient.addAdbRemoteServer(new AdbRemoteServer(device, processName, port, inspectorClient.getConsoleReader()));

                if (canStop) {
                    return;
                }
            } finally {
                if (!local.delete()) {
                    local.deleteOnExit();
                }
            }
        }
    }

    private void checkPendingConnectDevice() {
        for (Iterator<IDevice> iterator = pendingConnectDevice.values().iterator(); iterator.hasNext(); ) {
            IDevice device = iterator.next();
            try {
                if (!device.isOnline()) {
                    continue;
                }

                String bootcomplete = device.getSystemProperty("dev.bootcomplete").get(5, TimeUnit.SECONDS);
                if ("1".equals(bootcomplete)) {
                    iterator.remove();
                    System.err.println("device " + InspectorClient.getDeviceName(device) + "[" + device.getSerialNumber() + "] connected");
                    connectedDevice.put(device.getSerialNumber(), device);

                    if (bootCompleteListener != null) {
                        executorService.submit(() -> bootCompleteListener.onBootComplete(AndroidDebugBridgeManager.this, device));
                    }
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    @Override
    public void bridgeChanged(AndroidDebugBridge bridge) {
        inspectorClient.setAdb(bridge);
    }

    private static final String MANAGER_TAG = "Manager";
    private static final String PM_TAG = "PackageManager";

    private final List<IDevice> deviceList = new ArrayList<>(10);

    @Override
    public void deviceConnected(final IDevice device) {
        pendingConnectDevice.put(device.getSerialNumber(), device);

        if (bootCompleteListener != null) {
            bootCompleteListener.onDeviceConnected(this, device);
        }

        LogCatReceiverTask task = new LogCatReceiverTask(device);
        task.addLogCatListener(new LogCatListener() {
            private boolean logFirst;
            @Override
            public void log(List<LogCatMessage> msgList) {
                if (logInstall) {
                    synchronized (deviceList) {
                        DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                        for (LogCatMessage msg : msgList) {
                            if (MANAGER_TAG.equals(msg.getTag())) {
                                System.err.println("[#" + (deviceList.indexOf(device) + 1) + "]" + "[" + dateFormat.format(new Date()) + "][" + device.getSerialNumber() + "]" + msg.getMessage());
                            } else if(!logFirst || (msg.getLogLevel() == Log.LogLevel.INFO && PM_TAG.equals(msg.getTag()))) {
                                if (!logFirst && !deviceList.contains(device)) {
                                    deviceList.add(device);
                                }
                                System.out.println("[#" + (deviceList.indexOf(device) + 1) + "]" + "[" + dateFormat.format(new Date()) + "][" + device.getSerialNumber() + "]" + msg.getMessage());
                                logFirst = true;
                            }
                        }
                    }
                    return;
                }

                String lastProcessName = inspectorClient.getLastProcessName();
                if(lastProcessName == null) {
                    return;
                }

                if(AndroidDebugBridgeManager.this.logCatFilter == null ||
                        !lastProcessName.equals(AndroidDebugBridgeManager.this.logCatFilter.getAppName())) {
                    AndroidDebugBridgeManager.this.logCatFilter = new LogCatFilter("Inspector_logcat", AndroidDebugBridgeManager.this.logcatTag == null ? "" : AndroidDebugBridgeManager.this.logcatTag, "", "", lastProcessName, Log.LogLevel.VERBOSE);
                }

                for(LogCatMessage msg : msgList) {
                    processLogCatMessage(msg);
                }
            }
        });
        Thread thread = new Thread(task, "Inspector_logcat_" + device.getSerialNumber());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void deviceDisconnected(IDevice device) {
        synchronized (deviceList) {
            deviceList.remove(device);
        }

        if (pendingConnectDevice.remove(device.getSerialNumber()) == null) {
            System.err.println("device " + InspectorClient.getDeviceName(device) + "[" + device.getSerialNumber() + "] disconnected");
        }
        connectedDevice.remove(device.getSerialNumber());
        lastModifiedMap.remove(device.getSerialNumber());

        if (bootCompleteListener != null) {
            bootCompleteListener.onDeviceDisconnect(this, device);
        }
    }

    @Override
    public void deviceChanged(IDevice device, int changeMask) {
        log.debug("deviceChanged device=" + device + ", changeMask=0x" + Integer.toHexString(changeMask));
    }

    private boolean logInstall;

    public void setLogInstall(boolean logInstall) {
        this.logInstall = logInstall;
    }

    private String logcatTag;
    private LogCatFilter logCatFilter;

    public void setLogcatTag(String tag) {
        if(this.logcatTag != null) {
            this.logcatTag = null;
        } else {
            this.logcatTag = tag;
        }
        this.logCatFilter = null;

        System.out.println("logcat=" + this.logcatTag);

        if(this.logcatTag != null) {
            synchronized (this.msgQueue) {
                while(this.msgQueue.peek() != null) {
                    processLogCatMessage(this.msgQueue.poll());
                }
            }
        }
    }

    private final Queue<LogCatMessage> msgQueue = new LinkedBlockingQueue<>();

    private void processLogCatMessage(LogCatMessage msg) {
        if(logcatTag == null) {
            if(this.logCatFilter != null &&
                    this.logCatFilter.matches(msg)) {
                synchronized (msgQueue) {
                    msgQueue.offer(msg);

                    while(msgQueue.size() > 5000) {
                        msgQueue.poll();
                    }
                }
            }
            return;
        }

        if(this.logCatFilter != null &&
                this.logCatFilter.matches(msg)) {
            System.out.println(msg);

            inspectorClient.logWrite(msg);
        }
    }

}
