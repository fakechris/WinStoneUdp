package winstone;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.concurrent.ThreadPoolExecutor;

import javax.servlet.ServletException;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;

public class UdpRequestHandlerThread implements Runnable, RequestHandler {
	private ThreadPoolExecutor poolExecutor;
    private ObjectPool objectPool;
    private WinstoneInputStream inData;
    private WinstoneOutputStream outData;
    private WinstoneRequest req;
    private WinstoneResponse rsp;
    private Listener listener;
    private InputStream inStream;
    private OutputStream outStream;
    private String threadName;
    private long requestStartTime;
    private boolean simulateModUniqueId;
    private boolean saveSessions; 
    private SocketAddress peerAddr;
    
    /**
     * Constructor - this is called by the handler pool, and just sets up for
     * when a real request comes along.
     */
    public UdpRequestHandlerThread(ObjectPool objectPool, int threadIndex, 
            boolean simulateModUniqueId, boolean saveSessions, 
            ThreadPoolExecutor poolExecutor, SocketAddress peerAddr) {
    	this.poolExecutor = poolExecutor;
    	this.peerAddr = peerAddr;
        this.objectPool = objectPool;
        this.simulateModUniqueId = simulateModUniqueId;
        this.saveSessions = saveSessions;
        this.threadName = Launcher.RESOURCES.getString(
                "UDPRequestHandlerThread.ThreadName", "" + threadIndex);
    }
    
    /**
     * The main thread execution code.
     */
	public void run() {
        // Start request processing
        try {	            	
            // Get input/output streams
            // The keep alive loop - exiting from here means the connection has closed            
            try {
                long requestId = System.currentTimeMillis();
                this.listener.allocateRequestResponse(null, inStream, outStream, this, true);
                if (this.req == null) {
                    // Dead request - happens sometimes with ajp13 - discard
                    this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
                    return;
                }
                String servletURI = this.listener.parseURI(this, this.req, this.rsp, this.inData, this.peerAddr, true);
                if (servletURI == null) {
                    Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                            "UDPRequestHandlerThread.KeepAliveTimedOut", this.threadName);
                    
                    // Keep alive timed out - deallocate and go into wait state
                    this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
                    return;
                }
                if (this.inData.isEncryptedStream())
                	this.outData.setIsEncrypted(true);
                
                if (this.simulateModUniqueId) {
                    req.setAttribute("UNIQUE_ID", "" + requestId);
                }
                long headerParseTime = getRequestProcessTime();

                HostConfiguration hostConfig = req.getHostGroup().getHostByName(req.getServerName());
                Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                        "UDPRequestHandlerThread.StartRequest",
                        new String[] {"" + requestId, hostConfig.getHostname()});

                // Get the URI from the request, check for prefix, then
                // match it to a requestDispatcher
                WebAppConfiguration webAppConfig = hostConfig.getWebAppByURI(servletURI);
                if (webAppConfig == null) {
                    webAppConfig = hostConfig.getWebAppByURI("/");    
                }
                if (webAppConfig == null) {
                    Logger.log(Logger.WARNING, Launcher.RESOURCES,
                            "UDPRequestHandlerThread.UnknownWebapp",
                            new String[] { servletURI });
                    rsp.sendError(WinstoneResponse.SC_NOT_FOUND, 
                            Launcher.RESOURCES.getString("UDPRequestHandlerThread.UnknownWebappPage", servletURI));
                    rsp.flushBuffer();
                    req.discardRequestBody();
                    writeToAccessLog(servletURI, req, rsp, null);

                    // Process keep-alive
                    this.listener.processKeepAlive(req, rsp, inStream);
                    this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
                    Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES, "UDPRequestHandlerThread.FinishRequest",
                            "" + requestId);
                    Logger.log(Logger.SPEED, Launcher.RESOURCES, "UDPRequestHandlerThread.RequestTime",
                            new String[] { servletURI, "" + headerParseTime, "" + getRequestProcessTime() });
                    return;
                }
                
                req.setWebAppConfig(webAppConfig);

                // Now we've verified it's in the right webapp, send
                // request in scope notify
                ServletRequestListener reqLsnrs[] = webAppConfig.getRequestListeners();
                for (int n = 0; n < reqLsnrs.length; n++) {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(webAppConfig.getLoader());
                    reqLsnrs[n].requestInitialized(new ServletRequestEvent(webAppConfig, req));
                    Thread.currentThread().setContextClassLoader(cl);
                }

                // Lookup a dispatcher, then process with it
                processRequest(webAppConfig, req, rsp, 
                        webAppConfig.getServletURIFromRequestURI(servletURI));
                writeToAccessLog(servletURI, req, rsp, webAppConfig);

                this.outData.finishResponse();
                this.inData.finishRequest();

                Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                        "UDPRequestHandlerThread.FinishRequest",
                        "" + requestId);

                // Process keep-alive
                this.listener.processKeepAlive(req, rsp, inStream);

                // Set last accessed time on session as start of this
                // request
                req.markSessionsAsRequestFinished(this.requestStartTime, this.saveSessions);

                // send request listener notifies
                for (int n = 0; n < reqLsnrs.length; n++) {
                    ClassLoader cl = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(webAppConfig.getLoader());
                    reqLsnrs[n].requestDestroyed(new ServletRequestEvent(webAppConfig, req));
                    Thread.currentThread().setContextClassLoader(cl);                            
                }

                req.setWebAppConfig(null);
                rsp.setWebAppConfig(null);
                req.setRequestAttributeListeners(null);

                this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
                Logger.log(Logger.SPEED, Launcher.RESOURCES, "UDPRequestHandlerThread.RequestTime",
                        new String[] { servletURI, "" + headerParseTime, 
                                        "" + getRequestProcessTime() });
            } catch (InterruptedIOException errIO) {
                Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                        "UDPRequestHandlerThread.SocketTimeout", errIO);
            } catch (SocketException errIO) {
            }
            this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
            this.listener.releaseSocket(null, inStream, outStream); // shut sockets
        } catch (Throwable err) {
            try {
                this.listener.deallocateRequestResponse(this, req, rsp, inData, outData);
            } catch (Throwable errClose) {
            }
            try {
                this.listener.releaseSocket(null, inStream, outStream); // shut sockets
            } catch (Throwable errClose) {
            }
            Logger.log(Logger.ERROR, Launcher.RESOURCES,
                    "UDPRequestHandlerThread.RequestError", err);
        } finally {
            this.objectPool.releaseRequestHandler(this);
            Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES, "UDPRequestHandlerThread.ThreadExit");        	
        }
	}
	
    private void processRequest(WebAppConfiguration webAppConfig, WinstoneRequest req, 
            WinstoneResponse rsp, String path) throws IOException, ServletException {
        RequestDispatcher rd = null;
        javax.servlet.RequestDispatcher rdError = null;
        try {
            rd = webAppConfig.getInitialDispatcher(path, req, rsp);

            // Null RD means an error or we have been redirected to a welcome page
            if (rd != null) {
                Logger.log(Logger.FULL_DEBUG, Launcher.RESOURCES,
                        "UDPRequestHandlerThread.HandlingRD", rd.getName());
                rd.forward(req, rsp);
            }
            // if null returned, assume we were redirected
        } catch (Throwable err) {
            Logger.log(Logger.WARNING, Launcher.RESOURCES,
                    "UDPRequestHandlerThread.UntrappedError", err);
            rdError = webAppConfig.getErrorDispatcherByClass(err);
        }

        // If there was any kind of error, execute the error dispatcher here
        if (rdError != null) {
            try {
                if (rsp.isCommitted()) {
                    rdError.include(req, rsp);
                } else {
                    rsp.resetBuffer();
                    rdError.forward(req, rsp);
                }
            } catch (Throwable err) {
                Logger.log(Logger.ERROR, Launcher.RESOURCES, "UDPRequestHandlerThread.ErrorInErrorServlet", err);
            }
//            rsp.sendUntrappedError(err, req, rd != null ? rd.getName() : null);
        }
        rsp.flushBuffer();
        rsp.getWinstoneOutputStream().setClosed(true);
        req.discardRequestBody();
    }

	public void commenceRequestHandling(Socket socket, Listener listener) {
	}
    
	public void commenceRequestHandling(InputStream inStream, OutputStream outStream, Listener listener) {
		this.inStream = inStream;
		this.outStream = outStream;
		this.listener = listener;
		if (this.poolExecutor != null) {
			poolExecutor.execute(this);
		}
	}

	public void destroy() {
		if (this.poolExecutor != null) {
			poolExecutor.remove(this);
		}
	}

	public long getRequestProcessTime() {
		return System.currentTimeMillis() - this.requestStartTime;
	}

	public void setInStream(WinstoneInputStream inStream) {
		this.inData = inStream;
	}

	public void setOutStream(WinstoneOutputStream outStream) {
		this.outData = outStream;
	}

	public void setRequest(WinstoneRequest request) {
		this.req = request;
	}

	public void setRequestStartTime() {
		this.requestStartTime = System.currentTimeMillis();
	}

	public void setResponse(WinstoneResponse response) {
		 this.rsp = response;
	}

    protected void writeToAccessLog(String originalURL, WinstoneRequest request, WinstoneResponse response,
            WebAppConfiguration webAppConfig) {
        if (webAppConfig != null) {
            // Log a row containing appropriate data
            AccessLogger logger = webAppConfig.getAccessLogger();
            if (logger != null) {
                logger.log(originalURL, request, response);
            }
        }
    }

}
