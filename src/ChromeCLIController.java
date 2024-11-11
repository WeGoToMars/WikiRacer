import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** 
 * This class interacts with the browser to guide the user through the found path.
 *<p>
 * It recuires the Chrome CLI to be installed: https://github.com/prasmussen/chrome-cli. 
 * <b>Only the Chrome browser on MacOS is supported.</b>
 *<p>
 * This can also be used to cheat at the WikiRacer game such as https://wiki-race.com/ to prank your friends. Use responsibly! ;) 
 *
*/
public class ChromeCLIController {

    /**
     * Plays a rainbow animation in the browser and an "anime wow" sound effect
     * @throws Exception
     */
    private static void rainbow() throws Exception{
        String rainbow = """
            `
            h1, p, a, div {
              background-image: linear-gradient(to left, violet, indigo, blue, green, yellow, orange, red);   
              -webkit-background-clip: text;
              color: transparent !important;
              animation: rainbow 5s infinite;
              background-size: 1800% 1800%;
            }
            @keyframes rainbow { 
                0%{background-position:0% 82%}
                50%{background-position:100% 19%}
                100%{background-position:0% 82%}
            }`;
            """;
        String wow = "new Audio('https://www.myinstants.com/media/sounds/anime-wow-sound-effect.mp3').play()";
        new ProcessBuilder("chrome-cli", "execute", "document.head.appendChild(document.createElement(\"style\")).innerHTML = "+rainbow + wow).start();
    }

    /**
     * Execute a shell command and return its output.
     * 
     * This method splits the given command string into individual arguments and
     * executes it using the {@link ProcessBuilder} class. It then reads the output
     * of the process and returns it as a string.
     * 
     * The stderr stream is redirected to the stdout stream.
     * @param command the command to execute
     * @return the output of the command
     * @throws Exception if an error occurs while executing the command
     */
    public static String execute(String command) throws Exception {
        // Execute a shell command and return its output
        ProcessBuilder builder = new ProcessBuilder(command.split(" "));
        builder.redirectErrorStream(true);

        Process process = builder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        return output.toString();
    }

    /**
     * Retrieves the title of the active Wikipedia page.
     * 
     * @return the title of the active Wikipedia page as a String
     */
    public static String getWikiTitle() throws Exception{
        // Get title of the active Wikipedia page
        String html = execute("chrome-cli source");
        Document doc = Jsoup.parse(html);

        return doc.select("h1").text();
    }

    /**
     * On Wikipedia, one page can have numerous redirects that all link to the same page (see https://en.wikipedia.org/wiki/Wikipedia:Redirect).
     * When searching for a link to the next Wikipedia page, it might be a redirect, rather than the actual page.
     * 
     * This function returns a list of URLs of redirects to the given page.
     * 
     * @param title the title of the Wikipedia page
     * @return a list of URLs of redirects to the given page as Strings
     * @throws Exception if an error occurs while executing the query
     */
    private static ArrayList<String> getRedirects(String title) throws Exception {
        String req = "action=query&format=json&generator=redirects&grdlimit=max&grdnamespace=0&prop=info&inprop=url&titles=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
        String lastContinue = "";

        HttpClient client = HttpClient.newHttpClient();
        Gson gson = new Gson();
        ArrayList<String> result = new ArrayList<String>();
        result.add("'https://en.wikipedia.org/wiki/"+title.replace(" ", "_")+"'");

        while (true) {
            String q = req;
            q = q + "&" + lastContinue;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://en.wikipedia.org/w/api.php?" + q))
                .build();
            String response = 
                client.send(request, BodyHandlers.ofString()).body();

            JsonObject json = gson.fromJson(response, JsonObject.class);
            Set<String> keys = json.keySet();

            if (keys.contains("query")) {
                Map<String, JsonElement> m = json.getAsJsonObject("query").getAsJsonObject("pages").asMap();

                for (var entry : m.entrySet()) {
                    try {
                        result.add("'"+entry.getValue().getAsJsonObject().get("fullurl").getAsString()+"'");
                    } catch (Exception e) {
                        continue;
                    }
                }
            }
            if (!keys.contains("continue")) {
                break;
            }
            lastContinue = json.getAsJsonObject("continue").asMap()
                .entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"))
                .replace("\"", "")
                .replace("|", "%7C");
        }
        return result;
    }

    /**
     * Show the link to the next Wikipedia page in the browser.
     * 
     * Finds all links to the given page (including redirects) and scrolls to the
     * first one, highlighting it in yellow.
     * 
     * @param l the title of the Wikipedia page to show
     * @throws Exception if an error occurs while executing the query
     */
    public static void showLink(String l) throws Exception{
        ArrayList<String> redirects = getRedirects(l);

        String findLink = "l = document.links;for (let i=0; i<l.length; i++) {if("+redirects.toString()+".includes(l[i].href.split('#')[0])) {{ l[i].scrollIntoView({ behavior: 'smooth', block: 'center' }); l[i].style.backgroundColor = 'yellow';}}}";

        new ProcessBuilder("chrome-cli", "execute", findLink).start();
    }

    /**
     * Guide the user through the browser to the target Wikipedia page (interactive mode)
     * 
     * Shows each link in the path in yellow and waits for the user to click
     * on it before highlighting the next one.
     * 
     * @param p the path to guide the user through
     * @throws Exception if an error occurs while executing the query
     */
    public static void guide(Path p) throws Exception {
        for (int i = 0; i < p.path.size(); i++) {
            Link l = p.path.get(i);

            String currentPage = URLEncoder.encode(getWikiTitle(), StandardCharsets.UTF_8);
            String targetTitle = URLEncoder.encode(l.title, StandardCharsets.UTF_8);

            System.out.println((i+1) + "/" + p.path.size() + " -> " + l.title);
            ChromeCLIController.showLink(l.title);
            while (!currentPage.equals(targetTitle)) {
                Thread.sleep(200);
                currentPage = URLEncoder.encode(getWikiTitle(), StandardCharsets.UTF_8);
                //System.out.println(currentPage+" "+targetTitle);
            }
        }
        System.out.println("Done! 🌈😎");
        rainbow();
    }
    
    public static void main(String[] args) throws Exception{
        rainbow(); // wow!
    }
}
