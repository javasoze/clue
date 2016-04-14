package com.senseidb.clue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.EnumSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.CreateFlag;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FsServerDefaults;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsPermission;
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
    return new HdfsFileWriter(fs, new Path(dir, name), name);
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
  
  static class HdfsFileWriter extends OutputStreamIndexOutput {
    
    public static final String HDFS_SYNC_BLOCK = "clue.hdfs.sync.block";
    public static final int BUFFER_SIZE = 16384;
    
    public HdfsFileWriter(FileSystem fileSystem, Path path, String name) throws IOException {
      super("fileSystem=" + fileSystem + " path=" + path, name, getOutputStream(fileSystem, path), BUFFER_SIZE);
    }
    
    private static final OutputStream getOutputStream(FileSystem fileSystem, Path path) throws IOException {
      Configuration conf = fileSystem.getConf();      
      FsServerDefaults fsDefaults = fileSystem.getServerDefaults(path);
      EnumSet<CreateFlag> flags = EnumSet.of(CreateFlag.CREATE,
          CreateFlag.OVERWRITE);
      if (Boolean.getBoolean(HDFS_SYNC_BLOCK)) {
        flags.add(CreateFlag.SYNC_BLOCK);
      }
      return fileSystem.create(path, FsPermission.getDefault()
          .applyUMask(FsPermission.getUMask(conf)), flags, fsDefaults
          .getFileBufferSize(), fsDefaults.getReplication(), fsDefaults
          .getBlockSize(), null);
    }
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

  @Override
  public IndexOutput createTempOutput(String prefix, String suffix,
      IOContext context) throws IOException {    
    throw new UnsupportedOperationException();
  }
}
