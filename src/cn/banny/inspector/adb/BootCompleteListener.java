package cn.banny.inspector.adb;

import com.android.ddmlib.IDevice;

/**
 * boot complete listener
 * Created by zhkl0228 on 2017/3/13.
 */
public interface BootCompleteListener {

    void onBootComplete(AndroidDebugBridgeManager manager, IDevice device);

    void onDeviceDisconnect(AndroidDebugBridgeManager manager, IDevice device);

    void onDeviceConnected(AndroidDebugBridgeManager manager, IDevice device);

}
