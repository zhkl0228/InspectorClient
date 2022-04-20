package cn.banny.inspector;

import java.io.PrintWriter;

public interface Plugin {

    void onInitialize(InspectorClient client);

    void handleMsg(int type, String msg, PrintWriter logWriter);

    void handleCommand(String type, String data, PrintWriter logWriter);

    void handleCommandLine(String pluginCommand, String[] args);

}
