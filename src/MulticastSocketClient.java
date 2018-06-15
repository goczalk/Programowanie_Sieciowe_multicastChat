import java.io.IOException;
import java.net.*;
import java.util.Scanner;

public class MulticastSocketClient {

    final static String INET_ADDR = "224.0.0.3";
    final static int PORT = 8888;

    static InetAddress address;

    static String myNick;
    static String inputNick;
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

                while (true) {
                    String message = readMessage(clientSocket);
                    recognizeCommand(message);
                }

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });


        Thread sendingThread = new Thread(() -> {
        });

//        sendingThread.start();
        receivingThread.start();

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
        System.out.println("Your nick: " + inputNick);

        myNickQuestionFlag = true;
        sendMessage("NICK " + inputNick);

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
    private static void sendMessage(String message){
        try (DatagramSocket serverSocket = new DatagramSocket()) {

            DatagramPacket msgPacket = new DatagramPacket(message.getBytes(),
                                    message.getBytes().length, address, PORT);
            serverSocket.send(msgPacket);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String readMessage(MulticastSocket clientSocket) throws SocketTimeoutException {
        byte[] buf = new byte[256];
        // Receive the information and print it.
        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
        try {
            clientSocket.receive(msgPacket);
        } catch (SocketTimeoutException e){
          throw new SocketTimeoutException(); //because IOException is more general!
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(buf, 0, buf.length);
        System.out.println("Received msg: " + message);

        return message;
    }

    private static void recognizeCommand(String message) {
        recognizeNICKquestion(message);
//        recognizeNICKBUSYanswer(message);

    }

    private static void recognizeNICKBUSYanswer(String message) {
        if(message.contains("BUSY") && message.contains("NICK") && !myNickBusyAnswerFlag && message.contains(inputNick)){
            System.out.println("Sorry, this nick: " + inputNick + " is already occupied. Try again.");
            myNick = null;
            inputNick = null;
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
