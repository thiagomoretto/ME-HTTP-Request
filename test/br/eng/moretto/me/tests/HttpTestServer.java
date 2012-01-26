package br.eng.moretto.me.tests;

import java.io.IOException;

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
                response.setContentType("text/plain;charset=utf-8");
                response.getOutputStream().write(_mockResponseData.getBytes());
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
}
