import java.net.*;
import java.io.*;
import java.lang.Thread;

import java.util.HashMap;
import java.util.Map;


public class TCPClient {
    public static Socket connectToServer(String server, int port) throws Exception {
        Socket socket = new Socket(server, port);
        return socket;
    }

    public static int readLenFromServer(byte[] receiveBuffer, Socket socket, int offset, int length, int timeout) throws Exception {

        int readn = 0;
        int remain = length;
        if(length <= 0) return 0;

        socket.setSoTimeout(timeout);
        while(remain > 0) {
            int received = socket.getInputStream().read(receiveBuffer, offset + readn, remain);
            if(received < 0 || received == 0) break;
            readn += received;
            remain -= received;
        }

        return readn;
    }

    public static int readFromServer(byte[] receiveBuffer, Socket socket, int offset, int maxLength, int timeout) throws Exception {

        int readn = 0;
        socket.setSoTimeout(timeout);
        readn = socket.getInputStream().read(receiveBuffer, offset, maxLength);
        return readn;
    }

    public static void writeToServer(byte[] writeBuffer, Socket socket, int offset, int size, int timeout) throws Exception {

        socket.setSoTimeout(timeout);
        socket.getOutputStream().write(writeBuffer, offset, size);
    }


    public static void main(String args[]) throws Exception {
        Map<String, Socket> serverMap  = new HashMap<>();
        TCPClient tcpClient = new TCPClient();

        try{
            serverMap.put("9101", connectToServer("localhost", 9101));
            serverMap.put("9102", connectToServer("localhost", 9102));
            serverMap.put("9103", connectToServer("localhost", 9103));
            serverMap.put("9104", connectToServer("localhost", 9104));

            for(Socket socket : serverMap.values()) {
                {
                    String writeData = "abcdefghjfkljflksjfsjflkjf";
                    int length = writeData.length();
                    byte[] writeBuffer = writeData.getBytes();
                    tcpClient.writeToServer(writeBuffer, socket, 0, length, 10);

                    byte[] receiveBuffer = new byte[2048];
                    int readn_first = tcpClient.readLenFromServer(receiveBuffer, socket, 0, 10, 10);

                    System.out.println("read first size:" + readn_first + " data:" + new String(receiveBuffer));
                    int readn_second = tcpClient.readFromServer(receiveBuffer, socket, readn_first, 1024, 10);
                    System.out.println("read second size:" + readn_second + " data:" + new String(receiveBuffer));
                }
                {
                    String writeData = "1728974987";
                    int length = writeData.length();
                    byte[] writeBuffer = writeData.getBytes();
                    tcpClient.writeToServer(writeBuffer, socket, 0, length, 10);

                    byte[] receiveBuffer = new byte[2048];
                    int readn_first = tcpClient.readLenFromServer(receiveBuffer, socket, 0, 10, 10);

                    System.out.println("read first size:" + readn_first + " data:" + new String(receiveBuffer));
                    int readn_second = tcpClient.readFromServer(receiveBuffer, socket, readn_first, 1024, 10);
                    System.out.println("read second size:" + readn_second + " data:" + new String(receiveBuffer));
                }
            }

            for(Socket socket: serverMap.values()) {
                socket.close();
            }


        }catch(Exception e){
            System.out.println("Excep:" + e.getMessage());
        }finally{
        }

    }

}
