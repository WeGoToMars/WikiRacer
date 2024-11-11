import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


/**
 * This class interacts with the Wikipedia API to find links to other articles.
 * The API is wonderfully documented here: https://www.mediawiki.org/wiki/API:Main_page
 */
public class WikiAPI {
    /**
     * This function makes a query to the Wikipedia API and returns the list of links from the given query.
     * 
     * @param req the query string to send to the API. Supported parameters are documented here: https://www.mediawiki.org/wiki/API:Query
     * @return a list of Links as returned by the Wikipedia API
     */
    public static Link getLinks(Link l, String mode) {
        String req = "";
        
        // Make Wikipedia API query and return the list of links
        if (mode.equals("forward")) {
            req = "action=query&format=json&generator=links&gplnamespace=0&gpllimit=max&prop=revisions&rvprop=size&redirects=1&titles=" + l.title.replace(" ", "_").replace("&", "%26");
        } else if (mode.equals("backward")) {
            req = "action=query&format=json&generator=linkshere&glhnamespace=0&glhlimit=max&prop=revisions&rvprop=size&redirects=1&titles=" + l.title.replace(" ", "_").replace("&", "%26");
        }
        String lastContinue = "";

        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();
        ArrayList<Link> result = new ArrayList<Link>();

        for (int i = 0; i < 10; i++) {
            // Some articles have A LOT of links, and every API call returns 500 links in ~200-350 ms
            // Speed is paramount, therefore it's reasonable to stop continuing the query after 10 calls
            String q = req;
            q = q + "&" + lastContinue;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://en.wikipedia.org/w/api.php?" + q))
                .build();
            String response;
            try {
                response = client.send(request, BodyHandlers.ofString()).body();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }

            JsonObject json = gson.fromJson(response, JsonObject.class);
            Set<String> keys = json.keySet();

            if (keys.contains("query")) {
                // Process the response
                Map<String, JsonElement> m = json.getAsJsonObject("query").getAsJsonObject("pages").asMap();

                for (var entry : m.entrySet()) {
                    try {
                        result.add(new Link(entry.getValue()));
                    } catch (Exception e) {
                        // links to missing articles will still show up in the results
                        // they need to be skipped
                        continue;
                    }
                }
            }
            if (keys.contains("warnings") || keys.contains("errors")) {
                System.out.println(json.getAsJsonObject("warnings").toString());
            }
            if (!keys.contains("continue")) {
                // query is complete, break out of the loop and return result
                break;
            }
            // transform continue parameter into a query string to be appended to the next request
            lastContinue = json.getAsJsonObject("continue").asMap()
                .entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"))
                .replace("\"", "")
                .replace("|", "%7C")
                .replace("'", "%27")
                .replace("\"", "%22")
                .replace("&", "%26");
        }

        // sort resulting links based on their score
        Collections.sort(result, (o1, o2) -> o1.score - o2.score);

        l.links = result;
        l.mode = mode;

        return l;
    }

    public static String randomArticle() throws Exception {
        String url = "https://en.wikipedia.org/wiki/Special:Random";
        String response = HttpClient.newHttpClient().send(HttpRequest.newBuilder().uri(URI.create(url)).build(), BodyHandlers.ofString())
                            .headers().firstValue("location").get();
        return URLDecoder.decode(response.replace("https://en.wikipedia.org/wiki/", ""), StandardCharsets.UTF_8.name());
    }
}
