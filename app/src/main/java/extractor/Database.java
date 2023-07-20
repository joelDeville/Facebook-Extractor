package extractor;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

import java.util.HashMap;
import java.util.Set;

import java.util.logging.Logger;

import java.net.URL;

import java.time.ZoneId;
import java.time.LocalDate;

import com.restfb.types.StoryAttachment;
import com.restfb.types.Post;
import com.restfb.types.instagram.IgMedia;

import java.io.InputStream;
import java.io.IOException;

class Database {
    private String user;
    private String pass;
    private String dbURL;
    private String schema;
    private String postTable;
    private String mediaTable;
    private String instaTable;
    private Logger logger;

    Database() {
        user = "<database username>";
        pass = "<database password>";
        dbURL = "<database url>";
        schema = "<schema>";
    }

    void createSchema() {
        String schemaCreate = "CREATE SCHEMA IF NOT EXISTS %s;";
        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(schemaCreate, schema));
            stmt.executeUpdate();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    void createPostTables(Set<String> names) {
        String postCreate = "CREATE TABLE IF NOT EXISTS %s.%s (post_id VARCHAR(100) NOT NULL PRIMARY KEY," + 
            "content TEXT NOT NULL, post_date DATE NOT NULL);";
        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            
            for (String name : names) {
                //modify name to be SQL compliant and shorter
                String table = modifyPageName(name);
                table += "_post";

                //make the table
                PreparedStatement stmt = con.prepareStatement(String.format(postCreate, schema, table));
                stmt.executeUpdate();
                stmt.close();
            }
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    void createMediaTables(Set<String> names) {
        String mediaCreate = "CREATE TABLE IF NOT EXISTS %s.%s (media_id VARCHAR(100) PRIMARY KEY, " +
        "post_id VARCHAR(100) NOT NULL, media LONGBLOB NOT NULL);";

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);

            for (String name : names) {
                //shorten name and make it SQL compliant
                String table = modifyPageName(name);
                table += "_media";

                //create media table
                PreparedStatement stmt = con.prepareStatement(String.format(mediaCreate, schema, table));
                stmt.executeUpdate();
                stmt.close();
            }
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n" + "\n");
        }
    }

    void createInstaTable() {
        String createTable = "CREATE TABLE IF NOT EXISTS %s.%s (timeline_id VARCHAR(100) PRIMARY KEY, caption TEXT NOT NULL, " +
            "media LONGBLOB NOT NULL, post_date DATE NOT NULL);";

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(createTable, schema, instaTable));
            stmt.executeUpdate();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    private String modifyPageName(String input) {
        String tableName = "";
        for (String word : input.split(" +")) {
            tableName += word.charAt(0);
        }

        //replace all non letter/underscores/digits with random lowercase letter
        int random = (int) (Math.random() * 26) + 'a';
        tableName.replaceAll("\\W", String.valueOf((char) random));

        //add random character if still empty or first character is digit
        if (tableName.length() == 0 || Character.isDigit(tableName.charAt(0))) {
            tableName += (char) random;
        }

        return tableName.toLowerCase();
    }

    void setTableNames(String page, boolean setInstagramTable) {
        String table = modifyPageName(page);
        postTable = table + "_post";
        mediaTable = table + "_media";

        if (setInstagramTable) {
            instaTable = table + "_insta";
        }
    }

    //potential way to speed up algorithm, use hashmap of all stored content and counts to avoid going to database
    HashMap<String, Integer> getIDMediaCounts() {
        String selectQuery = "SELECT %s.post_id, COUNT(media_id) FROM %s.%s LEFT JOIN %s ON %s.post_id = %s.post_id GROUP BY %s.post_id;";
        HashMap<String, Integer> counts = new HashMap<>();

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            String query = String.format(selectQuery, postTable, schema, postTable, mediaTable, postTable, mediaTable, postTable);
            PreparedStatement stmt = con.prepareStatement(query);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                counts.put(rs.getString(1), rs.getInt(2));
            }
            rs.close();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }

        return counts;
    }

    void insertPost(Post p, Post parent) {
        String insertPost = "INSERT INTO %s.%s (post_id, content, post_date) VALUES(?, ?, ?);";

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(insertPost, schema, postTable));
            
            //try to get text content from multiple sources
            String text = p.getMessage();
            if (text == null) {
                //first try out parent, then continue checking this post
                if (parent != null) {
                    text = parent.getMessage();
                    text = (text == null) ? p.getDescription() : text;
                }

                //if still empty, so try description (might be redundant with next block)
                text = (text == null) ? p.getDescription() : text;

                if (text == null && p.getAttachments() != null) {
                    //try to extract description of attachment as final resort
                    text = p.getAttachments().getData().get(0).getDescription();
                }

                text = (text == null) ? "No text extracted" : text;
            }

            stmt.setString(1, p.getId());
            stmt.setString(2, text);
            
            ZoneId defaultZone = ZoneId.systemDefault();
            LocalDate postDate = p.getCreatedTime().toInstant().atZone(defaultZone).toLocalDate();
            stmt.setDate(3, Date.valueOf(postDate));

            //only execute update if post is not present
            if (!isPostPresent(p.getId())) {
                stmt.executeUpdate();
            }

            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    private boolean isPostPresent(String postID) {
        String searchQuery = "SELECT post_id FROM %s.%s WHERE %s.post_id = ?;";
        boolean found = false;

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(searchQuery, schema, postTable, postTable));
            stmt.setString(1, postID);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                found = true;
            }
            rs.close();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }

        return found;
    }

    void iterateMedia(String postID, Post.Attachments attach) {
        for (StoryAttachment attcs : attach.getData()) {
            //try to get sub attachments on this one
            if (attcs.getSubAttachments() != null) {
                for (StoryAttachment at : attcs.getSubAttachments().getData()) {
                    if (at.getMedia() != null) {
                        StoryAttachment.Media m = at.getMedia();
                        String ID = at.getTarget().getId();

                        if (m.getImage() != null && !isMediaPresent(ID)) {
                            insertMedia(postID, ID, m.getImage());
                        }
                    }
                }
            }

            //no sub attachments available
            if (attcs.getMedia() != null) {
                StoryAttachment.Media m = attcs.getMedia();
                String ID = attcs.getTarget().getId();

                //if ID is null (usually from GIFs/links), try extracting number out of Image source URL
                if (ID == null) {
                    StringBuilder num = new StringBuilder();
                    //find index to first number (?url)
                    String url = m.getImage().getSrc();
                    int ind = url.indexOf("url") - 2;

                    while (ind >= 0 && Character.isDigit(url.charAt(ind))) {
                        num.insert(0, url.charAt(ind));
                        ind--;
                    }

                    ID = num.toString();
                }

                if (m.getImage() != null && !isMediaPresent(ID)) {
                    insertMedia(postID, ID, m.getImage());
                }
            }
        }
    }

    private void insertMedia(String postID, String mediaID, StoryAttachment.Image at) {
        String insertMedia = "INSERT INTO %s.%s (media_id, post_id, media) VALUES(?, ?, ?);";

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(insertMedia, schema, mediaTable));
            stmt.setString(1, mediaID);
            stmt.setString(2, postID);
            InputStream in = new URL(at.getSrc()).openStream();
            stmt.setBinaryStream(3, in);
            stmt.executeUpdate();
            stmt.close();
            con.close();
        } catch (SQLException | IOException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    boolean isMediaPresent(String mediaID) {
        String searchQuery = "SELECT post_id FROM %s.%s WHERE %s.media_id = ?;";
        boolean found = false;

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(searchQuery, schema, mediaTable, mediaTable));
            stmt.setString(1, mediaID);
            
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                found = true;
            }
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }

        return found;
    }

    void insertInstaPost(IgMedia post) {
        String insertPost = "INSERT INTO %s.%s (timeline_id, caption, media, post_date) VALUES(?, ?, ?, ?);";

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(insertPost, schema, instaTable));
            stmt.setString(1, post.getId());
            stmt.setString(2, post.getCaption());
            
            if (post.getMediaUrl() != null) {
                InputStream data = new URL(post.getMediaUrl()).openStream();
                stmt.setBinaryStream(3, data);

                ZoneId defaultZone = ZoneId.systemDefault();
                LocalDate postDate = post.getTimestamp().toInstant().atZone(defaultZone).toLocalDate();
                stmt.setDate(4, Date.valueOf(postDate));

                //only if post is not present try to update
                if (!isInstaPostPresent(post.getId())) {
                    stmt.executeUpdate();
                }
            }

            stmt.close();
            con.close();
        } catch (SQLException | IOException e) {
            logger.info(e.getMessage() + "\n");
        }
    }

    boolean isInstaPostPresent(String ID) {
        String searchQuery = "SELECT timeline_id FROM %s.%s WHERE %s.timeline_id = ?;";
        boolean found = false;

        try {
            Connection con = DriverManager.getConnection(dbURL, user, pass);
            PreparedStatement stmt = con.prepareStatement(String.format(searchQuery, schema, instaTable, instaTable));
            stmt.setString(1, ID);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                found = true;
            }
            rs.close();
            stmt.close();
            con.close();
        } catch (SQLException e) {
            logger.info(e.getMessage() + "\n");
        }

        return found;
    }

    void setLogger(Logger logger) {
        this.logger = logger;
    }
}