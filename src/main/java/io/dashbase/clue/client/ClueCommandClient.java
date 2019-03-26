package io.dashbase.clue.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jline.console.ConsoleReader;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.FileNameCompleter;
import jline.console.completer.StringsCompleter;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import javax.net.ssl.*;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ClueCommandClient {

    private final ClueCommandService svc;
    private final CmdlineHelper cmdlineHelper;

    public ClueCommandClient(URL url) throws Exception {
        final OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if ("https".equals(url.getProtocol())) {
            try {
                setSslSocketFactory(okHttpClientBuilder);
            } catch(Exception e) {
                throw new IllegalStateException("cannot create ssl connection: " + e);
            }
        }

        final OkHttpClient okHttpClient = okHttpClientBuilder.readTimeout(10, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();

        Retrofit restAdator = new Retrofit.Builder().baseUrl(HttpUrl.get(url))
                .addConverterFactory(ScalarsConverterFactory.create())
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .client(okHttpClient).build();

        svc = restAdator.create(ClueCommandService.class);

        cmdlineHelper = new CmdlineHelper(new Supplier<Collection<String>>() {
            @Override
            public Collection<String> get() {
                try {
                    return getCommands();
                } catch (Exception e) {
                    System.out.println("unable obtaining command list");
                    return Collections.emptyList();
                }
            }
        }, null);
    }

    public final ClueCommandService service() {
        return svc;
    }

    // Create a trust manager that does not validate certificate chains
    private static final TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
            }
    };

    private static void setSslSocketFactory(OkHttpClient.Builder builder)  throws Exception {
        // Install the all-trusting trust manager
        final SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        // Create an ssl socket factory with our all-trusting manager
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
        builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }

    public Collection<String> getCommands() throws Exception {
        Call<Collection<String>> call = svc.commands();
        return call.execute().body();
    }


    public void handleCommand(String cmdName, String[] args, PrintStream out) throws Exception {
        String argString = CmdlineHelper.toString(Arrays.asList(args));
        Call<ResponseBody> call = svc.command(cmdName, argString);

        Response<ResponseBody> response = call.execute();
        try ( ResponseBody responseBody = response.body() ) {
            InputStream is = responseBody.byteStream();
            is.transferTo(System.out);
        }
    }

    public String readCommand() {
        try {
            return cmdlineHelper.readCommand();
        } catch (Exception e) {
            System.err.println("Error! Clue is unable to read line from stdin: " + e.getMessage());
            throw new IllegalStateException("Unable to read command line!", e);
        }
    }

    public void run() throws Exception {
        ConsoleReader consoleReader = new ConsoleReader();
        consoleReader.setBellEnabled(false);

        Collection<String> commands = getCommands();

        LinkedList<Completer> completors = new LinkedList<Completer>();
        completors.add(new StringsCompleter(commands));

        completors.add(new FileNameCompleter());

        consoleReader.addCompleter(new ArgumentCompleter(completors));



        while(true){
            String line = readCommand();
            if (line == null || line.isEmpty()) continue;
            line = line.trim();
            if ("exit".equals(line)) {
                break;
            }
            String[] parts = line.split("\\s");
            if (parts.length > 0){
                String cmd = parts[0];
                String[] cmdArgs = new String[parts.length - 1];
                System.arraycopy(parts, 1, cmdArgs, 0, cmdArgs.length);
                handleCommand(cmd, cmdArgs, System.out);
            }
        }
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 1){
            System.out.println("usage: <url>");
            System.exit(1);
        }

        String remoteLocation = args[0];
        if (!remoteLocation.startsWith("http://") && !remoteLocation.startsWith("https://")) {
            remoteLocation = "http://" + remoteLocation;
        }

        URL url = new URL(remoteLocation);
        ClueCommandClient client = new ClueCommandClient(url);
        client.run();
    }
}
