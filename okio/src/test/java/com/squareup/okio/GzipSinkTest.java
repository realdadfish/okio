package com.squareup.okio;

import java.io.IOException;

import org.junit.Test;

import com.squareup.okio.Buffer;
import com.squareup.okio.GzipSink;
import com.squareup.okio.GzipSource;
import com.squareup.okio.Segment;

import static com.squareup.okio.TestUtil.repeat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class GzipSinkTest {
  @Test public void gzipGunzip() throws Exception {
    Buffer data = new Buffer();
    String original = "It's a UNIX system! I know this!";
    data.writeUtf8(original);
    Buffer sink = new Buffer();
    GzipSink gzipSink = new GzipSink(sink);
    gzipSink.write(data, data.size());
    gzipSink.close();
    Buffer inflated = gunzip(sink);
    assertEquals(original, inflated.readUtf8(inflated.size()));
  }

  @Test public void closeWithExceptionWhenWritingAndClosing() throws IOException {
    MockSink mockSink = new MockSink();
    mockSink.scheduleThrow(0, new IOException("first"));
    mockSink.scheduleThrow(1, new IOException("second"));
    GzipSink gzipSink = new GzipSink(mockSink);
    gzipSink.write(new Buffer().writeUtf8(repeat('a', Segment.SIZE)), Segment.SIZE);
    try {
      gzipSink.close();
      fail();
    } catch (IOException expected) {
      assertEquals("first", expected.getMessage());
    }
    mockSink.assertLogContains("close()");
  }

  private Buffer gunzip(Buffer gzipped) throws IOException {
    Buffer result = new Buffer();
    GzipSource source = new GzipSource(gzipped);
    while (source.read(result, Integer.MAX_VALUE) != -1) {
    }
    return result;
  }
}
