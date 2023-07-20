package extractor;

import com.restfb.FacebookClient;
import com.restfb.DefaultFacebookClient;
import com.restfb.Connection;
import com.restfb.Version;
import com.restfb.Parameter;
import com.restfb.types.Post;
import com.restfb.types.StoryAttachment;

import java.util.HashMap;
import java.util.List;

class PageThread extends Log implements Runnable {
    private Database db;
    private FacebookClient fc;
    private HashMap<String, Integer> map;

    PageThread(String pageName, String token) {
        db = new Database();
        setLogger(pageName);
        db.setLogger(getLogger());
        db.setTableNames(pageName, false);
        map = db.getIDMediaCounts();
        fc = new DefaultFacebookClient(token, Version.LATEST);
    }

    @Override
    public void run() {
        //implement making a facebook connection and going through the contents of each page
        String fields = "id,parent_id,attachments{media,description,target,subattachments.limit(100)},message,created_time";
        Connection<Post> posts = fc.fetchConnection("me/posts", Post.class, Parameter.withFields(fields));

        for (List<Post> lp : posts) {
            for (Post p : lp) {
                //checking if ID is already in database map
                boolean logged = map.containsKey(p.getId());

                //try inserting post from parent ID if possible
                if (p.getParentId() != null) {
                    Post parent = fc.fetchObject(p.getParentId(), Post.class, Parameter.withFields(fields));

                    db.insertPost(p, parent);
                } else if (!logged) {
                    db.insertPost(p, null);
                }
                
                if (p.getAttachments() != null) {
                    int count = countAttachments(p.getAttachments());

                    Integer imageCount = map.get(p.getId());
                    if (imageCount == null || imageCount != count) {
                        db.iterateMedia(p.getId(), p.getAttachments());
                    }
                }
            }
        }
    }

    //traverses attachments of posts and returns number of attachments
    private int countAttachments(Post.Attachments at) {
        int count = 0;

        for (StoryAttachment st : at.getData()) {
            if (st.getSubAttachments() != null) {
                List<StoryAttachment> list = st.getSubAttachments().getData();
                count += list.size();
            } else {
                count++;
            }
        }

        return count;
    }
}