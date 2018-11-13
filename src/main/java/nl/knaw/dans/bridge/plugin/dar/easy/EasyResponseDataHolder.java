package nl.knaw.dans.bridge.plugin.dar.easy;

import nl.knaw.dans.bridge.plugin.lib.exception.BridgeException;
import nl.knaw.dans.bridge.plugin.lib.util.StateEnum;
import org.apache.abdera.Abdera;
import org.apache.abdera.model.Category;
import org.apache.abdera.model.Document;
import org.apache.abdera.model.Entry;
import org.apache.abdera.model.Feed;
import org.apache.abdera.parser.Parser;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;

/*
    @author Eko Indarto
 */
public class EasyResponseDataHolder implements nl.knaw.dans.bridge.plugin.lib.common.IResponseData {
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private String state;
    private String pid;
    private String landingPage;
    private String easyLandingPage;
    private String feedXml;
    private static Abdera abdera = null;

    private static synchronized Abdera getInstance() {
        if (abdera == null) {
            abdera = new Abdera();
        }
        return abdera;
    }

    public EasyResponseDataHolder(InputStream content) throws BridgeException {
        init(content);
    }

    private void init(InputStream inputStream) throws BridgeException {
        try {
            feedXml = IOUtils.toString(inputStream, "UTF-8");
            //Eko says, we can validate the feedXml by using javax.xml.validation.Validator
            //But this simple check is enough for now.
            if (feedXml == null || feedXml.isEmpty() ) {
                LOG.error("feedXml is null or empty");
                throw new BridgeException("init() - feed is null or empty", this.getClass());
            }
            LOG.info(feedXml);
        } catch (IOException e) {
            throw new BridgeException(e.getMessage(), e, this.getClass());
        }
        Parser parser = getInstance().getParser();
        Document<Feed> doc = parser.parse(new ByteArrayInputStream(feedXml.getBytes()));
        Feed feed = doc.getRoot();
        List<Category> categories = feed.getCategories("http://purl.org/net/sword/terms/state");
        if (categories.size() != 1) {
            LOG.error("Fatal Error: Zero or multiples categories. Catagories size:  {}", categories.size());
            throw new BridgeException("Zero or multiples categories. Catagories size:  " + categories.size(), this.getClass());
        } else {
            Category category = categories.get(0);
            state = category.getTerm();
            if (state.equals(StateEnum.ARCHIVED.toString())){
                List<Entry> entries = feed.getEntries();
                if (entries.size() != 1) {
                    LOG.error("Fatal Error: Zero or multiples entries. Entries size:  {}", entries.size());
                    throw new BridgeException("Entries size is not equals 1. Size: " + entries.size(), this.getClass());
                } else {
                    easyLandingPage = category.getText();
                    Entry entry = entries.get(0);
                    landingPage = entry.getLink("self").getHref().toString();
                    pid = entry.getLink("self").getHref().getPath().replaceFirst("/", "");
                }
            } else {
                LOG.debug("State is : {} \tfeed: {}", state, feedXml);
            }
        }
    }

    @Override
    public String getResponse() {
        return feedXml;
    }

    @Override
    public Optional<String> getState() {
        return Optional.of(state);
    }

    @Override
    public Optional<String> getPid() {
        return Optional.of(pid);
    }

    @Override
    public Optional<String> getLandingPage() {
        return Optional.of(landingPage);
    }

    @Override
    public Optional<String> getDarLandingPage() {
        return Optional.of(easyLandingPage);
    }

}
