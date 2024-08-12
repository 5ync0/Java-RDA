package server;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.imageio.ImageIO;

public class RemoteDesktopServerUI extends JFrame {
    private JTextField portField;
    private JButton startButton;
    private JTextArea logArea;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Robot robot;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private JLabel statusLabel;

    // Constructor to set up the UI
    public RemoteDesktopServerUI() {
        setTitle("Remote Desktop Server");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Port label and text field
        JLabel portLabel = new JLabel("Port:");
        gbc.gridx = 0;
        gbc.gridy = 0;
        add(portLabel, gbc);

        portField = new JTextField("5000", 10);
        gbc.gridx = 1;
        gbc.gridy = 0;
        add(portField, gbc);

        // Start button
        startButton = new JButton("Start Server");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        startButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                int port = Integer.parseInt(portField.getText());
                startServer(port);
                logArea.append("Server started on port: " + port + "\n");
                startButton.setEnabled(false);
                statusLabel.setText("Server Status: Running");
            }
        });
        add(startButton, gbc);

        // Log area
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        add(scrollPane, gbc);

        // Status label
        statusLabel = new JLabel("Server Status: Not started");
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.weighty = 0.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        add(statusLabel, gbc);

        // Adjust the frame size and visibility
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Method to start the server
    private void startServer(int port) {
        try {
            serverSocket = new ServerSocket(port);
            robot = new Robot();

            // Start a new thread to handle client connection
            new Thread(() -> {
                try {
                    clientSocket = serverSocket.accept();
                    outputStream = new DataOutputStream(clientSocket.getOutputStream());
                    inputStream = new DataInputStream(clientSocket.getInputStream());

                    // Start a thread to receive control commands
                    new ControlReceiver().start();

                    System.out.println("Client connected");

                    while (true) {
                        // Capture the screen and send it to the client
                        Rectangle screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
                        BufferedImage screenCapture = robot.createScreenCapture(screenRect);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ImageIO.write(screenCapture, "jpg", byteArrayOutputStream);

                        byte[] imageBytes = byteArrayOutputStream.toByteArray();
                        outputStream.writeInt(imageBytes.length);
                        outputStream.write(imageBytes);
                        outputStream.flush();

                        Thread.sleep(100); // Adjust the sleep time as needed
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            setTitle("Remote Desktop Server");
            setSize(400, 400);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Thread to handle control commands from the client
    private class ControlReceiver extends Thread {
        public void run() {
            try {
                while (true) {
                    String command = inputStream.readUTF();
                    switch (command) {
                        case "MOUSE_MOVE":
                            int x = inputStream.readInt();
                            int y = inputStream.readInt();
                            robot.mouseMove(x, y);
                            break;
                        case "MOUSE_PRESS":
                            int pressButton = inputStream.readInt();
                            robot.mousePress(pressButton);
                            break;
                        case "MOUSE_RELEASE":
                            int releaseButton = inputStream.readInt();
                            robot.mouseRelease(releaseButton);
                            break;
                        case "KEY_PRESS":
                            int keyCode = inputStream.readInt();
                            robot.keyPress(keyCode);
                            break;
                        case "KEY_RELEASE":
                            int releaseKeyCode = inputStream.readInt();
                            robot.keyRelease(releaseKeyCode);
                            break;
                        case "FILE_TRANSFER":
                            receiveFile();
                            break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Method to receive a file from the client
    private void receiveFile() {
        try {
            String fileName = inputStream.readUTF();
            long fileSize = inputStream.readLong();
            System.out.println("Receiving file: " + fileName + " (" + fileSize + " bytes)");

            File receivedFile = new File("received_" + fileName);
            FileOutputStream fileOutputStream = new FileOutputStream(receivedFile);
            byte[] buffer = new byte[4096];
            int read;
            long totalRead = 0;
            while (totalRead < fileSize && (read = inputStream.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalRead))) != -1) {
                fileOutputStream.write(buffer, 0, read);
                totalRead += read;
            }
            fileOutputStream.close();

            System.out.println("File received: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Main method to run the server UI
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RemoteDesktopServerUI().setVisible(true));
    }
}
