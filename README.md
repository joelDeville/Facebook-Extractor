This application is designed to crawl a Facebook account and the pages it manages and send the respective content to a database for storage.
In addition, if any pages have a linked Instagram business account, the algorithm will also crawl through the feed and store any posts.

Features and technologies used:
- multithreaded (single thread handles each account/page)
- Instagram access (accesses and stores content from linked Instagram accounts)
- Gradle (build automation for restfb and connector for JDBC connection)
- RestFB (API which makes Graph API calls for content from Facebook)
- SQL (sends queries to database)
- Error logging (Each thread has its own logger for error handling)
- Output file (Each thread's log gets combined into a single log file)

In its current state, the application requires the end user to create a Facebook Developer app of any kind.
This is needed because the App ID and App Secret are used to obtain long-life access tokens for the program.
Note: run this application with the browser still open to ensure a new long-life token is retrieved.

Also, the user will need access to a database, but there are free options online or the user could make a local database.

Details to manually fill in:
- database username (in Database.java)
- database password (in Database.java)
- database URL (in Database.java)
- database schema (in Database.java)
- App ID (in Requester.java)
- App Secret: (in Requester.java)
- Access Token (in token.txt)

Permissions to be set for required functionality in Facebook Developer application:
- user_posts (to access posts of the current account)
- pages_show_list (view pages)
- business_management (see pages marked for business)
- instagram_basic (view content of linked instagram accounts on pages)
- pages_read_engagement (read content on pages)
- public_profile (access user information, default permission)

Run '.\gradlew build' to create a fat jar file of this application.

Run '.\gradlew run' to begin executing the algorithm (make sure all fields/files are filled).