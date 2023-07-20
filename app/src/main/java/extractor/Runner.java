package extractor;

import java.util.Scanner;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;

public class Runner extends Log {
    public static void main(String[] args) {
        //start timing process and begin making requests
        long start = System.currentTimeMillis();
        Requester api = new Requester();
        api.makeAPIRequests();
        long end = System.currentTimeMillis();
        System.out.printf("Took %.2f minutes\n", (end - start) / 1000.0 / 60);
        
        try {
            combineFiles();
        } catch (IOException e) {
            System.err.println("Error combining thread log files");
        }
    }

    //combine thread log files into single log
    private static void combineFiles() throws IOException {
        File dir = new File("./output");
        String finalLog = String.format("%s log.txt", LocalDate.now().toString());
        FileWriter fw = new FileWriter(finalLog);
        dir.deleteOnExit();

        for (File f : dir.listFiles()) {
            if (f.getName().endsWith("txt") || f.getName().endsWith(".lck")) {
                Scanner sc = new Scanner(f);

                while (sc.hasNextLine()) {
                    fw.write(sc.nextLine() + "\n");
                }
                sc.close();
                f.deleteOnExit();
            }
        }

        fw.flush();
        fw.close();
    }
}