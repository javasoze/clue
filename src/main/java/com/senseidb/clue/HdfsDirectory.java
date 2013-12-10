package com.senseidb.clue;

import java.io.IOException;
import java.util.Collection;

import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.BaseDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;

public class HdfsDirectory extends BaseDirectory {
  private final FileSystem fs;
  private final Path dir;

  public HdfsDirectory(String name, FileSystem fs) {
    this.fs = fs;
    dir = new Path(name);
  }

  public HdfsDirectory(Path path, FileSystem fs) {
    this.fs = fs;
    dir = path;
  }

  @Override
  public void close() throws IOException {
    fs.close();
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context) throws IOException {
    return new HDFSIndexOutput(new Path(dir, name));
  }

  @Override
  public void sync(Collection<String> strings) throws IOException {
  }

  @Override
  public void deleteFile(String name) throws IOException {
    Path path = new Path(dir, name);
    fs.delete(path, false);
  }

  @Override
  public boolean fileExists(String name) throws IOException {
    return fs.exists(new Path(dir, name));
  }

  @Override
  public long fileLength(String name) throws IOException {
    return fs.getFileStatus(new Path(dir, name)).getLen();
  }

  @Override
  public String[] listAll() throws IOException {
    FileStatus[] statuses = fs.listStatus(dir);
    String[] files = new String[statuses.length];

    for (int i = 0; i < statuses.length; i++) {
      files[i] = statuses[i].getPath().getName();
    }
    return files;
  }

  @Override
  public IndexInput openInput(String name, IOContext context) throws IOException {
    return new HDFSIndexInput(new Path(dir, name));
  }

  private class HDFSIndexOutput extends IndexOutput {

    private final FSDataOutputStream out;
    private long currentPosition;
    
    HDFSIndexOutput(Path path) throws IOException {
      out = fs.create(path);
      currentPosition = 0;
    }
    
    @Override
    public void flush() throws IOException {
      out.flush();
    }

    @Override
    public void close() throws IOException {
      out.close();
    }
    
    @Override
    public long getFilePointer() {
      return currentPosition;
    }

    @Override
    public void seek(long pos) throws IOException {
      throw new UnsupportedOperationException("invalid call on seek");
    }

    @Override
    public long length() throws IOException {
      return currentPosition;
    }

    @Override
    public void writeByte(byte b) throws IOException {
      out.write(b & 0xFF);
      currentPosition++;
    }

    @Override
    public void writeBytes(byte[] b, int offset, int length) throws IOException {
      out.write(b, offset, length);
      currentPosition += length;
    }
    
  }
  
  private class HDFSIndexInput extends IndexInput {
    private final Path path;
    private final FSDataInputStream in;

    private HDFSIndexInput(Path path) throws IOException {
      super(path == null ? "" : path.getName());
      this.path = path;
      this.in = fs.open(path);
    }

    @Override
    public void close() throws IOException {
      fs.close();
    }

    @Override
    public long getFilePointer() {
      try {
        return in.getPos();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public long length() {
      try {
        return fs.getFileStatus(path).getLen();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public byte readByte() throws IOException {
      return in.readByte();
    }

    @Override
    public void readBytes(byte[] b, int offset, int len) throws IOException {
      in.readFully(b, offset, len);
    }

    @Override
    public void seek(long pos) throws IOException {
      in.seek(pos);
    }
  }
}

