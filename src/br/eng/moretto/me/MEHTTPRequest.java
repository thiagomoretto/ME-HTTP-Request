package br.eng.moretto.me;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MEHTTPRequest {
    public class PartiallyDownloadedException extends Exception {
        private static final long serialVersionUID = -3031107770544555842L;
        private long bytesRemaining;
        public PartiallyDownloadedException(long bytesRemaining) {
            this.bytesRemaining = bytesRemaining;
        }
        public long getBytesRemaining() {
            return bytesRemaining;
        }
    }
    
    public interface Listener {
        public void on(MEHTTPRequest request);
    }

    final int maxRedirectionsCount = 5; // TODO: use it.
    public static final String DOWNLOAD_PATH_APPENDIX = "-medownload";

    // request
    URL url;
    String destinationPath = null;
    String temporaryDestinationPath = null;
    OutputStream destinationOutputStream = null;
    MEHTTPRequest.Listener didRequestFinishedListener = null;
    MEHTTPRequest.Listener didRequestFailedListener = null;
    MEHTTPRequest.Listener didRequestRedirectedListener = null;
    boolean shouldFollowRedirects = true;
    boolean shouldAllowResumeDownloads = false;
    int redirectionsCount = 0;
    private Map<String, String> requestAdditionalHeaders = new HashMap<String, String>();

    // response
    Exception exception = null;
    int responseCode;
    private String responseData;
    private Map<String, List<String>> headers;

    // stats
    int totalContentReaded = 0;

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
        this.destinationOutputStream = null;
        if (path != null)
        {
            this.destinationPath = path;
            this.temporaryDestinationPath = path + DOWNLOAD_PATH_APPENDIX;
        }
        return this;
    }

    public MEHTTPRequest setDestinationOutputStream(OutputStream destinationOutputStream) {
        this.destinationPath = null;
        this.temporaryDestinationPath = null;
        this.destinationOutputStream = destinationOutputStream;
        return this;
    }

    public MEHTTPRequest setShouldAllowResumeDownloads(boolean shouldAllowResumeDownloads) {
        this.shouldAllowResumeDownloads = shouldAllowResumeDownloads;
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

    public int getTotalContentReaded() {
        return totalContentReaded;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public String getResponseData() {
        return responseData;
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

            if (shouldAllowResumeDownloads && temporaryDestinationPath != null)
            {
                File fd = new File(temporaryDestinationPath);
                if (fd.isFile())
                    urlConnection.setRequestProperty("Range", "bytes=" + fd.length() + "-");
            }

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
                if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK 
                        && destinationOutputStream != null)
                {
                    saveToOutputStream(urlConnection);
                    callListenerIfPresent(didRequestFinishedListener);
                }
                else if (urlConnection.getResponseCode() == HttpURLConnection.HTTP_OK 
                        && destinationPath != null)
                {
                    File fd = new File(destinationPath);
                    if (fd.isDirectory())
                        throw new Exception("Destination path is a directory. Please, append a file name.");
                    File tempFd = new File(temporaryDestinationPath);
                    saveToDestination(tempFd, urlConnection, shouldAllowResumeDownloads);
                    if(tempFd.length() == urlConnection.getContentLength()) {
                        tempFd.renameTo(fd);
                        callListenerIfPresent(didRequestFinishedListener);
                    } else {
                        prepareResponse(urlConnection);
                        throw new PartiallyDownloadedException(
                                        Math.abs(urlConnection.getContentLength() - tempFd.length()));
                    }
                }
                else
                {
                    Writer writer = new StringWriter();
                    String encoding = urlConnection.getContentEncoding();
                    if (encoding == null)
                        encoding = "UTF-8";

                    InputStream is = (InputStream) urlConnection.getContent();
                    Reader reader = new BufferedReader(new InputStreamReader(is, encoding));
                    char[] buf = new char[1024];
                    int count;
                    while ((count = reader.read(buf)) != -1) {
                        writer.write(buf, 0, count);
                    }
                    responseData = writer.toString();
                }
                prepareResponse(urlConnection);
                callListenerIfPresent(didRequestFinishedListener);
            }
        }
        catch (Exception e)
        {
            exception = e;
            callListenerIfPresent(didRequestFailedListener);
        }
    }

    private void saveToOutputStream(HttpURLConnection urlConnection) throws IOException {
        totalContentReaded = 0;
        BufferedInputStream bufin = null;
        try
        {
            bufin = new BufferedInputStream(urlConnection.getInputStream());
            urlConnection.getContentLength();
            int count;
            byte[] buf = new byte[1024];
            while ((count = bufin.read(buf)) != -1)
            {
                destinationOutputStream.write(buf, 0, count);
                totalContentReaded += count;
            }
            destinationOutputStream.flush();
        }
        finally
        {
            if (destinationOutputStream != null)
                destinationOutputStream.close();
            if (bufin != null)
                bufin.close();
        }
    }

    private void saveToDestination(File fd, HttpURLConnection urlConnection, boolean append) throws Exception
    {
        totalContentReaded = 0;
        BufferedOutputStream bufout = null; BufferedInputStream bufin = null;
        try
        {
            bufout = new BufferedOutputStream(new FileOutputStream(fd, append));
            bufin = new BufferedInputStream(urlConnection.getInputStream());
            urlConnection.getContentLength();
            int count;
            byte[] buf = new byte[1024];
            while ((count = bufin.read(buf)) != -1)
            {
                bufout.write(buf, 0, count);
                totalContentReaded += count;
            }
            bufout.flush();
        }
        finally
        {
            if (bufout != null)
                bufout.close();
            if (bufin != null)
                bufin.close();
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
