package cn.banny.inspector.completer;

import java.util.SortedSet;

/**
 * @author zhkl0228
 *
 */
public interface ServerCommandCompleter {
	
	/**
	 * 命令前缀
	 * @return null或者空字符串表示全局命令
	 */
	String getPrefix();
	
	boolean isGlobal();
	
	void exposeCommands(SortedSet<String> strings);
	
	String[] getHelp(String buf);
	
	ServerCommandCompleter addCommandHelp(String command, String...help);

}
