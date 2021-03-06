package com.deviceiot.platform.iot.integration;

import java.io.*;
import java.net.*;
import java.security.*;
import java.text.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.*;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import com.amazonaws.util.*;
import com.mashape.unirest.request.*;

import lombok.extern.slf4j.*;

@Slf4j
public class DeviceUnirestV4Signer {


    public GetRequest sign(GetRequest request, String awsAccessKeyId, String awsSecretAccessKey, String region, String serviceName) throws MalformedURLException {
        URL url = new URL(request.getUrl());
        TreeMap<String, String> hostHeader = new TreeMap<String, String>();
        hostHeader.put("host", url.getHost());

        this.accessKeyID = awsAccessKeyId;
        this.secretAccessKey = awsSecretAccessKey;
        this.regionName = region;
        this.serviceName = serviceName;
        this.httpMethodName = request.getHttpMethod().name();
        this.canonicalURI = url.getPath();
        this.urlQueryString = new URL(request.getUrl()).getQuery();
        this.queryParameters = null;
        this.awsHeaders = hostHeader;
        this.payload = (request.getBody() != null ? request.getBody().toString() : null);
        this.debug = true;

        xAmzDate = getTimeStamp();
        currentDate = getDate();

        Map<String, String> headers = getHeaders();
        request.header("Authorization", headers.get("Authorization"));
        request.header("host", url.getHost());
        request.header("x-amz-date", headers.get("x-amz-date"));
        return request;
    }

    public HttpRequestWithBody sign(HttpRequestWithBody request, String awsAccessKeyId, String awsSecretAccessKey, String region, String serviceName) throws IOException {
        URL url = new URL(request.getUrl());
        TreeMap<String, String> hostHeader = new TreeMap<String, String>();
        hostHeader.put("host", url.getHost());

        this.accessKeyID = awsAccessKeyId;
        this.secretAccessKey = awsSecretAccessKey;
        this.regionName = region;
        this.serviceName = serviceName;
        this.httpMethodName = request.getHttpMethod().name();
        this.canonicalURI = url.getPath();
        this.queryParameters = null;
        this.urlQueryString = new URL(request.getUrl()).getQuery();
        this.awsHeaders = hostHeader;
        this.payload = (request.getBody() != null ? IOUtils.toString(request.getBody().getEntity().getContent()) : null);
        this.debug = true;

        xAmzDate = getTimeStamp();
        currentDate = getDate();

        Map<String, String> headers = getHeaders();
        request.header("Authorization", headers.get("Authorization"));
        request.header("host", url.getHost());
        request.header("x-amz-date", headers.get("x-amz-date"));
        return request;
    }

    private String accessKeyID;
    private String secretAccessKey;
    private String regionName;
    private String serviceName;
    private String httpMethodName;
    private String canonicalURI;
    private TreeMap<String, String> queryParameters;
    private TreeMap<String, String> awsHeaders;
    private String payload;
    private boolean debug = false;
    private String urlQueryString;

    /* Other variables */
    private final String HMACAlgorithm = "AWS4-HMAC-SHA256";
    private final String aws4Request = "aws4_request";
    private String strSignedHeader;
    private String xAmzDate;
    private String currentDate;

    /**
     * Task 1: Create a Canonical Request for Signature Version 4.
     *
     * @return
     */
    private String prepareCanonicalRequest() {
        StringBuilder canonicalURL = new StringBuilder("");

        /* Step 1.1 Start with the HTTP request method (GET, PUT, POST, etc.), followed by a newline character. */
        canonicalURL.append(httpMethodName).append("\n");

        /* Step 1.2 Add the canonical URI parameter, followed by a newline character. */
        canonicalURI = canonicalURI == null || canonicalURI.trim().isEmpty() ? "/" : canonicalURI;
        canonicalURL.append(canonicalURI).append("\n");

        /* Step 1.3 Add the canonical query string, followed by a newline character. */
        StringBuilder queryString = new StringBuilder("");
        if (this.queryParameters != null && !queryParameters.isEmpty()) {
            for (Map.Entry<String, String> entrySet : queryParameters.entrySet()) {
                String key = entrySet.getKey();
                String value = entrySet.getValue();
                queryString.append(key).append("=").append(URLEncoder.encode(value)).append("&");
            }
            queryString.append("\n");
        } else if (this.urlQueryString != null && !this.urlQueryString.isEmpty()) {
            try {
                String u = "http://aws.com" + canonicalURI + "?" + this.urlQueryString;
                List<NameValuePair> urlPars = URLEncodedUtils.parse(new URI(u), "UTF8");
                Map<String, String> pars = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
                for (NameValuePair par : urlPars) {
                    pars.put(par.getName(), par.getValue());
                }
                for (Map.Entry<String, String> entrySet : pars.entrySet()) {
                    String key = entrySet.getKey();
                    String value = entrySet.getValue();
                    queryString.append(key).append("=").append(URLEncoder.encode(value)).append("&");
                }
                if(queryString.length() > 0){
                    queryString = queryString.deleteCharAt(queryString.length()-1);
                }
                queryString.append("\n");
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        } else {
            queryString.append("\n");
        }
        canonicalURL.append(queryString);

        /* Step 1.4 Add the canonical headers, followed by a newline character. */
        StringBuilder signedHeaders = new StringBuilder("");
        if (awsHeaders != null && !awsHeaders.isEmpty()) {
            for (Map.Entry<String, String> entrySet : awsHeaders.entrySet()) {
                String key = entrySet.getKey();
                String value = entrySet.getValue();
                signedHeaders.append(key).append(";");
                canonicalURL.append(key).append(":").append(value).append("\n");
            }

            /* Note: Each individual header is followed by a newline character, meaning the complete list ends with a newline character. */
            canonicalURL.append("\n");
        } else {
            canonicalURL.append("\n");
        }

        /* Step 1.5 Add the signed headers, followed by a newline character. */
        strSignedHeader = signedHeaders.substring(0, signedHeaders.length() - 1); // Remove last ";"
        canonicalURL.append(strSignedHeader).append("\n");

        /* Step 1.6 Use a hash (digest) function like SHA256 to create a hashed value from the payload in the body of the HTTP or HTTPS. */
        payload = payload == null ? "" : payload;
        canonicalURL.append(generateHex(payload));
        return canonicalURL.toString();
    }

    /**
     * Task 2: Create a String to Sign for Signature Version 4.
     *
     * @param canonicalURL
     * @return
     */
    private String prepareStringToSign(String canonicalURL) {
        String stringToSign = "";

        /* Step 2.1 Start with the algorithm designation, followed by a newline character. */
        stringToSign = HMACAlgorithm + "\n";

        /* Step 2.2 Append the request date value, followed by a newline character. */
        stringToSign += xAmzDate + "\n";

        /* Step 2.3 Append the credential scope value, followed by a newline character. */
        stringToSign += currentDate + "/" + regionName + "/" + serviceName + "/" + aws4Request + "\n";

        /* Step 2.4 Append the hash of the canonical request that you created in Task 1: Create a Canonical Request for Signature Version 4. */
        stringToSign += generateHex(canonicalURL);

        return stringToSign;
    }

    /**
     * Task 3: Calculate the AWS Signature Version 4.
     *
     * @param stringToSign
     * @return
     */
    private String calculateSignature(String stringToSign) {
        try {
            /* Step 3.1 Derive your signing key */
            byte[] signatureKey = getSignatureKey(secretAccessKey, currentDate, regionName, serviceName);

            /* Step 3.2 Calculate the signature. */
            byte[] signature = HmacSHA256(signatureKey, stringToSign);

            /* Step 3.2.1 Encode signature (byte[]) to Hex */
            String strHexSignature = bytesToHex(signature);
            return strHexSignature;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return null;
    }

    /**
     * Task 4: Add the Signing Information to the Request. We'll return Map of
     * all headers put this headers in your request.
     *
     * @return
     */
    public Map<String, String> getHeaders() {
        awsHeaders.put("x-amz-date", xAmzDate);

        /* Execute Task 1: Create a Canonical Request for Signature Version 4. */
        String canonicalURL = prepareCanonicalRequest();

        /* Execute Task 2: Create a String to Sign for Signature Version 4. */
        String stringToSign = prepareStringToSign(canonicalURL);

        /* Execute Task 3: Calculate the AWS Signature Version 4. */
        String signature = calculateSignature(stringToSign);

        if (signature != null) {
            Map<String, String> header = new HashMap<String, String>(0);
            header.put("x-amz-date", xAmzDate);
            header.put("Authorization", buildAuthorizationString(signature));
            return header;
        } else {
            return null;
        }
    }

    /**
     * Build string for Authorization header.
     *
     * @param strSignature
     * @return
     */
    private String buildAuthorizationString(String strSignature) {
        return HMACAlgorithm + " "
                + "Credential=" + accessKeyID + "/" + getDate() + "/" + regionName + "/" + serviceName + "/" + aws4Request + ","
                + "SignedHeaders=" + strSignedHeader + ","
                + "Signature=" + strSignature;
    }

    /**
     * Generate Hex code of String.
     *
     * @param data
     * @return
     */
    private String generateHex(String data) {
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(data.getBytes("UTF-8"));
            byte[] digest = messageDigest.digest();
            return String.format("%064x", new java.math.BigInteger(1, digest));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Apply HmacSHA256 on data using given key.
     *
     * @param data
     * @param key
     * @return
     * @throws Exception
     * @reference: http://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html#signature-v4-examples-java
     */
    private byte[] HmacSHA256(byte[] key, String data) throws Exception {
        String algorithm = "HmacSHA256";
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(key, algorithm));
        return mac.doFinal(data.getBytes("UTF8"));
    }

    /**
     * Generate AWS signature key.
     *
     * @param key
     * @param date
     * @param regionName
     * @param serviceName
     * @return
     * @throws Exception
     * @reference http://docs.aws.amazon.com/general/latest/gr/signature-v4-examples.html#signature-v4-examples-java
     */
    private byte[] getSignatureKey(String key, String date, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes("UTF8");
        byte[] kDate = HmacSHA256(kSecret, date);
        byte[] kRegion = HmacSHA256(kDate, regionName);
        byte[] kService = HmacSHA256(kRegion, serviceName);
        byte[] kSigning = HmacSHA256(kService, aws4Request);
        return kSigning;
    }

    final protected static char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Convert byte array to Hex
     *
     * @param bytes
     * @return
     */
    private String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars).toLowerCase();
    }

    /**
     * Get timestamp. yyyyMMdd'T'HHmmss'Z'
     *
     * @return
     */
    private String getTimeStamp() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));//server timezone
        return dateFormat.format(new Date());
    }

    /**
     * Get date. yyyyMMdd
     *
     * @return
     */
    private String getDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));//server timezone
        return dateFormat.format(new Date());
    }

}
