/**
 * Copyright (C) 2018 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.bridge.plugin.dar.easy;

import nl.knaw.dans.bridge.plugin.lib.common.*;
import nl.knaw.dans.bridge.plugin.lib.exception.BridgeException;
import org.apache.abdera.i18n.iri.IRI;
import org.apache.commons.io.FileUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class EasyTransformerTest {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String actualFilenameOfEasyDatasetXml = "src/test/resources/xml/hdl-101204-hkdsa-easy-dataset-result.xml";
    private static final String actualFilenameOfEasyFilesXml = "src/test/resources/xml/hdl-101204-hkdsa-easy-files-result.xml";

    static EasyTransformer easyTransformer;
    static SourceDar sourceDar;
    static DestinationDar destinationDar;
    static List<XslTransformer> xslTransformerList;

    @BeforeClass
    public static void setUp() throws Exception {
        easyTransformer = new EasyTransformer();
        URL metadataUrl = new File("src/test/resources/json/hdl-101204-hkdsa.json").toURI().toURL();
        cleanupTransformfileResult();

        URL dvnJsonToEasyDatasetXslUrl = new File("src/test/resources/xsl/dataverseJson-to-easy-dataset.xsl").toURI().toURL();
        URL dvnJsonToEasyFilesXslUrl = new File("src/test/resources/xsl/dataverseJson-to-easy-files.xsl").toURI().toURL();
        URL dvnJsonToSourceFilesLocationXslUrl = new File("src/test/resources/xsl/dataverseJson-to-files-location.xsl").toURI().toURL();
        sourceDar = new SourceDar(metadataUrl, "akm!10122004");
        destinationDar = new DestinationDar(new IRI("http://deasy.dans.knaw.nl/sword2/collection/1"), "ekoi", "secret", "DANS");
        xslTransformerList = Arrays.asList(
                new XslTransformer(EasyTransformer.XSL_OUTPUT_EASY_DATASET,dvnJsonToEasyDatasetXslUrl),
                new XslTransformer(EasyTransformer.XSL_OUTPUT_EASY_FILES,dvnJsonToEasyFilesXslUrl),
                new XslTransformer(EasyTransformer.XSL_OUTPUT_SOURCE_FILE_LIST,dvnJsonToSourceFilesLocationXslUrl));
    }

    @Test
    public void transformMetadata() throws BridgeException, IOException {
        Map<String, String> actualEasyTransform = easyTransformer.transformMetadata(sourceDar, destinationDar, xslTransformerList);

        File expectedEasyDatasetXmlFile = new File("src/test/resources/xml/hdl-101204-hkdsa-easy-dataset-expected.xml");
        String actualEasyDatasetXml = actualEasyTransform.get(EasyTransformer.XSL_OUTPUT_EASY_DATASET);
        File actualEasyDatasetXmlFile = new File(actualFilenameOfEasyDatasetXml);
        FileUtils.writeStringToFile(actualEasyDatasetXmlFile, actualEasyDatasetXml, StandardCharsets.UTF_8.name());

        assertTrue(FileUtils.contentEqualsIgnoreEOL(expectedEasyDatasetXmlFile, actualEasyDatasetXmlFile, StandardCharsets.UTF_8.name()));

        File expectedEasyFilesXmlFile = new File("src/test/resources/xml/hdl-101204-hkdsa-easy-files-expected.xml");
        String actualEasyFilesXml = actualEasyTransform.get(EasyTransformer.XSL_OUTPUT_EASY_FILES);
        File actualEasyFilesXmlFile = new File(actualFilenameOfEasyFilesXml);
        FileUtils.writeStringToFile(actualEasyFilesXmlFile, actualEasyFilesXml, StandardCharsets.UTF_8.name());

        assertTrue(FileUtils.contentEqualsIgnoreEOL(expectedEasyFilesXmlFile, actualEasyFilesXmlFile, StandardCharsets.UTF_8.name()));
    }

    @Test
    public void getSourceFileList() {
        Optional<SourceFileList> sourceFileList = easyTransformer.getSourceFileList(Mockito.anyString());

        assertTrue(sourceFileList.isPresent());

        SourceFileList sfl = sourceFileList.get();
        List<SourceFile> sourceFiles = sfl.getSourceFiles();
        List<String> actualSourceFileName = sfl.getSourceFiles().stream().map(sourceFile -> sourceFile.getName()).collect(Collectors.toList());
        List<String> expectedSourceFileName = Arrays.asList("Metadata export from DataverseNL/hdl-101204-hkdsa.json",
                                                            "cover-achter.jpg", "cover-voor.jpg");

        assertThat(actualSourceFileName, is(expectedSourceFileName));
    }

    @AfterClass
    public static void oneTimeTearDown() {
        // one-time cleanup
        cleanupTransformfileResult();
    }

    private static void cleanupTransformfileResult() {
        File actualResultFileOfEasyDatasetXml = new File(actualFilenameOfEasyDatasetXml);
        if (actualResultFileOfEasyDatasetXml.exists())
            actualResultFileOfEasyDatasetXml.delete();
        File actualResultFileOfEasyFilesXml = new File(actualFilenameOfEasyFilesXml);
        if (actualResultFileOfEasyFilesXml.exists())
            actualResultFileOfEasyFilesXml.delete();
    }
}