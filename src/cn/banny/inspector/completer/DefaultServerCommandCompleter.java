package cn.banny.inspector.completer;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;

import com.google.common.base.Strings;

/**
 * @author zhkl0228
 *
 */
public class DefaultServerCommandCompleter implements ServerCommandCompleter {
	
	private final String prefix;
	private final Map<String, String[]> map = new HashMap<>();

	DefaultServerCommandCompleter(String prefix) {
		super();
		this.prefix = prefix;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.completer.ServerCommandCompleter#getPrefix()
	 */
	@Override
	public String getPrefix() {
		return prefix;
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.completer.ServerCommandCompleter#exposeCommands(java.util.SortedSet)
	 */
	@Override
	public void exposeCommands(SortedSet<String> strings) {
		strings.addAll(map.keySet());
	}

	/* (non-Javadoc)
	 * @see cn.banny.inspector.completer.ServerCommandCompleter#getHelp(java.lang.String)
	 */
	@Override
	public String[] getHelp(String buf) {
		return map.get(buf);
	}

	@Override
	public ServerCommandCompleter addCommandHelp(String command, String... help) {
		this.map.put(command, help);
		return this;
	}

	@Override
	public boolean isGlobal() {
		return Strings.isNullOrEmpty(prefix);
	}

}
