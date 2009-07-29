/*
 * Copyright 2003-2006 Rick Knowles <winstone-devel at lists sourceforge net>
 * Distributed under the terms of either:
 * - the common development and distribution license (CDDL), v1.0; or
 * - the GNU Lesser General Public License, v2.1 or later
 */
package winstone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import winstone.crypto.RC4;

/**
 * The request stream management class.
 * 
 * @author <a href="mailto:rick_knowles@hotmail.com">Rick Knowles</a>
 * @version $Id: WinstoneInputStream.java,v 1.4 2006/02/28 07:32:47 rickknowles Exp $
 */
public class WinstoneInputStream extends javax.servlet.ServletInputStream {
    final int BUFFER_SIZE = 4096;
    private InputStream inData;
    private Integer contentLength;
    private int readSoFar;
    private ByteArrayOutputStream dump;
    
    private RC4 rc4;
    private boolean isEncrypted = false;
    private int preRead[] = new int[3];
    private int pos = -1; 
    
    /**
     * Constructor
     */
    public WinstoneInputStream(InputStream inData) {
        super();
        this.inData = inData;
        this.dump = new ByteArrayOutputStream();
    }

    public WinstoneInputStream(byte inData[]) {
        this(new ByteArrayInputStream(inData));
    }

    public InputStream getRawInputStream() {
        return this.inData;
    }
    
    public boolean isEncryptedStream() {
    	return this.isEncrypted;
    }

    public void setContentLength(int length) {
        this.contentLength = new Integer(length);
        this.readSoFar = 0;
    }
    
    public int read() throws IOException {
    	if (pos < 2) {    		
    		if (pos < 0)
    		{
	    		// 预读3个字节，决定是否需要解码 GET/POS/HEA/PUT
	    		preRead[0] = this.inData.read();
	    		preRead[1] = this.inData.read();
	    		preRead[2] = this.inData.read();
	    		this.isEncrypted = isEncryptedHttpHeader(preRead);
	    		if (this.isEncrypted) {
	    			rc4 = new RC4();
	    			preRead[0] = rc4.rc4((byte)preRead[0]);
	    			preRead[1] = rc4.rc4((byte)preRead[1]);
	    			preRead[2] = rc4.rc4((byte)preRead[2]);
	    		}
    		}
    		pos++;
    		return preRead[pos];
    	}
    	
        if (this.contentLength == null) {        	
            int data = getData();
            this.dump.write(data);
//            System.out.println("Char: " + (char) data);
            return data;
        } else if (this.contentLength.intValue() > this.readSoFar) {
            this.readSoFar++;
            int data = getData();
            this.dump.write(data);
//            System.out.println("Char: " + (char) data);
            return data;
        } else
            return -1;
    }

    public void finishRequest() {
        // this.inData = null;
        // byte content[] = this.dump.toByteArray();
        // com.rickknowles.winstone.ajp13.Ajp13Listener.packetDump(content,
        // content.length);
    }

    public int available() throws IOException {
        return this.inData.available();
    }

    /**
     * Wrapper for the servletInputStream's readline method
     */
    public byte[] readLine() throws IOException {
        // System.out.println("ReadLine()");
        byte buffer[] = new byte[BUFFER_SIZE];
        int charsRead = super.readLine(buffer, 0, BUFFER_SIZE);
        if (charsRead == -1) {
            Logger.log(Logger.DEBUG, Launcher.RESOURCES,
                    "WinstoneInputStream.EndOfStream");
            return new byte[0];
        }
        byte outBuf[] = new byte[charsRead];
        System.arraycopy(buffer, 0, outBuf, 0, charsRead);
        return outBuf;
    }

    private boolean isEncryptedHttpHeader(int[] buf) {
    	if ( (buf[0]=='G' && buf[1]=='E' && buf[2]=='T') ||
    		 (buf[0]=='P' && buf[1]=='U' && buf[2]=='T') ||
    		 (buf[0]=='H' && buf[1]=='E' && buf[2]=='A') ||
    		 (buf[0]=='P' && buf[1]=='O' && buf[2]=='S') )
    		return false;
    	return true;
    }
    
    private int getData() throws IOException {
    	if (this.isEncrypted)
    		return rc4.rc4((byte)this.inData.read());
    	else
    		return this.inData.read();
    }
    
}
