package ocsf.server;

import java.net.*;
import java.io.*;

public abstract class AbstractServer implements Runnable {
    private ServerSocket serverSocket = null;
    private Thread connectionListener = null;
    private int port;
    private int timeout = 500;
    private int backlog = 10;
    private ThreadGroup clientThreadGroup;
    private boolean readyToStop = false;

    public AbstractServer(int port) {
        this.port = port;
        this.clientThreadGroup = new ThreadGroup("ConnectionToClient threads") {
            public void uncaughtException(Thread thread, Throwable exception) {
                clientException((ConnectionToClient)thread, exception);
            }
        };
    }

    public void listen() throws IOException {
        if (!isListening()) {
            if (serverSocket == null) {
                serverSocket = new ServerSocket(getPort(), backlog);
            }
            serverSocket.setSoTimeout(timeout);
            connectionListener = new Thread(this);
            connectionListener.start();
        }
    }

    public void stopListening() {
        readyToStop = true;
    }

    public void close() throws IOException {
        if (serverSocket == null) return;
        stopListening();
        try {
            serverSocket.close();
        } finally {
            synchronized (this) {
                Thread[] clientThreadList = getClientConnections();
                for (int i=0; i<clientThreadList.length; i++) {
                    try {
                        ((ConnectionToClient)clientThreadList[i]).close();
                    } catch(Exception ex) {}
                }
                serverSocket = null;
            }
            serverClosed();
        }
    }

    public void sendToAllClients(Object msg) {
        Thread[] clientThreadList = getClientConnections();
        for (int i=0; i<clientThreadList.length; i++) {
            try {
                ((ConnectionToClient)clientThreadList[i]).sendToClient(msg);
            } catch (Exception ex) {}
        }
    }

    final public boolean isListening() {
        return (connectionListener != null);
    }

    final public boolean isClosed() {
        return (serverSocket == null);
    }

    synchronized final public Thread[] getClientConnections() {
        Thread[] clientThreadList = new Thread[clientThreadGroup.activeCount()];
        clientThreadGroup.enumerate(clientThreadList);
        return clientThreadList;
    }

    final public int getNumberOfClients() {
        return clientThreadGroup.activeCount();
    }

    final public int getPort() {
        return port;
    }

    final public void setPort(int port) {
        this.port = port;
    }

    final public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    final public void setBacklog(int backlog) {
        this.backlog = backlog;
    }

    final public void run() {
        serverStarted();
        try {
            while(!readyToStop) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    synchronized(this) {
                        if (!readyToStop) {
                            new ConnectionToClient(this.clientThreadGroup, clientSocket, this);
                        }
                    }
                } catch (InterruptedIOException exception) {
                    // Timeout occurred, loop again
                }
            }
        } catch (IOException exception) {
            if (!readyToStop) {
                listeningException(exception);
            }
        } finally {
            readyToStop = true;
            connectionListener = null;
            serverStopped();
        }
    }

    protected void clientConnected(ConnectionToClient client) {}
    synchronized protected void clientDisconnected(ConnectionToClient client) {}
    synchronized protected void clientException(ConnectionToClient client, Throwable exception) {}
    protected void listeningException(Throwable exception) {}
    protected void serverStarted() {}
    protected void serverStopped() {}
    protected void serverClosed() {}
    protected abstract void handleMessageFromClient(Object msg, ConnectionToClient client);

    final synchronized void receiveMessageFromClient(Object msg, ConnectionToClient client) {
        this.handleMessageFromClient(msg, client);
    }
}