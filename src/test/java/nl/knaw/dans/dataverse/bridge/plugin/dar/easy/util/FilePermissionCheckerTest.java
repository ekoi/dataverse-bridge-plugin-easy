package nl.knaw.dans.dataverse.bridge.plugin.dar.easy.util;

import nl.knaw.dans.dataverse.bridge.plugin.exception.BridgeException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Unit test for simple FilePermissionChecker.
 */

@RunWith(PowerMockRunner.class)
@PrepareForTest({FilePermissionChecker.class})
public class FilePermissionCheckerTest {

    @Before
    public void before(){
        mockStatic(FilePermissionChecker.class);
    }
    @Test
    public void checkRestrictedTest() throws BridgeException {
        when(FilePermissionChecker.check(Mockito.anyString())).thenReturn(FilePermissionChecker.PermissionStatus.RESTRICTED);
        FilePermissionChecker.PermissionStatus ps = FilePermissionChecker.check("https://dataverse.nl/api/access/datafile/11873");
        assertEquals(FilePermissionChecker.PermissionStatus.RESTRICTED, ps);
    }

    @Test
    public void whenExceptionThrown_thenAssertionSucceeds() {
        String url = "httpd://google.com";
        BridgeException thrown = assertThrows(BridgeException.class, () -> {
            when(FilePermissionChecker.check(Mockito.anyString())).thenCallRealMethod();
            FilePermissionChecker.check(url);
        });

        assertEquals("MalformedURLException, message: unknown protocol: httpd", thrown.getMessage());
        assertEquals("nl.knaw.dans.dataverse.bridge.plugin.dar.easy.util.FilePermissionChecker", thrown.getClassName());
    }
}
