package cn.banny.inspector;

import java.io.PrintWriter;

public interface Plugin {

    void onInitialize(InspectorClient client);

    void handleMsg(int type, String msg, PrintWriter logWriter);
}
