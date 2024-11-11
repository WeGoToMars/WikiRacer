import java.util.ArrayList;
import java.util.stream.Collectors;

public class Path {
    public ArrayList<Link> path;

    public Path() {
        path = new ArrayList<Link>();
    }

    public Path(ArrayList<Link> p) {
        path = p;
    }


    public Path append(Path p) {
        ArrayList<Link> a = new ArrayList<Link>(path);
        a.addAll(p.path);
    
        return new Path(a);
    }

    public Path append(Link l) {
        ArrayList<Link> a = new ArrayList<Link>(path);
        a.add(l);
    
        return new Path(a);
    }

    public Path prepend(Link l) {
        ArrayList<Link> a = new ArrayList<Link>(path);
        a.add(0, l);
    
        return new Path(a);
    }

    /**
     * Return a string representation of the path as a list of article titles separated by " -> ".
     * @return a string representation of the path
     */
    public String toString() {
        return path.stream().map(p -> String.valueOf(p.title))
                            .collect(Collectors.joining(" -> "));
    }
}
