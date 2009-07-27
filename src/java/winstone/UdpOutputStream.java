package winstone;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.DatagramChannel;

import winstone.crypto.RC4;

public class UdpOutputStream extends ByteArrayOutputStream {
	private RC4 rc4;
	private DatagramChannel channel;
	private SocketAddress client;
	public UdpOutputStream(DatagramChannel channel, SocketAddress client) {
		rc4 = new RC4();
		this.channel = channel;
		this.client = client;
	}
	
	public void write(int b) {		
		super.write( rc4.rc4((byte)b) );
	}

	public void write(byte[] b,
            int off,
            int len) {
		byte[] result = rc4.rc4(b, off, len);
		super.write(result, 0, len);
	}
	
	public void flush() {		
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
	
	public void close() {	
	}
}
