<xsl:stylesheet
        xmlns:dcterms="http://purl.org/dc/terms/"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output encoding="UTF-8" indent="yes" method="xml"/>
  <xsl:strip-space elements="*"/>

  <xsl:param name="dvnJson"/>

  <xsl:mode on-no-match="shallow-copy"/>

  <xsl:template name="initialTemplate">
    <xsl:apply-templates select="json-to-xml($dvnJson)"/>
  </xsl:template>
  <!--[@key='typeName' and text()='title']-->
  <xsl:template match="/" xpath-default-namespace="http://www.w3.org/2005/xpath-functions">
    <files>
      <file>
        <xsl:variable name="jsonfilename" select="concat(/map/string[@key='protocol']/.,'-',/map/string[@key='authority']/.,'-',/map/string[@key='identifier']/.,'.json')"/>
        <xsl:attribute name="filepath">
          <xsl:value-of select="concat('data/Metadata export from DataverseNL/',$jsonfilename)"/>
        </xsl:attribute>
        <dcterms:title><xsl:value-of select="$jsonfilename"/></dcterms:title>
        <dcterms:format>application/json</dcterms:format>
        <dcterms:accessibleToRights>ANONYMOUS</dcterms:accessibleToRights>
        <dcterms:visibleToRights>ANONYMOUS</dcterms:visibleToRights>
      </file>
      <xsl:for-each select="//map[@key='datasetVersion']/array[@key='files']/map">
        <file>
          <xsl:attribute name="filepath">
            <xsl:value-of select="concat('data/',./map[@key='dataFile']/string[@key='filename']/.)"/>
          </xsl:attribute>
          <dcterms:format xsi:type="dcterms:IMT"><xsl:value-of select="./map[@key='dataFile']/string[@key='contentType']/."/></dcterms:format>
          <dcterms:title><xsl:value-of select="./map[@key='dataFile']/string[@key='filename']/."/></dcterms:title>
          <xsl:choose>
            <xsl:when test="./boolean[@key='restricted']/. = 'true'">
              <dcterms:accessibleToRights>RESTRICTED_REQUEST</dcterms:accessibleToRights>
            </xsl:when>
            <xsl:otherwise>
              <dcterms:accessibleToRights>ANONYMOUS</dcterms:accessibleToRights>
            </xsl:otherwise>
          </xsl:choose>
          <dcterms:visibleToRights>ANONYMOUS</dcterms:visibleToRights>
        </file>
      </xsl:for-each>
    </files>
  </xsl:template>
</xsl:stylesheet>
