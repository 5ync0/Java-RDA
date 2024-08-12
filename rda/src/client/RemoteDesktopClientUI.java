package client;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import javax.imageio.ImageIO;

public class RemoteDesktopClientUI extends JFrame {
    private JLabel screenLabel;
    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private JButton sendFileButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    // Constructor to set up the client UI
    public RemoteDesktopClientUI(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());

            setTitle("Remote Desktop Client");
            setSize(800, 600);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            // Label to display the screen
            screenLabel = new JLabel();
            add(screenLabel, BorderLayout.CENTER);

            // Button panel to send files
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            sendFileButton = new JButton("Send File");
            sendFileButton.setToolTipText("Click to send a file to the server");
            sendFileButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JFileChooser fileChooser = new JFileChooser();
                    int option = fileChooser.showOpenDialog(RemoteDesktopClientUI.this);
                    if (option == JFileChooser.APPROVE_OPTION) {
                        File selectedFile = fileChooser.getSelectedFile();
                        sendFileToServer(selectedFile);
                    }
                }
            });
            buttonPanel.add(sendFileButton);
            add(buttonPanel, BorderLayout.SOUTH);

            // Status panel to display connection status and progress bar
            JPanel statusPanel = new JPanel(new BorderLayout());
            statusLabel = new JLabel("Connection Status: Connected");
            statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
            statusPanel.add(statusLabel, BorderLayout.CENTER);
            progressBar = new JProgressBar();
            progressBar.setStringPainted(true);
            statusPanel.add(progressBar, BorderLayout.SOUTH);
            add(statusPanel, BorderLayout.NORTH);

            // Mouse and keyboard listeners to send control commands to the server
            this.addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    try {
                        outputStream.writeUTF("MOUSE_PRESS");
                        outputStream.writeInt(InputEvent.getMaskForButton(e.getButton()));
                        outputStream.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                public void mouseReleased(MouseEvent e) {
                    try {
                        outputStream.writeUTF("MOUSE_RELEASE");
                        outputStream.writeInt(InputEvent.getMaskForButton(e.getButton()));
                        outputStream.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            this.addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) {
                    try {
                        outputStream.writeUTF("MOUSE_MOVE");
                        outputStream.writeInt(e.getX());
                        outputStream.writeInt(e.getY());
                        outputStream.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Added key listener to the main frame
            this.addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    try {
                        outputStream.writeUTF("KEY_PRESS");
                        outputStream.writeInt(e.getKeyCode());
                        outputStream.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }

                public void keyReleased(KeyEvent e) {
                    try {
                        outputStream.writeUTF("KEY_RELEASE");
                        outputStream.writeInt(e.getKeyCode());
                        outputStream.flush();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Make the frame focusable and request focus
            this.setFocusable(true);
            this.requestFocusInWindow();

            // Thread to continuously receive screen images from the server
            new Thread(() -> {
                try {
                    while (true) {
                        int length = inputStream.readInt();
                        byte[] imageBytes = new byte[length];
                        inputStream.readFully(imageBytes);
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
                        ImageIcon imageIcon = new ImageIcon(image);
                        screenLabel.setIcon(imageIcon);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            pack();
            setLocationRelativeTo(null);
            setVisible(true);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Method to send a file to the server
    public void sendFileToServer(File file) {
        new Thread(() -> {
            try {
                outputStream.writeUTF("FILE_TRANSFER");
                outputStream.writeUTF(file.getName());
                outputStream.writeLong(file.length());

                byte[] buffer = new byte[4096];
                long fileSize = file.length();
                long totalBytesSent = 0;
                try (FileInputStream fileInputStream = new FileInputStream(file)) {
                    int read;
                    while ((read = fileInputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, read);
                        totalBytesSent += read;
                        int progress = (int) ((totalBytesSent * 100) / fileSize);
                        progressBar.setValue(progress);
                        progressBar.setString("Sending: " + progress + "%");
                    }
                }

                outputStream.flush();
                System.out.println("File sent: " + file.getName());
                JOptionPane.showMessageDialog(this, "File sent successfully!", "File Transfer", JOptionPane.INFORMATION_MESSAGE);
                progressBar.setValue(0);
                progressBar.setString("");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Failed to send file: " + ex.getMessage(), "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                ex.printStackTrace();
            }
        }).start();
    }

    // Main method to run the client UI
    public static void main(String[] args) {
        String serverAddress = JOptionPane.showInputDialog(null, "Enter the server IP address:");
        int port = 5000; // Default port, you can make this dynamic if needed
        SwingUtilities.invokeLater(() -> new RemoteDesktopClientUI(serverAddress, port).setVisible(true));
    }
}
