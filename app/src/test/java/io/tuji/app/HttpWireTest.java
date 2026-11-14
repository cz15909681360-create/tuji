package io.tuji.app;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class HttpWireTest {
 HttpWire.Message parse(String s)throws Exception{return HttpWire.read(new ByteArrayInputStream(s.getBytes(StandardCharsets.ISO_8859_1)));}
 @Test public void readsFixedBodyWithoutReadingNextMessage()throws Exception{HttpWire.Message m=parse("POST / HTTP/1.1\r\nContent-Length: 3\r\n\r\nabcNEXT");assertEquals("abc",new String(PhotoBundle.readBounded(m.body,20)));}
 @Test public void readsChunkedUploads()throws Exception{HttpWire.Message m=parse("POST / HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n2;ext=1\r\nde\r\n0\r\n\r\n");assertEquals("abcde",new String(PhotoBundle.readBounded(m.body,20)));}
 @Test public void rejectsConflictingFraming(){assertThrows(IOException.class,()->parse("POST / HTTP/1.1\r\nContent-Length: 1\r\nTransfer-Encoding: chunked\r\n\r\n"));}
 @Test public void rejectsDuplicateLength(){assertThrows(IOException.class,()->parse("POST / HTTP/1.1\r\nContent-Length: 1\r\nContent-Length: 2\r\n\r\n"));}
 @Test public void rejectsTruncatedUpload()throws Exception{HttpWire.Message m=parse("POST / HTTP/1.1\r\nContent-Length: 4\r\n\r\nabc");assertThrows(EOFException.class,()->PhotoBundle.readBounded(m.body,20));}
 @Test public void rejectsOversizedMetadata()throws Exception{assertThrows(IOException.class,()->PhotoBundle.readBounded(new ByteArrayInputStream(new byte[20]),10));}
 @Test public void rejectsNegativeLength(){assertThrows(IOException.class,()->parse("POST / HTTP/1.1\r\nContent-Length: -1\r\n\r\n"));}
}
