package br.eng.moretto.me;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MEHTTPRequest {
    public interface Listener {
        public void on(MEHTTPRequest request);
    }

    final int maxRedirectionsCount = 5; // TODO: use it.

    // request
    URL url;
    String destinationPath = null;
    MEHTTPRequest.Listener didRequestFinishedListener = null;
    MEHTTPRequest.Listener didRequestFailedListener = null;
    MEHTTPRequest.Listener didRequestRedirectedListener = null;
    boolean shouldFollowRedirects = true;
    int redirectionsCount = 0;
    private Map<String, String> requestAdditionalHeaders = new HashMap<String, String>();

    // response
    Exception exception = null;
    int responseCode;
    private Map<String, List<String>> headers;

    public MEHTTPRequest(URL url) {
        this.url = url;
    }

    public MEHTTPRequest setDidRequestRedirectedListener(MEHTTPRequest.Listener listener) {
        this.didRequestRedirectedListener = listener;
        return this;
    }

    public MEHTTPRequest setDidRequestFinishedListener(MEHTTPRequest.Listener listener) {
        this.didRequestFinishedListener = listener;
        return this;
    }

    public MEHTTPRequest setDidRequestFailedListener(MEHTTPRequest.Listener listener) {
        this.didRequestFailedListener = listener;
        return this;
    }

    public MEHTTPRequest setDownloadDestinationPath(String path) {
        this.destinationPath = path;
        return this;
    }

    public MEHTTPRequest addHeader(String name, String value) {
        requestAdditionalHeaders.put(name, value);
        return this;
    }

    public MEHTTPRequest setShouldFollowRedirects(boolean value) {
        shouldFollowRedirects = value;
        return this;
    }

    public void startSynchronous() {
        makeRequest(url);
    }

    public void startAsynchronous() {
        new Thread() {
            public void run() {
                makeRequest(url);
            }
        }.start();
    }

    public int getResponseCode() {
        return responseCode;
    }

    public Exception getException() {
        return exception;
    }

    public Map<String, List<String>> getHeaders() {
        return headers;
    }

    private void makeRequest(URL requestURL) {
        try
        {
            HttpURLConnection urlConnection = (HttpURLConnection) requestURL.openConnection();
            urlConnection.setInstanceFollowRedirects(false);
            for (String requestHeaderName : requestAdditionalHeaders.keySet())
                urlConnection
                    .addRequestProperty(requestHeaderName, requestAdditionalHeaders.get(requestHeaderName));

            urlConnection.connect();
            if (shouldFollowRedirects &&
                    urlConnection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP ||
                    urlConnection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM ||
                    urlConnection.getResponseCode() == HttpURLConnection.HTTP_SEE_OTHER)
            {
                // is a redirection, lets make a new request.
                if (redirectionsCount >= maxRedirectionsCount)
                    throw new Exception("Max redirections count reached");
                redirectionsCount++;
                URL newURL = new URL(urlConnection.getHeaderField("Location"));
                prepareResponse(urlConnection);
                callListenerIfPresent(didRequestRedirectedListener);
                makeRequest(newURL);
            }
            else if (shouldFollowRedirects &&
                    urlConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED)
            {
                prepareResponse(urlConnection);
                callListenerIfPresent(didRequestRedirectedListener);
            }
            else {
                if (destinationPath != null)
                {
                    File fd = new File(destinationPath);
                    if (fd.isDirectory())
                        throw new Exception("Destination path is a directory. Please, append a file name.");
                    BufferedOutputStream bufout = 
                        new BufferedOutputStream(
                            new FileOutputStream(fd));
                    BufferedInputStream bufin = new BufferedInputStream(urlConnection.getInputStream());
                    urlConnection.getContentLength();
                    int count;
                    byte[] buf = new byte[1024];
                    while ((count = bufin.read(buf)) != -1) {
                        bufout.write(buf, 0, count);
                    }
                    bufout.flush();
                    bufout.close();
                    bufin.close();
                }

                prepareResponse(urlConnection);
                callListenerIfPresent(didRequestFinishedListener);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            exception = e;
            callListenerIfPresent(didRequestFailedListener);
        }
    }

    private void callListenerIfPresent(MEHTTPRequest.Listener listener) {
        if (listener != null)
            listener.on(this);
    }

    private void prepareResponse(HttpURLConnection urlConnection) throws IOException {
        this.responseCode = urlConnection.getResponseCode();
        this.headers = urlConnection.getHeaderFields();
    }
}
