package nl.knaw.dans.bridge.plugin.dar.easy;

import nl.knaw.dans.bridge.plugin.lib.common.*;
import nl.knaw.dans.bridge.plugin.lib.exception.BridgeException;
import nl.knaw.dans.bridge.plugin.lib.util.BridgeHelper;
import nl.knaw.dans.bridge.plugin.lib.util.StateEnum;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Link;
import org.apache.commons.io.FileUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

/*
    @author Eko Indarto
 */
public class EasyIngestAction implements IAction {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private ITransform iTransform = new EasyTransformer();
    private static final int TIMEOUT = 600000; //10 minutes. (Consider to put this in a properties file)
    private static final int CHUNK_SIZE = 104857600;//100MB. (Consider to put this in a properties file)

    @Override
    public Optional<Map<String, String>> transform(SourceDar sourceDar, DestinationDar destinationDar, List<XslTransformer> xslStreamSource) throws BridgeException {
        iTransform = new EasyTransformer();
        return Optional.of(iTransform.transformMetadata(sourceDar, destinationDar, xslStreamSource));
    }

    @Override
    public Optional<File> composeBag(SourceDar sourceDar, String bagitBaseDir, Map<String, String> transformedMetadata) throws BridgeException {
        LOG.info("Trying to compose bagit...");
        IBagitComposer iBagitComposer = new EasyBagComposer();
        LOG.info("EasyIngestAction - starting iTransform.getSourceFileList");
        Optional<SourceFileList> sourceFileList = iTransform.getSourceFileList(sourceDar.getApiToken());
        if (!sourceFileList.isPresent()) {
            //EASY requires that the original metadata has to include in 'Metadata export from DataverseNL' folder"
            LOG.error("SourceFileList is not present");
            throw new BridgeException("SourceFileList is not present", this.getClass());
        }
        LOG.info("EasyIngestAction - composeBag sourceFileList: {}", sourceFileList.get());
        File bagitFile = iBagitComposer.buildBag(bagitBaseDir, sourceDar.getMetadataUrl(), transformedMetadata, sourceFileList.get());
        return Optional.of(bagitFile);
    }

    @Override
    public EasyResponseDataHolder execute(DestinationDar destinationDar, Optional<File> baggitZippedFileOpt, Optional<Map<String, String>> transformedMetadata) throws BridgeException {

        EasyResponseDataHolder easyResponseDataHolder;
        long checkingTimePeriod = 5000;
        try {
            File bagitZippedFile = baggitZippedFileOpt.get();
            LOG.info("Trying to ingest '{}'", bagitZippedFile.getName());
            long bagitZippedFileSize = bagitZippedFile.length();
            LOG.info("Triying to get MD5 for {}", bagitZippedFile.getAbsolutePath());
            LOG.info("{} has size: {}",bagitZippedFile.getName(), FileUtils.byteCountToDisplaySize(bagitZippedFileSize));
            int numberOfChunks = 0;
            if (bagitZippedFileSize > CHUNK_SIZE) {
                numberOfChunks = getNumberOfChunks(bagitZippedFileSize);
                LOG.info("The '{}' file will send to EASY in partly, {} times, each {}", bagitZippedFile.getName(), numberOfChunks, FileUtils.byteCountToDisplaySize(CHUNK_SIZE));
            }

            DigestInputStream dis = getDigestInputStream(bagitZippedFile);

            CloseableHttpClient http = BridgeHelper.createHttpClient(destinationDar.getDarIri().toURI(), destinationDar.getDarUid(), destinationDar.getDarPwd(), TIMEOUT);
            CloseableHttpResponse response = BridgeHelper.sendChunk(dis, CHUNK_SIZE, "POST", destinationDar.getDarIri().toURI(), "bag.zip.1", "application/octet-stream", http,
                    CHUNK_SIZE < bagitZippedFileSize);

            String bodyText = BridgeHelper.readEntityAsString(response.getEntity());
            int rc = response.getStatusLine().getStatusCode();
            LOG.info("Response code: {}", rc);
            if (rc != HTTP_CREATED) {
                LOG.error("FAILED. Status = {}", response.getStatusLine());
                LOG.error("Response body follows:");
                LOG.error(bodyText);
                throw new BridgeException("Status = " + response.getStatusLine() + ". Response body follows:" + bodyText, this.getClass());
            }
            LOG.info("SUCCESS. Deposit receipt follows:");
            LOG.info(bodyText);

            Entry receipt = BridgeHelper.parse(bodyText);
            Link seIriLink = receipt.getLink("edit");
            URI seIri = seIriLink.getHref().toURI();

            long remaining = bagitZippedFileSize - CHUNK_SIZE;
            if (remaining > 0)
                LOG.info("Trying to ingest the remaining '{}'", FileUtils.byteCountToDisplaySize(remaining));
            else
                LOG.info("Ingesting is finish.");
            int count = 2;
            numberOfChunks --;
            while (remaining > 0) {
                checkingTimePeriod += 2000;
                LOG.info("POST-ing chunk of {} to SE-IRI (remaining: {}) ... [{}]", FileUtils.byteCountToDisplaySize(CHUNK_SIZE), FileUtils.byteCountToDisplaySize(remaining), numberOfChunks);
                response = BridgeHelper.sendChunk(dis, CHUNK_SIZE, "POST", seIri, "bag.zip." + count++, "application/octet-stream", http, remaining > CHUNK_SIZE);
                numberOfChunks --;
                remaining -= CHUNK_SIZE;
                bodyText = BridgeHelper.readEntityAsString(response.getEntity());
                if (response.getStatusLine().getStatusCode() != HTTP_OK) {
                    LOG.error("FAILED. Status = {}", response.getStatusLine());
                    LOG.error("Response body follows:");
                    LOG.error(bodyText);
                    throw new BridgeException("FAILED. Status = " + response.getStatusLine() + "Response body follows:" + bodyText, this.getClass());
                } else {
                    LOG.info("[bag.zip.{}] SUCCESS. Deposit receipt follows:", count);
                    LOG.info(bodyText);
                }
            }
            LOG.info("****** '{}' file is ingested. Now, check the EASY sword process state....", bagitZippedFile.getName());
            LOG.info("Retrieving Statement IRI (Stat-IRI) from deposit receipt ...");
            receipt = BridgeHelper.parse(bodyText);
            Link statLink = receipt.getLink("http://purl.org/net/sword/terms/statement");
            IRI statIri = statLink.getHref();
            LOG.info("Stat-IRI = {}", statIri);
            easyResponseDataHolder = trackDeposit(http, statIri.toURI(), checkingTimePeriod, bagitZippedFile.getName());
            LOG.info(easyResponseDataHolder.getState().get().toString());
        } catch (FileNotFoundException e) {
            LOG.error("FileNotFoundException: {}", e.getMessage());
            throw new BridgeException("execute - FileNotFoundException, msg: " + e.getMessage(), e, this.getClass());
        } catch (NoSuchAlgorithmException e) {
            LOG.error("NoSuchAlgorithmException: {}", e.getMessage());
            throw new BridgeException("execute - NoSuchAlgorithmException, msg: " + e.getMessage(), e, this.getClass());
        } catch (URISyntaxException e) {
            LOG.error("URISyntaxException: {}", e.getMessage());
            throw new BridgeException("execute - URISyntaxException, msg: " + e.getMessage(), e, this.getClass());
        } catch (IOException e) {
            LOG.error("IOException, msg: {}", e.getMessage());
            throw new BridgeException("execute - IOException, msg: " + e.getMessage(), e, this.getClass());
        }
        return easyResponseDataHolder;
    }

    private int getNumberOfChunks(long filesize) {
        int numberOfChunk = 0;
        if ((filesize% CHUNK_SIZE) != 0)
            numberOfChunk = 1;

        int x = (int) Math.floorDiv(filesize, CHUNK_SIZE);

        return (numberOfChunk + x);
    }


    private DigestInputStream getDigestInputStream(File bagitZipFile) throws FileNotFoundException, NoSuchAlgorithmException {
        FileInputStream fis = new FileInputStream(bagitZipFile);
        MessageDigest md = MessageDigest.getInstance("MD5");
        return new DigestInputStream(fis, md);
    }

    private EasyResponseDataHolder trackDeposit(CloseableHttpClient http, URI statUri, long checkingTimePeriod, String filename) throws BridgeException {
        //filename is just needed for logging convenient especially when a lot of ingest process in the same time.
        EasyResponseDataHolder easyResponseDataHolder;
        CloseableHttpResponse response;
        LOG.info("Checking Time Period: {} milliseconds.", checkingTimePeriod);
        LOG.info("Start polling Stat-IRI for the current status of the deposit, waiting {} milliseconds before every request ...", checkingTimePeriod);
        while (true) {
            try {
                Thread.sleep(checkingTimePeriod);
                LOG.info("Checking deposit status ... of '{}'", filename);
                response = http.execute(new HttpGet(statUri));
                int rc = response.getStatusLine().getStatusCode();
                LOG.info("response code: {}", rc);
                if (rc != HTTP_OK) {
                    String errMsg = "Status code != 200 (HTTP_OK).  statUri: " + statUri + ". Response: " + response.getEntity().getContent();
                    LOG.error(errMsg);
                    throw new BridgeException(errMsg, this.getClass());
                }
                easyResponseDataHolder = new EasyResponseDataHolder(response.getEntity().getContent());
                StateEnum state = easyResponseDataHolder.getState().get();
                LOG.info("[{}] Response state from EASY: {}", filename, state);
                switch (state) {
                    case ARCHIVED:
                    case FAILED:
                    case REJECTED:
                    case INVALID:
                        return easyResponseDataHolder;
                }
            } catch (InterruptedException e) {
                LOG.error("InterruptedException, msg: {}", e.getMessage());
                throw new BridgeException("InterruptedException ", e, this.getClass());
            } catch (ClientProtocolException e) {
                LOG.error("ClientProtocolException, msg: {}", e.getMessage());
                throw new BridgeException("ClientProtocolException ", e, this.getClass());
            } catch (IOException e) {
                LOG.error("IOException, msg: {}", e.getMessage());
                throw new BridgeException("IOException ", e, this.getClass());
            }
        }
    }
}
