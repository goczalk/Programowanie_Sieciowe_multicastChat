import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class MulticastSocketClient {

    final static String INET_ADDR = "224.0.0.3";
    final static int PORT = 8888;

    static InetAddress address;

    private static String myNick;
    private static String myRoom;
    private static String inputNick;
    private static boolean myNickQuestionFlag;
    private static boolean myNickBusyAnswerFlag;

    //    loopback
    public static void main(String[] args) throws UnknownHostException {
        // Get the address that we are going to connect to.
        address = InetAddress.getByName(INET_ADDR);

        inputAndCheckNick();
        //finaly nick is valid
        inputRoomAndSendJOIN();

        Thread receivingThread = new Thread(() -> {
            try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
                //Joint the Multicast group.
                clientSocket.joinGroup(address);
                while (true) {
                    String message = readMessage(clientSocket);
                    boolean isCommand = recognizeCommand(message);
                    if(!isCommand){
                        System.out.println(message);
                    }
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        receivingThread.start();

        Thread sendingThread = new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket()) {
                Scanner scanner = new Scanner(System.in);
                System.out.println("Write your message: ");

                while(true) {
                    String message = scanner.nextLine();
                    sendMessage(serverSocket, "MSG " + myNick + " " + message);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        sendingThread.start();


    }

    private static void inputRoomAndSendJOIN() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please write name of the room you want to join.");
        myRoom = scanner.next();

        try (DatagramSocket serverSocket = new DatagramSocket()) {
            sendMessage(serverSocket, "JOIN " + myRoom + " " + myNick);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private static boolean setTimeoutReadRecognizeNick(MulticastSocket clientSocket, int timeoutMillis) {
        boolean timeoutTimedOut = false;

        try {
            clientSocket.setSoTimeout(timeoutMillis);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try{
            while (true) {
                String message = readMessage(clientSocket);
                recognizeNICKBUSYanswer(message);
            }
        }
        catch (SocketTimeoutException e) {
            timeoutTimedOut = true;
        }
        finally {
            return timeoutTimedOut;
        }
    }

    private static void inputAndCheckNick() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please write your nick.");
        inputNick = scanner.next();

        myNickQuestionFlag = true;

        try (DatagramSocket serverSocket = new DatagramSocket()) {
            sendMessage(serverSocket, "NICK " + inputNick);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        boolean timeoutTimedOut = false;
        try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
            //Joint the Multicast group.
            clientSocket.joinGroup(address);

            //TODO: change timeout to 10s from 2s.
            //TODO: (BUG) when new user added on other user's timeout -> two users with same nick
            timeoutTimedOut = setTimeoutReadRecognizeNick(clientSocket, 2000); //change to 10s
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if(timeoutTimedOut) {
            System.out.println("Timeout passed. Your nick is: " + inputNick);
            myNick = inputNick;
        }

        myNickQuestionFlag = false;
    }


    // Open a new DatagramSocket, which will be used to send the data.
    private static void sendMessage(DatagramSocket serverSocket, String message){
        DatagramPacket msgPacket = new DatagramPacket(message.getBytes(),
                                                    message.getBytes().length, address, PORT);
        try {
            serverSocket.send(msgPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readMessage(MulticastSocket clientSocket) throws SocketTimeoutException {
        byte[] buf = new byte[256];
        // Receive the information
        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
        try {
            clientSocket.receive(msgPacket);
        } catch (SocketTimeoutException e){
          throw new SocketTimeoutException(); //because IOException is more general!
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(buf, 0, buf.length);
        return message;
    }

    private static boolean recognizeCommand(String message) {
        boolean isCommand = false;
        isCommand = recognizeNICKquestion(message);
        return isCommand;
    }

    private static void recognizeNICKBUSYanswer(String message) {
        if(message.contains("BUSY") && message.contains("NICK") && !myNickBusyAnswerFlag && message.contains(inputNick)){
            System.out.println("Sorry, this nick: " + inputNick + " is already occupied. Try again.");
            myNick = null;
            inputNick = null;
            inputAndCheckNick();
        }
    }

    /**
     *
     * @param message
     * @return true if NICK command, false otherwise
     */
    private static boolean recognizeNICKquestion(String message) {
        if(message.contains("NICK") && !myNickQuestionFlag){
            if(message.split("NICK")[1].trim().equals(myNick)){
                System.out.println("THIS IS MY NICK!");

                String busyMessage = "NICK " + myNick + " BUSY";
                myNickBusyAnswerFlag = true;

                try (DatagramSocket serverSocket = new DatagramSocket()) {
                    sendMessage(serverSocket, busyMessage);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return false;
    }
}
