package tech.techdad.nexus;

import org.apache.commons.cli.*;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
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

public class NexusCommandLine {

    private static final Logger LOGGER = LogManager.getLogger("nexus-cli");
    private static final String NEXUS = "nexus";

    public static void main(String[] arguments) {

        Options options = new Options();

        Option sourcefileArg = Option.builder("f")
                .longOpt("file")
                .desc("File to Upload")
                .hasArg()
                .argName("file")
                .required()
                .build();

        Option nexusArg = Option.builder("n")
                .longOpt(NEXUS)
                .desc("Nexus URL")
                .hasArg()
                .argName(NEXUS)
                .required()
                .build();

        options.addOption(sourcefileArg);
        options.addOption(nexusArg);

        LOGGER.debug("Nexus CLI: Upload a file to Nexus");

        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine args = parser.parse(options, arguments);

            String sourcefile = args.getOptionValue("file");
            String nexusUrl = args.getOptionValue(NEXUS);
            String destinationUrl = nexusUrl + "/" + sourcefile;

            LOGGER.debug(new ParameterizedMessage("Source file is {}", sourcefile));
            LOGGER.debug(new ParameterizedMessage("Nexus URL is {}", nexusUrl));

            File file = new File(sourcefile);

            if (file.isFile()) {
                LOGGER.info(new ParameterizedMessage("Preparing to upload {} to {}", file.getAbsoluteFile(), nexusUrl));

                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

                    CredentialsProvider provider = new BasicCredentialsProvider();
                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("admin", "admin123");
                    provider.setCredentials(AuthScope.ANY, credentials);

                    HttpClientContext context = HttpClientContext.create();
                    context.setCredentialsProvider(provider);

                    InputStream loadedSourcefile = new FileInputStream(file);
                    InputStreamEntity memory = new InputStreamEntity(loadedSourcefile);
                    BufferedHttpEntity data = new BufferedHttpEntity(memory);

                    // build http request and assign multipart upload data
                    HttpUriRequest request = RequestBuilder
                            .put(destinationUrl)
                            .setEntity(data)
                            .build();

                    LOGGER.debug(request);
                    LOGGER.info(new ParameterizedMessage("Executing request {}", request.getRequestLine()));

                    // Create a custom response handler
                    ResponseHandler<String> responseHandler = response -> {
                        int status = response.getStatusLine().getStatusCode();
                        LOGGER.debug(status);
                        if (status >= 200 && status < 300) {
                            HttpEntity entity = response.getEntity();
                            return entity != null ? EntityUtils.toString(entity) : null;
                        } else {
                            throw new ClientProtocolException("Unexpected response status: " + status);
                        }
                    };

                    httpClient.execute(request, responseHandler, context);

                } catch (IOException e) {
                    LOGGER.debug(e.getMessage());
                }
            }
        } catch (ParseException e) {
            LOGGER.error("Unable to parse arguments correctly, see help");
            LOGGER.error(e);

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("java -jar nexus-cli.jar", options);
            System.exit(1); // probably not needed
        } finally {
            System.exit(0); // probably not needed, end of programme anyway
        }
    }


}
