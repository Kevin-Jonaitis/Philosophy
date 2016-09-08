import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

/**
 * Created by Kevin on 9/7/2016.
 */
public class Philosophy {

    public static void main(String[] args) throws IOException {
        // We should fix "New York Times"
        new Philosophy("Lyne_Viaduct"); //theoretical physicist produces a loop
    }

    public Philosophy(String startingWord) throws IOException {
        String topic = startingWord;
        System.out.println(topic);
        while (!topic.toLowerCase().equals("Philosophy".toLowerCase())) {
            topic = parseFirstLink(fetchWikiTextFromTopic(topic));
            System.out.println(topic);
        }
    }

    /**
     * Given a topic, connect to the wikipedia page for that topic, and return the wikitext
     */
    public String fetchWikiTextFromTopic(String topic) throws IOException {
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
        String text = obj.getJSONObject("parse").getJSONObject("wikitext").get("*").toString();

        return text;


    }

    /**
     * This method parses the first link on the page. It skips anything that's in {{}}
     * (which I don't consider the "main text" things in {{}} are usually those boxes you find on the side of the page),
     * or anything in italics(denoted with '' '' ). It'll also skip anything in a <ref> tag.
     * These requirements are taken from https://en.wikipedia.org/wiki/Wikipedia:Getting_to_Philosophy#Method_summarized
     *
     * @param wikiText The wiki-formatted text for the page
     * @return the "name" of the first link.
     */
    private String parseFirstLink(String wikiText) {
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

                // We can't be having the link-name be file.
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
        return null;
    }
}
