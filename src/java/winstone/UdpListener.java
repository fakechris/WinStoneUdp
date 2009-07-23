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

public class UdpListener implements Listener, Runnable {
    protected int listenPort;
    protected String listenAddress;
    protected boolean doHostnameLookups;
    protected boolean interrupted;
    
    protected static boolean DEFAULT_HNL = false;

    protected UdpListener() {
    }
        
    /**
     * Constructor
     */
    public UdpListener(Map args) throws IOException {
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
			DatagramChannel channel = DatagramChannel.open();
		    DatagramSocket socket = channel.socket();
		    socket.bind(address);
            Logger.log(Logger.INFO, Launcher.RESOURCES, "UdpListener.StartupOK",
                    new String[] { getConnectorName().toUpperCase(),
                            this.listenPort + "" });

            ByteBuffer in = ByteBuffer.allocate(1024*16);            
            // max 65k out buffer
            ByteBuffer out = ByteBuffer.allocate(65*1024);
            out.order(ByteOrder.BIG_ENDIAN);
            
            // Enter the main loop
            while (!interrupted) {
            	in.clear();
            	SocketAddress client = channel.receive(in);
            	
                //TODO: process this
            	 out.clear();
                 //out.putLong(secondsSince1970);
                 //out.flip();
                 //out.position(4);
            	 
                 channel.send(out, client);
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
			OutputStream outSocket, RequestHandlerThread handler,
			boolean iAmFirst) throws SocketException, IOException {
		// TODO Auto-generated method stub
		
	}

	public void deallocateRequestResponse(RequestHandlerThread handler,
			WinstoneRequest req, WinstoneResponse rsp,
			WinstoneInputStream inData, WinstoneOutputStream outData)
			throws IOException {
		// TODO Auto-generated method stub
		
	}



	public String parseURI(RequestHandlerThread handler, WinstoneRequest req,
			WinstoneResponse rsp, WinstoneInputStream inData, Socket socket,
			boolean iAmFirst) throws IOException {
		// TODO Auto-generated method stub
		return null;
	}

	public boolean processKeepAlive(WinstoneRequest request,
			WinstoneResponse response, InputStream inSocket)
			throws IOException, InterruptedException {
		// TODO Auto-generated method stub
		return false;
	}

	public void releaseSocket(Socket socket, InputStream inSocket,
			OutputStream outSocket) throws IOException {
		// TODO Auto-generated method stub
		
	}



}
