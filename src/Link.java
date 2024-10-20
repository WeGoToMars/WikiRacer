package src;
import java.io.Serializable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;

public class Link implements Serializable {
    public Integer score = Integer.MAX_VALUE;
    public Integer pageid;
    public String title;
    public Integer size;

    public ArrayList<Link> links = null;
    public String mode = null;

    /**
     * Make a link object out of a JSON object returned by the Wikipedia API.
     * Link objects are used to represent Wikipediaarticles that we can navigate to.
     * <p>
     * This class also defines calculating the score of each page, please see the detailed comment lower in the code.
     * 
     * @param json the JSON object returned by the Wikipedia API
     * */
    public Link(JsonElement json) {
        JsonObject entry = json.getAsJsonObject();
        pageid = entry.get("pageid").getAsInt();
        title = entry.get("title").getAsString();
        size = entry.get("revisions").getAsJsonArray().get(0).getAsJsonObject().get("size").getAsInt();
        
        /**
         This is a the most important line to the algorithm.
         The score is a value that represents how desirable a particular page is for us to navigate to it.
         The LOWER the score, the more desirable the page is.

         I decided to base it on two easily identifiable factors that came from my own expirience of playing wikirace:
          1. Pageid, while it's not well defined, articles that were created earlier in Wikipedia's existance have a lower pageid.
             These articles _generally_ cover most prominent and well documented topics, which is beneficial for us.
          2. Size of the article in bytes. Longer articles have more links and more content, which is beneficial for us.

         The coeficients are currently set to 1 and -1, respectively, which provided a good mix in my tests.
        */
        score = pageid - size;
    }

    /**
     * Make a link object out of string representation of the article title. 
     * It is only used for initializing the initial article and the target article.
     */
    public Link(String t) {
        pageid = -t.hashCode();
        title = t;
    }

    @Override
    public boolean equals(Object o) {
        Link a = (Link) o;
        return this.pageid.equals(a.pageid);
    }

    /**
     * As Wikipeida pageids are unique, we can use them as hash codes to serialize link objects.
     * @return the hash code value for this Link object.
     */
    @Override
    public int hashCode() {
        return pageid;
    }

    public String toString() {
        return title+": pageid="+pageid.toString()+" size="+size.toString()+" score="+score.toString();
    }
}
