import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class MulticastSocketClient {

    final static String INET_ADDR = "224.0.0.3";
    final static int PORT = 8888;

    static String myNick;
    static boolean myNickMessageFlag;


//    loopback
    public static void main(String[] args) throws UnknownHostException {
        // Get the address that we are going to connect to.
        InetAddress address = InetAddress.getByName(INET_ADDR);

        System.out.println("Please write your nick.");
        Scanner scanner = new Scanner(System.in);
        String inputNick = scanner.next();
        System.out.println("Your nick: " + inputNick);

//wprowadzona nazwa wysyłana jest do grupy multicastowej za pomocą polecenia (NICK nazwa)
//i następnie każdy z programów przyłączonych uprzednio do grupy po odebraniu polecenia
//sprawdza unikalność tej nazwy z przypisaną nazwą dla swojego użytkownika i tylko w przypadku
//powtórzenia nazwy wysyła do grupy polecenie NICK nazwa BUSY

        // Open a new DatagramSocket, which will be used to send the data.

        Thread sendingThread = new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket()) {
                    if(myNick == null){
                        String msg = "NICK " + inputNick;
                        DatagramPacket msgPacket = new DatagramPacket(msg.getBytes(),
                        msg.getBytes().length, address, PORT);
                        serverSocket.send(msgPacket);
                        myNickMessageFlag = true;
                    }


//                        String msg = "Sent message no " + i;

                    // Create a packet that will contain the data
                    // (in the form of bytes) and send it.
//                        DatagramPacket msgPacket = new DatagramPacket(msg.getBytes(),
//                                msg.getBytes().length, address, PORT);
//                        serverSocket.send(msgPacket);

//                        System.out.println("Socket sent packet with msg: " + msg);
//                        Thread.sleep(500);

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        Thread receivingThread = new Thread(() -> {
            try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
                //Joint the Multicast group.
                clientSocket.joinGroup(address);
//                    byte[] buf = new byte[256];

                while (true) {
                    byte[] buf = new byte[256];
                    // Receive the information and print it.
                    DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
                    clientSocket.receive(msgPacket);

                    String msg = new String(buf, 0, buf.length);
                    System.out.println("Socket received msg: " + msg);

                    if(!myNickMessageFlag && msg.contains("NICK")){
                        if(msg.split("NICK")[1].trim().equals(myNick)){
//                                System.out.println("MY NICK");
                            String toSend = "NICK " + inputNick + " BUSY";
                            DatagramPacket msgPacketToSend = new DatagramPacket(toSend.getBytes(),
                                    toSend.getBytes().length, address, PORT);
                            try {
                                DatagramSocket serverSocket = new DatagramSocket();
                                serverSocket.send(msgPacketToSend);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        sendingThread.start();
        receivingThread.start();


    }

}

//
//        // Create a buffer of bytes, which will be used to store
//        // the incoming bytes containing the information from the server.
//        // Since the message is small here, 256 bytes should be enough.
//        byte[] buf = new byte[256];
//
//        // Create a new Multicast socket (that will allow other sockets/programs
//        // to join it as well.
//        try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
//            //Joint the Multicast group.
//            clientSocket.joinGroup(address);
//
//            while (true) {
//                // Receive the information and print it.
//                DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
//                clientSocket.receive(msgPacket);
//
//                String msg = new String(buf, 0, buf.length);
//                System.out.println("Socket 1 received msg: " + msg);
//            }
//        } catch (IOException ex) {
//            ex.printStackTrace();
//        }
