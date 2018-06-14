import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class MulticastSocketClient {

    final static String INET_ADDR = "224.0.0.3";
    final static int PORT = 8888;

    static InetAddress address;

    static String myNick;
    static boolean myNickQuestionFlag;
    static boolean myNickBusyAnswerFlag;

//    loopback
    public static void main(String[] args) throws UnknownHostException {
        // Get the address that we are going to connect to.
        address = InetAddress.getByName(INET_ADDR);

        inputAndCheckNick();

//wprowadzona nazwa wysyłana jest do grupy multicastowej za pomocą polecenia (NICK nazwa)
//i następnie każdy z programów przyłączonych uprzednio do grupy po odebraniu polecenia
//sprawdza unikalność tej nazwy z przypisaną nazwą dla swojego użytkownika i tylko w przypadku
//powtórzenia nazwy wysyła do grupy polecenie NICK nazwa BUSY


        Thread receivingThread = new Thread(() -> {
            try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
                //Joint the Multicast group.
                clientSocket.joinGroup(address);

                String message = readMessage(clientSocket);
                recognizeCommand(message);

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });


        Thread sendingThread = new Thread(() -> {
        });

//        sendingThread.start();
        receivingThread.start();

    }

    private static boolean setTimeoutReadRecognizeNick(MulticastSocket clientSocket, boolean setTimeoutFlag, int timeoutMillis) {
        boolean timeoutPassed = false;
        if(setTimeoutFlag){
            try {
                clientSocket.setSoTimeout(timeoutMillis);
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }

        try{
            while (true) {
                String message = readMessage(clientSocket);
                recognizeNICKBUSYanswer(message);
            }
        }
        catch (SocketTimeoutException e) {
            timeoutPassed = true;
        }
        finally {
            return timeoutPassed;
        }
    }

    private static void inputAndCheckNick() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please write your nick.");
        String inputNick = scanner.next();
        System.out.println("Your nick: " + inputNick);

        myNickQuestionFlag = true;
        sendMessage("NICK " + inputNick);

        boolean timeoutPassed = false;
        try (MulticastSocket clientSocket = new MulticastSocket(PORT)){
            //Joint the Multicast group.
            clientSocket.joinGroup(address);

            timeoutPassed = setTimeoutReadRecognizeNick(clientSocket, true, 2000); //change to 10s
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if(timeoutPassed) {
//            myNick = inputNick;
            System.out.println("TIMEOUT PASSED");
        }

        myNickQuestionFlag = false;
    }


    // Open a new DatagramSocket, which will be used to send the data.
    private static void sendMessage(String message){
        try (DatagramSocket serverSocket = new DatagramSocket()) {
//            if(myNick == null){
            DatagramPacket msgPacket = new DatagramPacket(message.getBytes(),
                                    message.getBytes().length, address, PORT);
            serverSocket.send(msgPacket);

//            myNickQuestionFlag = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readMessage(MulticastSocket clientSocket) throws SocketTimeoutException{
        byte[] buf = new byte[256];
        // Receive the information and print it.
        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
        try {
            clientSocket.receive(msgPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(buf, 0, buf.length);
        System.out.println("Received msg: " + message);

        return message;
    }

    private static void recognizeCommand(String message) {
        recognizeNICKquestion(message);
        recognizeNICKBUSYanswer(message);

    }

    private static void recognizeNICKBUSYanswer(String message) {
        //message = message.trim(); <- bo timeout?
        if(message.contains("BUSY") && message.contains("NICK") && !myNickBusyAnswerFlag && message.contains(myNick)){
            System.out.println("Sorry, this nick:" + myNick + "is already occupied. Please, write different");
            myNick = null;

            inputAndCheckNick();
        }
    }

    private static void recognizeNICKquestion(String message) {
        if(message.contains("NICK") && !myNickQuestionFlag){
            if(message.split("NICK")[1].trim().equals(myNick)){
                System.out.println("THIS IS MY NICK!");

                String busyMessage = "NICK " + myNick + " BUSY";
                myNickBusyAnswerFlag = true;
                sendMessage(busyMessage);
            }
        }
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
