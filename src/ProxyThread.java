import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProxyThread extends Thread  {
    private static final String CONNECT = "connect";
    private static final String HOST = "host";
    private static final int HTTPS_PORT = 443;
    private static final int HTTP_PORT = 80;
    private static final String OK_STATUS = "HTTP 200 OK\r\n";
    private static final String BAD_GATEWAY = "HTTP 502 Bad Gateway\r\n";
    private Socket client_socket;

    public ProxyThread(Socket client_socket) {
        this.client_socket = client_socket;
    }

    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(client_socket.getInputStream()));
            List<String> request_header = new ArrayList<>();
            String each_line;
            // add request header into the list
            while ((each_line = br.readLine()) != null && !each_line.isEmpty()) {
                request_header.add(each_line);
            }

            // testing example
            System.out.println("browser request header: ");
            for(String l: request_header) {
                System.out.println(l);
            }


            // determine whether it is a connect or non connect request
            if (request_header.size() >= 1) {
                String request = request_header.get(0).trim();
                int space_index = request.indexOf(" ");
                String request_type = request.substring(0, space_index);
                if (request_type.equalsIgnoreCase(CONNECT)) {
                    connectRequest(request_header, br);
                } else {
                    nonConnectRequest(request_header, br);
                }
            }
        } catch (IOException e) {

        }

    }

    // Modify the request header from the client and forward it and payload to the server
    // Modify the response header from the server and forward it and payload to the client
    private void nonConnectRequest(List<String> request_header, BufferedReader br) {

      // Todo: need to determine the host and send the request to the destined host!


        // step1: print the first line of HTTP request
        System.out.println("first line of request: " + request_header.get(0));

        // step2: turn-off the keep-alive
        boolean containsBody = false;
        String connection = "Connection: keep-alive";
        String proxyConnection = "Proxy-connection: keep-alive";
        for(int i = 0; i < request_header.size(); i++) {
            String element = request_header.get(i);

            if(element.contains("Content-Length")) {
                containsBody = true;
            }

            if(element.equals(connection)) {
                request_header.set(i, "Connection: close");
            }
            if(element.equals(proxyConnection)) {
                request_header.set(i, "Proxy-connection: close");
            }
        }

        // step3: lower HTTP request version to HTTP 1.0
        String[] firstLine = request_header.get(0).split(" ");
        firstLine[firstLine.length - 1] = "HTTP/1.0";
        request_header.set(0, Arrays.stream(firstLine).collect(Collectors.joining(" ")));


        // step4: send modified header and send the remaining body

        int portNumber = getPortNumber(request_header);
        String hostname = getHostName(request_header);


        try(
            Socket socket = new Socket(hostname, portNumber);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            DataOutputStream dOut = new DataOutputStream(socket.getOutputStream());
        ) {

            System.out.println();
            System.out.println("Modified request header: ");

            // send the request to the server
            // send modified request header
            StringBuffer requestHeader = new StringBuffer();
            for(String line: request_header) {
                requestHeader.append(line + "\r\n");
            }
            // add the empty line
            requestHeader.append("\r\n");
            dOut.writeBytes(requestHeader.toString());


            // send request payload if there is one
            String eachLine;
            if(containsBody) {
                while((eachLine = br.readLine()) != null) {
                    dOut.writeBytes(eachLine);
                }
            }

            // read the response back from the server
            boolean resBody = false;
            StringBuffer response = new StringBuffer();
            String resLine;
            while( (resLine = in.readLine()) != null && !resLine.isEmpty()) {
                if(resLine.contains("Content-Length")) {
                    resBody = true;
                }
                response.append(resLine + "\r\n");
            }
            response.append("\r\n");
            if(resBody) {
                while((resLine = in.readLine()) != null && !resLine.isEmpty()) {
                    response.append(resLine);
                }
            }


            System.out.println();
            System.out.println("Response from server: ");
            System.out.println(response.toString());

            // write the response to browser
            DataOutputStream outToBrowser = new DataOutputStream(client_socket.getOutputStream());
            outToBrowser.writeBytes(response.toString());
            outToBrowser.flush();

            socket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectRequest(List<String> request_header, BufferedReader bufferedReader) {
        int port_num = getPortNumber(request_header);
        String host_name = getHostName(request_header);

        DataOutputStream client_proxy_dto = null;
        try {
            client_proxy_dto = new DataOutputStream(client_socket.getOutputStream());
        } catch (IOException e) {
            System.out.println("Can't get outputStream from the socket");
        }
        if (host_name != null) {
            Socket socket_proxy_server = null;
            try {
                socket_proxy_server = new Socket(host_name, port_num);
                socket_proxy_server.setKeepAlive(true);
            } catch (UnknownHostException u) {
                System.out.println("Invalid host name. " + u.getMessage());
                try{
                    // tell the client that the host name is not valid
                    // if fail write sth back to the client and close the connection
                    client_proxy_dto.writeBytes(BAD_GATEWAY);
                    client_proxy_dto.flush();
                    client_proxy_dto.close();
                    client_socket.close();
                } catch (IOException e) {
                    System.out.println("Can't perform write on outputStream");
                }
            } catch (IOException e) {
                System.out.print(e.getMessage());
            }

            // if success write sth back to the client
            // established a connection between client-proxy, proxy-server and relay this information
            // keep connection alive
            try {
                client_proxy_dto.writeBytes(OK_STATUS);
                client_proxy_dto.writeBytes("\r\n");
                client_proxy_dto.flush();

                // write stuff from client to server through socket_proxy_server
                InputStream proxy_server_inputStream = socket_proxy_server.getInputStream();
                InputStreamReader proxy_server_inputStreamReader = new InputStreamReader(proxy_server_inputStream);
                BufferedReader proxy_server_bufReader = new BufferedReader(proxy_server_inputStreamReader);

                DataOutputStream proxy_server_dto = new DataOutputStream(socket_proxy_server.getOutputStream());



                client_proxy_dto.close();
                socket_proxy_server.close();
                client_socket.close();




                // Write stuff from client to server
//                String data_from_client;
//                while( !(data_from_client = bufferedReader.readLine()).isEmpty() && data_from_client != null) {
//                    proxy_server_dto.writeBytes(data_from_client);
//                }
                // Write stuff from server to client
//                String data_from_server;
//                while ((data_from_server = proxy_server_bufReader.readLine()) != null && !data_from_server.isEmpty()) {
//                    client_proxy_dto.writeBytes(data_from_server);
//                }
            } catch (IOException e) {
                System.out.println("Can't perform outputStream write");
            }
        } else {
            System.out.println("Invalid host name");
            try{
                client_proxy_dto.writeBytes(BAD_GATEWAY);
                client_proxy_dto.writeBytes("\r\n");
                client_proxy_dto.flush();
                client_proxy_dto.close();
                client_socket.close();
            } catch (IOException e) {
                System.out.println("Can't perform write on outputStream");
            }
        }
    }

    private String getHostName(List<String> request_header) {
        String hostname = "";
        for(String s: request_header) {
            if(s.trim().substring(0,4).equalsIgnoreCase("host")) {
                String tmp = s.trim().split(" ")[1];
                if(tmp.contains(":")) {
                     int index = tmp.indexOf(":");
                    hostname = tmp.substring(0, index);
                } else {
                    hostname = tmp;
                }
                break;
            }
        }
        return hostname;
    }

    private int getPortNumber(List<String> request_header) {
        int size = request_header.size();
        if (size < 2) {
            return -1;
        }
        int request_port_num = HTTP_PORT;

        String regex = ":\\d{3,}";
        Pattern pattern = Pattern.compile(regex);

        // get the server info from the client request header and establish a connection with the server
        for (int i = 0; i < size; i++) {
            String header_component = request_header.get(i).trim();
            if (i > 0) {
                int len = HOST.length();
                if (!header_component.substring(0, len).equalsIgnoreCase(HOST)) {
                    continue;
                }
            }

            Matcher matcher = pattern.matcher(header_component);
            if (matcher.find()) {
                request_port_num = Integer.parseInt(matcher.group().substring(1));
            } else {
                if (header_component.matches("https://")) {
                    request_port_num = HTTPS_PORT;
                }
            }
            if (i != 0) {
                break;
            }
        }
        return request_port_num;
    }
}
