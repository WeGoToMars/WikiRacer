import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class wikiracer {
    
    private static boolean USECHROMECLI = false;
    private static boolean QUIET = false;
    private static boolean BENCHMARK = false;

    private static String encode(String s) {return URLEncoder.encode(s, StandardCharsets.UTF_8);}

    // Forward and backward contain list of articles discovered in the search and a path to get to them
    // The goal of the algorithm is to find an overlap between the forward and backward
    public static LinkedHashMap<Link, Path> forward = new LinkedHashMap<Link, Path>();
    public static LinkedHashMap<Link, Path> backward = new LinkedHashMap<Link, Path>();

    // A list of articles that have already been scanned through the API
    public static ArrayList<String> scannedArticles = new ArrayList<String>();

    // a and b are lists of articles
    // The goal of the algorithm is to find an overlap between the forward and backward
    public static ArrayList<Link> a = new ArrayList<Link>();
    public static ArrayList<Link> b = new ArrayList<Link>();

    /**
     * Check if there is a path between the two given lists of articles. The algorithm is as follows:
     * <p>
     * The goal is to discover articles that we know how to get to and from until we find an overlap.
     * <p>
     * To do this, we calculate a score of each article trying to guess how useful it would be to navigate to it and scan it. See the Link class for more details on the score.
     * To make the process faster and more reliable, we asynchronously scan top-5 articles from each side simultaneously.
     * 
     * @param x the list of articles that we know how to get to
     * @param y the list of articles that we know how to get from
     * @return a path between the two lists of articles if one is found
     * @throws Exception if an error occurs while making the API query
     */
    private static Path findPath(ArrayList<Link> x, ArrayList<Link> y) throws Exception {
        Executor executor = Executors.newFixedThreadPool(10);
        CompletionService<Link> completionService = new ExecutorCompletionService<Link>(executor);
        int len = 0; 

        int MAX_EXPLORE = 4; // maximum number of articles to explore in each direction

        // if the list is small (the article is very sparecly connected), explore all the articles we can
        if (x.size() < 20) {MAX_EXPLORE = x.size();}
        for (int i = 0; i < Math.min(MAX_EXPLORE, x.size()); i++) {
            Link front = x.get(i);

            if (!scannedArticles.contains(front.title.replace(" ", "_"))) {
                completionService.submit(() -> 
                    WikiAPI.getLinks(front, "forward")
                );
                scannedArticles.add(front.title.replace(" ", "_"));
                len++;
            }
        }

        // if the list is small (the article is very sparecly connected), explore all the articles we can
        if (y.size() < 20) {MAX_EXPLORE = y.size();}
        for (int i = 0; i < Math.min(MAX_EXPLORE, y.size()); i++) {
            Link back = y.get(i);

            if (!scannedArticles.contains(back.title.replace(" ", "_"))) {
                completionService.submit(() -> 
                    WikiAPI.getLinks(back, "backward")
                );
                scannedArticles.add(back.title.replace(" ", "_"));
                len++;
            }
        }

        for (int i = 0; i < len; i++) {
            Future<Link> future = completionService.take();
            Link link = future.get();

            if (link.mode.equals("forward")) {
                a = link.links;

                if (a.size() == 0) {
                    System.out.println("[ERROR] No links found from " + link.title + "! Check the spelling of the page title if you entered it manually. ");
                }

                a.removeAll(forward.keySet()); // remove all articles that we already know how to get to, as existing path is quicker
                
                Path p = new Path(forward.get(link).append(link).path);
                for (var entry : a) {
                    forward.put(entry, p);
                }

                if (!QUIET) {System.out.print("⏩ Checked " + link.title + " (" + a.size() + " links)... ");}
            } else if (link.mode.equals("backward")) {
                b = link.links;

                if (b.size() == 0) {
                    System.out.println("[ERROR] No pages link to " + link.title + "! Check the spelling of the page title if you entered it manually. ");
                }

                b.removeAll(backward.keySet()); // remove all articles that we already know how to get from, as existing path is quicker
                
                Path p = new Path(backward.get(link).prepend(link).path);
                for (var entry : b) {
                    backward.put(entry, p);        
                }
                if (!QUIET) {System.out.print("⏪ Checked " + link.title + " (" + b.size() + " links)... ");}
            }
            LinkedHashMap<Link, Path> q = new LinkedHashMap<Link, Path>(forward); 

            // find the overlap between forward and backward
            q.keySet().retainAll(backward.keySet());

            if (!q.isEmpty()) { // if there is an overlap, display the found paths
                System.out.println("\n🚨 " + q.size() + " path(s) found!");

                int shortestPath = Integer.MAX_VALUE;
                ArrayList<Path> shortestPaths = new ArrayList<Path>();
                for (var entry : q.keySet().toArray()) {
                    Link u = (Link) entry;
                    Path w = forward.get(u).append(u).append(backward.get(u));
                    System.out.println(w.toString() + " (length " + w.path.size() + ")");
                    if (w.path.size() <= shortestPath) {
                        shortestPaths.add(w);
                        shortestPath = w.path.size();
                    }
                }
                Link u = (Link) q.keySet().toArray()[0];

                Path w = forward.get(u).append(u).append(backward.get(u));

                Random r = new Random();
                System.out.println("\nRandom shortest path: " + shortestPaths.get(r.nextInt(shortestPaths.size())).toString() + " (length " + shortestPath + ")\n");
                return w;
            } else {
                if (!QUIET) {System.out.println(String.format("(%d/%d)", forward.size(), backward.size()));}
            }
        }

        return null;
    }

    /**
     * Starts the WikiRacer.
     * 
     * If the program is started with the argument "-i", it will use the Chrome CLI (https://github.com/prasmussen/chrome-cli) to guide the user through the path.
     * Otherwise, the user will be prompted to enter the titles of the start and target pages.
     * 
     * @param args the command line arguments
     * @throws Exception if an error occurs while making the API query
     */
	@SuppressWarnings("resource")
    public static void main(String[] args) throws Exception {

        String START = null, FINISH = null;

        // parse command line arguments
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-i")) {
                    USECHROMECLI = true;
                } else if (args[i].equals("--start")) {
                    START = args[i + 1];
                } else if (args[i].equals("--finish")) {
                    FINISH = args[i + 1];
                } else if (args[i].equals("-q")) {
                    QUIET = true;
                } else if (args[i].equals("-b")) {
                    BENCHMARK = true;
                }
            }
        }

        Path p = new Path();

        if (START == null) {
            if (USECHROMECLI) {
                START = ChromeCLIController.getWikiTitle();
                System.out.print("Started WikiRacer in interactive mode.\n");
                System.out.print("Read the title from the open Wikipedia page: "+START+"\n");
            } else {
                System.out.print("Enter the title of the initial page: "); START = new Scanner(System.in).nextLine();
            }
        }

        if (FINISH == null) {
            System.out.print("Enter the title of the target page: "); FINISH = new Scanner(System.in).nextLine();
        }

        if (START.equals("random")) {
            START = WikiAPI.randomArticle();
        }
        if (FINISH.equals("random")) {
            FINISH = WikiAPI.randomArticle();
        }

        long stime = System.currentTimeMillis();

        forward.put(new Link(START), new Path());
        backward.put(new Link(FINISH), new Path());

        int MAX_LENGTH = 10;
        boolean ARTICLE_ISOLATED = false;
        for (int i = 0; i < MAX_LENGTH; i++) {
            long itertime = System.currentTimeMillis();
            a = new ArrayList<>(forward.keySet());
            b = new ArrayList<>(backward.keySet());
            
            try {
                // sort articles, so the articles with the smallest score are explored first
                Collections.sort(a, Comparator.nullsLast(Comparator.comparingInt(a -> a.score)));
                Collections.sort(b, Comparator.nullsLast(Comparator.comparingInt(b -> b.score)));
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);
            }

            p = findPath(a, b);
            if (p != null) {
                break;
            }
            if (!QUIET) {System.out.println(String.format("Iteration %d finished in %.2f seconds", i, (System.currentTimeMillis()-itertime)/1000.0));}

            if (forward.size() == 0) {
                System.out.println("Path can't be found, starting article is isolated.");
                ARTICLE_ISOLATED = true;
                break;
            }
            if (backward.size() == 0) {
                System.out.println("Path can't be found, target article is isolated.");
                ARTICLE_ISOLATED = true;
                break;
            }
        }

        double exec_time = (System.currentTimeMillis()-stime)/1000.0;

        System.out.println(String.format("⏱️ Execution time: %.2f seconds", exec_time));
        if (!QUIET) {System.out.println(String.format("Check the result: https://www.sixdegreesofwikipedia.com/?source=%s&target=%s", encode(START), encode(FINISH)));}

        if (p == null) {
            if (!ARTICLE_ISOLATED) {
                System.out.println("Can't find a valid path after MAX_LENGTH="+MAX_LENGTH+" iterations.");
            }
            if (BENCHMARK) {
                System.out.println("|"+START+"|"+FINISH+"|"+exec_time+"|"+"0"+"|");
            }
            System.exit(0);
        }
        
        if (BENCHMARK) {
            System.out.println("|"+START+"|"+FINISH+"|"+exec_time+"|"+p.path.size()+"|");
        }

        p.path.remove(0);
        if (USECHROMECLI) {ChromeCLIController.guide(p);} else {System.exit(0);}
    }
}