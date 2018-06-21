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
    private static boolean myWhoIsQuestion;
    private static String allUsersInMyRoom;
    private static boolean busyNick = true;

    public static void main(String[] args) throws UnknownHostException {
        // Get the address that we are going to connect to.
        address = InetAddress.getByName(INET_ADDR);

        while (busyNick) {
            inputAndCheckNick();
        }

        Thread receivingThread = new Thread(() -> {
            try (MulticastSocket clientSocket = new MulticastSocket(PORT)) {
                //Joint the Multicast group.
                clientSocket.joinGroup(address);
                while (true) {
                    String message = readMessage(clientSocket);
                    printMessageOrRecognizeCommand(message);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }

        });
        receivingThread.start();

        inputRoomAndSendJOIN();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                sendLEFTmessage();
            }
        });

        Thread sendingThread = new Thread(() -> {
            try (DatagramSocket serverSocket = new DatagramSocket()) {
                Scanner scanner = new Scanner(System.in);

                while (true) {
                    String message = scanner.nextLine();
                    boolean isCommand = recognizeInput(message);
                    if (!isCommand) {
                        sendMessage(serverSocket, "MSG " + myNick + " " + myRoom + " " + message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        sendingThread.start();

    }

    private static void inputAndCheckNick() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please write your nick.");
        inputNick = scanner.next();

        myNickQuestionFlag = true;

        System.out.println("Wait, checking if your nick is unique...");
        try (DatagramSocket serverSocket = new DatagramSocket()) {
            sendMessage(serverSocket, "NICK " + inputNick);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        boolean timeoutTimedOut = false;
        try (MulticastSocket clientSocket = new MulticastSocket(PORT)) {
            //Joint the Multicast group.
            clientSocket.joinGroup(address);

            //TODO: change timeout to 10s from 2s.
            //TODO: (BUG) when new user added on other user's timeout -> two users with same nick
            timeoutTimedOut = setTimeoutReadRecognizeNick(clientSocket, 2000); //change to 10s
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (timeoutTimedOut) {
            System.out.println("Timeout passed. Your nick is: " + inputNick);
            myNick = inputNick;
            busyNick = false;
        }

        myNickQuestionFlag = false;
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

        System.out.println("To exit the room, type: EXIT " + myRoom);
        System.out.println("To get info who is in the room, type: WHOIS");
        System.out.println("Write your message: ");
    }

    private static String getFirstPartOfSplittedBySpace(String message){
        String msg;
        try {
            msg = message.split(" ")[0].trim();
        } catch (ArrayIndexOutOfBoundsException e){
            return "";
        }
        return msg;
    }

    private static String getSecondPartOfSplittedBySpace(String message){
        String msg;
        try {
            msg = message.split(" ")[1].trim();
        } catch (ArrayIndexOutOfBoundsException e){
            return "";
        }
        return msg;
    }

    private static String getThirdPartOfSplittedBySpace(String message){
        String msg;
        try {
            msg = message.split(" ")[2].trim();
        } catch (ArrayIndexOutOfBoundsException e){
            return "";
        }
        return msg;
    }

    private static void printMessage(String message) {
        String[] partsOfMsg = message.split(" ");
        if (partsOfMsg[2].equals(myRoom)) {
            System.out.print(partsOfMsg[1] + ": ");
            for (int i = 3; i < partsOfMsg.length; i++) {
                System.out.print(partsOfMsg[i] + " ");
            }
            System.out.println();
        }
    }

    private static void printMessageOrRecognizeCommand(String message) {
        if(getFirstPartOfSplittedBySpace(message).equals("MSG")){
            printMessage(message);
        }
        else{
            recognizeNICKquestion(message);
            recognizeJOINcommand(message);
            recognizeLEFTcommand(message);
            recognizeWHOIScommand(message);
        }
    }

    private static String readMessage(MulticastSocket clientSocket) throws SocketTimeoutException {
        byte[] buf = new byte[256];
        // Receive the information
        DatagramPacket msgPacket = new DatagramPacket(buf, buf.length);
        try {
            clientSocket.receive(msgPacket);
        } catch (SocketTimeoutException e) {
            throw new SocketTimeoutException(); //because IOException is more general!
        } catch (IOException e) {
            e.printStackTrace();
        }

        String message = new String(buf, 0, buf.length);
        return message;
    }

    /**
     * @param message
     * @return true if EXIT command, false otherwise
     */
    private static boolean recognizeEXITinput(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("EXIT") && getSecondPartOfSplittedBySpace(message).equals(myRoom)) {
            System.out.println("You have just left room: " + myRoom);

            sendLEFTmessage();

            myRoom = null;
            inputRoomAndSendJOIN();

            return true;
        }
        return false;
    }

    /**
     * @param message
     * @return true if input was command to execute, not message; false otherwise
     */
    private static boolean recognizeInput(String message) {
        boolean isCommand = false;
        if (recognizeEXITinput(message) || recognizeWHOISinput(message)) {
            isCommand = true;
        }
        return isCommand;
    }

    private static void recognizeJOINcommand(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("JOIN")) {
            //rejoining other room, do not print JOIN message about yourself
            if (!message.contains(myNick)) {
                String[] partsOfCommand = message.split(" ");
                if (partsOfCommand[1].trim().equals(myRoom)) {
                    System.out.println(partsOfCommand[2] + " just joined your room!");
                }
            }
        }
    }

    private static void recognizeLEFTcommand(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("LEFT")) {
            //not your LEFT command
            if (!message.contains(myNick)) {
                if (getSecondPartOfSplittedBySpace(message).equals(myRoom)) {
                    String[] partsOfCommand = message.split(" ");
                    System.out.println(partsOfCommand[2] + " just left your room.");
                }
            }
        }
    }

    /**
     * @param message
     * @return true if nick is busy, false otherwise
     */
    private static boolean recognizeNICKBUSYanswer(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("NICK") && !myNickBusyAnswerFlag
                && getSecondPartOfSplittedBySpace(message).equals(inputNick) &&
                getThirdPartOfSplittedBySpace(message).equals("BUSY")) {
            System.out.println("Sorry, this nick: " + inputNick + " is already occupied. Try again.");
            myNick = null;
            inputNick = null;
            myNickQuestionFlag = false;
            return true;
        }
        return false;
    }

    private static void recognizeNICKquestion(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("NICK") && !myNickQuestionFlag) {
            if (getSecondPartOfSplittedBySpace(message).equals(myNick)) {
//                System.out.println("THIS IS MY NICK!");

                String busyMessage = "NICK " + myNick + " BUSY";
                myNickBusyAnswerFlag = true;

                try (DatagramSocket serverSocket = new DatagramSocket()) {
                    sendMessage(serverSocket, busyMessage);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static boolean recognizeROOMcommandAppendAllUsers(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("ROOM")) {
            if (myWhoIsQuestion) {
                String userName = message.split(" ")[2];
                if (allUsersInMyRoom == null) {
                    allUsersInMyRoom = userName;
                } else {
                    allUsersInMyRoom += ", " + userName;
                }
            }
            return true;
        }
        return false;
    }

    private static void recognizeWHOIScommand(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("WHOIS")) {
            if (getSecondPartOfSplittedBySpace(message).equals(myRoom)) {
                try (DatagramSocket serverSocket = new DatagramSocket()) {
                    sendMessage(serverSocket, "ROOM " + myRoom + " " + myNick);
                } catch (SocketException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * @param message
     * @return true is WHOIS input, false otherwise
     */
    private static boolean recognizeWHOISinput(String message) {
        if (getFirstPartOfSplittedBySpace(message).equals("WHOIS")) {
            System.out.println("Wait, collecting data...");
            sendWhoIsQuestionAndWaitForAnswer();
            return true;
        }
        return false;
    }


    private static void sendLEFTmessage() {
        try (DatagramSocket serverSocket = new DatagramSocket()) {
            sendMessage(serverSocket, "LEFT " + myRoom + " " + myNick);
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    // Open a new DatagramSocket, which will be used to send the data.
    private static void sendMessage(DatagramSocket serverSocket, String message) {
        DatagramPacket msgPacket = new DatagramPacket(message.getBytes(),
                message.getBytes().length, address, PORT);
        try {
            serverSocket.send(msgPacket);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void sendWhoIsQuestionAndWaitForAnswer() {
        myWhoIsQuestion = true;

        try (DatagramSocket serverSocket = new DatagramSocket()) {
            sendMessage(serverSocket, "WHOIS " + myRoom);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        boolean timeoutTimedOut = false;
        try (MulticastSocket clientSocket = new MulticastSocket(PORT)) {
            clientSocket.joinGroup(address);

            //TODO check if user can send input
            timeoutTimedOut = setTimeoutReadRoomCommandColectUsers(clientSocket, 2000);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        if (timeoutTimedOut) {
            System.out.println("In your room there is: " + allUsersInMyRoom);
        }

        myWhoIsQuestion = false;
        allUsersInMyRoom = null;
    }

    private static boolean setTimeoutReadRecognizeNick(MulticastSocket clientSocket, int timeoutMillis) {
        boolean timeoutTimedOut = false;

        try {
            clientSocket.setSoTimeout(timeoutMillis);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        boolean nickIsBusy;
        try {
            while (true) {
                String message = readMessage(clientSocket);
                nickIsBusy = recognizeNICKBUSYanswer(message);
                if (nickIsBusy) {
                    break;
                }
            }
        } catch (SocketTimeoutException e) {
            nickIsBusy = false;

            if (!nickIsBusy) {
                timeoutTimedOut = true;
            }
        } finally {
            return timeoutTimedOut;
        }
    }

    private static boolean setTimeoutReadRoomCommandColectUsers(MulticastSocket clientSocket, int timeoutMillis) {
        boolean timeoutTimedOut = false;

        try {
            clientSocket.setSoTimeout(timeoutMillis);
        } catch (SocketException e) {
            e.printStackTrace();
        }

        try {
            while (true) {
                String message = readMessage(clientSocket);
                recognizeROOMcommandAppendAllUsers(message);
            }
        } catch (SocketTimeoutException e) {
            timeoutTimedOut = true;
        } finally {
            return timeoutTimedOut;
        }
    }

}
