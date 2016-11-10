package edu.uci.ics.crawler4j.url;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;


/**
 * See http://en.wikipedia.org/wiki/URL_normalization（具体文档） for a reference Note: some
 * parts of the code are adapted from: http://stackoverflow.com/a/4057470/405418
 * 
 * @author Yasser Ganjisaffar [lastname at gmail dot com]
 */
//有些url其实是等价的，网络爬虫为了判断某个网页是否反复爬取，需要对其进行canonicalizer(规范化)表示
public class URLCanonicalizer {

  public static String getCanonicalURL(String url) {
    return getCanonicalURL(url, null);
  }

  public static String getCanonicalURL(String href, String context) {

    try {
      //得到标准格式的字符串形式的绝对路径
      URL canonicalURL = new URL(UrlResolver.resolveUrl(context == null ? "" : context, href));

      String host = canonicalURL.getHost().toLowerCase();
      if (host == "") {
        // This is an invalid Url.
        return null;
      }

      String path = canonicalURL.getPath();

      /*
       * Normalize: no empty segments (i.e., "//"), no segments equal to
       * ".", and no segments equal to ".." that are preceded by a segment
       * not equal to "..".
       */
      path = new URI(path.replace("\\", "/")).normalize().toString();
      
      int idx = path.indexOf("//");
      while (idx >= 0) {
        path = path.replace("//", "/");
        idx = path.indexOf("//");
      }

      while (path.startsWith("/../")) {
        path = path.substring(3);
      }

      path = path.trim();
      
      // 规范化的参数兼职对
      final SortedMap<String, String> params = createParameterMap(canonicalURL.getQuery());
      final String queryString;
      if (params != null && params.size() > 0) {
        String canonicalParams = canonicalize(params);
        queryString = (canonicalParams.isEmpty() ? "" : "?" + canonicalParams);
      } else {
        queryString = "";
      }

      if (path.length() == 0) {
        path = "/";
      }

      //Drop default port: example.com:80 -> example.com
      int port = canonicalURL.getPort();
      if (port == canonicalURL.getDefaultPort()) {
        port = -1;
      }

      String protocol = canonicalURL.getProtocol().toLowerCase();
      String pathAndQueryString = normalizePath(path) + queryString;

      URL result = new URL(protocol, host, port, pathAndQueryString);
      return result.toExternalForm();

    } catch (MalformedURLException | URISyntaxException ex) {
      return null;
    }
  }

  /**
   * Takes a query string, separates the constituent name-value pairs, and
   * stores them in a SortedMap ordered by lexicographical order.
   *
   * @return Null if there is no query string.
   */
  private static SortedMap<String, String> createParameterMap(final String queryString) {
    if (queryString == null || queryString.isEmpty()) {
      return null;
    }
    // 按&切分出所有的参数键值对
    final String[] pairs = queryString.split("&");
    final Map<String, String> params = new HashMap<>(pairs.length);

    for (final String pair : pairs) {
      if (pair.length() == 0) {
        continue;
      }
      
      // 得到所有的key和value
      String[] tokens = pair.split("=", 2);
      switch (tokens.length) {
      case 1:
        if (pair.charAt(0) == '=') {
          params.put("", tokens[0]);
        } else {
          params.put(tokens[0], "");
        }
        break;
      case 2:
        params.put(tokens[0], tokens[1]);
        break;
      }
    }
    return new TreeMap<>(params);
  }

  /**
   * Canonicalize the query string.
   *
   * @param sortedParamMap
   *            Parameter name-value pairs in lexicographical order.
   * @return Canonical form of query string.
   */
  private static String canonicalize(final SortedMap<String, String> sortedParamMap) {
    if (sortedParamMap == null || sortedParamMap.isEmpty()) {
      return "";
    }

    final StringBuffer sb = new StringBuffer(100);
    for (Map.Entry<String, String> pair : sortedParamMap.entrySet()) {
      // 对session不进行规范化	
      final String key = pair.getKey().toLowerCase();
      if (key.equals("jsessionid") || key.equals("phpsessid") || key.equals("aspsessionid")) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('&');
      }
      sb.append(percentEncodeRfc3986(pair.getKey()));
      if (!pair.getValue().isEmpty()) {
        sb.append('=');
        sb.append(percentEncodeRfc3986(pair.getValue()));
      }
    }
    return sb.toString();
  }

  /**
   * Percent-encode values according the RFC 3986. The built-in Java
   * URLEncoder does not encode according to the RFC, so we make the extra
   * replacements.
   *
   * @param string
   *            Decoded string.
   * @return Encoded string per RFC 3986.
   */
  private static String percentEncodeRfc3986(String string) {
    try {
      string = string.replace("+", "%2B");
      string = URLDecoder.decode(string, "UTF-8");
      string = URLEncoder.encode(string, "UTF-8");
      return string.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    } catch (Exception e) {
      return string;
    }
  }

  private static String normalizePath(final String path) {
    return path.replace("%7E", "~").replace(" ", "%20");
  }
}