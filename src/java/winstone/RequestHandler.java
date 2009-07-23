package winstone;

import java.net.Socket;

public interface RequestHandler {

	/**
	 * Assign a socket to the handler
	 */
	public void commenceRequestHandling(Socket socket, Listener listener);

	public void setRequest(WinstoneRequest request);

	public void setResponse(WinstoneResponse response);

	public void setInStream(WinstoneInputStream inStream);

	public void setOutStream(WinstoneOutputStream outStream);

	public void setRequestStartTime();

	public long getRequestProcessTime();

	/**
	 * Trigger the thread destruction for this handler
	 */
	public void destroy();

}