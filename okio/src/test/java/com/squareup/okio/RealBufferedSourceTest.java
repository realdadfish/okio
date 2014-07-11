/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.okio;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import org.junit.Test;

import com.squareup.okio.Buffer;
import com.squareup.okio.BufferedSource;
import com.squareup.okio.Okio;
import com.squareup.okio.RealBufferedSource;
import com.squareup.okio.Segment;
import com.squareup.okio.Source;

import static com.squareup.okio.TestUtil.assertByteArraysEquals;
import static com.squareup.okio.TestUtil.repeat;
import static com.squareup.okio.Util.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class RealBufferedSourceTest {
  @Test public void inputStreamFromSource() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("a");
    source.writeUtf8(repeat('b', Segment.SIZE));
    source.writeUtf8("c");

    InputStream in = new RealBufferedSource(source).inputStream();
    assertEquals(0, in.available());
    assertEquals(Segment.SIZE + 2, source.size());

    // Reading one byte buffers a full segment.
    assertEquals('a', in.read());
    assertEquals(Segment.SIZE - 1, in.available());
    assertEquals(2, source.size());

    // Reading as much as possible reads the rest of that buffered segment.
    byte[] data = new byte[Segment.SIZE * 2];
    assertEquals(Segment.SIZE - 1, in.read(data, 0, data.length));
    assertEquals(repeat('b', Segment.SIZE - 1), new String(data, 0, Segment.SIZE - 1, UTF_8));
    assertEquals(2, source.size());

    // Continuing to read buffers the next segment.
    assertEquals('b', in.read());
    assertEquals(1, in.available());
    assertEquals(0, source.size());

    // Continuing to read reads from the buffer.
    assertEquals('c', in.read());
    assertEquals(0, in.available());
    assertEquals(0, source.size());

    // Once we've exhausted the source, we're done.
    assertEquals(-1, in.read());
    assertEquals(0, source.size());
  }

  @Test public void inputStreamFromSourceBounds() throws IOException {
    Buffer source = new Buffer();
    source.writeUtf8(repeat('a', 100));
    InputStream in = new RealBufferedSource(source).inputStream();
    try {
      in.read(new byte[100], 50, 51);
      fail();
    } catch (ArrayIndexOutOfBoundsException expected) {
    }
  }

  @Test public void requireTracksBufferFirst() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("bb");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("aa");

    bufferedSource.require(2);
    assertEquals(2, bufferedSource.buffer().size());
    assertEquals(2, source.size());
  }

  @Test public void requireIncludesBufferBytes() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("b");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("a");

    bufferedSource.require(2);
    assertEquals("ab", bufferedSource.buffer().readUtf8(2));
  }

  @Test public void requireInsufficientData() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("a");

    BufferedSource bufferedSource = new RealBufferedSource(source);

    try {
      bufferedSource.require(2);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void requireReadsOneSegmentAtATime() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(repeat('a', Segment.SIZE));
    source.writeUtf8(repeat('b', Segment.SIZE));

    BufferedSource bufferedSource = new RealBufferedSource(source);

    bufferedSource.require(2);
    assertEquals(Segment.SIZE, source.size());
    assertEquals(Segment.SIZE, bufferedSource.buffer().size());
  }

  @Test public void skipInsufficientData() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("a");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    try {
      bufferedSource.skip(2);
      fail();
    } catch (EOFException expected) {
    }
  }

  @Test public void skipReadsOneSegmentAtATime() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8(repeat('a', Segment.SIZE));
    source.writeUtf8(repeat('b', Segment.SIZE));
    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.skip(2);
    assertEquals(Segment.SIZE, source.size());
    assertEquals(Segment.SIZE - 2, bufferedSource.buffer().size());
  }

  @Test public void skipTracksBufferFirst() throws Exception {
    Buffer source = new Buffer();
    source.writeUtf8("bb");

    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.buffer().writeUtf8("aa");

    bufferedSource.skip(2);
    assertEquals(0, bufferedSource.buffer().size());
    assertEquals(2, source.size());
  }

  @Test public void operationsAfterClose() throws IOException {
    Buffer source = new Buffer();
    BufferedSource bufferedSource = new RealBufferedSource(source);
    bufferedSource.close();

    // Test a sample set of methods.
    try {
      bufferedSource.indexOf((byte) 1);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.skip(1);
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.readByte();
      fail();
    } catch (IllegalStateException expected) {
    }

    try {
      bufferedSource.readByteString(10);
      fail();
    } catch (IllegalStateException expected) {
    }

    // Test a sample set of methods on the InputStream.
    InputStream is = bufferedSource.inputStream();
    try {
      is.read();
      fail();
    } catch (IOException expected) {
    }

    try {
      is.read(new byte[10]);
      fail();
    } catch (IOException expected) {
    }
  }

  @Test public void readAll() throws IOException {
    Buffer source = new Buffer();
    BufferedSource bufferedSource = Okio.buffer((Source) source);
    bufferedSource.buffer().writeUtf8("abc");
    source.writeUtf8("def");

    Buffer sink = new Buffer();
    assertEquals(6, bufferedSource.readAll(sink));
    assertEquals("abcdef", sink.readUtf8(6));
    assertTrue(source.exhausted());
    assertTrue(bufferedSource.exhausted());
  }

  @Test public void readAllExhausted() throws IOException {
    Buffer source = new Buffer();
    BufferedSource bufferedSource = Okio.buffer((Source) source);

    MockSink mockSink = new MockSink();
    assertEquals(0, bufferedSource.readAll(mockSink));
    assertTrue(source.exhausted());
    assertTrue(bufferedSource.exhausted());
    mockSink.assertLog();
  }

  /**
   * We don't want readAll to buffer an unbounded amount of data. Instead it
   * should buffer a segment, write it, and repeat.
   */
  @Test public void readAllReadsOneSegmentAtATime() throws IOException {
    Buffer write1 = new Buffer().writeUtf8(TestUtil.repeat('a', Segment.SIZE));
    Buffer write2 = new Buffer().writeUtf8(TestUtil.repeat('b', Segment.SIZE));
    Buffer write3 = new Buffer().writeUtf8(TestUtil.repeat('c', Segment.SIZE));

    Buffer source = new Buffer().writeUtf8(""
        + TestUtil.repeat('a', Segment.SIZE)
        + TestUtil.repeat('b', Segment.SIZE)
        + TestUtil.repeat('c', Segment.SIZE));

    MockSink mockSink = new MockSink();
    BufferedSource bufferedSource = Okio.buffer((Source) source);
    assertEquals(Segment.SIZE * 3, bufferedSource.readAll(mockSink));
    mockSink.assertLog(
        "write(" + write1 + ", " + write1.size() + ")",
        "write(" + write2 + ", " + write2.size() + ")",
        "write(" + write3 + ", " + write3.size() + ")");
  }

  @Test public void readByteArray() throws IOException {
    String string = "abcd" + repeat('e', Segment.SIZE);
    Buffer buffer = new Buffer().writeUtf8(string);
    BufferedSource source = Okio.buffer((Source) buffer);
    assertByteArraysEquals(string.getBytes(UTF_8), source.readByteArray());
  }

  @Test public void readByteArrayPartial() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("abcd");
    BufferedSource source = Okio.buffer((Source) buffer);
    assertEquals("[97, 98, 99]", Arrays.toString(source.readByteArray(3)));
    assertEquals("d", source.readUtf8(1));
  }

  @Test public void readByteString() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    BufferedSource source = Okio.buffer((Source) buffer);
    assertEquals("abcd" + repeat('e', Segment.SIZE), source.readByteString().utf8());
  }

  @Test public void readByteStringPartial() throws IOException {
    Buffer buffer = new Buffer().writeUtf8("abcd").writeUtf8(repeat('e', Segment.SIZE));
    BufferedSource source = Okio.buffer((Source) buffer);
    assertEquals("abc", source.readByteString(3).utf8());
    assertEquals("d", source.readUtf8(1));
  }
}
