package cn.banny.inspector;

import junit.framework.TestCase;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class AddressTest extends TestCase {

    private static Inet4Address getInet4Address() throws SocketException {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        while (enumeration.hasMoreElements()) {
            NetworkInterface networkInterface = enumeration.nextElement();
            if (networkInterface.isLoopback()) {
                continue;
            }
            Enumeration<InetAddress> addressEnumeration = networkInterface.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress address = addressEnumeration.nextElement();
                if (address instanceof Inet4Address) {
                    return (Inet4Address) address;
                }
            }
        }
        return null;
    }

    public void testAddress() throws Exception {
        Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
        while (enumeration.hasMoreElements()) {
            NetworkInterface networkInterface = enumeration.nextElement();
            if (networkInterface.isLoopback()) {
                continue;
            }
            Enumeration<InetAddress> addressEnumeration = networkInterface.getInetAddresses();
            while (addressEnumeration.hasMoreElements()) {
                InetAddress address = addressEnumeration.nextElement();
                if (address instanceof Inet4Address) {
                    System.out.println(networkInterface.getName() + ", address=" + address);
                }
            }
        }
        System.out.println(getInet4Address());
    }

}
