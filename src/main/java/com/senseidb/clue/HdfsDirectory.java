package com.senseidb.clue;

import java.io.IOException;
import java.util.Collection;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.lucene.store.BaseDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.OutputStreamIndexOutput;

import com.senseidb.clue.util.CustomBufferedIndexInput;

public class HdfsDirectory extends BaseDirectory {
  
  public static final int BUFFER_SIZE = 8192;
  
  private final FileSystem fs;
  private final Path dir;

  public HdfsDirectory(LockFactory lockFactory, Path dirPath,
      Configuration config) throws IOException {
    super(lockFactory);
    this.fs = FileSystem.get(config);
    dir = dirPath;
  }

  @Override
  public void close() throws IOException {
    fs.close();
  }

  @Override
  public IndexOutput createOutput(String name, IOContext context)
      throws IOException {    
    return new OutputStreamIndexOutput(name, fs.create(new Path(dir, name)), BUFFER_SIZE);
  }

  @Override
  public void sync(Collection<String> strings) throws IOException {
  }

  @Override
  public void renameFile(String source, String dest) throws IOException {
  }

  @Override
  public void deleteFile(String name) throws IOException {
    Path path = new Path(dir, name);
    fs.delete(path, false);
  }

  @Override
  public long fileLength(String name) throws IOException {
    return fs.getFileStatus(new Path(dir, name)).getLen();
  }

  @Override
  public String[] listAll() throws IOException {
    FileStatus[] statuses = fs.listStatus(dir);
    if (statuses == null) {
      return new String[0];
    }
    String[] files = new String[statuses.length];

    for (int i = 0; i < statuses.length; i++) {
      files[i] = statuses[i].getPath().getName();
    }
    return files;
  }

  @Override
  public IndexInput openInput(String name, IOContext context)
      throws IOException {
    Path path = new Path(dir, name);
    return new HdfsIndexInput(
        "HDFSIndexInput(path=\"" + path.getName() + "\")", fs, path, BUFFER_SIZE);    
  }

  static class HdfsIndexInput extends CustomBufferedIndexInput {
    
    private final Path path;
    private final FSDataInputStream inputStream;
    private final long length;
    private boolean clone = false;
    
    public HdfsIndexInput(String name, FileSystem fileSystem, Path path,
        int bufferSize) throws IOException {
      super(name);
      this.path = path;
      FileStatus fileStatus = fileSystem.getFileStatus(path);
      length = fileStatus.getLen();
      inputStream = fileSystem.open(path, bufferSize);
    }
    
    @Override
    protected void readInternal(byte[] b, int offset, int length)
        throws IOException {
      inputStream.readFully(getFilePointer(), b, offset, length);
    }
    
    @Override
    protected void seekInternal(long pos) throws IOException {

    }
    
    @Override
    protected void closeInternal() throws IOException {
      if (!clone) {
        inputStream.close();
      }
    }
    
    @Override
    public long length() {
      return length;
    }
    
    @Override
    public IndexInput clone() {
      HdfsIndexInput clone = (HdfsIndexInput) super.clone();
      clone.clone = true;
      return clone;
    }
  }
}
