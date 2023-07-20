package extractor;

import com.restfb.FacebookClient;
import com.restfb.DefaultFacebookClient;
import com.restfb.Version;
import com.restfb.Connection;
import com.restfb.Parameter;
import com.restfb.types.instagram.IgUser;
import com.restfb.types.instagram.IgMedia;

import java.util.List;

class InstaThread extends Log implements Runnable {
    private IgUser user;
    private FacebookClient client;
    private Database db;

    InstaThread(String token, IgUser user) {
        //set up page client and database
        client = new DefaultFacebookClient(token, Version.LATEST);
        db = new Database();
        setLogger(user.getUsername());
        db.setLogger(getLogger());
        db.setTableNames(user.getUsername(), true);
        db.createInstaTable();
        this.user = user;
    }

    @Override
    public void run() {
        //create execution path for going through media of user's feed and uploading it to database
        Connection<IgMedia> timeline = client.fetchConnection(user.getId() + "/media", IgMedia.class, Parameter.withFields("id,media_url,caption,timestamp"));

        for (List<IgMedia> mediaList : timeline) {
            for (IgMedia media : mediaList) {
                db.insertInstaPost(media);
            }
        }
    }
}
