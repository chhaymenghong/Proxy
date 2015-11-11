import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ProxyServer {

    public static final int PORT_NUM = 46103;
    public static void main(String[] args) {
        System.out.println(".........Proxy is Online..........");
        try {
            ServerSocket serverSocket = new ServerSocket(PORT_NUM);
            while (true) {
                Socket client_socket = serverSocket.accept();
                System.out.println("Accepted a Connection from a client.");
                new ProxyThread(client_socket).start();
            }

        } catch (IOException e) {
            System.out.println("WHATT");
        }
    }
}
