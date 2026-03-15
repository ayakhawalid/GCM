package ocsf.client;

import java.io.*;
import java.net.*;

public abstract class AbstractClient implements Runnable {

    private Socket clientSocket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private Thread clientReader;
    private boolean readyToStop = false;
    private String host;
    private int port;

    public AbstractClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void openConnection() throws IOException {
        if (isConnected()) return;

        try {
            clientSocket = new Socket(host, port);
            output = new ObjectOutputStream(clientSocket.getOutputStream());
            input = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException ex) {
            try { closeAll(); } catch (Exception exc) {}
            throw ex;
        }

        clientReader = new Thread(this);
        readyToStop = false;
        clientReader.start();
    }

    public void sendToServer(Object msg) throws IOException {
        if (clientSocket == null || output == null) {
            throw new SocketException("socket does not exist");
        }
        output.reset();
        output.writeObject(msg);
    }

    public void closeConnection() throws IOException {
        readyToStop = true;
        closeAll();
    }

    public boolean isConnected() {
        return clientReader != null && clientReader.isAlive();
    }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public void run() {
        connectionEstablished();
        Object msg;

        try {
            while (!readyToStop) {
                try {
                    msg = input.readObject();
                    if (!readyToStop) {
                        handleMessageFromServer(msg);
                    }
                } catch (ClassNotFoundException | RuntimeException ex) {
                    connectionException(ex);
                }
            }
        } catch (Exception exception) {
            if (!readyToStop) {
                try { closeAll(); } catch (Exception ex) {}
                clientReader = null;
                connectionException(exception);
            }
        } finally {
            clientReader = null;
            connectionClosed();
        }
    }

    protected void connectionClosed() {}
    protected void connectionException(Exception exception) {}
    protected void connectionEstablished() {}
    protected abstract void handleMessageFromServer(Object msg);

    private void closeAll() throws IOException {
        try {
            if (clientSocket != null) clientSocket.close();
            if (output != null) output.close();
            if (input != null) input.close();
        } finally {
            output = null;
            input = null;
            clientSocket = null;
        }
    }
}