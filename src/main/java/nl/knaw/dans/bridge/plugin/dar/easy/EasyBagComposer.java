package nl.knaw.dans.bridge.plugin.dar.easy;

import gov.loc.repository.bagit.Bag;
import gov.loc.repository.bagit.BagFactory;
import gov.loc.repository.bagit.Manifest;
import gov.loc.repository.bagit.PreBag;
import gov.loc.repository.bagit.transformer.impl.ChainingCompleter;
import gov.loc.repository.bagit.transformer.impl.DefaultCompleter;
import gov.loc.repository.bagit.transformer.impl.TagManifestCompleter;
import net.lingala.zip4j.exception.ZipException;
import nl.knaw.dans.bridge.plugin.lib.util.BagInfoCompleter;
import nl.knaw.dans.bridge.plugin.lib.common.IBagitComposer;
import nl.knaw.dans.bridge.plugin.lib.common.SourceFile;
import nl.knaw.dans.bridge.plugin.lib.common.SourceFileList;
import nl.knaw.dans.bridge.plugin.lib.exception.BridgeException;
import nl.knaw.dans.bridge.plugin.lib.util.BridgeHelper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/*
    @author Eko Indarto
 */
public class EasyBagComposer implements IBagitComposer {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String API_TOKEN = "API-TOKEN";
    private Path bagitDir;
    private Path bagTempDir;

    @Override
    public File buildBag(String bagitBaseDir, URL metadataUrl, Map<String, String> transformedXml, SourceFileList sourceFileList) throws BridgeException {
        LOG.info("Build bag....  Bagit base dir: {}  metadataUrl: {}" + bagitBaseDir, metadataUrl);
        bagTempDir = createTempDirectory(bagitBaseDir);
        Path metadataDir = createMetadataDir();
        createFileEasyDatasetXml(metadataDir, transformedXml.get(EasyTransformer.XSL_OUTPUT_EASY_DATASET));
        createFileEasyFilesXml(metadataDir, transformedXml.get(EasyTransformer.XSL_OUTPUT_EASY_FILES));
        LOG.info("buildBag - sourceFileList: {}", sourceFileList);
        downloadFiles(sourceFileList);
        composeBagit();
        return createBagitZip();
    }

    private Path createMetadataDir() throws BridgeException {
        LOG.info("bagitDir: {}\tabsoluth path: {}", bagitDir, bagitDir.toAbsolutePath());
        Path metadataDir = Paths.get(bagitDir + "/metadata");
        try {
            Files.createDirectories(metadataDir);
        } catch (IOException e) {
            LOG.error("IOException, msg: {}", e.getMessage());
            throw new BridgeException("buildEasyBag - Files.createDirectories, msg: " + e.getMessage(), e, this.getClass());
        }
        return metadataDir;
    }

    private void downloadFiles(SourceFileList sourceFileList) throws BridgeException {
        LOG.info("*** Starting download files ***");
        long MAX_FILESIZE = 2147483648L;//EASY support only max 2G each file, this should be configurable.

        String apiToken = sourceFileList.getApiToken();
        LOG.info("downloadFiles - apiToken: {}\tsourceFileList: {}\tsource files size: {}", apiToken, sourceFileList, sourceFileList.getSourceFiles().size());
        for (SourceFile sourceFile : sourceFileList.getSourceFiles()) {
            LOG.info("downloadFiles - apiToken: {}", apiToken);
            if (sourceFile.getSize() > MAX_FILESIZE) {
                String errmsg = "The size of " + sourceFile.getName() + "(" + sourceFile.getSource() + ") is more that 2G. Actual size: " + sourceFile.getSize() + " bytes.";
                throw new BridgeException("downloadFiles - " + errmsg, this.getClass());
            }
            try {
                LOG.info("**** Starting download file of '{}' from {}", sourceFile.getName(), sourceFile.getSource());
                Instant start = Instant.now();
                URL url;
                if (sourceFile.isRestricted()) {
                    url = new URL(sourceFile.getSource().replace(API_TOKEN, apiToken));
                    LOG.info("RESTRICTED FILE: {}", url);
                } else {
                    url = new URL(sourceFile.getSource());
                    LOG.info("PUBLIC FILE: {}", url);
                }
                File downloadedFile = new File(bagTempDir + "/data/" + sourceFile.getName());
                FileUtils.copyURLToFile(url,  downloadedFile);
                Instant finish = Instant.now();
                long timeElapsed = Duration.between(start, finish).getSeconds();
                LOG.info("**** '{}' is downloaded in {} seconds.", sourceFile.getName(), timeElapsed);
            } catch (IOException e) {
                throw new BridgeException("[downloadFiles] of '" + sourceFile.getName() + "' from " + sourceFile.getSource() + ". Error msg: " + e.getMessage(), e, this.getClass());

            }
        }
    }

    private void composeBagit() {
        LOG.info("Compose bagit");
        BagFactory bf = new BagFactory();
        BagInfoCompleter bic = new BagInfoCompleter(bf);
        DefaultCompleter dc = new DefaultCompleter(bf);
        dc.setPayloadManifestAlgorithm(Manifest.Algorithm.SHA1);
        TagManifestCompleter tmc = new TagManifestCompleter(bf);
        tmc.setTagManifestAlgorithm(Manifest.Algorithm.SHA1);
        ChainingCompleter completer = new ChainingCompleter(dc, new BagInfoCompleter(bf), tmc);
        PreBag pb = bf.createPreBag(bagTempDir.toFile());
        pb.makeBagInPlace(BagFactory.Version.V0_97, false, completer);
        Bag b = bf.createBag(bagTempDir.toFile());
    }

    private File createBagitZip() throws BridgeException {
        LOG.info("Creating bagit zip: {}.zip.", bagTempDir.toFile().getAbsolutePath());
        File zipFile = new File(bagTempDir.toFile().getAbsolutePath() + ".zip");
        try {
            BridgeHelper.zipDirectory(bagTempDir.toFile(), zipFile);
            boolean bagDirIsDeleted = FileUtils.deleteQuietly(bagTempDir.toFile());
            if (bagDirIsDeleted)
                LOG.info("{} file is deleted.", bagTempDir.toFile().getAbsolutePath());
            else
                LOG.warn("Deleting {} file is failed.", bagTempDir.toFile().getAbsolutePath());

        } catch (ZipException e) {
            LOG.error("createBagitZip is faild, msg: {}", e.getMessage());
            throw new BridgeException("createBagitZip is faild, msg: " + e.getMessage(), e, this.getClass());

        }
        LOG.info("{} is created.", zipFile.getName());
        return zipFile;
    }

    private void createFileEasyDatasetXml(Path metadataDir, String datasetXml) throws BridgeException {
        File datasetXmlFile = new File(metadataDir + "/" + EasyTransformer.XSL_OUTPUT_EASY_DATASET);
        try {
            boolean newXmlFileIsCreated = datasetXmlFile.createNewFile();
            if (!newXmlFileIsCreated)
                throw new BridgeException("Failed to create dataset.xml file", this.getClass());
            Files.write(datasetXmlFile.toPath(), datasetXml.getBytes());
        } catch (IOException e) {
            String msg = "createDatasetXmlFile, msg: " + e.getMessage();
            LOG.error("ERROR: " , msg);
            throw new BridgeException(msg, e, this.getClass());
        }
    }

    private void createFileEasyFilesXml(Path metadataDir, String filesXml) throws BridgeException {
        File filesXmlFile = new File(metadataDir + "/" + EasyTransformer.XSL_OUTPUT_EASY_FILES);
        try {
            Files.write(filesXmlFile.toPath(), filesXml.getBytes());
        } catch (IOException e) {
            String msg = "createFilesXmlFile, msg: " + e.getMessage();
            LOG.error(msg);
            throw new BridgeException(msg, e, this.getClass());
        }
    }

    private Path createTempDirectory(String baseDir) throws BridgeException {
        try {
            bagitDir = Files.createTempDirectory(Paths.get(baseDir), "bagit");
            return bagitDir;
        } catch (IOException e) {
            String msg = "createTempDirectory, msg: " + e.getMessage();
            LOG.error(msg);
            throw new BridgeException(msg, e, this.getClass());
        }
    }
}
