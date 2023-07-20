package extractor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.types.Account;
import com.restfb.types.User;
import com.restfb.types.instagram.IgUser;

class Requester extends Log {
    private FacebookClient fc;
    private String appId;
    private String appSecret;
    private String token;

    void makeAPIRequests() {
        appId = "<your app ID>";
        appSecret = "<your app secret>";
        setClient();

        if (fc == null) {
            System.err.println("Unable to initialize client");
            System.exit(1);
        }

        Connection<Account> con = fc.fetchConnection("me/accounts", Account.class, Parameter.withFields("name,access_token,instagram_business_account{username}"));
        HashMap<String, String> accounts = new HashMap<>();
        ArrayList<Thread> threads = new ArrayList<>();

        for (List<Account> accs : con) {
            for (Account a : accs) {
                //if instagram business account present, put a thread onto logging it
                IgUser business = a.getInstagramBusinessAccount();
                if (business != null) {
                    InstaThread userThread = new InstaThread(a.getAccessToken(), business);
                    Thread t = new Thread(userThread);
                    t.start();
                    threads.add(t);
                }
                accounts.put(a.getName(), a.getAccessToken());
            }
        }

        User main = fc.fetchObject("me", User.class, Parameter.withFields("name"));
        accounts.put(main.getName(), token);
        Database db = new Database();
        setLogger(Thread.currentThread().getName());
        db.setLogger(getLogger());
        db.createSchema();
        db.createPostTables(accounts.keySet());
        db.createMediaTables(accounts.keySet());

        //starting multithreaded extraction (one thread per page)
        for (Map.Entry<String, String> acc : accounts.entrySet()) {
            PageThread page = new PageThread(acc.getKey(), acc.getValue());
            Thread th = new Thread(page);
            threads.add(th);
            th.start();
        }

        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                System.err.println(e);
            }
        }
    }

    private void setClient() {
        try {
            Scanner sc = new Scanner(new File("token.txt"));
            token = sc.nextLine();
            fc = new DefaultFacebookClient(token, Version.LATEST);
            sc.close();

            FileWriter fw = new FileWriter("token.txt");
            String newToken = fc.obtainExtendedAccessToken(appId, appSecret).getAccessToken();
            token = newToken;
            fc = new DefaultFacebookClient(token, Version.LATEST);
            fw.write(newToken);
            fw.flush();
            fw.close();
        } catch (IOException e) {
            System.err.println(e);
        }
    }
}
