import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

/**
 * Created by Kevin on 9/7/2016.
 */
public class Philosophy {

    private static HashMap<String, Integer> topicNameToVisitedTopic = new HashMap<>();

    public static void main(String[] args) {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        Philosophy philosophy = new Philosophy();
        while(true) {
            System.out.println("Please enter a word to search for. Enter 1 to run while printing out steps. Enter 2 for stats. Enter 3 to quit.");
            try {
                String input = br.readLine();
                switch(input) {
                    case "1":
                        String inputWord = br.readLine();
                        philosophy.search(true, inputWord);
                        break;
                    case "2":
                        philosophy.printStats();
                        break;
                    case "3":
                        System.exit(0);
                    default:
                        philosophy.search(false, input);
                }
            } catch (IOException e) {
                System.err.println("There was a problem reading stdin. Please try again");
            }
            System.out.println();
        }
    }

    private void search(boolean debug, String startingWord) {
        int clicks;
        String topic = startingWord;
        LinkedHashSet<String> linkedHashSet = new LinkedHashSet<>();
        while (!topic.toLowerCase().equals("Philosophy".toLowerCase())) {
            if (debug) {
                System.out.println(topic);
            } else {
                // Cache check
                if (topicNameToVisitedTopic.get(topic) != null) {
                    addSearchToMap(linkedHashSet, topicNameToVisitedTopic.get(topic));
                    clicks = topicNameToVisitedTopic.get(topic) + linkedHashSet.size();
                    System.out.println(clicks + " clicks to get to Philosophy");
                    return;
                }
            }
            if (linkedHashSet.contains(topic.toLowerCase())) {
                System.out.println("We found a loop! Stopping.");
                return;
            } else {
                linkedHashSet.add(topic.toLowerCase());
            }
            try {
                String wikiText = fetchWikiTextFromTopic(topic);
                topic = parseFirstLink(wikiText);
            } catch (IOException e) {
                System.err.println("There was a problem parsing the following topic from wikipedia: " + topic);
                return;
            } catch (TopicNotFoundException | NoLinkFoundException e) {
                System.err.println(e.getMessage());
                return;
            }
        }

        System.out.println("Philosophy");
        linkedHashSet.add("Philosophy");
        addSearchToMap(linkedHashSet, 0);
        clicks = topicNameToVisitedTopic.get(startingWord);
        System.out.println(clicks + " clicks to get to Philosophy");
    }

    /**
     * This method takes a linked-list of topics on the way to philosophy, and adds them to a map of items
     * that contain how many steps it is from that item to philosophy.
     * If we already found Philosophy from the cache, then the offset is how many steps it is to Philosophy from the
     * most recent item in the LinkedSet.
     */
    private void addSearchToMap(LinkedHashSet<String> linkedHashSet, int offset) {
        int stepsToPhilosophy = linkedHashSet.size() - 1 + offset;
        for(String topic : linkedHashSet) {
            topicNameToVisitedTopic.put(topic, stepsToPhilosophy);
            stepsToPhilosophy--;
        }
    }

    /**
     * Given a topic, connect to the wikipedia page for that topic, and return the wikitext
     */
    public String fetchWikiTextFromTopic(String topic) throws TopicNotFoundException, IOException {
        String link = "https://en.wikipedia.org/w/api.php?action=parse&page=" +
                URLEncoder.encode(topic, "UTF-8") +
                "&prop=wikitext&format=json";

        URL url = new URL(link);
        URLConnection conn = url.openConnection();
        StringBuffer buffer = new StringBuffer();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));

        String inputLine;
        while ((inputLine = br.readLine()) != null) {
            buffer.append(inputLine);
        }
        br.close();

        String result = buffer.toString();

        JSONObject obj = new JSONObject(result);

        if (obj.has("error")) {
            throw new TopicNotFoundException(topic);
        }
        String text = obj.getJSONObject("parse").getJSONObject("wikitext").get("*").toString();

        return text;
    }

    /**
     * This method parses the first link on the page. It skips anything that's in {{}}
     * (which I don't consider in the "main text"; things in {{}} are usually those boxes you find on the side of the page),
     * or anything in italics(denoted with '' '' ). It'll also skip anything in a <ref> tag.
     * These requirements are taken from https://en.wikipedia.org/wiki/Wikipedia:Getting_to_Philosophy#Method_summarized
     *
     * @param wikiText The wiki-formatted text for the page
     * @return the "name" of the first link.
     */
    private String parseFirstLink(String wikiText) throws NoLinkFoundException {
        wikiText = wikiText.replaceAll("<!--(.*?)-->", ""); // Delete all html comments

        String linkName;
        for (int i = 0; i < wikiText.length(); i++) {
            linkName = "";
            if (i + 1 < wikiText.length() && // length check
                    wikiText.charAt(i) == '[' && wikiText.charAt(i + 1) == '[' ) {

                // This is a hack. We add one so that the split will actually have a value in it's second side.
                // Also its a terrible idea to try and parse XML with regex, but I don't believe wikipedia has nested
                // double references so it's okay.
                long numberOfUnclosedReferences = wikiText.substring(0, i + 1).split("<ref[^/]*?>").length - 1;
                numberOfUnclosedReferences = numberOfUnclosedReferences - (wikiText.substring(0, i + 1).split("</ref>").length - 1);

                long setsOfQuotes = StringUtils.countMatches(wikiText.substring(0, i), "''");
                long parenthesisOpen = StringUtils.countMatches(wikiText.substring(0, i), "(");
                long parenthesisClosed = StringUtils.countMatches(wikiText.substring(0, i), ")");
                long bracesOpen = StringUtils.countMatches(wikiText.substring(0, i), "{{");
                long bracesClosed = StringUtils.countMatches(wikiText.substring(0, i),"}}");
                long bracketsUnclosed = StringUtils.countMatches(wikiText.substring(0, i),"[[") - StringUtils.countMatches(wikiText.substring(0, i),"]]");
                long bracketLineOpened = StringUtils.countMatches(wikiText.substring(0, i),"{|");
                long bracketLineClosed = StringUtils.countMatches(wikiText.substring(0, i),"|}");

                i = i + 2;
                //Grab the full link text inside of the double "]]"
                while (!(wikiText.charAt(i) == ']' && wikiText.charAt(i + 1) == ']')) {
                    linkName = linkName + wikiText.charAt(i);
                    i++;
                }

                // We can't be having the link-name be file or image
                if (linkName.startsWith("File") || linkName.startsWith("Image")) {
                    continue;
                }
                // Sometimes links have two parts to them, but both parts link to the same page;
                // so just take the first one
                linkName = linkName.split("\\|")[0];

                // Conditions that must be met in order for us to be certain this is the first "link"
                if (setsOfQuotes % 2 == 0 &&
                        parenthesisOpen - parenthesisClosed <= 0 &&
                        bracesOpen - bracesClosed <= 0 &&
                        numberOfUnclosedReferences == 0 &&
                        bracketsUnclosed == 0 &&
                        bracketLineOpened - bracketLineClosed <= 0) {
                    return linkName;
                }
            }
        }
        throw new NoLinkFoundException();
    }

    private void printStats() {
        PriorityQueue<VisitedTopic> queue = new PriorityQueue<>(topicNameToVisitedTopic.size(), new StepsComparator());
        for (Map.Entry<String, Integer> entry : topicNameToVisitedTopic.entrySet()) {
            queue.add(new VisitedTopic(entry.getKey(), entry.getValue()));
        }
        while (!queue.isEmpty()) {
            VisitedTopic visitedTopic = queue.remove();
            System.out.println("Topic: \"" + visitedTopic.topic + "\"; steps to Philosophy: " + visitedTopic.stepsToPhilosophy);
        }
    }

    /**
     * An object containing a topic name, the number of times that topic has been hit, and the number of steps
     * from that topic to philosophy
     */
    public class VisitedTopic {
        final String topic;
        int stepsToPhilosophy;
        public VisitedTopic(String topic, int stepsToPhilosophy) {
            this.topic = topic;
            this.stepsToPhilosophy = stepsToPhilosophy;
        }
    }

    public class StepsComparator implements Comparator<VisitedTopic>
    {
        @Override
        public int compare(VisitedTopic x, VisitedTopic y)
        {
            // Assume neither string is null. Real code should
            // probably be more robust
            // You could also just return x.length() - y.length(),
            // which would be more efficient.
            if (x.stepsToPhilosophy < y.stepsToPhilosophy) {
                return -1;
            } else if (x.stepsToPhilosophy > y.stepsToPhilosophy) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    public class TopicNotFoundException extends Exception {

        TopicNotFoundException(String topic) {
            super("The topic \"" + topic + "\" was not found!");
        }
    }

    public class NoLinkFoundException extends Exception {

        public NoLinkFoundException() {
            super("There were no links found on this page!");
        }
    }
}
