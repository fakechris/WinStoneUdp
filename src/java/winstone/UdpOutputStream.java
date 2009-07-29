package winstone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;


public class UdpOutputStream extends ByteArrayOutputStream {
	private DatagramChannel channel;
	private SocketAddress client;
	public UdpOutputStream(DatagramChannel channel, SocketAddress client) {
		this.channel = channel;
		this.client = client;
	}
	
	public void flush() {		
	}
	
	public void close() {	
		// write data 
		ByteBuffer out = ByteBuffer.allocate(this.count);
        out.order(ByteOrder.BIG_ENDIAN);
        //out.clear();
        out.put(this.buf, 0, this.count);
        out.flip();
		try {
			this.channel.send(out, this.client);
		} catch (IOException err) {
			Logger.log(Logger.ERROR, Launcher.RESOURCES,
                    "UdpOutputStream.FlushError", err);
		}
	}
}
