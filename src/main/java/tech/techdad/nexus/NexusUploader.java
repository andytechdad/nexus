package tech.techdad.nexus;

import org.apache.commons.cli.*;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.*;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.*;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class NexusUploader {

    private static final Logger log = LogManager.getLogger("nexus-cli");

    public static void main(String[] arguments) {

        Options options = new Options();

        Option sourcefile_arg = Option.builder("f")
                .longOpt("file")
                .desc("File to Upload")
                .hasArg()
                .argName("file")
                .required()
                .build();

        Option nexus_arg = Option.builder("n")
                .longOpt("nexus")
                .desc("Nexus URL")
                .hasArg()
                .argName("nexus")
                .required()
                .build();

        options.addOption(sourcefile_arg);
        options.addOption(nexus_arg);

        System.out.println("Nexus CLI: Upload a file to Nexus");

        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine args = parser.parse( options, arguments);

            String sourcefile = args.getOptionValue( "file" );
            String nexusurl = args.getOptionValue( "nexus" );
            String destinationurl = nexusurl + "/" + sourcefile;

            log.debug(new ParameterizedMessage("Source file is {}", sourcefile));
            log.debug(new ParameterizedMessage("Nexus URL is {}", nexusurl));

            File f_sourcefile = new File(sourcefile);

            if (f_sourcefile.isFile()) {
                log.info(new ParameterizedMessage("Preparing to upload {} to {}", f_sourcefile.getAbsoluteFile(), nexusurl));

                try (CloseableHttpClient httpclient = HttpClients.createDefault()) {

                    CredentialsProvider provider = new BasicCredentialsProvider();
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin", "admin123");
                    provider.setCredentials(AuthScope.ANY, credentials);

                    HttpClientContext context = HttpClientContext.create();

                    context.setCredentialsProvider(provider);

                    InputStream loadedSourceFile = new FileInputStream(f_sourcefile);

                    InputStreamEntity memory = new InputStreamEntity(loadedSourceFile);

                    BufferedHttpEntity data = new BufferedHttpEntity(memory);

                    // build http request and assign multipart upload data
                    HttpUriRequest request = RequestBuilder
                            .put(destinationurl)
                            .setEntity(data)
                            .build();

                    log.debug(request);

                    log.info(new ParameterizedMessage("Executing request {}",request.getRequestLine()));

                    // Create a custom response handler
                    ResponseHandler<String> responseHandler = response -> {
                        int status = response.getStatusLine().getStatusCode();
                        log.debug(status);
                        if (status >= 200 && status < 300) {
                            HttpEntity entity = response.getEntity();
                            return entity != null ? EntityUtils.toString(entity) : null;
                        } else {
                            throw new ClientProtocolException("Unexpected response status: " + status);
                        }
                    };

                    httpclient.execute(request, responseHandler, context);

                } catch (IOException e) {
                    e.printStackTrace();
                }


            }

        }

        catch ( ParseException parseerror ) {

            log.error("Unable to parse arguments correctly, see help");

            log.error(parseerror);

            HelpFormatter formatter = new HelpFormatter();

            formatter.printHelp("java -jar nexus-cli.jar", options);

            System.exit(1);

        }

        finally {

            System.exit(0);

        }
    }


}
