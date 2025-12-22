package io.dashbase.clue.api;

import io.idias.block.BlockContext;
import io.idias.block.directory.BlockDirectory;
import io.idias.block.directory.BlockService;
import io.idias.s3.S3Client;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static io.dashbase.clue.util.OptionsParsingUtil.parseIntOption;
import static io.dashbase.clue.util.OptionsParsingUtil.parsePathOption;
import static io.dashbase.clue.util.OptionsParsingUtil.parseStringOption;

public class BlockDirectoryProvider implements DirectoryProvider {
  private static final String PROVIDER_NAME = "block";
  private static final String OPTION_THREADS = "threads";
  private static final String OPTION_REGION = "region";
  private static final String OPTION_ENDPOINT = "endpoint";
  private static final String OPTION_ACCESS_KEY = "accessKey";
  private static final String OPTION_SECRET_KEY = "secretKey";
  private static final String OPTION_CACHE_DIR = "cacheDir";
  private static final String OPTION_BUCKET = "bucket";
  private static final String OPTION_INDEX_PATH = "indexPath";
  private static final int DEFAULT_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());
  private static final String DEFAULT_REGION = "us-east1";
  private static final String DEFAULT_ENDPOINT = "http://localhost:6666/";
  private static final String DEFAULT_ACCESS_KEY = "admin";
  private static final String DEFAULT_SECRET_KEY = "password";
  private static final Path DEFAULT_CACHE_DIR =
      Path.of(System.getProperty("java.io.tmpdir"), "/tmp");
  private static final String DEFAULT_BUCKET = "my-bucket";
  private static final String DEFAULT_INDEX_PATH = "/";
  private static final boolean DEFAULT_ENABLE_TRACING = false;

  @Override
  public String getName() {
    return PROVIDER_NAME;
  }

  @Override
  public Directory build(String location, Map<String, String> options) throws IOException {
    int threads = parseIntOption(options, OPTION_THREADS, DEFAULT_THREADS);
    String region = parseStringOption(options, OPTION_REGION, DEFAULT_REGION);
    String endpoint = parseStringOption(options, OPTION_ENDPOINT, DEFAULT_ENDPOINT);
    String accessKey = parseStringOption(options, OPTION_ACCESS_KEY, DEFAULT_ACCESS_KEY);
    String secretKey = parseStringOption(options, OPTION_SECRET_KEY, DEFAULT_SECRET_KEY);
    Path cacheDir = parsePathOption(options, OPTION_CACHE_DIR, DEFAULT_CACHE_DIR);
    String bucket = parseStringOption(options, OPTION_BUCKET, DEFAULT_BUCKET);
    String indexPath = parseStringOption(options, OPTION_INDEX_PATH, DEFAULT_INDEX_PATH);

    if (!Files.exists(cacheDir)) {
        Files.createDirectories(cacheDir);
    }

    var s3Client = new S3Client(threads, region, endpoint, DEFAULT_ENABLE_TRACING, accessKey, secretKey);
    var blockCtx = BlockContext.defaults(cacheDir, s3Client.s3Client);
    BlockService blockService = new BlockService(blockCtx);
    return BlockDirectory.withoutManifest(blockService, bucket, indexPath, s3Client, cacheDir);
  }
}
