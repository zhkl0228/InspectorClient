package cn.banny.inspector.completer;

import cn.banny.inspector.InspectorClient;
import cn.banny.inspector.RemoteServer;
import com.android.ddmlib.AndroidDebugBridge;
import jline.console.completer.Completer;

import java.util.*;

import static jline.internal.Preconditions.checkNotNull;

/**
 * @author zhkl0228
 *
 */
public class ClientCompleter implements Completer {
	
	private final InspectorClient inspector;
	private final SortedSet<String> strings = new TreeSet<>();

	public ClientCompleter(InspectorClient inspector) {
		super();
		
		this.inspector = inspector;
	}

    public int complete(final String buffer, final int cursor, final List<CharSequence> candidates) {
        try {
        	strings.add("quit");
        	strings.add("exit");
        	strings.add("resetLog");
        	strings.add("help");
        	strings.add("clear");
        	strings.add("history");
        	
        	AndroidDebugBridge adb;
        	if((adb = inspector.getAdb()) != null && adb.getDevices().length == 1) {
        		strings.add("reboot");
        		strings.add("screenShot");
        		// strings.add("list");
        		strings.add("logcat");
        	}
			if (inspector.getTraceFile() != null) {
				strings.add("trace");
			}
			if(inspector.isConnected()) {
        		strings.add("reset");
        		strings.add("close");
        		addServerCommands(buffer);
        	} else {
        		strings.add("connect");
        		
        		for(RemoteServer server : inspector.getServers()) {
        			strings.add(server.getProcessName());
        		}
        	}
        	
        	// buffer could be null
            checkNotNull(candidates);

            if (buffer == null) {
                candidates.addAll(strings);
            } else {
                for (String match : strings.tailSet(buffer)) {
                    if (!match.startsWith(buffer)) {
                        break;
                    }

                    candidates.add(match);
                }
            }
            
            if(inspector.isConnected()) {
            	String buf = String.valueOf(buffer).trim();
            	String[] help = getHelp(buf);
            	if(help != null) {
            		System.out.println();
            		for(String str : help) {
            			System.out.println(str);
            		}
            		System.out.print(buffer);
            	}
            }

            return candidates.isEmpty() ? -1 : 0;
        } finally {
        	strings.clear();
        }
    }
	
	private String[] getHelp(String buf) {
		for(ServerCommandCompleter completer : serverCommands) {
			String[] help = completer.getHelp(buf);
			if(help != null) {
				return help;
			}
		}
		return null;
	}

	private final Set<ServerCommandCompleter> serverCommands = new HashSet<>();
	
	public void clearServerCommands() {
		serverCommands.clear();
	}
	
	public ServerCommandCompleter createCommandCompleter(String prefix) {
		ServerCommandCompleter completer = new DefaultServerCommandCompleter(prefix);
		serverCommands.add(completer);
		return completer;
	}

	private void addServerCommands(String buffer) {
		for(ServerCommandCompleter completer : this.serverCommands) {
			if(completer.isGlobal()) {
				completer.exposeCommands(strings);
				continue;
			}
			
			strings.add(completer.getPrefix());
			if(buffer.startsWith(completer.getPrefix())) {
				completer.exposeCommands(strings);
			}
		}
	}

}
