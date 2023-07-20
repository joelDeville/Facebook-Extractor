package extractor;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.io.File;
import java.io.IOException;

class Log {
    private Logger logger;
    
    protected void setLogger(String name) {
        //set up loggeer into temporary output directory
        File dir = new File("output");
        dir.mkdir();
        logger = Logger.getLogger(name);

        //ensuring log output only goes to file and nowhere else
        try {
            String fileName = String.format("%s/%s.txt", dir.getName(), name);
            FileHandler handler = new FileHandler(fileName);
            handler.setFormatter(new SimpleFormatter());
            logger.setUseParentHandlers(false);
            logger.addHandler(handler);
        } catch (IOException e) {
            System.err.println(e);
        }
    }

    protected Logger getLogger() {
        return logger;
    }
}
