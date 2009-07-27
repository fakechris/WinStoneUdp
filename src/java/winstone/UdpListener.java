/*
 * Copyright 2009-2010 Chris Song <chris__song@hotmail.com>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package winstone;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import winstone.crypto.RC4;

public class UdpListener implements Listener, Runnable {
    protected int listenPort;
    protected String listenAddress;
    protected boolean doHostnameLookups;
    protected boolean interrupted;
    protected ObjectPool objectPool;
    protected DatagramChannel channel;
    protected DatagramSocket socket;
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
		    socket = channel.socket();
		    socket.bind(address);
            Logger.log(Logger.INFO, Launcher.RESOURCES, "UdpListener.StartupOK",
                    new String[] { getConnectorName().toUpperCase(),
                            this.listenPort + "" });

            ByteBuffer in = ByteBuffer.allocate(1024*16);
            
            // Enter the main loop
            while (!interrupted) {
            	in.clear();
            	SocketAddress client = channel.receive(in);
            	
            	RC4 rc4 = new RC4();
            	byte[] result = rc4.rc4(in.array(), 0, in.position());
            	            
            	this.objectPool.handleRequest(client, result, new UdpOutputStream(channel, client), this);
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
		return null;
    }
    
	public String parseURI(RequestHandler handler, WinstoneRequest req,
			WinstoneResponse rsp, WinstoneInputStream inData, 
			SocketAddress peerAddr,
			boolean iAmFirst) throws IOException {

        req.setScheme(getConnectorScheme());
        req.setServerPort(socket.getLocalPort());
        req.setLocalPort(socket.getLocalPort());
        req.setLocalAddr(socket.getLocalAddress().getHostAddress());
        req.setRemoteIP(((InetSocketAddress)peerAddr).getAddress().getHostAddress());
        req.setRemotePort(((InetSocketAddress)peerAddr).getPort());
        if (this.doHostnameLookups) {
            req.setServerName(socket.getLocalAddress().getHostName());
            req.setRemoteName(((InetSocketAddress)peerAddr).getAddress().getHostName());
            req.setLocalName(socket.getLocalAddress().getHostName());
        } else {
            req.setServerName(socket.getLocalAddress().getHostAddress());
            req.setRemoteName(((InetSocketAddress)peerAddr).getAddress().getHostAddress());
            req.setLocalName(socket.getLocalAddress().getHostAddress());
        }
        
        byte uriBuffer[] = null;
        try {
            Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES, "HttpListener.WaitingForURILine");
            uriBuffer = inData.readLine();
        } catch (InterruptedIOException err) {
            throw err;
        } finally {            
        }
        handler.setRequestStartTime();

        // Get header data (eg protocol, method, uri, headers, etc)
        String uriLine = new String(uriBuffer);
        if (uriLine.trim().equals(""))
            throw new SocketException("Empty URI Line");
        String servletURI = parseURILine(uriLine, req, rsp);
        parseHeaders(req, inData);
        rsp.extractRequestKeepAliveHeader(req);
        int contentLength = req.getContentLength();
        if (contentLength != -1)
            inData.setContentLength(contentLength);
        return servletURI;
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
	}

	public boolean processKeepAlive(WinstoneRequest request,
			WinstoneResponse response, InputStream inSocket)
			throws IOException, InterruptedException {
		return true;
	}


	 /**
     * Processes the uri line into it's component parts, determining protocol,
     * method and uri
     */
    private String parseURILine(String uriLine, WinstoneRequest req,
            WinstoneResponse rsp) {
        Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES, "HttpListener.UriLine", uriLine.trim());
        
        // Method
        int spacePos = uriLine.indexOf(' ');
        if (spacePos == -1)
            throw new WinstoneException(Launcher.RESOURCES.getString(
                    "HttpListener.ErrorUriLine", uriLine));
        String method = uriLine.substring(0, spacePos).toUpperCase();
        String fullURI = null;

        // URI
        String remainder = uriLine.substring(spacePos + 1);
        spacePos = remainder.indexOf(' ');
        if (spacePos == -1) {
            fullURI = trimHostName(remainder.trim());
            req.setProtocol("HTTP/0.9");
            rsp.setProtocol("HTTP/0.9");
        } else {
            fullURI = trimHostName(remainder.substring(0, spacePos).trim());
            String protocol = remainder.substring(spacePos + 1).trim().toUpperCase();
            req.setProtocol(protocol);
            rsp.setProtocol(protocol);
        }

        req.setMethod(method);
        // req.setRequestURI(fullURI);
        return fullURI;
    }

    private String trimHostName(String input) {
        if (input == null)
            return null;
        else if (input.startsWith("/"))
            return input;

        int hostStart = input.indexOf("://");
        if (hostStart == -1)
            return input;
        String hostName = input.substring(hostStart + 3);
        int pathStart = hostName.indexOf('/');
        if (pathStart == -1)
            return "/";
        else
            return hostName.substring(pathStart);
    }

    /**
     * Parse the incoming stream into a list of headers (stopping at the first
     * blank line), then call the parseHeaders(req, list) method on that list.
     */
    public void parseHeaders(WinstoneRequest req, WinstoneInputStream inData)
            throws IOException {
        List headerList = new ArrayList();

        if (!req.getProtocol().startsWith("HTTP/0")) {
            // Loop to get headers
            byte headerBuffer[] = inData.readLine();
            String headerLine = new String(headerBuffer);

            while (headerLine.trim().length() > 0) {
                if (headerLine.indexOf(':') != -1) {
                    headerList.add(headerLine.trim());
                    Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                            "HttpListener.Header", headerLine.trim());
                }
                headerBuffer = inData.readLine();
                headerLine = new String(headerBuffer);
            }
        }

        // If no headers available, parse an empty list
        req.parseHeaders(headerList);
    }
}
