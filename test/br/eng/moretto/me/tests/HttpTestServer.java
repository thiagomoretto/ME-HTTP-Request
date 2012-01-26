package br.eng.moretto.me.tests;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Ignore;
import org.mortbay.jetty.Handler;
import org.mortbay.jetty.HttpConnection;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

/**
 * A server for answering HTTP requests with test response data.
 * 
 * @author Olaf Otto, Thiago Moretto
 */
@Ignore
public class HttpTestServer {
    public static final int HTTP_PORT = 50036;

    private Server _server;
    private String _responseBody;
    private String _requestBody;

    private int _mockResponseCode = 200;
    private String _mockResponseData;
    private String _mockContentType;
    private String _mockFile;
    private int _mockStopDownloadAtByte = -1;

    public HttpTestServer() {
    }

    public HttpTestServer(String mockData) {
        setMockResponseData(mockData);
    }

    public void start() throws Exception {
        configureServer();
        startServer();
    }

    private void startServer() throws Exception {
        _server.start();
    }

    protected void configureServer() {
        _server = new Server(HTTP_PORT);
        _server.setHandler(getMockHandler());
    }

    /**
     * Creates an {@link AbstractHandler handler} returning an arbitrary String as a response.
     *
     * @return never <code>null</code>.
     */
    public Handler getMockHandler() {
        Handler handler = new AbstractHandler() {

            public void handle(String target, HttpServletRequest request, HttpServletResponse response, int dispatch) throws IOException, ServletException {
                Request baseRequest = request instanceof Request ? (Request) request : HttpConnection.getCurrentConnection().getRequest();
                setResponseBody(getMockResponseData());
                response.setStatus(_mockResponseCode);
                if (_mockContentType != null)
                    response.setContentType(_mockContentType);
                else
                    response.setContentType("text/plain;charset=utf-8");

                int range = 0;
                String rangeHeader = baseRequest.getHeader("Range");
                if (rangeHeader != null)
                    range = Integer.parseInt(rangeHeader.split("=")[1].replace("-", ""));

                if (_mockFile != null)
                {
                    File fd = new File(_mockFile);
                    FileInputStream fis = new FileInputStream(fd);
                    byte[] b = new byte[1];
                    OutputStream os = response.getOutputStream();
                    int count = 0;
                    while (fis.read(b) != -1) {
                        if (count >= range)
                            os.write((byte) b[0]);
                        count ++;
                        if(_mockStopDownloadAtByte != -1 &&
                           _mockStopDownloadAtByte == count) {
                            break;
                        }
                    }
                    os.flush();
                } else {
                    response.getOutputStream().write(_mockResponseData.getBytes());
                }
                baseRequest.setHandled(true);
            }
        };
        return handler;
    }

    public void stop() throws Exception {
        _server.stop();
    }

    public void setResponseBody(String responseBody) {
        _responseBody = responseBody;
    }

    public String getResponseBody() {
        return _responseBody;
    }

    public void setRequestBody(String requestBody) {
        _requestBody = requestBody;
    }

    public String getRequestBody() {
        return _requestBody;
    }

    public static void main(String[] args) {
        HttpTestServer server = new HttpTestServer();
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void setMockResponseCode(int mockResponseCode) {
        _mockResponseCode = mockResponseCode;
    }

    public void setMockResponseData(String mockResponseData) {
        _mockResponseData = mockResponseData;
    }

    public String getMockResponseData() {
        return _mockResponseData;
    }

    protected Server getServer() {
        return _server;
    }

    public void setMockContentType(String mockContentType) {
        _mockContentType = mockContentType;
    }

    public void setMockResponseFile(String mockFile) {
        _mockFile = mockFile;
    }

    public void setMockStopDownloadAtByte(int mockStopDownloadAtByte) {
        this._mockStopDownloadAtByte = mockStopDownloadAtByte;
    }
}
