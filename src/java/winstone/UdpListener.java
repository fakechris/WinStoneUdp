/*
 * Copyright 2009-2010 Chris Song <chris__song@hotmail.com>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package winstone;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;
import java.util.Map;

import winstone.crypto.RC4;

public class UdpListener implements Listener, Runnable {
    protected int listenPort;
    protected String listenAddress;
    protected boolean doHostnameLookups;
    protected boolean interrupted;
    protected ObjectPool objectPool;
    protected DatagramChannel channel;
    protected HostGroup hostGroup;
    
    protected static boolean DEFAULT_HNL = false;

    protected UdpListener() {
    }
        
    /**
     * Constructor
     */
    public UdpListener(Map args, ObjectPool objectPool, HostGroup hostGroup) throws IOException {
    	this.objectPool = objectPool;
    	this.hostGroup = hostGroup;
        // Load resources
        this.listenPort = Integer.parseInt(WebAppConfiguration.stringArg(args,
                getConnectorName() + "Port", "" + getDefaultPort()));
        this.listenAddress = WebAppConfiguration.stringArg(args,
                getConnectorName() + "ListenAddress", null);
        this.doHostnameLookups = WebAppConfiguration.booleanArg(args,
                getConnectorName() + "DoHostnameLookups", DEFAULT_HNL);
    }
    
    /**
     * The default port to use - this is just so that we can override for the
     * SSL connector.
     */
    protected int getDefaultPort() {
        return 36969;
    }

    /**
     * The name to use when getting properties - this is just so that we can
     * override for the SSL connector.
     */
    protected String getConnectorName() {
        return getConnectorScheme();
    }

    protected String getConnectorScheme() {
        return "udp";
    }

	public boolean start() {
        if (this.listenPort < 0) {
            return false;
        } else {
            this.interrupted = false;
            Thread thread = new Thread(this, Launcher.RESOURCES.getString(
                    "Listener.ThreadName", new String[] { getConnectorName(),
                            "" + this.listenPort }));
            thread.setDaemon(true);
            thread.start();
            return true;
        }
	}
	
    /**
     * Interrupts the listener thread. This will trigger a listener shutdown
     * once the so timeout has passed.
     */
    public void destroy() {
        this.interrupted = true;
    }
        
	public void run() {
		try {
			SocketAddress address = new InetSocketAddress(this.listenPort);
			channel = DatagramChannel.open();
		    DatagramSocket socket = channel.socket();
		    socket.bind(address);
            Logger.log(Logger.INFO, Launcher.RESOURCES, "UdpListener.StartupOK",
                    new String[] { getConnectorName().toUpperCase(),
                            this.listenPort + "" });

            ByteBuffer in = ByteBuffer.allocate(1024*16);
            
            // max 65k out buffer
            //ByteBuffer out = ByteBuffer.allocate(65*1024);
            //out.order(ByteOrder.BIG_ENDIAN);
            
            // Enter the main loop
            while (!interrupted) {
            	in.clear();
            	SocketAddress client = channel.receive(in);
            	
            	RC4 rc4 = new RC4();
            	byte[] result = rc4.rc4(in.array(), in.position());
            	
            	this.objectPool.handleRequest(client, result, this);
            	
                //TODO: process this
            	// out.clear();
                 //out.putLong(secondsSince1970);
                 //out.flip();
                 //out.position(4);
            	 
                 //channel.send(out, client);
            }

            socket.close();
            channel.close();

        } catch (Throwable err) {
            Logger.log(Logger.ERROR, Launcher.RESOURCES, "UdpListener.ShutdownError",
                    getConnectorName().toUpperCase(), err);
        }

        Logger.log(Logger.INFO, Launcher.RESOURCES, "UdpListener.ShutdownOK",
                getConnectorName().toUpperCase());
	}
    
	public void allocateRequestResponse(Socket socket, InputStream inSocket,
			OutputStream outSocket, RequestHandler handler,
			boolean iAmFirst) throws SocketException, IOException {
        Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                "UdpListener.AllocatingRequest", Thread.currentThread()
                        .getName());
        socket.setSoTimeout(CONNECTION_TIMEOUT);

        // Build input/output streams, plus request/response
        WinstoneInputStream inData = new WinstoneInputStream(inSocket);
        WinstoneOutputStream outData = new WinstoneOutputStream(outSocket, false);
        WinstoneRequest req = this.objectPool.getRequestFromPool();
        WinstoneResponse rsp = this.objectPool.getResponseFromPool();
        outData.setResponse(rsp);
        req.setInputStream(inData);
        rsp.setOutputStream(outData);
        rsp.setRequest(req);
        // rsp.updateContentTypeHeader("text/html");
        req.setHostGroup(this.hostGroup);

        // Set the handler's member variables so it can execute the servlet
        handler.setRequest(req);
        handler.setResponse(rsp);
        handler.setInStream(inData);
        handler.setOutStream(outData);
        
        // If using this listener, we must set the server header now, because it
        // must be the first header. Ajp13 listener can defer to the Apache Server
        // header
        rsp.setHeader("Server", Launcher.RESOURCES.getString("ServerVersion"));
	}

	public String parseURI(RequestHandler handler, WinstoneRequest req,
			WinstoneResponse rsp, WinstoneInputStream inData, Socket socket,
			boolean iAmFirst) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public void deallocateRequestResponse(RequestHandler handler,
			WinstoneRequest req, WinstoneResponse rsp,
			WinstoneInputStream inData, WinstoneOutputStream outData)
			throws IOException {
        handler.setInStream(null);
        handler.setOutStream(null);
        handler.setRequest(null);
        handler.setResponse(null);
        if (req != null)
            this.objectPool.releaseRequestToPool(req);
        if (rsp != null)
            this.objectPool.releaseResponseToPool(rsp);
	}

	public void releaseSocket(Socket socket, InputStream inSocket,
			OutputStream outSocket) throws IOException {
        inSocket.close();
        outSocket.close();
        socket.close();
	}

	public boolean processKeepAlive(WinstoneRequest request,
			WinstoneResponse response, InputStream inSocket)
			throws IOException, InterruptedException {
		return true;
	}


}
