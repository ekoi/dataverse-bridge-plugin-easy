<xsl:stylesheet
        xmlns:emd="http://easy.dans.knaw.nl/easy/easymetadata/"
        xmlns:dc="http://purl.org/dc/elements/1.1/"
        xmlns:dcterms="http://purl.org/dc/terms/"
        xmlns:ddm="http://easy.dans.knaw.nl/schemas/md/ddm/"
        xmlns:dcx-dai="http://easy.dans.knaw.nl/schemas/dcx/dai/"
        xmlns:mods="http://www.loc.gov/mods/v3"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:id-type="http://easy.dans.knaw.nl/schemas/vocab/identifier-type/"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xpath-default-namespace="http://www.w3.org/2005/xpath-functions"
        version="3.0">
  <xsl:output encoding="UTF-8" indent="yes" method="xml"/>
  <xsl:strip-space elements="*"/>

  <xsl:param name="dvnJson"/>

  <xsl:mode on-no-match="shallow-copy"/>

  <xsl:template name="initialTemplate">
    <xsl:apply-templates select="json-to-xml($dvnJson)"/>
  </xsl:template>
  <!--[@key='typeName' and text()='title']-->
  <xsl:template match="/">
    <ddm:DDM>
      <xsl:call-template name="profile"/>
      <xsl:call-template name="dcmiMetadata"/>
    </ddm:DDM>
  </xsl:template>
  <xsl:template name="profile">
    <ddm:profile>
      <dc:title>
        <xsl:value-of select="//array[@key='fields']/map/string[@key='typeName' and text()='title']/following-sibling::string[@key='value']/."/>
      </dc:title>
      <dcterms:description>
        <xsl:value-of select="//map[@key='dsDescriptionValue']/string[@key='typeName' and text()='dsDescriptionValue']/following-sibling::string[@key='value']/."/>
      </dcterms:description>
      <xsl:for-each select="//map[@key='authorName']">
        <xsl:variable name="intial" select="substring-after(./string[@key='typeName' and text()='authorName']/following-sibling::string[@key='value']/., ', ')"/>
        <xsl:variable name="surname" select="substring-before(./string[@key='typeName' and text()='authorName']/following-sibling::string[@key='value']/., ', ')"/>
        <dcx-dai:creatorDetails>
          <dcx-dai:author>
            <!-- <dcx-dai:titles></dcx-dai:titles> -->
            <dcx-dai:initials>
              <xsl:value-of select="substring($intial, 1, 1)"/>
            </dcx-dai:initials>
            <dcx-dai:insertions/>
            <dcx-dai:surname>
              <xsl:value-of select="$surname"/>
            </dcx-dai:surname>
            <dcx-dai:organization>
              <dcx-dai:name xml:lang="en">
                <xsl:value-of select="//string[@key='typeName' and text()='authorAffiliation']/following-sibling::string[@key='value']/."/>
              </dcx-dai:name>
            </dcx-dai:organization>
          </dcx-dai:author>
        </dcx-dai:creatorDetails>
      </xsl:for-each>
      <ddm:created>
        <xsl:value-of select="/map/string[@key='publicationDate']/."/>
        <!--<xsl:value-of select="format-dateTime(/map/map[@key='datasetVersion']/string[@key='releaseTime']/.,'[Y0001]-[M01]-[D01]')"/>-->
      </ddm:created>
      <!-- current date instead, according to document -->
      <ddm:available>
        <!-- EKO: Comment out for junit test purpose  -->
        <!--<xsl:value-of select="format-dateTime(current-dateTime(), '[Y0001]-[M01]-[D01]')"/>-->
      </ddm:available>
      <xsl:for-each select="/map/map/map[@key='metadataBlocks']/map[@key='citation']/array[@key='fields']/map/string[@key='typeName' and text()='subject']/following-sibling::array[@key='value']/string/.">

        <!--<ddm:audience>-->
        <!--<xsl:value-of select="."/>-->
        <!--</ddm:audience>-->
        <xsl:variable name="audience">
          <xsl:call-template name="audiencefromkeyword">
            <xsl:with-param name="val" select="."/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:if test="$audience != ''">
          <ddm:audience>
            <xsl:value-of select="$audience"/>
          </ddm:audience>
        </xsl:if>
      </xsl:for-each>
      <ddm:accessRights>OPEN_ACCESS</ddm:accessRights>
    </ddm:profile>
  </xsl:template>

  <xsl:template name="dcmiMetadata">
    <ddm:dcmiMetadata>
      <!-- TODO maybe add dcterms:rightsHolder ? -->
      <!-- TODO maybe add dc:publisher ? -->
      <!-- TODO maybe add dc:date ? -->
      <!-- TODO maybe add dc:language ? -->

      <xsl:for-each select="/map/map/map[@key='metadataBlocks']/map[@key='citation']/array[@key='fields']/map/string[@key='typeName' and text()='language']/following-sibling::array[@key='value']/string/.">
        <dc:language>
          <xsl:value-of select="."/>
        </dc:language>
      </xsl:for-each>
      <!--<dc:license><xsl:value-of select="/map/map[@key='datasetVersion']/string[@key='license']/."/></dc:license>-->
      <dcterms:license><xsl:value-of select="/map/map[@key='datasetVersion']/string[@key='license']/."/></dcterms:license>
      <dcterms:rightsHolder/>
      <!-- list all keywords, even if allreay mapped to audience, because we have human readable Datavese specific text -->
      <xsl:for-each select="/map/map/map[@key='metadataBlocks']/map[@key='citation']/array[@key='fields']/map/string[@key='typeName' and text()='keyword']/following-sibling::array[@key='value']/map/.">
        <dc:subject>
          <xsl:value-of select="./map[@key='keywordValue']/string[@key='value']/."/>
        </dc:subject>
      </xsl:for-each>

      <!--<ddm:additional-xml>-->
        <!--<mods:recordInfo>-->
            <!--<mods:recordOrigin><xsl:value-of select="/map/string[@key='publisher']/."/></mods:recordOrigin>-->
        <!--</mods:recordInfo>-->
      <!--</ddm:additional-xml>-->
      <!-- Always a Dataset
            We cannot be more specific, but maybe Collection might be semantically better? -->
      <dc:type xsi:type="dcterms:DCMIType">Dataset</dc:type>

      <!-- compile list of distinct file mime types -->
      <!--TODO: EKO-->
      <!--<xsl:for-each select="ddi:otherMat/ddi:notes[@level='file' and @subject='Content/MIME Type']">-->
        <!--<xsl:if test="not(.=preceding::*)">-->
          <!--&lt;!&ndash; as long as it is a true mime type we can add the dcterms:IMT &ndash;&gt;-->
          <!--<dc:format xsi:type="dcterms:IMT"><xsl:value-of select="."/></dc:format>-->
        <!--</xsl:if>-->
      <!--</xsl:for-each>-->

      <!-- TODO would like to have the handle (pid) here, would be very strange not to have it.
          It points to the dataset in Dataverse which has all versions and not just the one deposited in EASY,
          also it might be just a tumbstone. -->
      <ddm:isVersionOf>
        <xsl:attribute name="href"><xsl:value-of select="/map/string[@key='persistentUrl']/."/> </xsl:attribute>
        <xsl:value-of select="concat(/map/string[@key='protocol']/.,':',/map/string[@key='authority']/.,'/',/map/string[@key='identifier']/.)"/>
      </ddm:isVersionOf>
      <!-- maybe also add handle as relation with link and dcterms:isVersionOf, then it becomes a clickable link in EASY ?  -->

      <!-- Note: Where do we put the version information; like V1 in the citation? -->

      <!--Geospatial-->
      <!--Country-->
      <dcterms:spatial>
        <xsl:value-of select="/map/map/map[@key='metadataBlocks']/map[@key='geospatial']/array/map/array[@key='value']/map/map[@key='country']/string[@key='value']/."/>
      </dcterms:spatial>
      <!--State-->
      <dcterms:spatial>
        <xsl:value-of select="/map/map/map[@key='metadataBlocks']/map[@key='geospatial']/array/map/array[@key='value']/map/map[@key='state']/string[@key='value']/."/>
      </dcterms:spatial>
      <!--City-->
      <dcterms:spatial>
        <xsl:value-of select="/map/map/map[@key='metadataBlocks']/map[@key='geospatial']/array/map/array[@key='value']/map/map[@key='city']/string[@key='value']/."/>
      </dcterms:spatial>
      <!--Other-->
      <dcterms:spatial>
        <xsl:value-of select="/map/map/map[@key='metadataBlocks']/map[@key='geospatial']/array/map/array[@key='value']/map/map[@key='otherGeographicCoverage']/string[@key='value']/."/>
      </dcterms:spatial>

      <dcterms:spatial xsi:type="dcterms:Box">name=Western Australia; northlimit=-13.5; southlimit=-35.5; westlimit=112.5; eastlimit=129
      </dcterms:spatial>

    </ddm:dcmiMetadata>
  </xsl:template>
  <!-- Mapping from the Dataverse keywords to the Narcis Discipline types (https://easy.dans.knaw.nl/schemas/vocab/2015/narcis-type.xsd) -->
  <xsl:template name="audiencefromkeyword">
    <xsl:param name="val"/>
    <!-- make our own map, it's small -->
    <xsl:choose>
      <xsl:when test="$val = 'Agricultural sciences'">
        <xsl:value-of select="'D18000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Law'">
        <xsl:value-of select="'D40000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Social Sciences'">
        <xsl:value-of select="'D60000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Arts and Humanities'">
        <xsl:value-of select="'D30000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Astronomy and Astrophysics'">
        <xsl:value-of select="'D17000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Business and Management'">
        <xsl:value-of select="'D70000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Chemistry'">
        <xsl:value-of select="'D13000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Computer and Information Science'">
        <xsl:value-of select="'D16000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Earth and Environmental Sciences'">
        <xsl:value-of select="'D15000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Engineering'">
        <xsl:value-of select="'D14400'"/>
      </xsl:when>
      <xsl:when test="$val = 'Mathematical Sciences'">
        <xsl:value-of select="'D11000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Medicine, Health and Life Sciences'">
        <xsl:value-of select="'D20000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Physics'">
        <xsl:value-of select="'D12000'"/>
      </xsl:when>
      <xsl:when test="$val = 'Other'">
        <xsl:value-of select="'E10000'"/>
      </xsl:when>
      <xsl:otherwise>
        <!-- Don't do the default mapping to E10000, otherwise we cannot detect that nothing was found -->
        <xsl:value-of select="''"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>
</xsl:stylesheet>
