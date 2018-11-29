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

import net.sf.saxon.s9api.SaxonApiException;
import nl.knaw.dans.bridge.plugin.lib.common.*;
import nl.knaw.dans.bridge.plugin.lib.exception.BridgeException;
import nl.knaw.dans.bridge.plugin.lib.util.BridgeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.util.*;

/**
 * @author Eko Indarto
 */
public class EasyTransformer implements ITransform {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    public static final String XSL_OUTPUT_EASY_DATASET = "dataset.xml";
    public static final String XSL_OUTPUT_EASY_FILES = "files.xml";
    public static final String XSL_OUTPUT_SOURCE_FILE_LIST = "source-files-location";
    private final XPath xPath = XPathFactory.newInstance().newXPath();
    private List<SourceFile> sourceFiles;

    @Override
    public Map<String, String> transformMetadata(SourceDar sourceDar, DestinationDar destinationDar, List<XslTransformer> xslTransformerList) throws BridgeException {
        URL jsonMetadataUrl = sourceDar.getMetadataUrl();
        LOG.info("transformMetadata - jsonMetadataUrl {}", jsonMetadataUrl);
        String datasetXml = convertJsonToXml(jsonMetadataUrl, xslTransformerList, XSL_OUTPUT_EASY_DATASET);
        LOG.info("transformMetadata - datasetXml {}", datasetXml);
        if (destinationDar.getDarUserAffiliation() != null){//dcterms:rightHolder is optional
            datasetXml = addDctermsRightHolder(datasetXml, destinationDar.getDarUserAffiliation());
            LOG.info("transformMetadata - addDctermsRightHolder: {}", datasetXml);
        }
        String filesXml = convertJsonToXml(jsonMetadataUrl, xslTransformerList, XSL_OUTPUT_EASY_FILES);
        LOG.info("transformMetadata - filesXml {}", filesXml);
        String sourceFileListXmlOutput = convertJsonToXml(jsonMetadataUrl, xslTransformerList, XSL_OUTPUT_SOURCE_FILE_LIST);
        LOG.info("transformMetadata - sourceFileListXmlOutput {}", sourceFileListXmlOutput);
        sourceFiles = createSourceFileList(sourceFileListXmlOutput);
        LOG.info("transformMetadata - sourceFiles{}", sourceFiles);
        Map<String, String> transformResult = new HashMap<>();
        transformResult.put(XSL_OUTPUT_EASY_DATASET, datasetXml);
        transformResult.put(XSL_OUTPUT_EASY_FILES, filesXml);
        return transformResult;
    }

    private String addDctermsRightHolder(String datasetXml, String userAffiliation) throws BridgeException {
        try {
            Document datasetXmlDoc =  BridgeHelper.buildDocumentFromString(datasetXml);
            Node rightsHolder = (Node)xPath.evaluate("//*[local-name()='rightsHolder']", datasetXmlDoc, XPathConstants.NODE);
            if(rightsHolder != null)//this isn't required element.
                rightsHolder.setTextContent(userAffiliation);
            return BridgeHelper.transform(datasetXmlDoc);
        } catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException |TransformerException e) {
            LOG.error("EasyTransformer - addDctermsRightHolder, causes by: {}", e.getMessage());
            throw new BridgeException("EasyTransformer - addDctermsRightHolder , caused by: " + e.getMessage(), e
                    , this.getClass());
        }
    }

    private List<SourceFile> createSourceFileList(String sourceFileListXmlOutput) throws BridgeException{
        LOG.info("createSourceFileList - sourceFileListXmlOutput{}", sourceFileListXmlOutput);
        List<SourceFile> sourceFiles = new ArrayList<>();
        try {
            Document sourceFilesDoc = BridgeHelper.buildDocumentFromString(sourceFileListXmlOutput);
            NodeList fileElementList = (NodeList) xPath.evaluate("//file", sourceFilesDoc, XPathConstants.NODESET);
            for(int i = 0; i < fileElementList.getLength(); i++) {
                Node fileElement = fileElementList.item(i);
                NamedNodeMap namedNodeMap = fileElement.getAttributes();
                String restricted = namedNodeMap.getNamedItem("restricted").getTextContent();
                SourceFile sourceFile = new SourceFile(fileElement.getTextContent(), Boolean.parseBoolean(restricted)
                                        , Long.parseLong(namedNodeMap.getNamedItem("size").getTextContent())
                                        , namedNodeMap.getNamedItem("url").getTextContent());
                sourceFiles.add(sourceFile);
            }
        } catch (XPathExpressionException | ParserConfigurationException | IOException | SAXException e) {
            LOG.error("EasyTransformer - createSourceFileList, causes by: {}", e.getMessage());
            throw new BridgeException("EasyTransformer - createSourceFileList, caused by: " + e.getMessage(), e
                    , this.getClass());
        }
        LOG.info("createSourceFileList - listOfSourceFiles {}", sourceFiles.size());
        return sourceFiles;
    }

    @Override
    public Optional<SourceFileList> getSourceFileList(String apiToken) {
        return Optional.of(new SourceFileList(apiToken, sourceFiles));
    }

    private String convertJsonToXml(URL jsonMetadataUrl, List<XslTransformer> xslTransformerList, String output) throws BridgeException {
        LOG.info("convertJsonToXml - output: {}", output);
        XslTransformer xslSource = xslTransformerList.stream().filter(i -> i.getName().equals(output)).findAny()
                                                                .orElseThrow(() -> new BridgeException("Transformer of " + output + " NOT FOUND", this.getClass()));
       try {
            return BridgeHelper.transformJsonToXml(jsonMetadataUrl, xslSource.getUrl());
        } catch (SaxonApiException | IOException e) {
            LOG.error("EasyTransformer - convertJsonToXml jsonMetadataUrl: {} \toutput: {}, \tcaused by: {}", jsonMetadataUrl, output, e.getMessage());
            throw new BridgeException("EasyTransformer - convertJsonToXml, caused by: " + e.getMessage()
                    , e, this.getClass());
        }
    }

}
