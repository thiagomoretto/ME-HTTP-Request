package br.eng.moretto.me.tests;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import br.eng.moretto.me.MEHTTPRequest;

public class MEHTTPRequestTests {
    String urlString = "http://localhost:50036";

    boolean stdOutRequestDidRedirectedListenerWasCalled,
            stdOutRequestDidFinishedListenerWasCalled,
            stdErrRequestDidFailedListenerWasCalled;

    MEHTTPRequest.Listener stdOutRequestDidRedirectedListener = new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdOutRequestDidRedirectedListenerWasCalled = true;
            printHeaders(request.getHeaders());
        }
    };
    MEHTTPRequest.Listener stdOutRequestDidFinishedListener = new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdOutRequestDidFinishedListenerWasCalled = true;
        }
    };
    MEHTTPRequest.Listener stdErrRequestDidFailedListener =  new MEHTTPRequest.Listener() {
        @Override
        public void on(MEHTTPRequest request) {
            stdErrRequestDidFailedListenerWasCalled = true;
            request.getException().printStackTrace(System.err);
        }
    };
    
    static HttpTestServer httpTestServer;

    @BeforeClass
    static public void setupClass() throws Exception {
        httpTestServer = new HttpTestServer();
        httpTestServer.start();
    }

    @Before
    public void setup() throws Exception {
        stdOutRequestDidRedirectedListenerWasCalled = false;
        stdOutRequestDidFinishedListenerWasCalled = false;
        stdErrRequestDidFailedListenerWasCalled = false;
    }

    @Test
    public void shouldMakeGetRequestReceiveOk() throws MalformedURLException {
        httpTestServer.setMockResponseCode(200);
        httpTestServer.setMockResponseData("MY OMG DATA");
        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener);
        request.addHeader("X-MyProduct-HEADER", "FOOBAR");
        request.startSynchronous();
        printHeaders(request.getHeaders());

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertEquals("MY OMG DATA", request.getResponseData());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }

    @Test
    public void shouldMakeGetRequestReceiveNotModified() throws MalformedURLException {
        httpTestServer.setMockResponseCode(304);
        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener);
        request.addHeader("X-MyProduct-HEADER", "FOOBAR");
        request.startSynchronous();
        printHeaders(request.getHeaders());

        Assert.assertEquals(304, request.getResponseCode());
        Assert.assertTrue(stdOutRequestDidRedirectedListenerWasCalled);
    }

    @Test
    public void shouldMakeGetRequestAndSaveContentOfFile() throws MalformedURLException {
        httpTestServer.setMockResponseCode(200);

        URL testURL = new URL(urlString);
        MEHTTPRequest request = new MEHTTPRequest(testURL);
        request .setShouldFollowRedirects(true)
                .setDidRequestRedirectedListener(stdOutRequestDidRedirectedListener)
                .setDidRequestFinishedListener(stdOutRequestDidFinishedListener)
                .setDidRequestFailedListener(stdErrRequestDidFailedListener)
                .setDownloadDestinationPath("/tmp/temp_file")
                .startSynchronous();

        Assert.assertEquals(200, request.getResponseCode());
        Assert.assertTrue(new File("/tmp/temp_file").exists());
        Assert.assertTrue(stdOutRequestDidFinishedListenerWasCalled);
    }

    private void printHeaders(Map<String, List<String>> headers)
    {
        for (String header : headers.keySet())
        {
            List<String> values = headers.get(header);
            System.out.print("Header: " + header);
            for (String hv : values)
            {
                System.out.println("\t" + hv);
            }
        }
    }
}
