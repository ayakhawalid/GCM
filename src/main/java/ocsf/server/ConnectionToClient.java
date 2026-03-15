package ocsf.server;

import java.net.*;
import java.io.*;

public class ConnectionToClient extends Thread {
    private AbstractServer server;
    private Socket clientSocket;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private boolean readyToStop;

    ConnectionToClient(ThreadGroup group, Socket clientSocket, AbstractServer server) throws IOException {
        super(group, (Runnable)null);
        this.clientSocket = clientSocket;
        this.server = server;

        clientSocket.setSoTimeout(0); // Disable timeout

        try {
            input = new ObjectInputStream(clientSocket.getInputStream());
            output = new ObjectOutputStream(clientSocket.getOutputStream());
        } catch (IOException ex) {
            try {
                closeAll();
            } catch (Exception exc) { }
            throw ex;
        }

        start();
    }

    final public void sendToClient(Object msg) throws IOException {
        if (clientSocket == null || output == null)
            throw new SocketException("socket does not exist");
        output.writeObject(msg);
        output.reset();
    }

    final public void close() throws IOException {
        readyToStop = true;
        try {
            closeAll();
        } finally {
            server.clientDisconnected(this);
        }
    }

    final public InetAddress getInetAddress() {
        return clientSocket == null ? null : clientSocket.getInetAddress();
    }

    public String toString() {
        return clientSocket == null ? null :
                clientSocket.getInetAddress().getHostName() + " (" + clientSocket.getInetAddress().getHostAddress() + ")";
    }

    public void run() {
        server.clientConnected(this);
        try {
            Object msg;
            while (!readyToStop) {
                try {
                    msg = input.readObject();
                    server.receiveMessageFromClient(msg, this);
                } catch (ClassNotFoundException ex) {
                    // Invalid object received
                } catch (RuntimeException ex) {
                    // Catch any runtime exception
                }
            }
        } catch (Exception exception) {
            if (!readyToStop) {
                try {
                    closeAll();
                } catch (Exception ex) { }
                server.clientException(this, exception);
            }
        }
    }

    private void closeAll() throws IOException {
        if (clientSocket != null) {
            clientSocket.close();
            clientSocket = null;
        }
        if (input != null) {
            input.close();
            input = null;
        }
        if (output != null) {
            output.close();
            output = null;
        }
    }
}